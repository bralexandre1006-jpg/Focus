package com.alexandre.focus.nat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.alexandre.focus.nat.data.FocusStore
import com.alexandre.focus.nat.ui.FocusApp

class MainActivity : ComponentActivity() {
    private lateinit var store: FocusStore
    private var version by mutableIntStateOf(0)

    // Import du JSON exporté par l'app actuelle (le temps du chantier, les deux apps sont séparées).
    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        val ok = store.importJson(text)
        com.alexandre.focus.nat.ui.C.setAccent(store.root.optJSONObject("settings")?.optString("accent"))
        Toast.makeText(this, if (ok) "Données importées" else "Fichier illisible", Toast.LENGTH_SHORT).show()
        version++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusStore(this)
        com.alexandre.focus.nat.ui.C.setAccent(store.root.optJSONObject("settings")?.optString("accent"))
        setContent {
            val v = version
            // toute modification des données redessine l'écran
            FocusApp(store, v, onChanged = { version++ }, onImport = { pick.launch(arrayOf("application/json", "text/plain", "*/*")) })
        }
    }
}
