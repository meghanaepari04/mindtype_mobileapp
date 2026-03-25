package com.mindtype.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Stores keystroke timing metadata.
 * !! NEVER stores the actual character typed — only keyCode (integer) and timestamps !!
 */
@Entity(
    tableName = "keystroke_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class KeystrokeEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id") val eventId: Int = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** Android KeyEvent key code — integer only, NOT the character */
    @ColumnInfo(name = "key_code") val keyCode: Int,
    @ColumnInfo(name = "down_time") val downTime: Long,
    @ColumnInfo(name = "event_time") val eventTime: Long,
    /** event_time - down_time, in ms */
    @ColumnInfo(name = "dwell_time") val dwellTime: Float,
    /** this.down_time - prev.event_time, in ms */
    @ColumnInfo(name = "flight_time") val flightTime: Float,
    @ColumnInfo(name = "touch_pressure") val touchPressure: Float,
    @ColumnInfo(name = "touch_size") val touchSize: Float,
    /** 1 if KEYCODE_DEL, 0 otherwise */
    @ColumnInfo(name = "is_backspace") val isBackspace: Int
)
