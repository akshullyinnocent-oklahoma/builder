package com.example.builder

import android.content.Context
import android.util.Log
import java.io.File

class ProjectBuilderEngine(private val context: Context) {

    private val TAG = "ProjectBuilderEngine"

    // Resolves the absolute project directory
    fun getProjectDir(projectId: Int): File {
        val dir = File(context.filesDir, "projects/$projectId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // Resolves a relative path to a safe absolute file path inside the project
    private fun resolveSafeFile(projectId: Int, relativePath: String): File? {
        val baseDir = getProjectDir(projectId).canonicalFile
        val targetFile = File(baseDir, relativePath).canonicalFile
        // Prevent directory traversal attacks
        return if (targetFile.path.startsWith(baseDir.path)) {
            targetFile
        } else {
            Log.e(TAG, "Directory traversal attempt detected: $relativePath")
            null
        }
    }

    // Initializes a complete skeleton Android application with GitHub actions configured
    fun initializeProject(projectId: Int, appName: String) {
        val baseDir = getProjectDir(projectId)
        Log.d(TAG, "Initializing project $projectId at ${baseDir.absolutePath}")

        // 1. Create directory structure
        val appSrcDir = File(baseDir, "app/src/main/java/com/example/createdapp/ui/theme")
        appSrcDir.mkdirs()
        File(baseDir, "app/src/main/res/values").mkdirs()
        File(baseDir, ".github/workflows").mkdirs()
        File(baseDir, "gradle/wrapper").mkdirs()

        // 2. Write settings.gradle.kts
        writeFile(projectId, "settings.gradle.kts", """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "$appName"
            include(":app")
        """.trimIndent())

        // 3. Write project-level build.gradle.kts
        writeFile(projectId, "build.gradle.kts", """
            plugins {
                id("com.android.application") version "8.1.1" apply false
                id("org.jetbrains.kotlin.android") version "1.8.10" apply false
            }
        """.trimIndent())

        // 4. Write app/build.gradle.kts
        writeFile(projectId, "app/build.gradle.kts", """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }
            android {
                namespace = "com.example.createdapp"
                compileSdk = 33
                defaultConfig {
                    applicationId = "com.example.createdapp"
                    minSdk = 24
                    targetSdk = 33
                    versionCode = 1
                    versionName = "1.0"
                }
                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
                buildFeatures {
                    compose = true
                }
                composeOptions {
                    kotlinCompilerExtensionVersion = "1.4.3"
                }
            }
            dependencies {
                implementation("androidx.core:core-ktx:1.10.1")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
                implementation("androidx.activity:activity-compose:1.7.2")
                implementation(platform("androidx.compose:compose-bom:2023.05.01"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-graphics")
                implementation("androidx.compose.material3:material3")
            }
        """.trimIndent())

        // 5. Write AndroidManifest.xml
        writeFile(projectId, "app/src/main/AndroidManifest.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:theme="@android:style/Theme.Material.NoActionBar">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent())

        // 6. Write MainActivity.kt
        writeFile(projectId, "app/src/main/java/com/example/createdapp/MainActivity.kt", """
            package com.example.createdapp

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp
            import com.example.createdapp.ui.theme.CreatedAppTheme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        CreatedAppTheme {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                Greeting("$appName")
                            }
                        }
                    }
                }
            }

            @Composable
            fun Greeting(name: String) {
                Text(text = "Welcome to ${'$'}name!", modifier = Modifier.padding(16.dp))
            }
        """.trimIndent())

        // 7. Write CreatedAppTheme.kt
        writeFile(projectId, "app/src/main/java/com/example/createdapp/ui/theme/Theme.kt", """
            package com.example.createdapp.ui.theme

            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.runtime.Composable

            private val DarkColorScheme = darkColorScheme()

            @Composable
            fun CreatedAppTheme(content: @Composable () -> Unit) {
                MaterialTheme(
                    colorScheme = DarkColorScheme,
                    content = content
                )
            }
        """.trimIndent())

        // 8. Write strings.xml
        writeFile(projectId, "app/src/main/res/values/strings.xml", """
            <resources>
                <string name="app_name">$appName</string>
            </resources>
        """.trimIndent())

        // 9. Write .github/workflows/build_apk.yml
        writeFile(projectId, ".github/workflows/build_apk.yml", """
            name: Build APK

            on:
              push:
                branches: [ "main", "master" ]
              pull_request:
                branches: [ "main", "master" ]

            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                - uses: actions/checkout@v4
                - name: Set up JDK 17
                  uses: actions/setup-java@v4
                  with:
                    java-version: '17'
                    distribution: 'temurin'
                    cache: gradle

                - name: Grant execute permission for gradlew
                  run: chmod +x gradlew || true

                - name: Build with Gradle
                  run: ./gradlew assembleDebug
        """.trimIndent())

        // 10. Write gradle-wrapper.properties
        writeFile(projectId, "gradle/wrapper/gradle-wrapper.properties", """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.0-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent())

        // 11. Write gradlew
        writeFile(projectId, "gradlew", """
            #!/usr/bin/env sh
            # Simple fallback gradlew shell script
            exec gradle "${'$'}@"
        """.trimIndent())

        // 12. Write gradlew.bat
        writeFile(projectId, "gradlew.bat", """
            @rem Simple fallback gradlew.bat
            @gradle %*
        """.trimIndent())
    }

    // Helper to write a file inside the project directory
    fun writeFile(projectId: Int, relativePath: String, content: String): Boolean {
        val file = resolveSafeFile(projectId, relativePath) ?: return false
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            Log.d(TAG, "File written successfully: $relativePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file: $relativePath", e)
            false
        }
    }

    // Helper to read a file inside the project directory
    fun readFile(projectId: Int, relativePath: String): String? {
        val file = resolveSafeFile(projectId, relativePath) ?: return null
        return try {
            if (file.exists() && file.isFile) file.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: $relativePath", e)
            null
        }
    }

    // Helper to delete a file inside the project directory
    fun deleteFile(projectId: Int, relativePath: String): Boolean {
        val file = resolveSafeFile(projectId, relativePath) ?: return false
        return try {
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $relativePath", e)
            false
        }
    }

    // Helper to list files inside a directory
    fun listDir(projectId: Int, relativePath: String): List<String>? {
        val dir = resolveSafeFile(projectId, relativePath) ?: return null
        return try {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.map { file ->
                    val rel = file.relativeTo(getProjectDir(projectId)).path
                    if (file.isDirectory) "$rel/" else rel
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list dir: $relativePath", e)
            null
        }
    }

    // Walks the entire project directory and lists all relative files
    fun listAllProjectFiles(projectId: Int): List<String> {
        val baseDir = getProjectDir(projectId)
        val filesList = mutableListOf<String>()
        baseDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                filesList.add(file.relativeTo(baseDir).path)
            }
        }
        return filesList
    }

    // Executes tool commands parsed from LLM responses and returns corresponding XML feedback blocks
    fun executeParsedTools(projectId: Int, llmResponse: String): List<ToolExecutionResult> {
        val results = mutableListOf<ToolExecutionResult>()

        // 1. Parse create_file
        val createFileRegex = Regex("""<create_file\s+path="([^"]+)">([\s\S]*?)<\/create_file>""")
        createFileRegex.findAll(llmResponse).forEach { match ->
            val path = match.groupValues[1]
            val content = match.groupValues[2]
            val success = writeFile(projectId, path, content)
            results.add(ToolExecutionResult(
                toolName = "create_file",
                arguments = "path=$path",
                success = success,
                output = if (success) "Successfully created and written to $path." else "Failed to create file $path."
            ))
        }

        // 2. Parse edit_file
        val editFileRegex = Regex("""<edit_file\s+path="([^"]+)">\s*<target><!\[CDATA\[([\s\S]*?)\]\]><\/target>\s*<replacement><!\[CDATA\[([\s\S]*?)\]\]><\/replacement>\s*<\/edit_file>""")
        editFileRegex.findAll(llmResponse).forEach { match ->
            val path = match.groupValues[1]
            val target = match.groupValues[2]
            val replacement = match.groupValues[3]

            val fileContent = readFile(projectId, path)
            if (fileContent != null) {
                if (fileContent.contains(target)) {
                    val updatedContent = fileContent.replace(target, replacement)
                    val success = writeFile(projectId, path, updatedContent)
                    results.add(ToolExecutionResult(
                        toolName = "edit_file",
                        arguments = "path=$path",
                        success = success,
                        output = if (success) "Successfully applied changes to $path." else "Failed to write updated changes to $path."
                    ))
                } else {
                    results.add(ToolExecutionResult(
                        toolName = "edit_file",
                        arguments = "path=$path",
                        success = false,
                        output = "Error: Target content was not found in $path. Ensure target matches the file's text exactly."
                    ))
                }
            } else {
                results.add(ToolExecutionResult(
                    toolName = "edit_file",
                    arguments = "path=$path",
                    success = false,
                    output = "Error: File $path does not exist."
                ))
            }
        }

        // 3. Parse view_file
        val viewFileRegex = Regex("""<view_file\s+path="([^"]+)"\s*\/?>""")
        viewFileRegex.findAll(llmResponse).forEach { match ->
            val path = match.groupValues[1]
            val content = readFile(projectId, path)
            results.add(ToolExecutionResult(
                toolName = "view_file",
                arguments = "path=$path",
                success = content != null,
                output = content ?: "Error: File $path does not exist or is unreadable."
            ))
        }

        // 4. Parse list_dir
        val listDirRegex = Regex("""<list_dir\s+path="([^"]+)"\s*\/?>""")
        listDirRegex.findAll(llmResponse).forEach { match ->
            val path = match.groupValues[1]
            val contents = listDir(projectId, path)
            results.add(ToolExecutionResult(
                toolName = "list_dir",
                arguments = "path=$path",
                success = contents != null,
                output = contents?.joinToString("\n") ?: "Error: Directory $path does not exist."
            ))
        }

        // 5. Parse delete_file
        val deleteFileRegex = Regex("""<delete_file\s+path="([^"]+)"\s*\/?>""")
        deleteFileRegex.findAll(llmResponse).forEach { match ->
            val path = match.groupValues[1]
            val success = deleteFile(projectId, path)
            results.add(ToolExecutionResult(
                toolName = "delete_file",
                arguments = "path=$path",
                success = success,
                output = if (success) "Successfully deleted $path." else "Failed to delete $path."
            ))
        }

        return results
    }

    data class ToolExecutionResult(
        val toolName: String,
        val arguments: String,
        val success: Boolean,
        val output: String
    )
}
