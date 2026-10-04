package com.nook.app.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nook.app.designsystem.theme.NookTheme

/**
 * The Nook mark: an arched doorway (a "nook") with a warm dot inside.
 * [drawProgress] traces the arch 0→1, [dotScale] pops the dot in.
 * Geometry matches res/drawable/ic_launcher_foreground.xml (108-unit grid).
 */
@Composable
fun NookLogo(
    modifier: Modifier = Modifier,
    drawProgress: Float = 1f,
    dotScale: Float = 1f,
    strokeColor: Color = NookTheme.colors.text,
    dotColor: Color = NookTheme.colors.accent,
) {
    Canvas(modifier) {
        val unit = size.minDimension / 54f // logo occupies the 54-unit centre of the 108 grid
        val ox = (size.width - 54f * unit) / 2f - 27f * unit
        val oy = (size.height - 54f * unit) / 2f - 27f * unit
        fun p(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)

        val path = Path().apply {
            val start = p(36f, 76f)
            moveTo(start.x, start.y)
            val top = p(36f, 48f)
            lineTo(top.x, top.y)
            arcTo(
                rect = Rect(p(36f, 30f), p(72f, 66f)),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            val end = p(72f, 76f)
            lineTo(end.x, end.y)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val segment = Path()
        measure.getSegment(0f, measure.length * drawProgress.coerceIn(0f, 1f), segment, true)
        drawPath(segment, strokeColor, style = Stroke(width = 6f * unit, cap = StrokeCap.Round))
        if (dotScale > 0f) {
            drawCircle(dotColor, radius = 5.5f * unit * dotScale, center = p(54f, 62f))
        }
    }
}
