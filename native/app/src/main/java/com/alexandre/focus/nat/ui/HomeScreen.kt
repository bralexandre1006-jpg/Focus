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
fun HomeScreen(store: FocusStore, version: Int, onChanged: () -> Unit, onImport: () -> Unit, onGym: () -> Unit = {}) {
    val today = FocusStore.today()
    var badges by remember { mutableStateOf(false) }
    var askFreeze by remember { mutableStateOf(store.hasData && store.canFreeze()) }
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().padding(horizontal = 14.dp)) {
        // en-tête : logo, série, réglages
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(Modifier.size(24.dp, 30.dp))
            Spacer(Modifier.width(12.dp))
            val n = store.streak()
            if (n > 0) Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x33FF8A3D)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { T("🔥", 16.sp); Spacer(Modifier.width(6.dp)); T("$n", 17.sp); T(" jour${if (n > 1) "s" else ""}", 15.sp, Color(0xFFFFB27A))
                store.freezeToday()?.let { f -> val st = store.steps(); Spacer(Modifier.width(8.dp))
                    T(if (st >= f.optInt("goal")) "gel validé" else "gel ${fmt(st)} / ${fmt(f.optInt("goal"))}", 12.sp, Color(0xFF9FD4FF)) } }
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
            StepsCard(store, today) { badges = true }
            store.program(today)?.let { pg -> FCard(C.spt) { CardTitle("Programme", C.spt); Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { T(pg.name, 17.sp, modifier = Modifier.weight(1f)); T(if (pg.done) "Terminé" else "Semaine ${pg.week} / ${pg.total}", 13.sp, C.mut, FontWeight.Bold) }
                Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.card2)) {
                    Box(Modifier.fillMaxWidth(if (pg.total > 0) (pg.week.toFloat() / pg.total).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(C.spt)) } } }
            WeightCard(store, today, onChanged)
            SessionCard(store, today, onChanged, onGym)
            NutritionCard(store, today)
        }
    }
    if (badges) BadgesDialog(store) { badges = false }
    if (askFreeze) {
        val n = store.streak(); val goal = store.stepGoal + store.FRZ
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { store.markFreezeAsked(); askFreeze = false },
            containerColor = C.card, title = { T("Ta série va s'arrêter", 20.sp) },
            text = { T("Tu n'as rien noté hier. Gèle ce jour pour garder ta série : il te faudra ${fmt(goal)} pas aujourd'hui, soit ton objectif plus 2 500.", 15.sp, C.mut, FontWeight.Bold) },
            confirmButton = { Btn("Geler le jour", C.acc, C.bg, Modifier.padding(4.dp)) { store.freeze(); askFreeze = false; onChanged() } },
            dismissButton = { Btn("Annuler", C.card2, C.txt, Modifier.padding(4.dp)) { store.markFreezeAsked(); askFreeze = false } })
    }
}

/** Badges : mêmes paliers que l'app web, débloqués en couleur, verrouillés en gris. */
@Composable
fun BadgesDialog(store: FocusStore, onClose: () -> Unit) = androidx.compose.ui.window.Dialog(onClose, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
    // calcul en arrière-plan : la fenêtre s'ouvre tout de suite
    val groups by produceState<List<FocusStore.BadgeGroup>>(emptyList(), store.rev) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { store.badges() } }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { T("Badges", 26.sp, modifier = Modifier.weight(1f)); Btn("✕", C.card2, C.txt, Modifier.width(46.dp), onClose) } }
        items(groups.size) { gi -> val g = groups[gi]
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { T(g.title, 18.sp, modifier = Modifier.weight(1f)); T("${g.counts.count { it > 0 }}/${g.tiers.size}", 13.sp, C.acc) }
                T("Record : ${fmt(g.best)} pas", 13.sp, C.mut, FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                g.tiers.chunked(4).forEachIndexed { ci, row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEachIndexed { j, x -> val c = g.counts[ci * 4 + j]; val on = c > 0
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) C.acc.copy(alpha = .14f) else C.card).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(if (on) "🏅" else "🔒", 22.sp); T(if (x >= 1_000_000) "${x / 1_000_000}M" else "${x / 1000}k", 14.sp, if (on) C.txt else C.mut)
                            if (c > 1) T("×$c", 12.sp, C.acc) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } } }
            }
        }
    }
}

@Composable
fun Btn(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { T(label, 15.sp, fg) }

@Composable
fun StepsCard(store: FocusStore, k: String, onBadges: () -> Unit = {}) {
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
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x33FFC44A)).clickable(onClick = onBadges), contentAlignment = Alignment.Center) { T("🏆", 15.sp) }
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

/** Séance : jour de séance (validée / manquée), jour de repos ou jour off. */
@Composable
fun SessionCard(store: FocusStore, k: String, onChanged: () -> Unit, onGym: () -> Unit = {}) {
    val td = store.isTrainingDay(k); val wo = store.workout(k)
    FCard(Color(0xFF7896FF)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Séance de sport", Color(0xFFB88CFF)); Spacer(Modifier.weight(1f))
            if (td) T("Jour de séance", 11.sp, C.spt)
        }
        Spacer(Modifier.height(10.dp))
        when {
            store.isOff(k) -> T("Jour off", 16.sp, C.mut)
            !td && wo == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                T("😴", 30.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { T("Jour de repos", 16.sp); T("J'ai quand même fait une séance", 13.sp, C.acc, FontWeight.Bold, Modifier.clickable { store.setWorkout(true); onChanged() }) }
            }
            td && wo == null && store.list("routines").isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Lancer ma séance", C.acc, C.bg, Modifier.fillMaxWidth(), onGym)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("✓ Faite", Color(0x223DDC84), C.ok, Modifier.weight(1f)) { store.setWorkout(true); onChanged() }
                    Btn("✕ Ratée", Color(0x22FF4D6A), C.bad, Modifier.weight(1f)) { store.setWorkout(false); onChanged() } }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val yes = wo == true; val no = wo == false
                Btn(if (yes) "✓ Séance validée" else "✓ Faite", if (yes) Color(0x553DDC84) else Color(0x223DDC84), C.ok, Modifier.weight(1f)) { store.setWorkout(if (yes) null else true); onChanged() }
                Btn(if (no) "✕ Séance manquée" else "✕ Ratée", if (no) Color(0x55FF4D6A) else Color(0x22FF4D6A), C.bad, Modifier.weight(1f)) { store.setWorkout(if (no) null else false); onChanged() }
            }
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
        var open by remember { mutableStateOf(false) }
        if (m.n > 0) {
            Spacer(Modifier.height(4.dp))
            T(if (open) "Masquer les repas ▴" else "Repas (${m.n}) ▾", 13.sp, C.cal, FontWeight.ExtraBold, Modifier.clickable { open = !open }.padding(vertical = 4.dp))
            if (open) {
                val meals = store.dayOrNull(k)?.optJSONArray("meals")
                for (i in 0 until (meals?.length() ?: 0)) { val x = meals!!.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { T(x.optString("name"), 14.sp, C.txt, FontWeight.Bold, Modifier.weight(1f)); T("${x.optDouble("kcal", 0.0).roundToInt()} kcal", 14.sp, C.mut, FontWeight.Bold) } }
                Spacer(Modifier.height(6.dp))
            }
        }
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
