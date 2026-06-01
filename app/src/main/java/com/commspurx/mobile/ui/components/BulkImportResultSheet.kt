package com.commspurx.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commspurx.mobile.data.model.BulkImportJobResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportResultSheet(
    job: BulkImportJobResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Bulk import results",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (job != null) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                job != null -> {
                    SummaryRow(job)
                    if (!job.errorMessage.isNullOrBlank() && job.successCount == 0) {
                        Text(
                            text = job.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    if (job.rejectedRows.isNotEmpty()) {
                        Text(
                            text = "Rejected rows",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "Row",
                                        modifier = Modifier.weight(0.25f),
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "Reason",
                                        modifier = Modifier.weight(0.75f),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                HorizontalDivider()
                            }
                            items(job.rejectedRows, key = { "${it.rowNumber}-${it.reason}" }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = if (row.rowNumber > 0) row.rowNumber.toString() else "—",
                                        modifier = Modifier.weight(0.25f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = row.reason,
                                        modifier = Modifier.weight(0.75f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    } else {
                        Text(
                            text = "All rows were imported successfully.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun SummaryRow(job: BulkImportJobResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryCard(label = "Total rows", value = job.totalRows, modifier = Modifier.weight(1f))
        SummaryCard(
            label = "Added",
            value = job.successCount,
            modifier = Modifier.weight(1f),
            highlight = SummaryHighlight.Success,
        )
        SummaryCard(
            label = "Rejected",
            value = job.rejectedCount,
            modifier = Modifier.weight(1f),
            highlight = SummaryHighlight.Danger,
        )
    }
}

private enum class SummaryHighlight { Success, Danger }

@Composable
private fun SummaryCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    highlight: SummaryHighlight? = null,
) {
    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (highlight) {
                SummaryHighlight.Success -> MaterialTheme.colorScheme.secondary
                SummaryHighlight.Danger -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
