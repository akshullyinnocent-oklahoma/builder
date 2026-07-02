package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubSyncScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val selectedProject by viewModel.selectedProject.collectAsState()
    val pushProgress by viewModel.githubPushProgress.collectAsState()
    val pushSuccessUrl by viewModel.githubPushSuccessUrl.collectAsState()
    val pushError by viewModel.githubPushError.collectAsState()

    var tokenInput by remember { mutableStateOf("") }
    var ownerInput by remember { mutableStateOf("") }
    var repoInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Sync input fields when project changes
    LaunchedEffect(selectedProject) {
        if (selectedProject != null) {
            tokenInput = selectedProject!!.githubToken ?: ""
            val repoParts = selectedProject!!.githubRepo?.split("/")
            if (repoParts != null && repoParts.size == 2) {
                ownerInput = repoParts[0]
                repoInput = repoParts[1]
            } else {
                ownerInput = ""
                repoInput = selectedProject!!.name.lowercase()
                    .replace(" ", "-")
                    .replace(Regex("[^a-z0-9_-]"), "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Push to GitHub", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("github_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Cloud Publish Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Export workspace directly as an open-source GitHub repository. Features automated APK build integration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Input Fields
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("GitHub Personal Access Token (PAT)") },
                placeholder = { Text("ghp_xxxxxxxxxxxxxx") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_token_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Token Visibility"
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ownerInput,
                    onValueChange = { ownerInput = it },
                    label = { Text("GitHub Owner") },
                    placeholder = { Text("username") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("github_owner_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("Repository Name") },
                    placeholder = { Text("my-built-app") },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("github_repo_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Actions panel with CI/CD build explanation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SettingsSuggest,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Automated APK Assembly (CI/CD)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Every push automatically bundles a `.github/workflows/build_apk.yml` workflow. GitHub Actions will compile your Android APK on every push with zero setup required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Submit button / state indicators
            Button(
                onClick = {
                    viewModel.pushToGitHub(tokenInput.trim(), ownerInput.trim(), repoInput.trim())
                },
                enabled = pushProgress == null && tokenInput.isNotEmpty() && ownerInput.isNotEmpty() && repoInput.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("github_push_button")
            ) {
                if (pushProgress != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Push Workspace to GitHub")
                }
            }

            // Progress steps monitor
            AnimatedVisibility(
                visible = pushProgress != null || pushSuccessUrl != null || pushError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            pushSuccessUrl != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            pushError != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sync Console Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (pushProgress != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = pushProgress!!,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (pushSuccessUrl != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Workspace pushed successfully!",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    "Your repository with configured APK build pipelines is active.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pushSuccessUrl))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Launch, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open GitHub Repository")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearGithubPushState() },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }

                        if (pushError != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Push failed",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = pushError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(
                                    onClick = { viewModel.clearGithubPushState() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
