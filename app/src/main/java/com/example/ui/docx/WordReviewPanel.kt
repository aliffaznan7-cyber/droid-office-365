package com.example.ui.docx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DocxBlock
import com.example.domain.model.DocxDocumentModel
import com.example.domain.model.DocxTrackChangeType
import com.example.ui.viewmodel.DocxDocumentMutator
import com.example.ui.viewmodel.WordDialogRequest

@Composable
fun WordReviewPanel(
    model: DocxDocumentModel?,
    onSelectBlock: (String) -> Unit,
    onToggleCommentResolved: (String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onAcceptChange: (blockId: String, runId: String) -> Unit,
    onRejectChange: (blockId: String, runId: String) -> Unit,
    onAcceptAllChanges: () -> Unit,
    onRejectAllChanges: () -> Unit,
    onOpenDialog: (WordDialogRequest) -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (model == null) return
    val (wordCount, charCount) = DocxDocumentMutator.computeWordAndCharCounts(model)
    val trackedItems = model.blocks.filterIsInstance<DocxBlock.Paragraph>().flatMap { para ->
        para.runs.filter { it.trackChangeType != DocxTrackChangeType.NONE }
            .map { run -> para.id to run }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .testTag("word_review_panel")
    ) {
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Review & References",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onClosePanel, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Review Panel", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
            }

            // Live Word & Character Statistics
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column {
                            Text("Words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$wordCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$charCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Blocks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${model.blocks.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tracked Changes Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tracked Changes (${trackedItems.size})", style = MaterialTheme.typography.titleSmall)
                    if (trackedItems.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onAcceptAllChanges, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DoneAll, contentDescription = "Accept all", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onRejectAllChanges, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Reject all", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (trackedItems.isEmpty()) {
                item {
                    Text(
                        text = if (model.trackChangesEnabled) "Track Changes is active. Edits will appear here."
                        else "No tracked revisions yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(trackedItems, key = { it.second.id }) { (blockId, run) ->
                    val isIns = run.trackChangeType == DocxTrackChangeType.INSERTION
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isIns) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBlock(blockId) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isIns) "Inserted by ${run.trackAuthor}" else "Deleted by ${run.trackAuthor}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isIns) Color(0xFF166534) else Color(0xFF991B1B)
                                )
                                Text(
                                    text = run.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (!isIns) TextDecoration.LineThrough else TextDecoration.Underline,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onAcceptChange(blockId, run.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onRejectChange(blockId, run.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Comments Thread Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Comments (${model.comments.size})", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { onOpenDialog(WordDialogRequest.COMMENT) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            items(model.comments, key = { it.id }) { comment ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (comment.resolved) MaterialTheme.colorScheme.surfaceContainerLow
                    else Color(0xFFFEF9C3).copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelectBlock(comment.targetBlockId) }
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${comment.author}  #${comment.id}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row {
                                IconButton(onClick = { onToggleCommentResolved(comment.id) }, modifier = Modifier.size(26.dp)) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Resolve comment",
                                        tint = if (comment.resolved) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(onClick = { onDeleteComment(comment.id) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete comment", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                        Text(
                            text = comment.text,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = if (comment.resolved) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }

            // Footnotes & Endnotes Section
            val allNotes = model.footnotes + model.endnotes
            if (allNotes.isNotEmpty()) {
                item {
                    Text("Footnotes & Endnotes (${allNotes.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(allNotes, key = { "${it.kind}_${it.id}" }) { note ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBlock(note.targetBlockId) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "${note.kind.name} [${note.number}]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = note.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Citations Manager Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Citations (${model.citations.size})", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { onOpenDialog(WordDialogRequest.CITATION) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Add Source", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            items(model.citations, key = { it.id }) { cit ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "[${cit.tag}] • ${cit.style}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = DocxDocumentMutator.formatCitationEntry(cit),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
