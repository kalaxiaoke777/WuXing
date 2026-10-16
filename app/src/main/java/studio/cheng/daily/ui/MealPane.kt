package studio.cheng.daily.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.cheng.daily.data.MealEntry
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.todayKey
import studio.cheng.daily.vision.MealVision
import studio.cheng.daily.vision.copyUri
import studio.cheng.daily.vision.decodeOriented
import studio.cheng.daily.vision.drawSample
import studio.cheng.daily.vision.limitSide
import studio.cheng.daily.vision.loadAssetBitmap
import studio.cheng.daily.vision.saveMealJpeg
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun MealPane(state: RitualState, store: RitualStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vision = remember { MealVision() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val meals = state.mealsByDay[todayKey()].orEmpty()
    val current = meals.find { it.id == selected } ?: meals.lastOrNull()
    val shot = remember {
        File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")
    }
    val shotUri = remember(shot) {
        FileProvider.getUriForFile(context, "${context.packageName}.files", shot)
    }

    fun begin(bitmap: Bitmap) {
        scope.launch {
            busy = true
            error = null
            val limited = limitSide(bitmap, 1280)
            try {
                val reading = withContext(Dispatchers.Default) { vision.score(limited) }
                val path = withContext(Dispatchers.IO) { saveMealJpeg(context, limited) }
                selected = store.addMeal(path, reading)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e("Cheng", "meal", t)
                error = "没有读成这一餐"
            } finally {
                busy = false
                if (limited !== bitmap && !limited.isRecycled) limited.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) {
            error = "没有拿到照片"
            return@rememberLauncherForActivityResult
        }
        val bitmap = decodeOriented(shot.absolutePath)
        if (bitmap == null) error = "这张图读不出来" else begin(bitmap)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                camera.launch(shotUri)
            } catch (_: Throwable) {
                error = "镜头打不开，用相册或样张"
            }
        } else {
            error = "没有镜头权限"
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = copyUri(context, uri)
        val bitmap = file?.let { decodeOriented(it.absolutePath) }
        if (bitmap == null) error = "这张图读不出来" else begin(bitmap)
    }

    fun shoot() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                camera.launch(shotUri)
            } catch (_: Throwable) {
                error = "镜头打不开，用相册或样张"
            }
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    fun sample(heavy: Boolean) {
        val asset = if (heavy) "samples/heavy.jpg" else "samples/light.jpg"
        val bitmap = loadAssetBitmap(context, asset) ?: drawSample(heavy)
        begin(bitmap)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        PaneHeader("饮食", if (busy) "在读" else "这一餐", state.notice)
        if (meals.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                meals.forEach { meal ->
                    MealThumb(meal.imagePath, meal.id == current?.id) { selected = meal.id }
                }
            }
        }
        current?.let { meal ->
            if (meal.imagePath.isNotBlank()) {
                MealPhoto(meal.imagePath)
            }
            Slip(meal)
            Seal("撤下这一餐", "撤下这一餐", { store.removeMeal(meal.id) }, Modifier.padding(top = 8.dp))
        } ?: EmptySlip()
        error?.let {
            InkText(it, modifier = Modifier.padding(top = 8.dp), size = 13.sp, color = Ink.cinnabar)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("拍摄", "拍摄这一餐", { shoot() }, accent = true, enabled = !busy)
            Seal("相册", "从相册选一餐", { gallery.launch("image/*") }, enabled = !busy)
        }
        Row(Modifier.padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("清淡样张", "用清淡样张", { sample(false) }, enabled = !busy)
            Seal("偏重样张", "用偏重样张", { sample(true) }, enabled = !busy)
        }
        BranchRail(Phase.Earth, state, store)
    }
}

@Composable
private fun EmptySlip() {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(7.dp, 10.dp)
                .background(Color(0x55000000)),
        )
        Column(
            Modifier
                .graphicsLayer {
                    rotationX = 8f
                    rotationY = -7f
                    cameraDistance = 14f * density
                }
                .background(Ink.paper)
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            InkText("尚未落笔", size = 28.sp, color = Ink.paperInk, display = true)
            InkText(
                "拍下盘子，或先用一张样张，看读数怎么落在纸上。",
                modifier = Modifier.padding(top = 8.dp),
                size = 14.sp,
                color = Ink.paperFaint,
                line = 22.sp,
            )
        }
    }
}

@Composable
private fun Slip(meal: MealEntry) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(7.dp, 10.dp)
                .background(Color(0x66000000)),
        )
        Column(
            Modifier
                .graphicsLayer {
                    rotationX = 7f
                    rotationY = -8f
                    cameraDistance = 14f * density
                    shape = RoundedCornerShape(2.dp)
                    clip = true
                }
                .background(Ink.paper)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                InkText(meal.title, size = 32.sp, color = Ink.paperInk, display = true)
                Spacer(Modifier.weight(1f))
                InkText("${meal.score}", size = 56.sp, color = Ink.paperInk, display = true)
            }
            if (meal.foods.isNotEmpty()) {
                InkText(
                    "读到 ${meal.foods.joinToString(" · ")}",
                    modifier = Modifier.padding(top = 4.dp),
                    size = 13.sp,
                    color = Ink.paperFaint,
                )
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PaperMark("蔬果", meal.produce)
                PaperMark("层次", meal.variety)
                PaperMark("清淡", meal.fresh)
                PaperMark("均衡", meal.balance)
            }
            InkText(
                meal.commentary,
                modifier = Modifier.padding(top = 14.dp),
                size = 14.sp,
                color = Ink.paperInk,
                line = 22.sp,
            )
            InkText(
                "线上读数尚未接入",
                modifier = Modifier.padding(top = 10.dp),
                size = 11.sp,
                color = Ink.paperFaint,
            )
        }
    }
}

@Composable
private fun PaperMark(name: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 2.dp, height = 58.dp)
                .background(Ink.paperInk.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 2.dp, height = (58f * value.coerceIn(0, 100) / 100f).dp)
                    .background(Ink.paperInk),
            )
        }
        InkText(name, modifier = Modifier.padding(top = 6.dp), size = 11.sp, color = Ink.paperFaint)
    }
}

@Composable
private fun MealPhoto(path: String) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = "这一餐的照片",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(160.dp)
            .graphicsLayer {
                rotationY = -14f
                rotationX = 9f
                cameraDistance = 12f * density
                shape = RoundedCornerShape(2.dp)
                clip = true
            },
    )
}

@Composable
private fun MealThumb(path: String, selected: Boolean, onClick: () -> Unit) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }
    Box(
        Modifier
            .size(58.dp)
            .graphicsLayer {
                rotationY = if (selected) 0f else -16f
                cameraDistance = 8f * density
            }
            .border(1.dp, if (selected) Ink.bronze else Ink.line)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = "查看这餐读数" },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
