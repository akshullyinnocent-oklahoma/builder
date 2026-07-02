package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Project
import com.example.ui.AppViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

enum class Screen {
    ChatBuilder,
    CodeExplorer,
    GitHubSync,
    ApiKeys
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: AppViewModel = viewModel()
                val projects by viewModel.projects.collectAsState()
                val selectedProject by viewModel.selectedProject.collectAsState()

                var currentScreen by remember { mutableStateOf(Screen.ChatBuilder) }

                // Side drawer states
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // Dialog state
                var showCreateDialog by remember { mutableStateOf(false) }
                var newProjectName by remember { mutableStateOf("") }
                var newProjectDesc by remember { mutableStateOf("") }

                // Auto-initialization flow: load directly into active chat workspace
                LaunchedEffect(projects) {
                    if (projects.isNotEmpty()) {
                        if (selectedProject == null) {
                            viewModel.selectProject(projects.first())
                        }
                    } else {
                        // Create a professional default workspace if database is empty
                        viewModel.createNewProject("Workspace 1", "Active development workspace")
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight(),
                            drawerContainerColor = MaterialTheme.colorScheme.background
                        ) {
                            // Professional Title Header (Gemini-inspired)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "AI App Builder",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    )
                                )
                            }

                            // Start a New Workspace button
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("drawer_new_workspace_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Workspace", fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "Recent Workspaces",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                            )

                            // Workspaces dynamic stream list
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(projects) { project ->
                                    val isSelected = selectedProject?.id == project.id

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.selectProject(project)
                                                scope.launch { drawerState.close() }
                                                currentScreen = Screen.ChatBuilder
                                            },
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            Color.Transparent
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChatBubbleOutline,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = project.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    if (project.description.isNotEmpty()) {
                                                        Text(
                                                            text = project.description,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            // Delete icon
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteProject(project)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete workspace",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Configuration Panel footer
                            NavigationDrawerItem(
                                label = { Text("API Keys Settings", fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                                selected = currentScreen == Screen.ApiKeys,
                                onClick = {
                                    currentScreen = Screen.ApiKeys
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (selectedProject != null) {
                                NavigationDrawerItem(
                                    label = { Text("Push to GitHub", fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                    selected = currentScreen == Screen.GitHubSync,
                                    onClick = {
                                        currentScreen = Screen.GitHubSync
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Crossfade(
                            targetState = currentScreen,
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                Screen.ChatBuilder -> {
                                    ChatBuilderScreen(
                                        viewModel = viewModel,
                                        onOpenDrawer = {
                                            scope.launch { drawerState.open() }
                                        },
                                        onNavigateToExplorer = {
                                            currentScreen = Screen.CodeExplorer
                                        },
                                        onNavigateToGithub = {
                                            currentScreen = Screen.GitHubSync
                                        }
                                    )
                                }
                                Screen.CodeExplorer -> {
                                    CodeExplorerScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            currentScreen = Screen.ChatBuilder
                                        }
                                    )
                                }
                                Screen.GitHubSync -> {
                                    GitHubSyncScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            currentScreen = Screen.ChatBuilder
                                        }
                                    )
                                }
                                Screen.ApiKeys -> {
                                    ApiKeysScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            currentScreen = Screen.ChatBuilder
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // New Project Workspace dialog
                if (showCreateDialog) {
                    AlertDialog(
                        onDismissRequest = { showCreateDialog = false },
                        title = { Text("New Workspace Details") },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = newProjectName,
                                    onValueChange = { newProjectName = it },
                                    label = { Text("Workspace Name") },
                                    placeholder = { Text("Weather App") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dialog_project_name_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newProjectDesc,
                                    onValueChange = { newProjectDesc = it },
                                    label = { Text("Workspace Description") },
                                    placeholder = { Text("Native local SQLite task manager...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dialog_project_desc_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    maxLines = 3
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (newProjectName.isNotEmpty()) {
                                        viewModel.createNewProject(newProjectName.trim(), newProjectDesc.trim())
                                        showCreateDialog = false
                                        newProjectName = ""
                                        newProjectDesc = ""
                                        currentScreen = Screen.ChatBuilder
                                        scope.launch { drawerState.close() }
                                    }
                                },
                                enabled = newProjectName.isNotEmpty(),
                                modifier = Modifier.testTag("dialog_create_button")
                            ) {
                                Text("Create Workspace")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}
