package com.doujinzip.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

internal enum class ActionSymbol {
    FILTER, SORT, GRID, LIST, FOLDER, SERIES, SELECT, REFRESH, SYNC, STAR, HEART,
    PAGE, CLEAR, DELETE, LANGUAGE, EDIT, READ, MERGE, SAVE, HIDE, PLAY, PAUSE,
    SEARCH, DEVICE, PC, BACK, SETTINGS, ADD, BELL, LOCK, APPEARANCE, INFO, BOOK, NEWSPAPER,
}

/** Small vector icons share the surrounding button's color and accessible label. */
@Composable
internal fun ActionIcon(symbol: ActionSymbol, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(20.dp)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            val outline = Stroke(width = 1.8f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)
            fun box(x: Float, y: Float, w: Float, h: Float) =
                drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(1.5f), style = outline)
            fun path(vararg points: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
                val path = Path().apply {
                    moveTo(points.first().first, points.first().second)
                    points.drop(1).forEach { lineTo(it.first, it.second) }
                    if (close) close()
                }
                if (fill) drawPath(path, color) else drawPath(path, color, style = outline)
            }
            when (symbol) {
                ActionSymbol.BOOK -> { path(12f to 5f, 8f to 3f, 2f to 3f, 2f to 19f, 8f to 19f, 12f to 21f, 16f to 19f, 22f to 19f, 22f to 3f, 16f to 3f, close = true); line(12f, 5f, 12f, 21f) }

                ActionSymbol.LOCK -> { box(5f, 10f, 14f, 11f); drawArc(color, 180f, 180f, false, Offset(8f, 2f), Size(8f, 16f), style = outline); line(12f, 14f, 12f, 17f) }
                ActionSymbol.APPEARANCE -> { drawCircle(color, 9f, Offset(12f, 12f), style = outline); drawArc(color, 90f, 180f, true, Offset(3f, 3f), Size(18f, 18f)) }
                ActionSymbol.INFO -> { drawCircle(color, 9f, Offset(12f, 12f), style = outline); line(12f, 11f, 12f, 17f); drawCircle(color, 1f, Offset(12f, 7f)) }

                ActionSymbol.BELL -> { drawPath(Path().apply { moveTo(4f,18f); lineTo(6f,15f); lineTo(6f,10f); cubicTo(6f,2f,18f,2f,18f,10f); lineTo(18f,15f); lineTo(20f,18f); close() },color,style=outline); drawArc(color,0f,180f,false,Offset(10f,19f),Size(4f,3f),style=outline) }
                ActionSymbol.FILTER -> path(3f to 4f, 21f to 4f, 14f to 12f, 14f to 21f, 10f to 18f, 10f to 12f, close = true, fill = true)
                ActionSymbol.SORT -> { line(3f, 6f, 15f, 6f); line(3f, 12f, 12f, 12f); line(3f, 18f, 9f, 18f) }
                ActionSymbol.GRID -> { box(3f, 3f, 7f, 7f); box(14f, 3f, 7f, 7f); box(3f, 14f, 7f, 7f); box(14f, 14f, 7f, 7f) }
                ActionSymbol.LIST -> repeat(3) { row -> val y = 5f + row * 7f; box(3f, y - 1f, 2f, 2f); line(9f, y, 21f, y) }
                ActionSymbol.NEWSPAPER -> { box(3f, 3f, 16f, 18f); path(19f to 7f, 22f to 7f, 22f to 21f, 19f to 21f); box(6f, 6f, 5f, 5f); line(14f, 6f, 16f, 6f); line(14f, 10f, 16f, 10f); line(6f, 15f, 16f, 15f); line(6f, 18f, 16f, 18f) }
                ActionSymbol.FOLDER -> path(3f to 6f, 10f to 6f, 12f to 8f, 21f to 8f, 21f to 20f, 3f to 20f, close = true)
                ActionSymbol.SERIES -> { box(3f, 5f, 5f, 15f); box(10f, 5f, 5f, 15f); path(17f to 6f, 21f to 5f, 24f to 19f, 20f to 20f, close = true) }
                ActionSymbol.SELECT -> { box(3f, 3f, 18f, 18f); path(7f to 12f, 11f to 16f, 18f to 8f) }
                ActionSymbol.REFRESH, ActionSymbol.SYNC -> {
                    drawArc(color, 35f, 280f, false, Offset(4f, 4f), Size(16f, 16f), style = outline)
                    path(15f to 3f, 20f to 5f, 19f to 10f)
                    if (symbol == ActionSymbol.SYNC) path(9f to 21f, 4f to 19f, 5f to 14f)
                }
                ActionSymbol.STAR -> path(12f to 2f, 15f to 8f, 22f to 9f, 17f to 14f, 18f to 21f, 12f to 18f, 6f to 21f, 7f to 14f, 2f to 9f, 9f to 8f, close = true)
                ActionSymbol.HEART -> drawPath(Path().apply {
                    moveTo(12f, 21f); cubicTo(5f, 16f, 1f, 11f, 3f, 6f); cubicTo(5f, 1f, 10f, 3f, 12f, 6f)
                    cubicTo(14f, 3f, 19f, 1f, 21f, 6f); cubicTo(23f, 11f, 19f, 16f, 12f, 21f); close()
                }, color, style = outline)
                ActionSymbol.PAGE -> { path(4f to 2f, 15f to 2f, 20f to 7f, 20f to 22f, 4f to 22f, close = true); path(15f to 2f, 15f to 7f, 20f to 7f); line(8f, 12f, 16f, 12f); line(8f, 17f, 14f, 17f) }
                ActionSymbol.CLEAR -> { line(5f, 5f, 19f, 19f); line(19f, 5f, 5f, 19f) }
                ActionSymbol.DELETE -> { line(3f, 6f, 21f, 6f); line(9f, 3f, 15f, 3f); path(5f to 6f, 6f to 21f, 18f to 21f, 19f to 6f); line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f) }
                ActionSymbol.LANGUAGE -> { drawCircle(color, 9f, Offset(12f, 12f), style = outline); drawOval(color, Offset(8f, 3f), Size(8f, 18f), style = outline); line(3f, 12f, 21f, 12f) }
                ActionSymbol.EDIT -> { path(4f to 16f, 17f to 3f, 21f to 7f, 8f to 20f, 3f to 21f, close = true); line(14f, 6f, 18f, 10f) }
                ActionSymbol.READ -> { drawCircle(color, 9f, Offset(12f, 12f), style = outline); path(7f to 12f, 11f to 16f, 18f to 8f) }
                ActionSymbol.MERGE -> { path(4f to 20f, 4f to 16f, 12f to 9f, 20f to 16f, 20f to 20f); line(12f, 9f, 12f, 3f); path(8f to 7f, 12f to 3f, 16f to 7f) }
                ActionSymbol.SAVE -> { path(3f to 3f, 18f to 3f, 21f to 6f, 21f to 21f, 3f to 21f, close = true); box(7f, 3f, 9f, 6f); box(7f, 14f, 10f, 7f) }
                ActionSymbol.HIDE -> { drawOval(color, Offset(2f, 6f), Size(20f, 12f), style = outline); drawCircle(color, 3f, Offset(12f, 12f), style = outline); line(3f, 3f, 21f, 21f) }
                ActionSymbol.PLAY -> path(7f to 3f, 21f to 12f, 7f to 21f, close = true, fill = true)
                ActionSymbol.PAUSE -> { box(6f, 4f, 4f, 16f); box(14f, 4f, 4f, 16f) }
                ActionSymbol.SEARCH -> { drawCircle(color, 7f, Offset(10f, 10f), style = outline); line(15f, 15f, 22f, 22f) }
                ActionSymbol.DEVICE -> { box(6f, 2f, 12f, 20f); line(10f, 18f, 14f, 18f) }
                ActionSymbol.PC -> { box(2f, 3f, 20f, 14f); line(12f, 17f, 12f, 22f); line(7f, 22f, 17f, 22f) }
                ActionSymbol.BACK -> { line(3f, 12f, 21f, 12f); path(10f to 5f, 3f to 12f, 10f to 19f) }
                ActionSymbol.ADD -> { line(4f, 12f, 20f, 12f); line(12f, 4f, 12f, 20f) }
                ActionSymbol.SETTINGS -> {
                    val teeth = Path().apply {
                        repeat(32) { point ->
                            val angle = point * Math.PI / 16
                            val radius = if (point % 4 in 1..2) 10f else 7.5f
                            val x = 12f + kotlin.math.cos(angle).toFloat() * radius
                            val y = 12f + kotlin.math.sin(angle).toFloat() * radius
                            if (point == 0) moveTo(x, y) else lineTo(x, y)
                        }; close()
                    }
                    drawPath(teeth, color, style = outline)
                    drawCircle(color, 3f, Offset(12f, 12f), style = outline)
                }
            }
        }
    }
}
