package com.exhibition.smartdoorlock.ui.rateproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exhibition.smartdoorlock.data.local.ReviewEntity
import com.exhibition.smartdoorlock.data.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewFormState(
    val visitorName: String = "",
    val relation: String = "",
    val rating: Int? = null,
    val reviewText: String = "",
    val visitorNameError: Boolean = false,
    val ratingError: Boolean = false
)

data class ReviewStats(
    val total: Int = 0,
    val average: Double = 0.0,
    val highest: Int = 0,
    val lowest: Int = 0,
    val latestVisitorName: String? = null
)

class RateProjectViewModel(private val repository: ReviewRepository) : ViewModel() {

    companion object {
        const val MAX_REVIEW_LENGTH = 500
    }

    private val _formState = MutableStateFlow(ReviewFormState())
    val formState: StateFlow<ReviewFormState> = _formState.asStateFlow()

    val reviews: StateFlow<List<ReviewEntity>> = repository.allReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Derived from `reviews` (not a second, independent Room subscription) so the
    // database is only queried once — stats simply recompute whenever the list does.
    val stats: StateFlow<ReviewStats> = reviews
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewStats())

    private fun computeStats(list: List<ReviewEntity>): ReviewStats {
        if (list.isEmpty()) return ReviewStats()
        // `reviews` is already ordered newest-first by the DAO query.
        return ReviewStats(
            total = list.size,
            average = list.map { it.rating }.average(),
            highest = list.maxOf { it.rating },
            lowest = list.minOf { it.rating },
            latestVisitorName = list.first().visitorName
        )
    }

    fun onVisitorNameChanged(value: String) {
        _formState.value = _formState.value.copy(visitorName = value, visitorNameError = false)
    }

    fun onRelationChanged(value: String) {
        _formState.value = _formState.value.copy(relation = value)
    }

    fun onRatingSelected(rating: Int) {
        _formState.value = _formState.value.copy(rating = rating, ratingError = false)
    }

    fun onReviewTextChanged(value: String) {
        if (value.length <= MAX_REVIEW_LENGTH) {
            _formState.value = _formState.value.copy(reviewText = value)
        }
    }

    fun submit() {
        val state = _formState.value
        val nameValid = state.visitorName.isNotBlank()
        val ratingValid = state.rating != null
        if (!nameValid || !ratingValid) {
            _formState.value = state.copy(visitorNameError = !nameValid, ratingError = !ratingValid)
            return
        }
        viewModelScope.launch {
            repository.addReview(
                visitorName = state.visitorName.trim(),
                relation = state.relation.trim(),
                rating = state.rating!!,
                review = state.reviewText.trim()
            )
            resetForm()
        }
    }

    /** Clears only the current form fields — saved reviews are untouched. */
    fun resetForm() {
        _formState.value = ReviewFormState()
    }
}
