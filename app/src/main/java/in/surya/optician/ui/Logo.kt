package `in`.surya.optician.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Surya Optician mark: an eye under a rising sun. Draws on any background by choosing [eyeColor]. */
@Composable
fun SuryaLogo(
    modifier: Modifier = Modifier,
    eyeColor: Color = Brand.Navy,
    accent: Color = Color(0xFFF59E0B)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dy = 0.055f
        fun p(nx: Float, ny: Float) = Offset(nx * w, (ny + dy) * h)
        val eyeStroke = Stroke(width = 0.045f * w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val rayStroke = Stroke(width = 0.04f * w, cap = StrokeCap.Round)

        // sun rays
        val center = p(0.5f, 0.52f)
        val angles = listOf(25f, 46.67f, 68.33f, 90f, 111.67f, 133.33f, 155f)
        angles.forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val inner = Offset(center.x + 0.26f * w * c, center.y - 0.26f * h * s)
            val outer = Offset(center.x + 0.41f * w * c, center.y - 0.41f * h * s)
            drawLine(color = accent, start = inner, end = outer, strokeWidth = rayStroke.width, cap = StrokeCap.Round)
        }

        // eye outline
        val eye = Path().apply {
            moveTo(p(0.12f, 0.62f).x, p(0.12f, 0.62f).y)
            quadraticBezierTo(p(0.5f, 0.30f).x, p(0.5f, 0.30f).y, p(0.88f, 0.62f).x, p(0.88f, 0.62f).y)
            quadraticBezierTo(p(0.5f, 0.94f).x, p(0.5f, 0.94f).y, p(0.12f, 0.62f).x, p(0.12f, 0.62f).y)
            close()
        }
        drawPath(eye, color = eyeColor, style = eyeStroke)

        // iris and pupil
        val eyeCenter = p(0.5f, 0.62f)
        drawCircle(color = eyeColor, radius = 0.115f * w, center = eyeCenter, style = Stroke(width = 0.04f * w))
        drawCircle(color = accent, radius = 0.05f * w, center = eyeCenter)
    }
}

@Composable
fun SuryaLogo(size: androidx.compose.ui.unit.Dp, eyeColor: Color = Brand.Navy, accent: Color = Color(0xFFF59E0B)) {
    SuryaLogo(modifier = Modifier.size(size), eyeColor = eyeColor, accent = accent)
}
