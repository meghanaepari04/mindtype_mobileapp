package com.mindtype.mobile.data.dao

import androidx.room.*
import com.mindtype.mobile.data.entity.StressLabelEntity

@Dao
interface StressLabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: StressLabelEntity)

    @Query("SELECT * FROM stress_labels WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getLabelsForSession(sessionId: String): List<StressLabelEntity>

    @Query("SELECT * FROM stress_labels ORDER BY timestamp ASC")
    suspend fun getAllLabels(): List<StressLabelEntity>
}
