package com.jalthouse.pausephone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Smooth 0→1 ease, clamped. */
fun ease(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return 0.5f - 0.5f * cos(PI.toFloat() * c)
}

/** 0 = gathered in, 1 = fully spread out, for a breath cycle that starts by breathing out. */
fun spread(t: Float): Float = if (t < 0.5f) ease(t * 2f) else 1f - ease((t - 0.5f) * 2f)

private class Dots(n: Int, seed: Int) {
    val angle = FloatArray(n)
    val radius = FloatArray(n)
    val lag = FloatArray(n)
    val size = FloatArray(n)
    val alpha = FloatArray(n)
    val phase = FloatArray(n)
    val wobble = FloatArray(n)

    init {
        val r = Random(seed)
        for (i in 0 until n) {
            angle[i] = r.nextFloat() * 2f * PI.toFloat()
            radius[i] = r.nextFloat().toDouble().pow(0.55).toFloat() // denser near the middle
            lag[i] = radius[i] * 0.18f + r.nextFloat() * 0.05f        // outer dots trail a little
            size[i] = 0.8f + r.nextFloat() * 2f
            alpha[i] = 0.25f + r.nextFloat() * 0.75f
            phase[i] = r.nextFloat() * 6.28f
            wobble[i] = 0.5f + r.nextFloat() * 1.5f
        }
    }
}

/**
 * Thousands of tiny dots that spread out roundly on the out-breath and gather back in.
 * [elapsed] is read during drawing only, so the cloud animates without recomposing.
 */
@Composable
fun DotCloud(
    elapsed: () -> Float,
    breathSec: Int,
    modifier: Modifier = Modifier,
    count: Int = 3000,
    color: Color = Ink.mist,
) {
    val dots = remember(count) { Dots(count, seed = 7) }
    Canvas(modifier) {
        val el = elapsed()
        val cycle = breathSec * 2f
        val scale = min(size.width, size.height) / 640f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val born = (el / 1.5f).coerceAtMost(1f)
        for (i in 0 until count) {
            val t = (((el / cycle) - dots.lag[i]) % 1f + 1f) % 1f
            val s = spread(t)
            val rad = (22f + dots.radius[i] * 272f * (0.18f + 0.82f * s)) * born
            val w = sin(el * dots.wobble[i] + dots.phase[i]) * 4f
            val x = cx + cos(dots.angle[i]) * (rad + w) * scale
            val y = cy + sin(dots.angle[i]) * (rad + w) * scale
            val a = dots.alpha[i] * (0.35f + 0.65f * (1f - dots.radius[i] * 0.5f * s))
            val z = dots.size[i] * (1.2f - 0.4f * s) * scale
            drawRect(color, topLeft = Offset(x, y), size = Size(z, z), alpha = a.coerceIn(0f, 1f))
        }
    }
}
