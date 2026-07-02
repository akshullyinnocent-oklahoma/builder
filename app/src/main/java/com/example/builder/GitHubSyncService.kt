package com.example.builder

import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubSyncService {

    private val TAG = "GitHubSyncService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    interface ProgressCallback {
        fun onProgress(step: String, percentage: Float)
        fun onSuccess(repoUrl: String)
        fun onError(error: String)
    }

    fun pushProjectToGitHub(
        token: String,
        repoOwner: String,
        repoName: String,
        projectDir: File,
        callback: ProgressCallback
    ) {
        Thread {
            try {
                // 1. Check if repo exists or create it
                callback.onProgress("Checking repository on GitHub...", 0.1f)
                val hasRepo = checkIfRepoExists(token, repoOwner, repoName)
                if (!hasRepo) {
                    callback.onProgress("Creating repository: $repoName...", 0.2f)
                    val createSuccess = createRepository(token, repoName)
                    if (!createSuccess) {
                        callback.onError("Failed to create repository on GitHub.")
                        return@Thread
                    }
                }

                // 2. Ensure main branch exists by pushing a README if empty
                callback.onProgress("Initializing branch on GitHub...", 0.3f)
                val baseCommitSha = getLatestCommitSha(token, repoOwner, repoName)
                val currentBaseCommit = if (baseCommitSha == null) {
                    // Create initial commit with README.md to bootstrap
                    val bootstrapSuccess = createReadmePlaceholder(token, repoOwner, repoName)
                    if (!bootstrapSuccess) {
                        callback.onError("Failed to bootstrap repository with README.")
                        return@Thread
                    }
                    // Wait a moment and get SHA again
                    Thread.sleep(1500)
                    getLatestCommitSha(token, repoOwner, repoName)
                } else {
                    baseCommitSha
                }

                if (currentBaseCommit == null) {
                    callback.onError("Could not establish a base commit on main branch.")
                    return@Thread
                }

                // Get base tree SHA
                val baseTreeSha = getTreeShaForCommit(token, repoOwner, repoName, currentBaseCommit)
                if (baseTreeSha == null) {
                    callback.onError("Failed to retrieve base tree SHA.")
                    return@Thread
                }

                // 3. Find and upload all project files as blobs
                callback.onProgress("Finding project files...", 0.4f)
                val filesToUpload = mutableListOf<File>()
                projectDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        filesToUpload.add(file)
                    }
                }

                if (filesToUpload.isEmpty()) {
                    callback.onError("No files found to upload in project directory.")
                    return@Thread
                }

                val treeNodesArray = JSONArray()
                val totalFiles = filesToUpload.size
                Log.d(TAG, "Uploading $totalFiles files as Git blobs")

                filesToUpload.forEachIndexed { index, file ->
                    val relativePath = file.relativeTo(projectDir).path
                    callback.onProgress("Uploading blob ${index + 1}/$totalFiles: $relativePath", 0.4f + (0.3f * (index.toFloat() / totalFiles)))

                    val fileBytes = file.readBytes()
                    val base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

                    val blobSha = createGitBlob(token, repoOwner, repoName, base64Content)
                    if (blobSha != null) {
                        val treeNode = JSONObject()
                            .put("path", relativePath.replace("\\", "/")) // Normalize path separators for GitHub
                            .put("mode", "100644")
                            .put("type", "blob")
                            .put("sha", blobSha)
                        treeNodesArray.put(treeNode)
                    } else {
                        Log.e(TAG, "Failed to create blob for $relativePath")
                    }
                }

                if (treeNodesArray.length() == 0) {
                    callback.onError("Failed to upload any file blobs to GitHub.")
                    return@Thread
                }

                // 4. Create Git tree
                callback.onProgress("Assembling file tree on GitHub...", 0.8f)
                val newTreeSha = createGitTree(token, repoOwner, repoName, baseTreeSha, treeNodesArray)
                if (newTreeSha == null) {
                    callback.onError("Failed to create new tree on GitHub.")
                    return@Thread
                }

                // 5. Create Git commit
                callback.onProgress("Creating commit...", 0.9f)
                val newCommitSha = createGitCommit(token, repoOwner, repoName, "Sync project files via AI App Builder", newTreeSha, currentBaseCommit)
                if (newCommitSha == null) {
                    callback.onError("Failed to create commit on GitHub.")
                    return@Thread
                }

                // 6. Update reference
                callback.onProgress("Pushing changes to main branch...", 0.95f)
                val updateSuccess = updateMainReference(token, repoOwner, repoName, newCommitSha)
                if (updateSuccess) {
                    val repoUrl = "https://github.com/$repoOwner/$repoName"
                    callback.onProgress("Push completed successfully!", 1.0f)
                    callback.onSuccess(repoUrl)
                } else {
                    callback.onError("Failed to update branch reference on GitHub.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Git push failed due to exception", e)
                callback.onError("Execution error: ${e.message}")
            }
        }.start()
    }

    private fun checkIfRepoExists(token: String, owner: String, repo: String): Boolean {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun createRepository(token: String, repoName: String): Boolean {
        val json = JSONObject()
            .put("name", repoName)
            .put("description", "Created with AI App Builder")
            .put("private", false)
            .put("auto_init", false)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/user/repos")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getLatestCommitSha(token: String, owner: String, repo: String): String? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/main")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    JSONObject(bodyString).getJSONObject("object").getString("sha")
                } else {
                    // Try master branch as well
                    getLatestCommitShaMaster(token, owner, repo)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getLatestCommitShaMaster(token: String, owner: String, repo: String): String? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/master")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    JSONObject(bodyString).getJSONObject("object").getString("sha")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createReadmePlaceholder(token: String, owner: String, repo: String): Boolean {
        val readmeContent = "# Android Project\nCreated via Android AI App Builder."
        val base64Content = Base64.encodeToString(readmeContent.toByteArray(), Base64.NO_WRAP)

        val json = JSONObject()
            .put("message", "Initial commit with README")
            .put("content", base64Content)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/README.md")
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getTreeShaForCommit(token: String, owner: String, repo: String, commitSha: String): String? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/commits/$commitSha")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    JSONObject(bodyString).getJSONObject("tree").getString("sha")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createGitBlob(token: String, owner: String, repo: String, base64Content: String): String? {
        val json = JSONObject()
            .put("content", base64Content)
            .put("encoding", "base64")

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/blobs")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    JSONObject(bodyString).getString("sha")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createGitTree(token: String, owner: String, repo: String, baseTreeSha: String, treeNodes: JSONArray): String? {
        val json = JSONObject()
            .put("base_tree", baseTreeSha)
            .put("tree", treeNodes)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/trees")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    JSONObject(bodyString).getString("sha")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createGitCommit(
        token: String,
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentCommitSha: String
    ): String? {
        val json = JSONObject()
            .put("message", message)
            .put("tree", treeSha)
            .put("parents", JSONArray().put(parentCommitSha))

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/commits")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    JSONObject(bodyString).getString("sha")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateMainReference(token: String, owner: String, repo: String, commitSha: String): Boolean {
        val json = JSONObject()
            .put("sha", commitSha)
            .put("force", true)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/main")
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
