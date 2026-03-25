package com.mindtype.mobile.data.dao

import androidx.room.*
import com.mindtype.mobile.data.entity.KeystrokeEventEntity

@Dao
interface KeystrokeEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: KeystrokeEventEntity)

    @Query("SELECT COUNT(*) FROM keystroke_events WHERE session_id = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM keystroke_events WHERE timestamp >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("DELETE FROM keystroke_events WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
