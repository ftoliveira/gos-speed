package com.gos.speed.ui.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun SpeedometerGauge(
    speedKmh: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0f, 220f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 80f),
        label = "speed"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.42f
        val trackStroke = r * 0.09f

        val startAngle = 150f
        val totalSweep = 240f
        val speedProgress = (animatedSpeed / 220f).coerceIn(0f, 1f)

        // Background track
        drawArc(
            color = surfaceVariant,
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = trackStroke, cap = StrokeCap.Round)
        )

        // Speed arc with color interpolation
        if (speedProgress > 0.001f) {
            val arcColor = lerp(primaryColor, errorColor, (speedProgress * speedProgress))
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = totalSweep * speedProgress,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = trackStroke, cap = StrokeCap.Round)
            )
        }

        // Major tick marks (11 ticks: 0, 20, 40, ..., 220)
        val majorTickCount = 11
        val innerMajor = r - trackStroke * 2.2f
        val outerMajor = r - trackStroke * 0.3f
        for (i in 0..majorTickCount) {
            val frac = i.toFloat() / majorTickCount
            val angleDeg = startAngle + totalSweep * frac
            val rad = Math.toRadians(angleDeg.toDouble())
            val cos = cos(rad).toFloat()
            val sin = sin(rad).toFloat()
            drawLine(
                color = onSurface.copy(alpha = 0.5f),
                start = Offset(cx + cos * innerMajor, cy + sin * innerMajor),
                end = Offset(cx + cos * outerMajor, cy + sin * outerMajor),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Minor tick marks (4 per major interval = 44 total)
        val minorTickCount = 44
        val innerMinor = r - trackStroke * 1.6f
        for (i in 0..minorTickCount) {
            if (i % 4 == 0) continue
            val frac = i.toFloat() / minorTickCount
            val angleDeg = startAngle + totalSweep * frac
            val rad = Math.toRadians(angleDeg.toDouble())
            val cos = cos(rad).toFloat()
            val sin = sin(rad).toFloat()
            drawLine(
                color = onSurface.copy(alpha = 0.2f),
                start = Offset(cx + cos * innerMinor, cy + sin * innerMinor),
                end = Offset(cx + cos * outerMajor, cy + sin * outerMajor),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Needle
        val needleAngleRad = Math.toRadians((startAngle + totalSweep * speedProgress).toDouble())
        val needleCos = cos(needleAngleRad).toFloat()
        val needleSin = sin(needleAngleRad).toFloat()
        val needleLen = r - trackStroke * 2.5f
        val needleColor = lerp(primaryColor, errorColor, speedProgress * speedProgress)
        drawLine(
            color = needleColor,
            start = Offset(cx - needleCos * trackStroke * 0.5f, cy - needleSin * trackStroke * 0.5f),
            end = Offset(cx + needleCos * needleLen, cy + needleSin * needleLen),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Center dot
        drawCircle(color = needleColor, radius = 10.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = surfaceVariant, radius = 5.dp.toPx(), center = Offset(cx, cy))

        // Speed text (nativeCanvas)
        drawIntoCanvas { canvas ->
            val speedText = animatedSpeed.toInt().toString()
            val unitText = "km/h"

            val speedPaint = AndroidPaint().apply {
                color = onSurface.toArgb()
                textAlign = AndroidPaint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                textSize = r * 0.52f
                isAntiAlias = true
            }
            val unitPaint = AndroidPaint().apply {
                color = onSurface.copy(alpha = 0.6f).toArgb()
                textAlign = AndroidPaint.Align.CENTER
                typeface = Typeface.DEFAULT
                textSize = r * 0.2f
                isAntiAlias = true
            }

            val speedBounds = android.graphics.Rect()
            speedPaint.getTextBounds(speedText, 0, speedText.length, speedBounds)

            val textY = cy + speedBounds.height() / 2f - 8.dp.toPx()
            canvas.nativeCanvas.drawText(speedText, cx, textY, speedPaint)
            canvas.nativeCanvas.drawText(unitText, cx, textY + r * 0.26f, unitPaint)
        }

        // Speed labels at major ticks (every 40 km/h: 0, 40, 80, 120, 160, 200, 220)
        drawIntoCanvas { canvas ->
            val labelPaint = AndroidPaint().apply {
                color = onSurface.copy(alpha = 0.45f).toArgb()
                textAlign = AndroidPaint.Align.CENTER
                textSize = r * 0.14f
                isAntiAlias = true
            }
            val labelRadius = r - trackStroke * 3.8f
            listOf(0, 40, 80, 120, 160, 200, 220).forEachIndexed { idx, speed ->
                val frac = speed.toFloat() / 220f
                val angleDeg = startAngle + totalSweep * frac
                val rad = Math.toRadians(angleDeg.toDouble())
                val lx = cx + cos(rad).toFloat() * labelRadius
                val ly = cy + sin(rad).toFloat() * labelRadius + labelPaint.textSize / 3f
                canvas.nativeCanvas.drawText(speed.toString(), lx, ly, labelPaint)
            }
        }
    }
}
