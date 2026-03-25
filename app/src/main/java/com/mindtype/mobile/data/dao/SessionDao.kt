package com.mindtype.mobile.data.dao

import androidx.room.*
import com.mindtype.mobile.data.entity.SessionEntity

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("UPDATE sessions SET end_time = :endTime WHERE session_id = :sessionId")
    suspend fun closeSession(sessionId: String, endTime: Long)

    @Query("SELECT * FROM sessions WHERE user_id = :userId ORDER BY start_time DESC")
    suspend fun getSessionsForUser(userId: String): List<SessionEntity>
}
