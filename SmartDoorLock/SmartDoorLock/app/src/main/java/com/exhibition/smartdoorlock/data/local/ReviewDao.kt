package com.exhibition.smartdoorlock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Insert
    suspend fun insert(review: ReviewEntity): Long

    /** Newest first — the UI (list + "latest review" stat) both want this order. */
    @Query("SELECT * FROM reviews ORDER BY createdAt DESC")
    fun getAllReviews(): Flow<List<ReviewEntity>>
}
