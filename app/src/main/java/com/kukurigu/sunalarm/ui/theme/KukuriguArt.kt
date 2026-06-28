package com.kukurigu.sunalarm.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn Kukurigú art: a stylized crowing rooster (the app is named after the
 * Bulgarian for "cock-a-doodle-doo"), a rotating sun-burst, and a dawn-sky backdrop.
 * Everything is vector/Canvas so it scales crisply with no bitmap assets.
 */

/** Colour set for [drawRooster], tuned for light vs. dark surroundings. */
data class RoosterPalette(
    val body: Color,
    val bodyShade: Color,
    val comb: Color,
    val beak: Color,
    val beakShade: Color,
    val wattle: Color,
    val eyeWhite: Color,
    val pupil: Color,
    val leg: Color,
    val blush: Color,
    val tail: List<Color>,
)

fun roosterPalette(dark: Boolean): RoosterPalette = if (dark) {
    RoosterPalette(
        body = Color(0xFFFFE6BE),
        bodyShade = Color(0xFFE6B27C),
        comb = Color(0xFFFF5C6E),
        beak = Color(0xFFFFC247),
        beakShade = Color(0xFFE89B1E),
        wattle = Color(0xFFFF5C6E),
        eyeWhite = Color(0xFFFFFFFF),
        pupil = Color(0xFF2A1B12),
        leg = Color(0xFFE89B1E),
        blush = Color(0xFFFF8A9B),
        tail = listOf(Color(0xFF4EC8B6), Color(0xFFFF8A3C), Color(0xFF9A7BFF), Color(0xFFFFC247)),
    )
} else {
    RoosterPalette(
        body = Color(0xFFFFF1DC),
        bodyShade = Color(0xFFF2C99A),
        comb = Color(0xFFE5322B),
        beak = Color(0xFFF9A825),
        beakShade = Color(0xFFD9821B),
        wattle = Color(0xFFE5322B),
        eyeWhite = Color(0xFFFFFFFF),
        pupil = Color(0xFF2A1B12),
        leg = Color(0xFFD9821B),
        blush = Color(0xFFF2806B),
        tail = listOf(Color(0xFF1FA89A), Color(0xFFE8590C), Color(0xFF7C5CFF), Color(0xFFF2A007)),
    )
}

/**
 * Draws a crowing rooster facing right, fitted into the largest centred square of
 * the current canvas. Geometry is authored in a virtual 100×100 box.
 */
fun DrawScope.drawRooster(palette: RoosterPalette) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun pt(x: Float, y: Float) = Offset(ox + x / 100f * s, oy + y / 100f * s)
    fun ln(v: Float) = v / 100f * s

    // --- Tail feathers (drawn first, behind the body) ---
    val feathers = listOf(
        Triple(Offset(42f, 66f), Offset(24f, 58f) to Offset(12f, 34f), palette.tail[0]),
        Triple(Offset(42f, 64f), Offset(23f, 44f) to Offset(17f, 19f), palette.tail[1]),
        Triple(Offset(45f, 64f), Offset(31f, 38f) to Offset(31f, 13f), palette.tail[2]),
        Triple(Offset(47f, 64f), Offset(42f, 38f) to Offset(46f, 14f), palette.tail[3]),
    )
    for ((base, ctrlTip, color) in feathers) {
        val (ctrl, tip) = ctrlTip
        val path = Path().apply {
            val b = pt(base.x, base.y); val c = pt(ctrl.x, ctrl.y); val t = pt(tip.x, tip.y)
            moveTo(b.x, b.y)
            quadraticTo(c.x, c.y, t.x, t.y)
        }
        drawPath(path, color, style = Stroke(width = ln(8.5f), cap = StrokeCap.Round))
    }

    // --- Legs + feet (behind the body so the tops are covered) ---
    fun line(a: Offset, b: Offset, w: Float, color: Color) =
        drawLine(color, pt(a.x, a.y), pt(b.x, b.y), strokeWidth = ln(w), cap = StrokeCap.Round)
    line(Offset(49f, 80f), Offset(46f, 93f), 3.6f, palette.leg)
    line(Offset(58f, 80f), Offset(61f, 93f), 3.6f, palette.leg)
    for (foot in listOf(Offset(46f, 93f), Offset(61f, 93f))) {
        line(foot, Offset(foot.x - 5f, foot.y + 4f), 2.4f, palette.leg)
        line(foot, Offset(foot.x, foot.y + 5f), 2.4f, palette.leg)
        line(foot, Offset(foot.x + 5f, foot.y + 4f), 2.4f, palette.leg)
    }

    // --- Body ---
    drawOval(palette.body, topLeft = pt(27f, 47f), size = Size(ln(47f), ln(40f)))

    // --- Folded wing ---
    val wing = Path().apply {
        val a = pt(39f, 62f); val c1 = pt(52f, 54f); val e = pt(67f, 63f); val c2 = pt(53f, 76f)
        moveTo(a.x, a.y)
        quadraticTo(c1.x, c1.y, e.x, e.y)
        quadraticTo(c2.x, c2.y, a.x, a.y)
        close()
    }
    drawPath(wing, palette.bodyShade)

    // --- Head ---
    drawCircle(palette.body, ln(16f), pt(67f, 40f))

    // --- Comb (three red bumps) ---
    drawCircle(palette.comb, ln(7f), pt(57f, 25f))
    drawCircle(palette.comb, ln(7.5f), pt(66f, 21f))
    drawCircle(palette.comb, ln(7f), pt(75f, 25f))

    // --- Wattle (hangs under the chin) ---
    drawCircle(palette.wattle, ln(6.5f), pt(79f, 54f))

    // --- Beak (open, crowing) ---
    val upperBeak = Path().apply {
        val a = pt(82f, 36f); val b = pt(100f, 32f); val c = pt(84f, 43f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
    }
    drawPath(upperBeak, palette.beak)
    val lowerBeak = Path().apply {
        val a = pt(84f, 45f); val b = pt(97f, 47f); val c = pt(82f, 48f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
    }
    drawPath(lowerBeak, palette.beakShade)

    // --- Eye + cheek blush ---
    drawCircle(palette.blush.copy(alpha = 0.35f), ln(5f), pt(76f, 45f))
    drawCircle(palette.eyeWhite, ln(5f), pt(71f, 35f))
    drawCircle(palette.pupil, ln(2.6f), pt(72.5f, 35f))
}

/** A rotating sun-burst centred on [center]. Alternates long/short rays. */
private fun DrawScope.drawSunRays(center: Offset, innerR: Float, color: Color, count: Int = 14) {
    val step = 360f / count
    val halfW = innerR * 0.12f
    for (i in 0 until count) {
        val outerR = innerR + if (i % 2 == 0) innerR * 0.95f else innerR * 0.5f
        rotate(i * step, pivot = center) {
            val ray = Path().apply {
                moveTo(center.x - halfW, center.y - innerR)
                lineTo(center.x + halfW, center.y - innerR)
                lineTo(center.x, center.y - outerR)
                close()
            }
            drawPath(ray, color)
        }
    }
}

/** Static rooster, used in headers and empty states. */
@Composable
fun Rooster(modifier: Modifier = Modifier, dark: Boolean = isSystemInDarkTheme()) {
    val palette = roosterPalette(dark)
    Canvas(modifier) { drawRooster(palette) }
}

/** Animated crowing rooster with a rotating sun-burst behind it. */
@Composable
fun CrowingRooster(modifier: Modifier = Modifier, dark: Boolean = isSystemInDarkTheme()) {
    val palette = roosterPalette(dark)
    val transition = rememberInfiniteTransition(label = "rooster")
    val rayAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label = "rays",
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(820, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "bob",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.46f)
            val inner = size.minDimension * 0.30f
            drawCircle(
                Color(0xFFFFD27A).copy(alpha = if (dark) 0.16f else 0.28f),
                radius = inner * 1.6f,
                center = center,
            )
            rotate(rayAngle, pivot = center) {
                drawSunRays(center, inner, Color(0xFFFFC247).copy(alpha = 0.5f))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            translate(top = -bob * size.minDimension * 0.03f) {
                drawRooster(palette)
            }
        }
    }
}

/** A dawn-sky vertical gradient backdrop. */
@Composable
fun DawnBackdrop(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = if (dark) {
        listOf(Color(0xFF0C0A18), Color(0xFF35124A), Color(0xFF7E2F44), Color(0xFFC25A2C))
    } else {
        listOf(Color(0xFF241953), Color(0xFF7A2F7E), Color(0xFFDB5E37), Color(0xFFF6B454))
    }
    Box(
        modifier = modifier.background(Brush.verticalGradient(colors)),
        content = content,
    )
}

/** Home-screen hero banner: dawn gradient + wordmark + rooster. */
@Composable
fun KukuriguHero(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    DawnBackdrop(modifier.clip(RoundedCornerShape(24.dp)), dark = dark) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Kukurigú!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wake with the sun — four dawns, one rooster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Rooster(modifier = Modifier.size(96.dp), dark = dark)
        }
    }
}
