package com.extensionbox.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: ModuleDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<ModuleDataEntity>)

    @Query("SELECT * FROM module_data WHERE moduleKey = :key ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(key: String): ModuleDataEntity?

    @Query("SELECT * FROM module_data WHERE moduleKey = :key AND timestamp >= :after ORDER BY timestamp ASC")
    fun getHistoryFlow(key: String, after: Long): Flow<List<ModuleDataEntity>>

    @Query("SELECT * FROM module_data WHERE moduleKey = :key AND timestamp >= :after ORDER BY timestamp ASC")
    suspend fun getHistoryList(key: String, after: Long): List<ModuleDataEntity>

    @Query("DELETE FROM module_data WHERE timestamp < :before")
    suspend fun clearOldData(before: Long)
}
