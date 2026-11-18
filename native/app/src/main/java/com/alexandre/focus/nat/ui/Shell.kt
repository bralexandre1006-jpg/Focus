package com.alexandre.focus.nat.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.alexandre.focus.nat.data.FocusStore
import kotlinx.coroutines.delay

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

// logo Focus (mêmes tracés que l'écran d'ouverture web, viewBox 255 155 535 670)
private val LOGO_A by lazy { p("M305 427 L772 173 L772 290 C772 365 720 427 655 427 Z") }
private val LOGO_B by lazy { p("M272 495 L772 495 L692 578 C648 622 588 642 532 612 L532 808 C522 760 498 705 455 665 Z") }

/** Ouverture : les deux morceaux du logo arrivent en diagonale, petit rebond, puis zoom de sortie. */
@Composable
fun Splash(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }; val b = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }; val out = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val spring = CubicBezierEasing(.2f, 1.25f, .4f, 1f)
        kotlinx.coroutines.coroutineScope {
            val x = kotlinx.coroutines.async { a.animateTo(1f, tween(550, easing = spring)) }
            delay(120); b.animateTo(1f, tween(550, easing = spring)); x.await()
        }
        pop.animateTo(1.16f, tween(180)); pop.animateTo(1f, tween(220))
        delay(80); out.animateTo(1f, tween(380, easing = CubicBezierEasing(.6f, 0f, .8f, .2f)))
        onDone()
    }
    Box(Modifier.fillMaxSize().background(C.bg).graphicsLayer { alpha = 1f - out.value }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp, 130.dp).graphicsLayer { val s = pop.value * (1f + out.value * 6f); scaleX = s; scaleY = s }) {
            val k = size.width / 535f
            withTransform({ scale(k, k, Offset.Zero); translate(-255f, -155f) }) {
                translate((1 - a.value) * 900f, -(1 - a.value) * 480f) { drawPath(LOGO_A, C.acc, alpha = a.value.coerceIn(0f, 1f)) }
                translate(-(1 - b.value) * 900f, (1 - b.value) * 480f) { drawPath(LOGO_B, C.acc, alpha = b.value.coerceIn(0f, 1f)) }
            }
        }
    }
}

@Composable
fun Logo(modifier: Modifier) = Canvas(modifier) {
    val k = size.width / 535f
    withTransform({ scale(k, k, Offset.Zero); translate(-255f, -155f) }) { drawPath(LOGO_A, C.acc); drawPath(LOGO_B, C.acc) }
}

private val TAB_ICONS by lazy {
    listOf(
        "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z",
        "M7 2v20M4 2v6a3 3 0 0 0 6 0V2M17 22V2c-2.5 1-4 3.5-4 7v5h4",
        "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11",
        "M4 20V10M10 20V4M16 20v-7M22 20H2",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM16 2v4M8 2v4M3 10h18"
    ).map { p(it) }
}

@Composable
fun Icon24(path: Path, color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(22.dp)) {
    val k = size.width / 24f
    scale(k, k, Offset.Zero) { drawPath(path, color, style = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

/** Barre d'onglets : pastille de couleur sous l'onglet actif, déplacement court sans rebond. */
@Composable
fun TabBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(horizontal = 22.dp).height(58.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xF716161D)).border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(22.dp)).padding(5.dp)
    ) {
        val w = maxWidth / 5
        val x by animateDpAsState(w * sel, tween(200), label = "tab")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(17.dp)).background(C.acc))
        Row(Modifier.fillMaxSize()) {
            TAB_ICONS.forEachIndexed { i, ic ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSel(i) }, contentAlignment = Alignment.Center) {
                    Icon24(ic, if (i == sel) C.bg else C.mut)
                }
            }
        }
    }
}

@Composable
fun FocusApp(store: FocusStore, version: Int, onChanged: () -> Unit, onImport: () -> Unit) {
    var splash by rememberSaveable { mutableStateOf(true) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Box(Modifier.fillMaxSize().padding(bottom = 76.dp).navigationBarsPadding()) {
            when (tab) {
                0 -> HomeScreen(store, version, onChanged, onImport)
                1 -> MealsScreen(store)
                2 -> GymScreen(store)
                3 -> StatsScreen(store)
                else -> HistoryScreen(store)
            }
        }
        if (store.hasData) TabBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
        if (splash) Splash { splash = false }
    }
}
