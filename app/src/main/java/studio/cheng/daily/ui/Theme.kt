package studio.cheng.daily.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import studio.cheng.daily.R

object Ink {
    val bg = Color(0xFF100E0C)
    val line = Color(0xFF3C342C)
    val text = Color(0xFFE7E0D4)
    val muted = Color(0xFF8E867C)
    val faint = Color(0xFF5E564C)
    val bronze = Color(0xFFC6A36A)
    val bronzeDeep = Color(0xFF6E522F)
    val celadon = Color(0xFF8EAEA4)
    val moon = Color(0xFFC9D0D6)
    val cinnabar = Color(0xFFB56A58)
    val moss = Color(0xFF8A9A6A)
    val paper = Color(0xFFE6D9C4)
    val paperInk = Color(0xFF2A241C)
    val paperFaint = Color(0xFF6E6256)
}

val LocalDisplay = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

@Composable
fun ChengTheme(content: @Composable () -> Unit) {
    val display = FontFamily(Font(R.font.zcool_xiaowei))
    CompositionLocalProvider(LocalDisplay provides display, content = content)
}
