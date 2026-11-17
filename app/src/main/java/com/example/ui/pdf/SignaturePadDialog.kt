package com.example.ui.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.SignaturePreset

@Composable
fun SignaturePadDialog(
    savedSignatures: List<SignaturePreset>,
    activeColorArgb: Long,
    onSelectPreset: (SignaturePreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSaveNewSignature: (label: String, strokes: List<List<NormalizedPoint>>) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("Authorized Signature") }
    val strokes = remember { mutableStateListOf<List<NormalizedPoint>>() }
    val currentStroke = remember { mutableStateListOf<NormalizedPoint>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = "Signature Pad",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = "Vector Signature Studio",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Sign with stylus, mouse, or touch. Saved signatures flatten losslessly into PDFs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (savedSignatures.isNotEmpty()) {
                    Text(
                        text = "Saved Signatures (Tap to Place)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedSignatures.take(3).forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { onSelectPreset(preset) },
                                            modifier = Modifier.testTag("place_signature_${preset.id}")
                                        ) {
                                            Text("Place on PDF")
                                        }
                                        TextButton(onClick = { onDeletePreset(preset.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete signature"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Signature Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_label_input")
                )

                // Interactive drawing box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.coerceAtLeast(1).toFloat()
                                val h = size.height.coerceAtLeast(1).toFloat()
                                currentStroke.clear()
                                currentStroke.add(
                                    NormalizedPoint(
                                        x = (down.position.x / w).coerceIn(0f, 1f),
                                        y = (down.position.y / h).coerceIn(0f, 1f),
                                        pressure = down.pressure.coerceIn(0.4f, 1.5f)
                                    )
                                )
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    currentStroke.add(
                                        NormalizedPoint(
                                            x = (change.position.x / w).coerceIn(0f, 1f),
                                            y = (change.position.y / h).coerceIn(0f, 1f),
                                            pressure = change.pressure.coerceIn(0.4f, 1.5f)
                                        )
                                    )
                                }
                                if (currentStroke.size >= 2) {
                                    strokes.add(currentStroke.toList())
                                }
                                currentStroke.clear()
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Baseline guide
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(24.dp.toPx(), size.height * 0.78f),
                            end = Offset(size.width - 24.dp.toPx(), size.height * 0.78f),
                            strokeWidth = 1.5f
                        )
                        val inkColor = Color(activeColorArgb)
                        val drawStrokeList = strokes.toList() + listOfNotNull(
                            currentStroke.toList().takeIf { it.size >= 2 }
                        )
                        drawStrokeList.forEach { pts ->
                            if (pts.size >= 2) {
                                val path = Path()
                                path.moveTo(pts.first().x * size.width, pts.first().y * size.height)
                                for (i in 1 until pts.size) {
                                    val prev = pts[i - 1]
                                    val cur = pts[i]
                                    val midX = ((prev.x + cur.x) * 0.5f) * size.width
                                    val midY = ((prev.y + cur.y) * 0.5f) * size.height
                                    path.quadraticTo(
                                        prev.x * size.width,
                                        prev.y * size.height,
                                        midX,
                                        midY
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = inkColor,
                                    style = Stroke(
                                        width = 4.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = "Sign here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            currentStroke.clear()
                        },
                        modifier = Modifier.testTag("clear_signature_button")
                    ) {
                        Text("Clear")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = strokes.isNotEmpty(),
                            onClick = { onSaveNewSignature(label, strokes.toList()) },
                            modifier = Modifier.testTag("save_signature_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Signature")
                        }
                    }
                }
            }
        }
    }
}
