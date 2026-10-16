package studio.cheng.daily.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.random.Random

fun limitSide(src: Bitmap, maxSide: Int): Bitmap {
    val longest = max(src.width, src.height)
    if (longest <= maxSide) return src
    val ratio = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(
        src,
        (src.width * ratio).toInt().coerceAtLeast(1),
        (src.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}

fun decodeOriented(path: String, maxSide: Int = 1280): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }
    if (rotation == 0f) return raw
    val matrix = Matrix().apply { postRotate(rotation) }
    val turned = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (turned !== raw) raw.recycle()
    return turned
}

fun copyUri(context: Context, uri: Uri): File? {
    val dest = File(context.cacheDir, "picked.jpg")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return null
        dest
    } catch (_: Exception) {
        null
    }
}

fun saveMealJpeg(context: Context, bitmap: Bitmap): String {
    val dir = File(context.filesDir, "meals").apply { mkdirs() }
    val file = File(dir, "${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    return file.absolutePath
}

fun loadAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
}

fun drawSample(heavy: Boolean): Bitmap {
    val size = 640
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val random = Random(if (heavy) 3 else 7)
    if (!heavy) {
        paint.color = Color.parseColor("#D7CBB8")
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.color = Color.parseColor("#EFE6D6")
        canvas.drawCircle(320f, 340f, 250f, paint)
        val greens = intArrayOf(0xFF2F6B45.toInt(), 0xFF3E8A52.toInt(), 0xFF1E4D34.toInt(), 0xFF7FA35A.toInt())
        repeat(28) {
            paint.color = greens[it % greens.size]
            canvas.drawCircle(180f + random.nextFloat() * 280f, 180f + random.nextFloat() * 280f, 26f + random.nextFloat() * 34f, paint)
        }
        paint.color = 0xFFC44536.toInt()
        repeat(7) {
            canvas.drawCircle(200f + random.nextFloat() * 240f, 200f + random.nextFloat() * 240f, 16f, paint)
        }
        paint.color = 0xFFE2C15A.toInt()
        repeat(12) {
            canvas.drawCircle(210f + random.nextFloat() * 220f, 220f + random.nextFloat() * 200f, 8f, paint)
        }
    } else {
        paint.color = 0xFF1A120E.toInt()
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.color = 0xFFC48A3A.toInt()
        canvas.drawCircle(320f, 330f, 210f, paint)
        paint.color = 0xFF8A4B22.toInt()
        canvas.drawCircle(320f, 330f, 150f, paint)
        paint.color = 0xFFE6C56B.toInt()
        canvas.drawRoundRect(230f, 250f, 410f, 330f, 18f, 18f, paint)
        paint.color = 0xFFD24B3A.toInt()
        canvas.drawCircle(300f, 300f, 26f, paint)
    }
    return bitmap
}
