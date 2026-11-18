package com.alexandre.focus.nat.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.alexandre.focus.nat.R

/** Couleurs et police reprises de l'app web (variables CSS). */
object C {
    val bg = Color(0xFF0B0B0F)
    val card = Color(0xFF16161C)
    val card2 = Color(0xFF1F1F27)
    val line = Color(0x14FFFFFF)
    val txt = Color(0xFFF4F4F6)
    val mut = Color(0xFF8E8E9E)
    var acc = Color(0xFFD7FF3A)
    var acc2 = Color(0xFF7BE07E)
    /** Couleur de l'app choisie dans les réglages web (S.settings.accent). */
    fun setAccent(k: String?) {
        val m = mapOf("lime" to (0xFFD7FF3A to 0xFF7BE07E), "bleu" to (0xFF5AB8FF to 0xFF7C8CFF), "violet" to (0xFFB88CFF to 0xFFFF7AD9),
            "orange" to (0xFFFFB347 to 0xFFFF6B4A), "rose" to (0xFFFF7AB8 to 0xFFFFB86B), "cyan" to (0xFF4FE3D0 to 0xFF5AB8FF), "mono" to (0xFFFFFFFF to 0xFFBDBDC6))
        val (a, b) = m[k] ?: m["lime"]!!; acc = Color(a); acc2 = Color(b)
    }
    val ok = Color(0xFF3DDC84)
    val bad = Color(0xFFFF4D6A)
    val cal = Color(0xFFFF6B4A)
    val wgt = Color(0xFF6BD36E)
    val spt = Color(0xFFB88CFF)
    val pro = Color(0xFFFF5C8A)
    val carb = Color(0xFFFFC44A)
    val fat = Color(0xFF5AB8FF)
}

val Jakarta = FontFamily(Font(R.font.jakarta700, FontWeight.Bold), Font(R.font.jakarta800, FontWeight.ExtraBold))
