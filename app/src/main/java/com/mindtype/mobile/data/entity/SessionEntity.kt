package com.mindtype.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["user_id"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id") val sessionId: String,   // UUID
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "start_time") val startTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "end_time") val endTime: Long? = null
)
