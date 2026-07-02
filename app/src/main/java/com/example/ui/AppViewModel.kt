package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.builder.GitHubSyncService
import com.example.builder.LLMService
import com.example.builder.ProjectBuilderEngine
import com.example.data.database.AppDatabase
import com.example.data.model.ApiKey
import com.example.data.model.ChatMessage
import com.example.data.model.Project
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AppViewModel"

    private val database = AppDatabase.getDatabase(application)
    private val repository = AppRepository(
        database.projectDao(),
        database.apiKeyDao(),
        database.chatMessageDao()
    )

    private val builderEngine = ProjectBuilderEngine(application)
    private val llmService = LLMService()
    private val gitHubService = GitHubSyncService()

    // Database UI States
    val projects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiKeys: StateFlow<List<ApiKey>> = repository.allApiKeysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Selection States
    private val _selectedProject = MutableStateFlow<Project?>(null)
    val selectedProject: StateFlow<Project?> = _selectedProject.asStateFlow()

    private val _selectedProvider = MutableStateFlow("google")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoadingAI = MutableStateFlow(false)
    val isLoadingAI: StateFlow<Boolean> = _isLoadingAI.asStateFlow()

    // File Explorer States
    private val _projectFiles = MutableStateFlow<List<String>>(emptyList())
    val projectFiles: StateFlow<List<String>> = _projectFiles.asStateFlow()

    private val _selectedFileContent = MutableStateFlow<Pair<String, String>?>(null) // Pair(relativePath, content)
    val selectedFileContent: StateFlow<Pair<String, String>?> = _selectedFileContent.asStateFlow()

    // GitHub Push States
    private val _githubPushProgress = MutableStateFlow<String?>(null)
    val githubPushProgress: StateFlow<String?> = _githubPushProgress.asStateFlow()

    private val _githubPushSuccessUrl = MutableStateFlow<String?>(null)
    val githubPushSuccessUrl: StateFlow<String?> = _githubPushSuccessUrl.asStateFlow()

    private val _githubPushError = MutableStateFlow<String?>(null)
    val githubPushError: StateFlow<String?> = _githubPushError.asStateFlow()

    init {
        // Collect messages whenever selected project changes
        viewModelScope.launch {
            selectedProject.collect { project ->
                if (project != null) {
                    repository.getMessagesForProject(project.id).collect { messages ->
                        _chatMessages.value = messages
                    }
                } else {
                    _chatMessages.value = emptyList()
                }
            }
        }
    }

    // Set active workspace
    fun selectProject(project: Project?) {
        _selectedProject.value = project
        if (project != null) {
            refreshProjectFiles()
        } else {
            _projectFiles.value = emptyList()
            _selectedFileContent.value = null
        }
    }

    // Create a new project workspace
    fun createNewProject(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = Project(name = name, description = description)
            val id = repository.createProject(project)
            // Initialize local files
            builderEngine.initializeProject(id.toInt(), name)
            
            withContext(Dispatchers.Main) {
                val createdProject = project.copy(id = id.toInt())
                selectProject(createdProject)
            }
        }
    }

    // Refresh Code Explorer list
    fun refreshProjectFiles() {
        val project = selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val files = builderEngine.listAllProjectFiles(project.id)
            _projectFiles.value = files
        }
    }

    // Read a file's content into the workspace viewer
    fun loadFileContent(relativePath: String) {
        val project = selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val content = builderEngine.readFile(project.id, relativePath)
            if (content != null) {
                _selectedFileContent.value = Pair(relativePath, content)
            }
        }
    }

    // Update active file manually in editor
    fun saveFileContent(relativePath: String, content: String) {
        val project = selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val success = builderEngine.writeFile(project.id, relativePath, content)
            if (success) {
                _selectedFileContent.value = Pair(relativePath, content)
                refreshProjectFiles()
            }
        }
    }

    fun closeFileViewer() {
        _selectedFileContent.value = null
    }

    // API Key Management
    fun selectProvider(providerId: String) {
        _selectedProvider.value = providerId
    }

    fun saveProviderKey(providerId: String, apiKey: String, baseUrl: String?, modelName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val keyObj = ApiKey(
                providerId = providerId,
                apiKey = apiKey,
                baseUrl = baseUrl,
                selectedModel = modelName
            )
            repository.saveApiKey(keyObj)
        }
    }

    fun deleteProviderKey(providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteApiKeyByProvider(providerId)
        }
    }

    // Delete a project and its physical files
    fun deleteProject(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProject(project)
            repository.deleteMessagesForProject(project.id)
            val dir = builderEngine.getProjectDir(project.id)
            dir.deleteRecursively()
            withContext(Dispatchers.Main) {
                if (selectedProject.value?.id == project.id) {
                    selectProject(null)
                }
            }
        }
    }

    // Submit user message and trigger local builder LLM loop
    fun sendMessage(userText: String) {
        val project = selectedProject.value ?: return
        if (userText.trim().isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Save user message to DB
            val userMsg = ChatMessage(projectId = project.id, role = "user", content = userText)
            repository.insertMessage(userMsg)

            // 2. Fetch active API Key for selected provider
            val provider = selectedProvider.value
            val apiKeyConfig = repository.getApiKeyByProvider(provider)
            if (apiKeyConfig == null || apiKeyConfig.apiKey.trim().isEmpty()) {
                val errorMsg = ChatMessage(
                    projectId = project.id,
                    role = "system",
                    content = "Error: No API key configured for provider '$provider'. Please configure your API key in settings first."
                )
                repository.insertMessage(errorMsg)
                return@launch
            }

            withContext(Dispatchers.Main) {
                _isLoadingAI.value = true
            }

            // 3. Assemble complete conversation context
            val history = _chatMessages.value.toMutableList()
            // Limit history to last 15 messages to prevent context blowup in this mobile builder
            val limitedHistory = if (history.size > 15) history.takeLast(15) else history

            // 4. Construct complete, system prompt informing LLM of builder tools
            val systemPrompt = """
                You are an expert Android AI Coding Agent, mimicking Google AI Studio's agentic app builder.
                Your job is to help the user build, modify, and polish their native Android project in this workspace.
                The project's local directory contains a real, functioning Android app codebase (Jetpack Compose, Kotlin, Gradle Kotlin DSL).

                You can inspect files, create files, edit files, and list directory structures directly by outputting tool calls in your response.
                To call a tool, you MUST use the exact XML format described below. You can output multiple tool calls in a single response.

                Available Tools:

                1. Create a File:
                <create_file path="relative/path/to/file.kt">
                file content here
                </create_file>

                2. Edit a File (Surgical Replacement):
                Specify the precise target block to match, and the replacement block. The target content must match characters exactly.
                <edit_file path="relative/path/to/file.kt">
                <target><![CDATA[
                exact block to replace
                ]]></target>
                <replacement><![CDATA[
                new code block
                ]]></replacement>
                </edit_file>

                3. View a File:
                <view_file path="relative/path/to/file.kt"/>

                4. List Directory Contents:
                <list_dir path="relative/path/to/directory"/>

                5. Delete a File:
                <delete_file path="relative/path/to/file.kt"/>

                Rules:
                - Do not under any circumstances create Mock or Simulated functionality. Keep things fully functional.
                - All file paths should be relative to the project root.
                - When writing code, write clean, complete, professional Kotlin files with proper imports.
                - Avoid verbose commentary. Describe your changes clearly but concisely.
                - After editing files, explain what was added and how it works.
            """.trimIndent()

            // 5. Call LLM
            llmService.callLLM(apiKeyConfig, systemPrompt, limitedHistory, object : LLMService.Callback {
                override fun onSuccess(response: String) {
                    viewModelScope.launch(Dispatchers.IO) {
                        // Save model response text
                        val modelMsg = ChatMessage(projectId = project.id, role = "model", content = response)
                        repository.insertMessage(modelMsg)

                        // 6. Execute parsed tool calls on local filesystem!
                        val toolResults = builderEngine.executeParsedTools(project.id, response)
                        if (toolResults.isNotEmpty()) {
                            // Compile tool execution feedback
                            val feedbackBuilder = StringBuilder()
                            feedbackBuilder.append("🔧 **Tool Executions Log:**\n")
                            toolResults.forEach { res ->
                                feedbackBuilder.append("- **${res.toolName}** (${res.arguments}): ${if (res.success) "✅ Success" else "❌ Failed"}\n")
                                if (!res.success) {
                                    feedbackBuilder.append("  *Error: ${res.output}*\n")
                                }
                            }

                            val toolFeedbackMsg = ChatMessage(
                                projectId = project.id,
                                role = "system",
                                content = feedbackBuilder.toString(),
                                isToolCall = true,
                                toolName = toolResults.joinToString(", ") { it.toolName },
                                toolResult = toolResults.joinToString("\n---\n") { "${it.toolName} output:\n${it.output}" }
                            )
                            repository.insertMessage(toolFeedbackMsg)
                            
                            // Let's refresh files list in UI
                            refreshProjectFiles()
                        }

                        // Set project updated time
                        val updatedProject = project.copy(updatedAt = System.currentTimeMillis())
                        repository.updateProject(updatedProject)
                        withContext(Dispatchers.Main) {
                            _selectedProject.value = updatedProject
                            _isLoadingAI.value = false
                        }
                    }
                }

                override fun onError(error: String) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val errorMsg = ChatMessage(
                            projectId = project.id,
                            role = "system",
                            content = "❌ **Error from LLM API:**\n$error"
                        )
                        repository.insertMessage(errorMsg)
                        withContext(Dispatchers.Main) {
                            _isLoadingAI.value = false
                        }
                    }
                }
            })
        }
    }

    // Push Workspace to GitHub
    fun pushToGitHub(token: String, repoOwner: String, repoName: String) {
        val project = selectedProject.value ?: return
        if (token.trim().isEmpty() || repoOwner.trim().isEmpty() || repoName.trim().isEmpty()) {
            _githubPushError.value = "All fields (Token, Owner, Repository Name) are required."
            return
        }

        _githubPushProgress.value = "Preparing workspace push..."
        _githubPushSuccessUrl.value = null
        _githubPushError.value = null

        val baseDir = builderEngine.getProjectDir(project.id)

        gitHubService.pushProjectToGitHub(
            token = token,
            repoOwner = repoOwner,
            repoName = repoName,
            projectDir = baseDir,
            callback = object : GitHubSyncService.ProgressCallback {
                override fun onProgress(step: String, percentage: Float) {
                    _githubPushProgress.value = step
                }

                override fun onSuccess(repoUrl: String) {
                    _githubPushProgress.value = null
                    _githubPushSuccessUrl.value = repoUrl
                    // Save PAT details to project in Room
                    viewModelScope.launch(Dispatchers.IO) {
                        val updatedProject = project.copy(
                            githubRepo = "$repoOwner/$repoName",
                            githubToken = token
                        )
                        repository.updateProject(updatedProject)
                        withContext(Dispatchers.Main) {
                            _selectedProject.value = updatedProject
                        }
                    }
                }

                override fun onError(error: String) {
                    _githubPushProgress.value = null
                    _githubPushError.value = error
                }
            }
        )
    }

    fun clearGithubPushState() {
        _githubPushProgress.value = null
        _githubPushSuccessUrl.value = null
        _githubPushError.value = null
    }
}
