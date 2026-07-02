package com.example.data.repository

import com.example.data.database.ApiKeyDao
import com.example.data.database.ChatMessageDao
import com.example.data.database.ProjectDao
import com.example.data.model.ApiKey
import com.example.data.model.ChatMessage
import com.example.data.model.Project
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val projectDao: ProjectDao,
    private val apiKeyDao: ApiKeyDao,
    private val chatMessageDao: ChatMessageDao
) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()
    val allApiKeysFlow: Flow<List<ApiKey>> = apiKeyDao.getAllApiKeysFlow()

    suspend fun getProjectById(id: Int): Project? = projectDao.getProjectById(id)

    suspend fun createProject(project: Project): Long = projectDao.insertProject(project)

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun getApiKeys(): List<ApiKey> = apiKeyDao.getAllApiKeys()

    suspend fun getApiKeyByProvider(providerId: String): ApiKey? = apiKeyDao.getApiKeyByProvider(providerId)

    suspend fun saveApiKey(apiKey: ApiKey) = apiKeyDao.insertApiKey(apiKey)

    suspend fun deleteApiKeyByProvider(providerId: String) = apiKeyDao.deleteApiKeyByProvider(providerId)

    fun getMessagesForProject(projectId: Int): Flow<List<ChatMessage>> = chatMessageDao.getMessagesForProject(projectId)

    suspend fun insertMessage(message: ChatMessage): Long = chatMessageDao.insertMessage(message)

    suspend fun deleteMessagesForProject(projectId: Int) = chatMessageDao.deleteMessagesForProject(projectId)
}
