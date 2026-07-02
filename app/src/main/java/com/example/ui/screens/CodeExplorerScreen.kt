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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeExplorerScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val projectFiles by viewModel.projectFiles.collectAsState()
    val selectedFileContent by viewModel.selectedFileContent.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()

    var activeContent by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }

    // Synchronize editor buffer when active file content changes
    LaunchedEffect(selectedFileContent) {
        activeContent = selectedFileContent?.second ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedProject?.name ?: "Workspace",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Project Code Explorer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("code_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshProjectFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh files list")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (selectedFileContent != null) {
                // Code editor screen
                val relativePath = selectedFileContent!!.first
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Editor header details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = relativePath.substringAfterLast("/"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { viewModel.closeFileViewer() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close file editor")
                            }
                            IconButton(
                                onClick = {
                                    viewModel.saveFileContent(relativePath, activeContent)
                                },
                                modifier = Modifier.testTag("save_file_button")
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = "Save changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        text = relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Text editor canvas
                    OutlinedTextField(
                        value = activeContent,
                        onValueChange = { activeContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("code_text_editor"),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            } else {
                // Main File explorer list
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Search box
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = { Text("Filter files...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("file_filter_input"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchInput.isNotEmpty()) {
                                IconButton(onClick = { searchInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true
                    )

                    val filteredFiles = projectFiles.filter {
                        it.contains(searchInput, ignoreCase = true)
                    }

                    if (filteredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No files found",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredFiles) { path ->
                                FileItemCard(
                                    relativePath = path,
                                    onClick = { viewModel.loadFileContent(path) }
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
fun FileItemCard(
    relativePath: String,
    onClick: () -> Unit
) {
    val fileName = relativePath.substringAfterLast("/")
    val parentPath = relativePath.substringBeforeLast("/", "")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val extension = fileName.substringAfterLast(".", "")
            val fileIcon = when (extension) {
                "kt", "java" -> Icons.Default.Code
                "xml" -> Icons.Default.Source
                "yml", "yaml" -> Icons.Default.Build
                "properties", "pro" -> Icons.Default.Settings
                "gradle", "kts" -> Icons.Default.Engineering
                else -> Icons.Default.Description
            }

            Icon(
                imageVector = fileIcon,
                contentDescription = null,
                tint = when (extension) {
                    "kt", "java" -> MaterialTheme.colorScheme.primary
                    "xml" -> MaterialTheme.colorScheme.secondary
                    "gradle", "kts" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (parentPath.isNotEmpty()) {
                    Text(
                        text = parentPath,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
