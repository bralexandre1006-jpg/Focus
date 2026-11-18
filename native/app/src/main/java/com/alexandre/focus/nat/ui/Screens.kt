package com.alexandre.focus.nat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexandre.focus.nat.data.FocusStore
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val dayFmt = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
private fun dayLabel(k: String) = dayFmt.format(FocusStore.parse(k)).replaceFirstChar { it.uppercase() }

/** Repas : recettes en cartes (comme la page web), données lues depuis le JSON. */
@Composable
fun MealsScreen(store: FocusStore) {
    val recs = store.list("recipes").sortedBy { it.optString("name").lowercase() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        if (recs.isEmpty()) item { FCard(C.cal) { T("Aucune recette", 18.sp); T("Crée tes recettes dans l'app actuelle pour l'instant.", 14.sp, C.mut, FontWeight.Bold) } }
        items(recs) { r ->
            val m = store.recipePer(r)
            FCard(C.cal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(r.optString("name"), 19.sp)
                        T("${r.optInt("portions", 1)} portion${if (r.optInt("portions", 1) > 1) "s" else ""} · ${m.n} ingrédient${if (m.n > 1) "s" else ""}", 13.sp, C.mut, FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) { T("${m.kcal.roundToInt()}", 28.sp); T("kcal", 12.sp, C.mut, FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacBar("Protéines", m.p, 60.0, C.pro, Modifier.weight(1f)); MacBar("Glucides", m.c, 120.0, C.carb, Modifier.weight(1f)); MacBar("Lipides", m.f, 50.0, C.fat, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MacBar(lab: String, v: Double, max: Double, col: Color, modifier: Modifier) = Column(modifier) {
    Row(verticalAlignment = Alignment.Bottom) { T("${v.roundToInt()}", 17.sp); T(" g", 13.sp, C.mut, FontWeight.Bold) }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x12FFFFFF))) {
        Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(col))
    }
    Spacer(Modifier.height(4.dp)); T(lab, 11.sp, C.mut, FontWeight.Bold)
}

/** Muscu : routines et dernières séances (lecture seule pour l'instant). */
@Composable
fun GymScreen(store: FocusStore) {
    val routines = store.list("routines"); val wos = store.list("workouts").sortedByDescending { it.optLong("start") }.take(20)
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item { T("Routines", 15.sp, C.mut) }
        if (routines.isEmpty()) item { FCard(C.spt) { T("Aucune routine", 16.sp) } }
        items(routines) { r ->
            val ex = r.optJSONArray("ex")?.length() ?: 0
            FCard(C.spt) { T(r.optString("name"), 18.sp); T("$ex exercice${if (ex > 1) "s" else ""}", 13.sp, C.mut, FontWeight.Bold) }
        }
        item { Spacer(Modifier.height(6.dp)); T("Dernières séances", 15.sp, C.mut) }
        items(wos) { w ->
            FCard(C.spt) {
                Row { Column(Modifier.weight(1f)) { T(w.optString("name"), 16.sp); T(dayLabel(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(w.optLong("start")))), 12.sp, C.mut, FontWeight.Bold) }
                    T("${w.optInt("min")} min", 15.sp, C.spt) }
            }
        }
    }
}

/** Stats : pas des 7 derniers jours en barres + moyennes. */
@Composable
fun StatsScreen(store: FocusStore) {
    val t = FocusStore.today(); val ks = (6 downTo 0).map { FocusStore.addDays(t, -it) }
    val vals = ks.map { store.steps(it) }; val mx = (vals.maxOrNull() ?: 0).coerceAtLeast(store.stepGoal)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FCard(C.acc) {
            CardTitle("Pas · 7 derniers jours", C.acc)
            Spacer(Modifier.height(6.dp))
            T("Moy. ${vals.average().roundToInt()} / jour", 14.sp, C.mut, FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                ks.forEachIndexed { i, k ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight((vals[i].toFloat() / mx).coerceIn(.02f, 1f) * .85f).clip(RoundedCornerShape(6.dp))
                            .background(if (vals[i] >= store.stepGoal) C.acc else Color(0xFF3A3F2A)))
                        Spacer(Modifier.height(6.dp)); T("LMMJVSD"[FocusStore.weekday(k)].toString(), 11.sp, C.mut, FontWeight.Bold)
                    }
                }
            }
        }
        val w = store.lastWeight()
        FCard(C.wgt) { CardTitle("Poids", C.wgt); Spacer(Modifier.height(6.dp)); T(w?.let { String.format(Locale.FRANCE, "%.1f kg", it) } ?: "—", 28.sp) }
    }
}

/** Historique : jour par jour (pas, poids, kcal, séance). */
@Composable
fun HistoryScreen(store: FocusStore) {
    val ks = store.sortedDays().filter { it <= FocusStore.today() }.take(120)
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        items(ks) { k ->
            val m = store.macros(k); val w = store.weight(k); val wo = store.workout(k)
            FCard(Color(0xFF8C8CAA)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(dayLabel(k), 15.sp)
                        T("${store.steps(k)} pas · ${m.kcal.roundToInt()} kcal${w?.let { " · " + String.format(Locale.FRANCE, "%.1f kg", it) } ?: ""}", 13.sp, C.mut, FontWeight.Bold)
                    }
                    if (wo != null) T(if (wo) "Séance" else "Ratée", 13.sp, if (wo) C.ok else C.bad)
                }
            }
        }
    }
}
