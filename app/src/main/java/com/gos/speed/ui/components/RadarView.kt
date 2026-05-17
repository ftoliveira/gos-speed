package com.gos.speed.ui.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun RadarView(
    distanceMeters: Float?,
    azimuthDegrees: Float?,
    maxRangeMeters: Float = 10f,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val animAzimuth by animateFloatAsState(
        targetValue = azimuthDegrees ?: 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 80f),
        label = "azimuth"
    )
    val animDistance by animateFloatAsState(
        targetValue = distanceMeters?.coerceIn(0f, maxRangeMeters) ?: 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 100f),
        label = "distance"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.85f

        // Rings
        listOf(1f / 3f, 2f / 3f, 1f).forEach { frac ->
            drawCircle(color = surface, radius = r * frac, center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }

        // Cross lines
        val axisColor = onSurface.copy(alpha = 0.15f)
        drawLine(axisColor, Offset(cx, cy - r), Offset(cx, cy + r), 1.dp.toPx())
        drawLine(axisColor, Offset(cx - r, cy), Offset(cx + r, cy), 1.dp.toPx())

        // Cardinal labels
        drawIntoCanvas { canvas ->
            val labelPaint = AndroidPaint().apply {
                color = onSurface.copy(alpha = 0.5f).toArgb()
                textSize = 10.dp.toPx()
                textAlign = AndroidPaint.Align.CENTER
                isAntiAlias = true
            }
            val off = r + 14.dp.toPx()
            canvas.nativeCanvas.drawText("N", cx, cy - off + labelPaint.textSize / 3, labelPaint)
            canvas.nativeCanvas.drawText("S", cx, cy + off + labelPaint.textSize / 3, labelPaint)
            canvas.nativeCanvas.drawText("L", cx + off, cy + labelPaint.textSize / 3, labelPaint)
            canvas.nativeCanvas.drawText("O", cx - off, cy + labelPaint.textSize / 3, labelPaint)

            // Ring labels (1m, 3m, 5m mapped to max range)
            listOf(maxRangeMeters / 3f to 1f / 3f, maxRangeMeters * 2f / 3f to 2f / 3f, maxRangeMeters to 1f).forEach { (label, frac) ->
                val labelStr = if (label == label.toInt().toFloat()) "${label.toInt()}m" else "%.1fm".format(label)
                canvas.nativeCanvas.drawText(labelStr, cx + r * frac * 0.7f, cy - 4.dp.toPx(), labelPaint)
            }
        }

        // Self dot
        drawCircle(color = primary, radius = 6.dp.toPx(), center = Offset(cx, cy))

        // Peer dot (if there's a distance reading)
        if (distanceMeters != null) {
            val angleRad = Math.toRadians((animAzimuth - 90.0)) // 0° = North = up
            val distFrac = (animDistance / maxRangeMeters).coerceIn(0f, 1f)
            val peerX = cx + cos(angleRad).toFloat() * r * distFrac
            val peerY = cy + sin(angleRad).toFloat() * r * distFrac

            // Glow
            drawCircle(color = secondary.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = Offset(peerX, peerY))
            drawCircle(color = secondary, radius = 8.dp.toPx(), center = Offset(peerX, peerY))

            // Line from self to peer
            drawLine(color = secondary.copy(alpha = 0.4f), start = Offset(cx, cy), end = Offset(peerX, peerY), strokeWidth = 1.5.dp.toPx())

            // "?" if no azimuth
            if (azimuthDegrees == null) {
                drawIntoCanvas { canvas ->
                    val p = AndroidPaint().apply {
                        color = Color.White.toArgb()
                        textSize = 10.dp.toPx()
                        textAlign = AndroidPaint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText("?", peerX, peerY + p.textSize / 3, p)
                }
            }
        }
    }
}
