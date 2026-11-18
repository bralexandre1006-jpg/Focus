package com.alexandre.focus.nat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Données de Focus : le même objet JSON que l'app web (`localStorage['focus.v1']`).
 * On garde l'objet brut pour ne jamais perdre un champ que la version native ne connaît pas encore.
 * Fichier : filesDir/focus-data.json, écrit aussi par l'app web (pont) pour la bascule.
 */
class FocusStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "focus-data.json")
    var root: JSONObject = load()
        private set

    private fun load(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    } catch (e: Exception) { JSONObject() }

    val hasData get() = (root.optJSONObject("days")?.length() ?: 0) > 0 || root.optBoolean("onboarded")

    fun importJson(text: String): Boolean = try {
        val o = JSONObject(text); root = o; save(); true
    } catch (e: Exception) { false }

    fun save() {
        val tmp = File(ctx.filesDir, "focus-data.json.tmp")
        tmp.writeText(root.toString()); tmp.renameTo(file)
    }

    // ---------- accès ----------
    fun profile(): JSONObject = root.optJSONObject("profile") ?: JSONObject().also { root.put("profile", it) }
    fun days(): JSONObject = root.optJSONObject("days") ?: JSONObject().also { root.put("days", it) }
    fun day(k: String = today()): JSONObject = days().optJSONObject(k) ?: JSONObject().apply {
        put("weight", JSONObject.NULL); put("workout", JSONObject.NULL); put("calories", 0); put("steps", 0); put("meals", JSONArray())
        days().put(k, this)
    }
    fun dayOrNull(k: String): JSONObject? = days().optJSONObject(k)

    val stepGoal get() = profile().optInt("stepGoal", 8000).takeIf { it > 0 } ?: 8000
    val kcalGoal get() = profile().optInt("calGoal", 2200).takeIf { it > 0 } ?: 2200
    val proteinGoal get() = profile().optInt("proteinGoal", 0)
    val targetWeight get() = profile().optDouble("targetWeight", Double.NaN)
    val startWeight get() = profile().optDouble("startWeight", Double.NaN)

    fun steps(k: String = today()) = dayOrNull(k)?.optInt("steps", 0) ?: 0
    fun weight(k: String = today()): Double? = dayOrNull(k)?.let { if (it.isNull("weight")) null else it.optDouble("weight").takeIf { w -> w > 0 } }

    /** Dernier poids connu, en remontant le temps. */
    fun lastWeight(): Double? = days().keys().asSequence().toList().sortedDescending().firstNotNullOfOrNull { weight(it) }

    data class Macros(val kcal: Double, val p: Double, val c: Double, val f: Double, val n: Int)
    fun macros(k: String = today()): Macros {
        val d = dayOrNull(k) ?: return Macros(0.0, 0.0, 0.0, 0.0, 0)
        val m = d.optJSONArray("meals") ?: JSONArray()
        var kc = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
        for (i in 0 until m.length()) { val x = m.getJSONObject(i); kc += x.optDouble("kcal", 0.0); p += x.optDouble("p", 0.0); c += x.optDouble("c", 0.0); f += x.optDouble("f", 0.0) }
        if (m.length() == 0) kc = d.optDouble("calories", 0.0)
        return Macros(kc, p, c, f, m.length())
    }

    fun setWeight(w: Double?) {
        val d = day(); if (w == null) d.put("weight", JSONObject.NULL) else d.put("weight", w)
        d.put("wsrc", "man"); save()
    }

    /** Même règle que l'app web : un jour compte s'il contient une info notée ou si l'objectif de pas est atteint. */
    fun noted(k: String): Boolean {
        val d = dayOrNull(k) ?: return false
        return (weight(k) ?: 0.0) > 0 || (d.optJSONArray("meals")?.length() ?: 0) > 0 || d.optDouble("calories", 0.0) > 0 ||
            !d.isNull("workout") && d.has("workout") || d.has("off") && !d.isNull("off") || d.optInt("steps", 0) >= stepGoal || d.has("frz")
    }
    fun streak(): Int {
        var k = today(); if (!noted(k)) k = addDays(k, -1)
        var n = 0; while (noted(k)) { n++; k = addDays(k, -1) }
        return n
    }

    // ---------- séance ----------
    /** Jour de séance prévu (jours fixes du profil ; décalages et programme : à porter). */
    fun isTrainingDay(k: String = today()): Boolean {
        val d = dayOrNull(k); if (d != null && d.has("off") && !d.isNull("off")) return false
        val sh = root.optJSONObject("shifts")?.optString(k, ""); if (sh == "gym") return true; if (sh == "rest") return false
        val td = profile().optJSONArray("trainingDays") ?: return false
        val wd = weekday(k); for (i in 0 until td.length()) if (td.optInt(i) == wd) return true
        return false
    }
    fun workout(k: String = today()): Boolean? = dayOrNull(k)?.let { if (!it.has("workout") || it.isNull("workout")) null else it.optBoolean("workout") }
    fun setWorkout(v: Boolean?) { day().put("workout", v ?: JSONObject.NULL); save() }
    fun isOff(k: String = today()) = dayOrNull(k)?.let { it.has("off") && !it.isNull("off") } ?: false

    fun list(key: String): List<JSONObject> { val a = root.optJSONArray(key) ?: return emptyList(); return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }

    /** Kcal et macros par portion d'une recette (même calcul que l'app web). */
    fun recipePer(r: JSONObject): Macros {
        val ing = r.optJSONArray("ingredients") ?: JSONArray(); var kc = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
        for (i in 0 until ing.length()) { val x = ing.getJSONObject(i); val g = x.optDouble("grams", x.optDouble("qty", 100.0)) / 100.0
            kc += g * x.optDouble("kcal", 0.0); p += g * x.optDouble("p", 0.0); c += g * x.optDouble("c", 0.0); f += g * x.optDouble("f", 0.0) }
        val n = r.optDouble("portions", 1.0).coerceAtLeast(1.0)
        return Macros(kc / n, p / n, c / n, f / n, ing.length())
    }

    fun sortedDays(): List<String> = days().keys().asSequence().toList().sortedDescending()

    companion object {
        private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun today(): String = fmt.format(Date())
        fun weekday(k: String): Int { val c = Calendar.getInstance(); c.time = fmt.parse(k)!!; return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 }
        fun parse(k: String): Date = fmt.parse(k)!!
        fun addDays(k: String, n: Int): String { val c = Calendar.getInstance(); c.time = fmt.parse(k)!!; c.add(Calendar.DAY_OF_YEAR, n); return fmt.format(c.time) }
    }
}
