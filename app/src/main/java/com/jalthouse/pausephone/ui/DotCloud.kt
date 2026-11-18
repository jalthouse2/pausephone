package com.jalthouse.pausephone.ui

import android.graphics.Matrix
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.atan2
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

/**
 * One breath: in (the cloud spreads), a short pause at the top, out (it gathers
 * into the ensō), and a longer rest in the ensō.
 */
class Breath(val inOut: Float) {
    val hold = inOut * 0.35f
    val rest = inOut * 0.6f
    val cycle = 2 * inOut + hold + rest

    private fun pos(t: Float): Float = ((t % cycle) + cycle) % cycle

    /** 0 = resting as the ensō, 1 = fully spread. Time 0 starts at the beginning of a rest. */
    fun spread(t: Float): Float {
        val p = pos(t - rest)
        return when {
            p < inOut -> ease(p / inOut)
            p < inOut + hold -> 1f
            p < 2 * inOut + hold -> 1f - ease((p - inOut - hold) / inOut)
            else -> 0f
        }
    }

    fun word(t: Float): String {
        val p = pos(t - rest)
        return when {
            p < inOut -> "breathe in"
            p < inOut + hold -> "pause"
            p < 2 * inOut + hold -> "breathe out"
            else -> "rest"
        }
    }
}

private class Dots(n: Int, seed: Int) {
    val angle = FloatArray(n)
    val radius = FloatArray(n)
    val lag = FloatArray(n)
    val size = FloatArray(n)
    val alpha = FloatArray(n)
    val phase = FloatArray(n)
    val wobble = FloatArray(n)
    val ex = FloatArray(n) // where each dot sits in the ensō, in a 200 × 200 box
    val ey = FloatArray(n)

    init {
        val r = Random(seed)
        for (i in 0 until n) {
            angle[i] = r.nextFloat() * 2f * PI.toFloat()
            radius[i] = r.nextFloat().toDouble().pow(0.55).toFloat() // denser near the middle
            lag[i] = radius[i] * 0.25f + r.nextFloat() * 0.12f       // outer dots trail a little
            size[i] = 0.8f + r.nextFloat() * 2f
            alpha[i] = 0.25f + r.nextFloat() * 0.75f
            phase[i] = r.nextFloat() * 6.28f
            wobble[i] = 0.5f + r.nextFloat() * 1.5f
        }
        // Points inside the brush ensō and the pause bars.
        val barCount = (n * 0.08f).toInt()
        val ring = sample(ENSO_PATH, n - barCount, r)
        val bars = sample(PAUSE_PATH, barCount, r)
        // Inner dots go to the pause bars; the rest go round the ensō in angle order,
        // so each dot travels roughly straight in and out.
        val byRadius = (0 until n).sortedBy { radius[it] }
        val inner = byRadius.take(bars.size)
        val outer = byRadius.drop(bars.size).sortedBy { normalise(angle[it]) }
        inner.forEachIndexed { k, i -> ex[i] = bars[k].first; ey[i] = bars[k].second }
        val ringSorted = ring.sortedBy { atan2(it.second - 100f, it.first - 100f) }
        outer.forEachIndexed { k, i ->
            val p = ringSorted.getOrElse(k) { ringSorted.lastOrNull() ?: (100f to 100f) }
            ex[i] = p.first
            ey[i] = p.second
        }
    }

    private fun normalise(a: Float): Float = atan2(sin(a), cos(a))

    private fun sample(pathData: String, count: Int, r: Random): List<Pair<Float, Float>> {
        if (count <= 0) return emptyList()
        val k = 4f
        val path = PathParser().parsePathString(pathData).toPath().asAndroidPath()
        path.fillType = android.graphics.Path.FillType.EVEN_ODD
        path.transform(Matrix().apply { setScale(k, k) })
        val region = Region().apply { setPath(path, Region(0, 0, (200 * k).toInt(), (200 * k).toInt())) }
        val bounds = region.bounds
        val out = ArrayList<Pair<Float, Float>>(count)
        var guard = 0
        while (out.size < count && guard++ < count * 80) {
            val x = bounds.left + r.nextFloat() * bounds.width()
            val y = bounds.top + r.nextFloat() * bounds.height()
            if (region.contains(x.toInt(), y.toInt())) out.add(x / k to y / k)
        }
        return out
    }
}

/**
 * Thousands of tiny dots. They rest as the ensō, spread out roundly as you breathe in,
 * pause, and gather back into the ensō as you breathe out.
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
    val breath = remember(breathSec) { Breath(breathSec.toFloat()) }
    Canvas(modifier) {
        val el = elapsed()
        val scale = min(size.width, size.height) / 640f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val born = (el / 1.2f).coerceAtMost(1f)
        val k = 1.9f
        for (i in 0 until count) {
            val s = breath.spread(el - dots.lag[i] * breath.inOut)
            val w = sin(el * dots.wobble[i] + dots.phase[i]) * (0.6f + 3.4f * s)
            val far = 40f + dots.radius[i] * 270f + w
            val fx = cos(dots.angle[i]) * far
            val fy = sin(dots.angle[i]) * far
            val ensoX = (dots.ex[i] - 100f) * k + cos(dots.phase[i]) * w * 0.3f
            val ensoY = (dots.ey[i] - 100f) * k + sin(dots.phase[i]) * w * 0.3f
            val x = cx + (ensoX + (fx - ensoX) * s) * scale
            val y = cy + (ensoY + (fy - ensoY) * s) * scale
            val a = (0.55f + 0.45f * dots.alpha[i]) * (1f - 0.55f * s * dots.radius[i]) * born
            val z = dots.size[i] * (1.3f - 0.5f * s) * scale
            drawRect(color, topLeft = Offset(x, y), size = Size(z, z), alpha = a.coerceIn(0f, 1f))
        }
    }
}
