package com.exhibition.smartdoorlock.ui.rateproject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exhibition.smartdoorlock.data.local.ReviewEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RateProjectScreen(viewModel: RateProjectViewModel) {
    val formState by viewModel.formState.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val stats by viewModel.stats.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Text("Rate This Project", style = MaterialTheme.typography.headlineMedium) }

        item { StatsCard(stats) }

        item {
            ReviewFormCard(
                formState = formState,
                onNameChange = viewModel::onVisitorNameChanged,
                onRelationChange = viewModel::onRelationChanged,
                onRatingSelect = viewModel::onRatingSelected,
                onReviewTextChange = viewModel::onReviewTextChanged,
                onSubmit = viewModel::submit,
                onReset = viewModel::resetForm
            )
        }

        item { Text("Visitor Reviews (${reviews.size})", style = MaterialTheme.typography.titleLarge) }

        if (reviews.isEmpty()) {
            item {
                Text(
                    "No reviews yet. Be the first to share feedback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(reviews, key = { it.id }) { review -> ReviewRow(review) }
        }
    }
}

@Composable
private fun StatsCard(stats: ReviewStats) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Overview", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Total", stats.total.toString())
                StatItem("Average", if (stats.total > 0) String.format(Locale.US, "%.1f", stats.average) else "—")
                StatItem("Highest", if (stats.total > 0) stats.highest.toString() else "—")
                StatItem("Lowest", if (stats.total > 0) stats.lowest.toString() else "—")
            }
            if (stats.latestVisitorName != null) {
                Text(
                    "Latest: ${stats.latestVisitorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewFormCard(
    formState: ReviewFormState,
    onNameChange: (String) -> Unit,
    onRelationChange: (String) -> Unit,
    onRatingSelect: (Int) -> Unit,
    onReviewTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Share Your Feedback", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = formState.visitorName,
                onValueChange = onNameChange,
                label = { Text("Visitor name") },
                singleLine = true,
                isError = formState.visitorNameError,
                supportingText = { if (formState.visitorNameError) Text("Name is required") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = formState.relation,
                onValueChange = onRelationChange,
                label = { Text("Relation to student (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rating", style = MaterialTheme.typography.labelLarge)
                RatingSelector(selected = formState.rating, onSelect = onRatingSelect)
                if (formState.ratingError) {
                    Text(
                        "Please select a rating",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column {
                OutlinedTextField(
                    value = formState.reviewText,
                    onValueChange = onReviewTextChange,
                    label = { Text("Review (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${formState.reviewText.length}/${RateProjectViewModel.MAX_REVIEW_LENGTH}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSubmit, modifier = Modifier.weight(1f)) { Text("Submit") }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
            }
        }
    }
}

/** Two rows of five so all ten cards stay comfortably tappable on a phone screen. */
@Composable
private fun RatingSelector(selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingRow((1..5).toList(), selected, onSelect)
        RatingRow((6..10).toList(), selected, onSelect)
    }
}

@Composable
private fun RatingRow(values: List<Int>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        values.forEach { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    value.toString(),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(review: ReviewEntity) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(review.visitorName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${review.rating}/10",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!review.relation.isNullOrBlank()) {
                Text(
                    review.relation,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (review.review.isNotBlank()) {
                Text(review.review, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatTimestamp(review.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}
