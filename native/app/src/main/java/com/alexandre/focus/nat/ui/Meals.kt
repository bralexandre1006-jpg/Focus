package com.alexandre.focus.nat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexandre.focus.nat.data.FocusStore
import org.json.JSONObject
import kotlin.math.roundToInt

val SLOTS = listOf("breakfast" to "Petit-déj", "lunch" to "Midi", "dinner" to "Soir", "snack" to "Collations")

/** Couleur d'une recette selon son emoji (mêmes teintes que les icônes de l'app web). */
fun recipeColor(e: String): Color = when {
    e.contains("🍗") || e.contains("🍳") -> Color(0xFFFF8A3D)
    e.contains("🥗") || e.contains("🥑") -> Color(0xFF7BE07E)
    e.contains("🥩") || e.contains("🍎") -> Color(0xFFFF4D6A)
    e.contains("🐟") -> Color(0xFF4DA3FF)
    e.contains("🥤") -> Color(0xFF4FE3D0)
    e.contains("🥣") -> Color(0xFF9EC5FF)
    e.contains("🍌") -> Color(0xFFF5C13A)
    else -> Color(0xFFFFB347)
}

@Composable
fun Field(v: String, onV: (String) -> Unit, ph: String, modifier: Modifier = Modifier, num: Boolean = false) = BasicTextField(
    v, onV, modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(C.card2).padding(horizontal = 14.dp, vertical = 12.dp),
    textStyle = TextStyle(color = C.txt, fontSize = 16.sp, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Decimal else KeyboardType.Text), singleLine = true, cursorBrush = SolidColor(C.acc),
    decorationBox = { inner -> if (v.isEmpty()) T(ph, 15.sp, C.mut, FontWeight.Bold); inner() })

private fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0

/** Fenêtre d'ajout : « Mes recettes » ou « Ajout rapide », comme dans l'app web. */
@Composable
fun AddMealDialog(store: FocusStore, slot: String, onClose: () -> Unit, onAdded: () -> Unit) = Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Ajouter · ${SLOTS.first { it.first == slot }.second}", 22.sp, modifier = Modifier.weight(1f)); Btn("✕", C.card2, C.txt, Modifier.width(46.dp), onClose)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).padding(4.dp)) {
            listOf("Mes recettes", "Ajout rapide").forEachIndexed { i, l ->
                Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(if (mode == i) C.txt else Color.Transparent).clickable { mode = i }, contentAlignment = Alignment.Center) { T(l, 15.sp, if (mode == i) C.bg else C.mut) }
            }
        }
        if (mode == 0) {
            var q by remember { mutableStateOf("") }; var pick by remember { mutableStateOf<JSONObject?>(null) }; var por by remember { mutableDoubleStateOf(1.0) }
            Field(q, { q = it }, "Rechercher une recette", Modifier.fillMaxWidth())
            val recs = store.list("recipes").filter { q.isBlank() || it.optString("name").contains(q, true) }.sortedBy { it.optString("name").lowercase() }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recs) { r -> val m = store.recipePer(r); val sel = pick?.optString("id") == r.optString("id")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (sel) C.acc.copy(alpha = .14f) else C.card)
                        .border(1.dp, if (sel) C.acc else Color.Transparent, RoundedCornerShape(16.dp)).clickable { pick = r; por = 1.0 }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(r.optString("emoji", "🍽️"), 24.sp); Spacer(Modifier.width(12.dp))
                        T(r.optString("name"), 16.sp, modifier = Modifier.weight(1f)); T("${m.kcal.roundToInt()} kcal", 14.sp, C.mut, FontWeight.Bold)
                    }
                }
            }
            pick?.let { r -> val m = store.recipePer(r)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("−", C.card2, C.txt, Modifier.width(46.dp)) { por = (por - .5).coerceAtLeast(.5) }
                    T("${if (por % 1.0 == 0.0) por.toInt() else por} portion${if (por > 1) "s" else ""} · ${(m.kcal * por).roundToInt()} kcal", 15.sp, modifier = Modifier.weight(1f))
                    Btn("+", C.card2, C.txt, Modifier.width(46.dp)) { por += .5 }
                }
                Btn("Ajouter", C.acc, C.bg, Modifier.fillMaxWidth()) {
                    store.addMeal(JSONObject().put("slot", slot).put("name", r.optString("name")).put("recipeId", r.optString("id")).put("portions", por)
                        .put("kcal", (m.kcal * por).roundToInt()).put("p", m.p * por).put("c", m.c * por).put("f", m.f * por)); onAdded()
                }
            }
        } else {
            var name by remember { mutableStateOf("") }; var tot by remember { mutableStateOf(true) }
            var kc by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var c by remember { mutableStateOf("") }; var f by remember { mutableStateOf("") }; var g by remember { mutableStateOf("100") }
            Field(name, { name = it }, "Nom (ex : Pomme)", Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).padding(4.dp)) {
                listOf("Pour 100 g" to false, "Repas entier" to true).forEach { (l, v) ->
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (tot == v) C.txt else Color.Transparent).clickable { tot = v }, contentAlignment = Alignment.Center) { T(l, 14.sp, if (tot == v) C.bg else C.mut) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(kc, { kc = it }, "kcal", Modifier.weight(1f), true); Field(p, { p = it }, "Prot.", Modifier.weight(1f), true)
                Field(c, { c = it }, "Gluc.", Modifier.weight(1f), true); Field(f, { f = it }, "Lip.", Modifier.weight(1f), true)
            }
            if (!tot) Row(verticalAlignment = Alignment.CenterVertically) { T("Quantité mangée", 15.sp, C.mut, FontWeight.Bold, Modifier.weight(1f)); Field(g, { g = it }, "g", Modifier.width(110.dp), true) }
            Spacer(Modifier.weight(1f))
            Btn("Ajouter", C.acc, C.bg, Modifier.fillMaxWidth()) {
                if (num(kc) <= 0) return@Btn
                val k = if (tot) 1.0 else num(g) / 100.0
                store.addMeal(JSONObject().put("slot", slot).put("name", name.ifBlank { if (tot) "Repas" else "Aliment" }).put("portions", 1).apply { if (!tot) put("grams", num(g)) }
                    .put("kcal", (num(kc) * k).roundToInt()).put("p", num(p) * k).put("c", num(c) * k).put("f", num(f) * k)); onAdded()
            }
        }
    }
}

/** Liste des repas du jour par créneau (dans la carte Nutrition de l'accueil). */
@Composable
fun MealSlots(store: FocusStore, k: String, onAdd: (String) -> Unit, onChanged: () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val meals = store.dayOrNull(k)?.optJSONArray("meals")
    SLOTS.forEach { (id, lab) ->
        val items = (0 until (meals?.length() ?: 0)).map { meals!!.getJSONObject(it) }.filter { it.optString("slot") == id }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(lab, 15.sp, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) T("${items.sumOf { it.optDouble("kcal", 0.0) }.roundToInt()} kcal", 13.sp, C.mut, FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(C.cal.copy(alpha = .18f)).clickable { onAdd(id) }, contentAlignment = Alignment.Center) { T("+", 17.sp, C.cal) }
            }
            items.forEach { x -> Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(x.optString("name"), 14.sp, C.txt, FontWeight.Bold, Modifier.weight(1f)); T("${x.optDouble("kcal", 0.0).roundToInt()} kcal", 13.sp, C.mut, FontWeight.Bold)
                Spacer(Modifier.width(10.dp)); T("✕", 14.sp, C.bad, modifier = Modifier.clickable { store.removeMeal(k, x.optString("id")); onChanged() })
            } }
        }
    }
}

/** Page Recettes : cartes pleine hauteur teintées, comme l'app web. */
@Composable
fun MealsScreen(store: FocusStore) {
    val recs = store.list("recipes").sortedBy { it.optString("name").lowercase() }
    val uses = remember(store.rev) { store.cached("uses") { val m = HashMap<String, Int>(); store.sortedDays().forEach { k -> store.dayOrNull(k)?.optJSONArray("meals")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("recipeId")?.takeIf { it.isNotEmpty() }?.let { m[it] = (m[it] ?: 0) + 1 } } }; m } }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        recs.forEach { r -> val m = store.recipePer(r); val col = recipeColor(r.optString("emoji")); val u = uses[r.optString("id")] ?: 0
            Column(Modifier.fillMaxWidth().weight(1f, fill = true).heightIn(min = 132.dp, max = 260.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(col.copy(alpha = .16f), C.card, C.card))).border(1.dp, col.copy(alpha = .22f), RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(col.copy(alpha = .2f)), contentAlignment = Alignment.Center) { T(r.optString("emoji", "🍽️"), 26.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { T(r.optString("name"), 19.sp)
                        T("${r.optInt("portions", 1)} portion${if (r.optInt("portions", 1) > 1) "s" else ""} · ${m.n} ingrédient${if (m.n > 1) "s" else ""}${if (u > 0) " · ajoutée $u fois" else ""}", 13.sp, C.mut, FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.End) { T("${m.kcal.roundToInt()}", 30.sp); T("kcal", 12.sp, C.mut, FontWeight.Bold) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacBar("Protéines", m.p, 60.0, C.pro, Modifier.weight(1f)); MacBar("Glucides", m.c, 120.0, C.carb, Modifier.weight(1f)); MacBar("Lipides", m.f, 50.0, C.fat, Modifier.weight(1f))
                }
            }
        }
        if (recs.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(C.card), contentAlignment = Alignment.Center) {
            T("Crée tes plats une fois avec leurs ingrédients.", 15.sp, C.mut, FontWeight.Bold) }
        Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, Color(0x29FFFFFF), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(C.acc), contentAlignment = Alignment.Center) { T("+", 18.sp, C.bg) }
                Spacer(Modifier.width(10.dp)); T("Nouvelle recette (bientôt)", 16.sp) }
        }
    }
}
