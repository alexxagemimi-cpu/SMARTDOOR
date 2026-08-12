package com.exhibition.smartdoorlock.data.repository

import com.exhibition.smartdoorlock.data.local.ReviewDao
import com.exhibition.smartdoorlock.data.local.ReviewEntity
import kotlinx.coroutines.flow.Flow

class ReviewRepository(private val dao: ReviewDao) {

    val allReviews: Flow<List<ReviewEntity>> = dao.getAllReviews()

    suspend fun addReview(visitorName: String, relation: String?, rating: Int, review: String) {
        dao.insert(
            ReviewEntity(
                visitorName = visitorName,
                relation = relation?.ifBlank { null },
                rating = rating,
                review = review,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
