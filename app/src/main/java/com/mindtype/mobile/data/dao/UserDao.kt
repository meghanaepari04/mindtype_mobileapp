package com.mindtype.mobile.data.dao

import androidx.room.*
import com.mindtype.mobile.data.entity.UserEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getUser(userId: String): UserEntity?
}
