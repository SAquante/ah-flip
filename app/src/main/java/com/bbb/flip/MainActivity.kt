package com.bbb.flip

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1300),
    background = Color(0xFF11151C),
    onBackground = Color(0xFFE7E9EE),
    surface = Panel,
    onSurface = Color(0xFFE7E9EE),
)

class MainActivity : ComponentActivity() {

    private lateinit var vm: AppViewModel
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]

        // A small *visible* WebView on top: it holds the Cloudflare clearance for every API
        // call. Fully hidden or zero-alpha WebViews get their timers throttled, which makes
        // fetch() promises stall, so it stays a few dp of real, unobstructed surface.
        val side = (6 * resources.displayMetrics.density).toInt()
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(side, side).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
        }
        val root = FrameLayout(this).apply {
            addView(
                androidx.compose.ui.platform.ComposeView(this@MainActivity).apply {
                    setContent {
                        MaterialTheme(colorScheme = Scheme) {
                            val state by vm.state.collectAsState()
                            AppRoot(state, vm)
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(webView)
        }
        setContentView(root)
        vm.bindWebView(webView)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

@Composable
private fun AppRoot(state: UiState, vm: AppViewModel) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { BottomBar(state, vm) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.settingsOpen -> SettingsScreen(state, vm)
                    state.tab == 0 -> SummaryScreen(state)
                    state.tab == 1 -> AuctionScreen(state, vm)
                    state.tab == 2 -> FlipsScreen(state, vm)
                    state.tab == 3 -> DisenchantScreen(state, vm)
                    state.tab == 4 -> CraftScreen(state, vm)
                    state.tab == 5 -> StatsScreen(state, vm)
                    else -> SearchScreen(state, vm)
                }
                // Only block the screen when there is nothing to show; a refresh with cached
                // data runs silently behind the UI.
                if (state.loading && state.lastUpdated == 0L) LoadingOverlay(state.phase)
                if (state.loading && state.lastUpdated > 0L) RefreshStrip(state.phase)
                RuNames.progress?.let { (done, total) -> TranslationBar(done, total) }
                state.error?.let { ErrorBanner(it) { vm.refresh() } }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(phase: String) {
    Box(Modifier.fillMaxSize().background(Color(0xE611151C)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(phase, color = Dim, fontSize = 13.sp)
        }
    }
}

/** Thin "refreshing" hint shown while cached data stays on screen. */
@Composable
private fun RefreshStrip(phase: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(bottom = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1D2431))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Gold)
            Spacer(Modifier.width(8.dp))
            Text(phase.ifBlank { "Обновляю…" }, color = Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TranslationBar(done: Int, total: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1D2431))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Gold)
            Spacer(Modifier.width(8.dp))
            Text("Перевожу названия: $done / $total", color = Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF3A1D22))
                .clickable { retry() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ошибка загрузки", color = Bad, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(message, color = Color(0xFFE7E9EE), fontSize = 11.sp, maxLines = 3)
            }
            Spacer(Modifier.width(8.dp))
            Text("Повторить", color = Gold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BottomBar(state: UiState, vm: AppViewModel) {
    val tabs = listOf("Обзор", "Аук", "Флипы", "Распыл", "Крафт", "Стата", "Поиск")
    Column {
        HorizontalDivider(color = PanelAlt)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1117))
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = !state.settingsOpen && state.tab == index
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openSettings(false); vm.selectTab(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        color = if (selected) Gold else Dim,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .background(if (selected) Gold else Color.Transparent, RoundedCornerShape(1.dp)),
                    )
                }
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.loading) { vm.refresh() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⟳", color = if (state.loading) Dim else Gold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(16.dp).height(2.dp))
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.openSettings(true) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚙", color = if (state.settingsOpen) Gold else Dim, fontSize = 13.sp)
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(if (state.settingsOpen) Gold else Color.Transparent, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}
