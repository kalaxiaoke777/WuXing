package studio.cheng.daily.vision

import android.graphics.Bitmap
import studio.cheng.daily.data.MealReading
import kotlin.math.roundToInt

/**
 * 拍照记餐的临时读数。菜名和正式评分留给以后的线上接口，
 * 这里只根据画面色彩给出一张纸上的对照。
 */
class MealVision {
    fun score(bitmap: Bitmap): MealReading {
        val pigment = pigment(bitmap)
        val score = (
            pigment.produce * 0.34f +
                pigment.variety * 0.18f +
                pigment.fresh * 0.28f +
                pigment.balance * 0.20f
            ).roundToInt().coerceIn(0, 100)
        return MealReading(
            score = score,
            title = titleFor(score),
            commentary = speak(pigment.produce, pigment.fresh),
            produce = pigment.produce,
            variety = pigment.variety,
            fresh = pigment.fresh,
            balance = pigment.balance,
            foods = emptyList(),
            usedModel = false,
        )
    }

    private data class Pigment(val produce: Int, val variety: Int, val fresh: Int, val balance: Int)

    private fun pigment(src: Bitmap): Pigment {
        val sample = Bitmap.createScaledBitmap(src, 40, 40, true)
        val pixels = IntArray(40 * 40)
        sample.getPixels(pixels, 0, 40, 0, 0, 40, 40)
        if (sample !== src) sample.recycle()
        var green = 0
        var warm = 0
        var beige = 0
        var counted = 0
        val hist = IntArray(12)
        pixels.forEach { color ->
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val delta = maxC - minC
            if (maxC < 0.08f) return@forEach
            counted++
            val sat = if (maxC == 0f) 0f else delta / maxC
            val hue = hue(r, g, b, maxC, delta)
            if (sat > 0.18f) hist[(hue / 30f).toInt().coerceIn(0, 11)]++
            if (hue in 70f..165f && sat > 0.18f) green++
            if ((hue < 32f || hue > 345f) && sat > 0.28f && maxC > 0.22f) warm++
            if (sat < 0.16f && maxC in 0.32f..0.92f) beige++
        }
        val denom = counted.coerceAtLeast(1).toFloat()
        val produce = ((green + warm * 0.7f) / denom * 170f).roundToInt().coerceIn(0, 100)
        val variety = (hist.count { it > 6 } * 16).coerceIn(8, 100)
        val fresh = ((1f - beige / denom) * 100f).roundToInt().coerceIn(0, 100)
        val balance = (produce * 0.42f + variety * 0.28f + fresh * 0.30f).roundToInt().coerceIn(0, 100)
        return Pigment(produce, variety, fresh, balance)
    }

    private fun hue(r: Float, g: Float, b: Float, maxC: Float, delta: Float): Float {
        if (delta == 0f) return 0f
        val sector = when (maxC) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        val degrees = sector * 60f
        return if (degrees < 0f) degrees + 360f else degrees
    }

    private fun titleFor(score: Int): String = when {
        score >= 85 -> "清爽"
        score >= 70 -> "均衡"
        score >= 55 -> "普通"
        else -> "偏重"
    }

    private fun speak(produce: Int, fresh: Int): String = when {
        produce >= 65 && fresh >= 55 -> "绿色和暖色散得比较开。线上读数还没接上，这次只看色彩。"
        fresh < 45 -> "画面偏熟褐、偏单一。线上读数还没接上，这次只看色彩。"
        else -> "层次还在，蔬果不算突出。线上读数还没接上，这次只看色彩。"
    }
}
