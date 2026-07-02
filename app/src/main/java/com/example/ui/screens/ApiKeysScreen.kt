package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApiKey
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val apiKeys by viewModel.apiKeys.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    var keyInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    val providers = listOf(
        ProviderItem("google", "Google Gemini", "Official Google AI endpoint"),
        ProviderItem("openrouter", "OpenRouter", "Unified LLM aggregator"),
        ProviderItem("deepseek", "DeepSeek", "Official DeepSeek endpoint"),
        ProviderItem("nvidia", "Nvidia NIM", "High-performance Nvidia catalog"),
        ProviderItem("mistral", "Mistral AI", "Official Mistral endpoints"),
        ProviderItem("custom", "Custom Provider", "Any OpenAI-compatible API")
    )

    // Clear connection status when switching provider
    LaunchedEffect(selectedProvider) {
        viewModel.clearConnectionStatus()
    }

    // Sync input fields when selected provider changes
    LaunchedEffect(selectedProvider) {
        val existing = apiKeys.find { it.providerId == selectedProvider }
        if (existing != null) {
            keyInput = existing.apiKey
            urlInput = existing.baseUrl ?: ""
            modelInput = existing.selectedModel ?: ""
        } else {
            keyInput = ""
            urlInput = ""
            modelInput = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure API Keys", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("api_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card introducing BYOK
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "Bring Your Own Key",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Configure your own LLM API keys. Click 'Test & Fetch Models' to verify your connection and retrieve available models instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Provider selection row/grid
            item {
                Text(
                    text = "Select Provider",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.take(3).forEach { provider ->
                        ProviderBadge(
                            item = provider,
                            isSelected = selectedProvider == provider.id,
                            hasKey = apiKeys.any { it.providerId == provider.id },
                            onClick = { viewModel.selectProvider(provider.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.takeLast(3).forEach { provider ->
                        ProviderBadge(
                            item = provider,
                            isSelected = selectedProvider == provider.id,
                            hasKey = apiKeys.any { it.providerId == provider.id },
                            onClick = { viewModel.selectProvider(provider.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Key inputs card
            item {
                val activeProvider = providers.first { it.id == selectedProvider }
                val existing = apiKeys.find { it.providerId == selectedProvider }
                val fetchedModels = remember(existing) {
                    existing?.availableModels?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Configure ${activeProvider.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeProvider.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom base URL for custom provider
                        if (selectedProvider == "custom") {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Base URL (e.g., http://10.0.2.2:11434/v1)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_url_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // API Key input
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("API Key") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        imageVector = if (showKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Model name config (optional/manual)
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("Active Model") },
                            placeholder = { Text("Select from list or type name") },
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("model_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Fetched model list chip selector
                        if (fetchedModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Fetched Models (Tap to set active):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(fetchedModels) { modelName ->
                                    val isSelected = modelInput == modelName
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            modelInput = modelName
                                            viewModel.selectActiveModel(selectedProvider, modelName)
                                        },
                                        label = { Text(modelName, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Connection test status display
                        if (connectionStatus != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val status = connectionStatus!!
                            when {
                                status == "testing" -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Testing connection & fetching models...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                status.startsWith("success:") -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = status.removePrefix("success:"),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                status.startsWith("error:") -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = status.removePrefix("error:"),
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (existing != null) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.deleteProviderKey(selectedProvider)
                                        keyInput = ""
                                        urlInput = ""
                                        modelInput = ""
                                        viewModel.clearConnectionStatus()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("delete_key_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }

                            Button(
                                onClick = {
                                    if (keyInput.isNotEmpty()) {
                                        viewModel.testConnectionAndFetchModels(
                                            providerId = selectedProvider,
                                            apiKey = keyInput.trim(),
                                            baseUrl = if (selectedProvider == "custom") urlInput.trim() else null
                                        )
                                    }
                                },
                                enabled = keyInput.isNotEmpty() && connectionStatus != "testing",
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("test_key_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test & Fetch")
                            }

                            Button(
                                onClick = {
                                    if (keyInput.isNotEmpty()) {
                                        val finalModel = modelInput.trim()
                                        viewModel.saveProviderKey(
                                            providerId = selectedProvider,
                                            apiKey = keyInput.trim(),
                                            baseUrl = if (selectedProvider == "custom") urlInput.trim() else null,
                                            modelName = if (finalModel.isEmpty()) null else finalModel,
                                            availableModels = existing?.availableModels
                                        )
                                    }
                                },
                                enabled = keyInput.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("save_key_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Key")
                            }
                        }
                    }
                }
            }

            // Active list summarizing configured LLMs
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active LLM Integrations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (apiKeys.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No keys configured yet.",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Add at least one key above to start building.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(apiKeys) { key ->
                    val provItem = providers.find { it.id == key.providerId } ?: return@items
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provItem.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Model: ${key.selectedModel ?: "None Selected"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (key.providerId == "custom") {
                                    Text(
                                        text = "URL: ${key.baseUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteProviderKey(key.providerId) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete key",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderBadge(
    item: ProviderItem,
    isSelected: Boolean,
    hasKey: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else if (hasKey) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = if (borderColor != Color.Transparent) {
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        } else null
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when (item.id) {
                        "google" -> Icons.Default.AutoAwesome
                        "openrouter" -> Icons.Default.Hub
                        "deepseek" -> Icons.Default.Psychology
                        "nvidia" -> Icons.Default.Memory
                        "mistral" -> Icons.Default.Air
                        else -> Icons.Default.Settings
                    },
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.name.split(" ").first(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasKey) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

data class ProviderItem(
    val id: String,
    val name: String,
    val description: String
)
