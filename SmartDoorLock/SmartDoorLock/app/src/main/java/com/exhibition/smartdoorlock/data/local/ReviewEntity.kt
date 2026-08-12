package com.exhibition.smartdoorlock.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitorName: String,
    val relation: String?,
    val rating: Int,
    val review: String,
    val createdAt: Long
)
