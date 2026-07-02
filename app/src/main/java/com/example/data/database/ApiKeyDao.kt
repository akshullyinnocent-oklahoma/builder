package com.example.data.database

import androidx.room.*
import com.example.data.model.ApiKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys")
    fun getAllApiKeysFlow(): Flow<List<ApiKey>>

    @Query("SELECT * FROM api_keys")
    suspend fun getAllApiKeys(): List<ApiKey>

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId")
    suspend fun getApiKeyByProvider(providerId: String): ApiKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(apiKey: ApiKey)

    @Query("DELETE FROM api_keys WHERE providerId = :providerId")
    suspend fun deleteApiKeyByProvider(providerId: String)
}
