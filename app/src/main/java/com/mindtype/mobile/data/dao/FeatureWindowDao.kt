package com.mindtype.mobile.data.dao

import androidx.room.*
import com.mindtype.mobile.data.entity.FeatureWindowEntity

@Dao
interface FeatureWindowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: FeatureWindowEntity)

    @Query("SELECT * FROM feature_windows WHERE session_id = :sessionId ORDER BY window_start ASC")
    suspend fun getWindowsForSession(sessionId: String): List<FeatureWindowEntity>

    @Query("SELECT * FROM feature_windows WHERE window_start >= :sinceMs ORDER BY window_start ASC")
    suspend fun getWindowsSince(sinceMs: Long): List<FeatureWindowEntity>

    @Query("SELECT * FROM feature_windows ORDER BY window_start DESC LIMIT 1")
    suspend fun getLatestWindow(): FeatureWindowEntity?

    @Query("SELECT * FROM feature_windows ORDER BY window_start ASC")
    suspend fun getAllWindows(): List<FeatureWindowEntity>

    @Query("UPDATE feature_windows SET stress_label = :label WHERE window_id = :windowId")
    suspend fun updateStressLabel(windowId: Int, label: String)
}
