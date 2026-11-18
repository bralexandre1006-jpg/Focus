package com.alexandre.focus.nat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexandre.focus.nat.data.FocusStore
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val nf = NumberFormat.getIntegerInstance(Locale.FRANCE)
private fun fmt(n: Number) = nf.format(n.toDouble().roundToInt()).replace(' ', ' ').replace(' ', ' ')
private fun f1(d: Double) = String.format(Locale.FRANCE, "%.1f", d)

@Composable
fun T(text: String, size: TextUnit, color: Color = C.txt, w: FontWeight = FontWeight.ExtraBold, modifier: Modifier = Modifier) =
    androidx.compose.material3.Text(text, modifier, color = color, fontSize = size, fontWeight = w, fontFamily = Jakarta, letterSpacing = (-0.02).sp)

/** Carte teintée : même recette que l'app web (dégradé léger de la couleur de la carte vers le fond). */
@Composable
fun FCard(tint: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = .13f), tint.copy(alpha = .03f), C.card)))
            .border(1.dp, tint.copy(alpha = .12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

@Composable
fun CardTitle(label: String, dot: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
    Spacer(Modifier.width(8.dp))
    T(label.uppercase(), 11.sp, C.mut)
}

@Composable
fun HomeScreen(store: FocusStore, version: Int, onChanged: () -> Unit, onImport: () -> Unit) {
    val today = FocusStore.today()
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().padding(horizontal = 14.dp)) {
        // en-tête : logo, série, réglages
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            T("F", 26.sp, C.acc)
            Spacer(Modifier.width(12.dp))
            val n = store.streak()
            if (n > 0) Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x33FF8A3D)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { T("🔥", 16.sp); Spacer(Modifier.width(6.dp)); T("$n", 17.sp); T(" jour${if (n > 1) "s" else ""}", 15.sp, Color(0xFFFFB27A)) }
        }
        if (!store.hasData) {
            FCard(C.acc) {
                T("Bienvenue dans Focus natif", 18.sp)
                Spacer(Modifier.height(6.dp))
                T("Importe le fichier JSON exporté depuis l'app actuelle (Réglages › Données › Exporter).", 14.sp, C.mut, FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Btn("Importer mes données", C.acc, Color(0xFF0B0B0F), Modifier.fillMaxWidth(), onImport)
            }
            return@Column
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepsCard(store, today)
            WeightCard(store, today, onChanged)
            NutritionCard(store, today)
        }
    }
}

@Composable
fun Btn(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { T(label, 15.sp, fg) }

@Composable
fun StepsCard(store: FocusStore, k: String) {
    val s = store.steps(k); val g = store.stepGoal
    val p by animateFloatAsState((s.toFloat() / g).coerceIn(0f, 1f), tween(900), label = "ring")
    FCard(C.acc) {
        CardTitle("Pas du jour", C.acc)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 9.dp.toPx(); val inset = w / 2
                    val sz = Size(size.width - w, size.height - w)
                    drawArc(C.card2, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                    drawArc(Brush.linearGradient(listOf(C.acc, Color(0xFF5BE39A))), -90f, 360f * p, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                T("${(s * 100 / g)}%", 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                T(fmt(s), 30.sp)
                T("sur ${fmt(g)} pas", 14.sp, C.mut, FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                val kcal = s * 0.04
                T(fmt(kcal), 22.sp)
                T("kcal brûlées", 11.sp, C.mut, FontWeight.Bold)
                T("${f1(s * 0.00075)} km", 11.sp, C.mut, FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WeightCard(store: FocusStore, k: String, onChanged: () -> Unit) {
    val w = store.weight(k) ?: store.lastWeight()
    val tgt = store.targetWeight; val st = store.startWeight
    var input by remember(k) { mutableStateOf("") }
    FCard(C.wgt) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T("MON POIDS", 11.sp, C.mut)
                Row(verticalAlignment = Alignment.Bottom) { T(w?.let { f1(it) } ?: "—", 30.sp); T(" kg", 15.sp, C.mut, FontWeight.Bold) }
            }
            if (!tgt.isNaN() && !st.isNaN() && w != null && st != tgt) {
                val pct = ((st - w) / (st - tgt) * 100).coerceIn(0.0, 100.0)
                Column(horizontalAlignment = Alignment.End) { T("${pct.roundToInt()}%", 20.sp, C.wgt); T("vers ${f1(tgt)} kg", 12.sp, C.mut, FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                input, { input = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = TextStyle(color = C.txt, fontSize = 17.sp, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, cursorBrush = SolidColor(C.acc),
                decorationBox = { inner -> if (input.isEmpty()) T("Poids du jour", 16.sp, C.mut, FontWeight.Bold); inner() }
            )
            Btn("✓", Color(0x333DDC84), C.ok, Modifier.width(52.dp)) {
                input.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 }?.let { store.setWeight((it * 100).roundToInt() / 100.0); input = ""; onChanged() }
            }
            Btn("✕", Color(0x26FF4D6A), C.bad, Modifier.width(52.dp)) { store.setWeight(null); onChanged() }
        }
    }
}

@Composable
fun NutritionCard(store: FocusStore, k: String) {
    val m = store.macros(k); val g = store.kcalGoal
    val p by animateFloatAsState((m.kcal / g).toFloat().coerceIn(0f, 1f), tween(900), label = "kcal")
    FCard(C.cal) {
        CardTitle("Nutrition", C.cal)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) { T(fmt(m.kcal), 30.sp); T(" / ${fmt(g)} kcal", 15.sp, C.mut, FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.card2)) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(C.cal, Color(0xFFFFB347)))))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pg = store.proteinGoal
            Mac(if (pg > 0) "${m.p.roundToInt()} / $pg g" else "${m.p.roundToInt()} g", "Protéines", C.pro, Modifier.weight(1f))
            Mac("${m.c.roundToInt()} g", "Glucides", C.carb, Modifier.weight(1f))
            Mac("${m.f.roundToInt()} g", "Lipides", C.fat, Modifier.weight(1f))
        }
    }
}

@Composable
fun Mac(v: String, lab: String, col: Color, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 8.dp)
) {
    T(v, 15.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(col)); Spacer(Modifier.width(5.dp)); T(lab, 11.sp, C.mut, FontWeight.Bold)
    }
}
