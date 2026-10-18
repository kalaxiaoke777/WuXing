package studio.cheng.daily.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.cheng.daily.device.CloudApi
import studio.cheng.daily.device.CloudError
import studio.cheng.daily.device.Session
import studio.cheng.daily.data.MealEntry
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.dayIntake
import studio.cheng.daily.data.energyLine
import studio.cheng.daily.data.heatTenth
import studio.cheng.daily.data.intakeTarget
import studio.cheng.daily.data.seasonLabel
import studio.cheng.daily.data.tenthText
import studio.cheng.daily.data.todayKey
import kotlin.math.sin
import studio.cheng.daily.vision.MealVision
import studio.cheng.daily.vision.copyUri
import studio.cheng.daily.vision.decodeOriented
import studio.cheng.daily.vision.limitSide
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
    var ledger by remember { mutableStateOf(false) }
    val meals = state.mealsByDay[todayKey()].orEmpty()
    val current = meals.find { it.id == selected } ?: meals.lastOrNull()
    val ml = state.waterByDay[todayKey()] ?: 0
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
                val path = withContext(Dispatchers.IO) { saveMealJpeg(context, limited) }
                val cloud = if (Session.token(context) != null) {
                    try {
                        val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                        CloudApi.understandMeal(context, bytes)
                    } catch (cloudError: CloudError) {
                        error = cloudError.message
                        null
                    }
                } else {
                    null
                }
                val reading = cloud ?: withContext(Dispatchers.Default) { vision.score(limited) }
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
                error = "镜头打不开，改用相册"
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
                error = "镜头打不开，改用相册"
            }
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        val intake = dayIntake(state)
        PaneHeader("饮食", if (busy) "在读" else if (intake > 0) "$intake kcal" else "进食与饮水", state.notice)
        InkText(energyLine(state), modifier = Modifier.padding(top = 8.dp), size = 13.sp, color = Ink.bronze, line = 20.sp)
        InkText(
            "${seasonLabel(state.season)}建议摄入 ${intakeTarget(state)} kcal",
            modifier = Modifier.padding(top = 4.dp),
            size = 12.sp,
            color = Ink.faint,
        )
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
            MeasureStepper(
                label = "热量",
                tenth = meal.heatTenth(),
                suffix = "kcal",
                step = 50,
                min = 0,
                max = 40000,
                onCommit = { store.setMealHeat(meal.id, it) },
                minusDesc = "降低这餐热量",
                plusDesc = "提高这餐热量",
            modifier = Modifier.padding(top = 12.dp),
        )
            Seal("撤下这一餐", "撤下这一餐", { store.removeMeal(meal.id) }, Modifier.padding(top = 8.dp))
        } ?: EmptySlip()
        error?.let {
            InkText(it, modifier = Modifier.padding(top = 8.dp), size = 13.sp, color = Ink.cinnabar)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("拍摄", "拍摄这一餐", { shoot() }, accent = true, enabled = !busy)
            Seal("相册", "从相册选一餐", { gallery.launch("image/*") }, enabled = !busy)
            Seal("今日各餐", "打开今日摄入", { ledger = true })
        }
        if (ledger) {
            Dialog(onDismissRequest = { ledger = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .background(Ink.bg)
                        .padding(16.dp),
                ) {
                    MealLedger(meals, store) { ledger = false }
                }
            }
        }
        InkText("饮水", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WaterCup(ml / state.waterGoalMl.toFloat(), Modifier.size(92.dp, 132.dp))
            Column(Modifier.padding(start = 16.dp)) {
                InkText("$ml", size = 36.sp, display = true)
                InkText("/ ${state.waterGoalMl} ml", size = 13.sp, color = Ink.muted)
            }
        }
        MeasureStepper(
            label = "今日",
            tenth = ml,
            suffix = "ml",
            step = 100,
            min = 0,
            max = 8000,
            onCommit = store::setWater,
            minusDesc = "减少饮水",
            plusDesc = "增加饮水",
            modifier = Modifier.padding(top = 8.dp),
            decimal = false,
        )
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
                "拍下盘子，或从相册选一张。各餐的名字和热量在「今日各餐」里改。",
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
                InkText(if (meal.title == "补记") "${meal.kcal}" else meal.title, size = 32.sp, color = Ink.paperInk, display = true)
                Spacer(Modifier.weight(1f))
                InkText(
                    if (meal.title == "补记") "kcal" else "${meal.score}",
                    size = if (meal.title == "补记") 16.sp else 56.sp,
                    color = if (meal.title == "补记") Ink.paperFaint else Ink.paperInk,
                    display = meal.title != "补记",
                )
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
                if (meal.usedModel) "这一餐由模型读过" else "模型没读成，这张按画面记的",
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
        } else {
            Box(Modifier.fillMaxSize().background(Ink.paper), contentAlignment = Alignment.Center) {
                InkText("记", size = 16.sp, color = Ink.paperInk, display = true)
            }
        }
    }
}

@Composable
private fun WaterCup(level: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val rx = size.width * 0.34f
        val ry = rx * 0.28f
        val top = size.height * 0.05f
        val bot = size.height * 0.94f
        val bodyTop = top + ry
        val bodyBot = bot - ry
        val filled = level.coerceIn(0f, 1f)
        val ySurf = bodyBot - (bodyBot - bodyTop) * filled
        val glass = Path().apply {
            addOval(Rect(cx - rx, top, cx + rx, top + ry * 2f))
            addRect(Rect(cx - rx, bodyTop, cx + rx, bodyBot))
            addOval(Rect(cx - rx, bot - ry * 2f, cx + rx, bot))
        }
        drawPath(glass, Color(0x18E7E0D4))
        if (filled > 0.02f) {
            clipPath(glass) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFD7E6E0), Color(0xFF4E736A)),
                        startY = ySurf,
                        endY = bot,
                    ),
                    topLeft = Offset(cx - rx, ySurf - sin(1.2f) * 2f),
                    size = Size(rx * 2f, bot - ySurf + 8f),
                )
            }
        }
        drawOval(Ink.bronze, Offset(cx - rx, top), Size(rx * 2f, ry * 2f), style = Stroke(1.5f))
        drawOval(Ink.bronze.copy(alpha = 0.7f), Offset(cx - rx, bot - ry * 2f), Size(rx * 2f, ry * 2f), style = Stroke(1.2f))
    }
}

@Composable
private fun MealLedger(meals: List<MealEntry>, store: RitualStore, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var heat by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, Ink.line)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkText("今日摄入", modifier = Modifier.weight(1f), size = 18.sp, display = true)
            Seal("收回", "关闭今日各餐", onClose)
        }
        if (meals.isEmpty()) {
            InkText("今天还没有一餐。", modifier = Modifier.padding(top = 10.dp), size = 13.sp, color = Ink.faint)
        }
        meals.forEachIndexed { index, meal ->
            MealEditRow(index, meal, store)
        }
        InkText("加一餐", modifier = Modifier.padding(top = 14.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        LedgerField(name, { name = it.take(12) }, "不填就叫第${meals.size + 1}餐")
        LedgerField(heat, { heat = it }, "热量，一位小数")
        Seal(
            "记下",
            "记下这一餐",
            {
                val tenth = parseMeasure(heat, true) ?: return@Seal
                store.addNamedMeal(name, tenth)
                name = ""
                heat = ""
            },
            Modifier.padding(top = 8.dp),
            accent = true,
            enabled = heat.isNotBlank(),
        )
    }
}

@Composable
private fun MealEditRow(index: Int, meal: MealEntry, store: RitualStore) {
    var name by remember(meal.id, meal.title) { mutableStateOf(meal.title) }
    var heat by remember(meal.id, meal.heatTenth()) { mutableStateOf(tenthText(meal.heatTenth())) }
    Column(Modifier.padding(top = 12.dp)) {
        InkText("第${index + 1}餐", size = 12.sp, color = Ink.muted)
        LedgerField(name, { name = it.take(12) }, "第${index + 1}餐")
        LedgerField(heat, { heat = it }, "热量")
        Seal(
            "改这餐",
            "保存这餐的名字和热量",
            {
                store.setMealName(meal.id, name)
                parseMeasure(heat, true)?.let { store.setMealHeat(meal.id, it) }
            },
            Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LedgerField(value: String, onChange: (String) -> Unit, hint: String) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = Ink.text, fontSize = 16.sp),
        singleLine = true,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.bronze),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Ink.line)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (value.isEmpty()) InkText(hint, size = 14.sp, color = Ink.faint)
                inner()
            }
        },
    )
}
