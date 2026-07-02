package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_keys")
data class ApiKey(
    @PrimaryKey val providerId: String, // "google", "openrouter", "nvidia", "deepseek", "mistral", "custom"
    val apiKey: String,
    val baseUrl: String? = null,
    val selectedModel: String? = null,
    val availableModels: String? = null
)
