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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

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

    /** Set when GitHub reports a newer release; drives the update dialog. */
    private val updateState = mutableStateOf<Updates.Release?>(null)
    private var updateShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]

        // Full-size WebView underneath the Compose UI: it solves the Cloudflare challenge and
        // renders each API document for us to read. It must be a real on-screen view, because a
        // hidden or tiny WebView gets its renderer frozen and documents stop loading.
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(0xFF11151C.toInt())
        }
        val root = FrameLayout(this).apply {
            addView(webView)
            addView(
                androidx.compose.ui.platform.ComposeView(this@MainActivity).apply {
                    setContent {
                        MaterialTheme(colorScheme = Scheme) {
                            val state by vm.state.collectAsState()
                            AppRoot(state, vm)
                            updateState.value?.let { release ->
                                UpdateDialog(
                                    release = release,
                                    onInstall = {
                                        Updates.downloadAndInstall(this@MainActivity, release)
                                        updateState.value = null
                                    },
                                    onDismiss = { updateState.value = null },
                                )
                            }
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(root)
        vm.bindWebView(webView)
        checkForUpdate()
    }

    /**
     * Asks GitHub Releases for a newer build once per launch. Silent on failure — an update check
     * must never get in the way of using the app.
     */
    private fun checkForUpdate() {
        if (updateShown) return
        lifecycleScope.launch {
            val release = Updates.check() ?: return@launch
            if (isFinishing || isDestroyed) return@launch
            updateShown = true
            // Compose reads this state; setting it from a coroutine off the main thread would not
            // trigger recomposition reliably.
            runOnUiThread { updateState.value = release }
        }
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
                    state.tab == 0 -> SummaryScreen(state, vm)
                    state.tab == 1 -> AuctionScreen(state, vm)
                    state.tab == 2 -> FlipsScreen(state, vm)
                    state.tab == 3 -> DisenchantScreen(state, vm)
                    state.tab == 4 -> CraftScreen(state, vm)
                    state.tab == 5 -> StatsScreen(state, vm)
                    else -> SearchScreen(state, vm)
                }
                // A refresh takes minutes (each request is a WebView navigation), so it must be
                // obvious that it is running — otherwise the app looks frozen or "not updating".
                if (state.loading && state.lastUpdated == 0L) LoadingOverlay(state.phase)
                if (state.loading && state.lastUpdated > 0L) RefreshStrip(state.phase)
                RuNames.progress?.let { (done, total) -> TranslationBar(done, total) }
                state.error?.let { ErrorBanner(it) { vm.refresh() } }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    release: Updates.Release,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = Gold,
        textContentColor = Color(0xFFE7E9EE),
        title = { Text("Вышло обновление ${release.version}") },
        text = {
            Column {
                Text(
                    "Установлено: ${BuildConfig.VERSION_NAME}",
                    color = Dim,
                    fontSize = 12.sp,
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(release.notes, fontSize = 12.sp, maxLines = 12)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "APK скачается и откроется системный установщик.",
                    color = Dim,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall) { Text("Скачать и установить", color = Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже", color = Dim) }
        },
    )
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

/** Full-width "refreshing" bar while cached data stays readable underneath. */
@Composable
private fun RefreshStrip(phase: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 62.dp)
                .background(Color(0xFF1D2431))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Gold)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(phase.ifBlank { "Обновляю…" }, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Занимает 2–3 минуты, данные на экране пока старые", color = Dim, fontSize = 10.sp)
            }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weighted tabs: with 9 items a fixed padding pushes the last ones off-screen.
            tabs.forEachIndexed { index, label ->
                val selected = !state.settingsOpen && state.tab == index
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openSettings(false); vm.selectTab(index) }
                        .padding(vertical = 4.dp),
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
                    .weight(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.loading) { vm.refresh() }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⟳", color = if (state.loading) Dim else Gold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(16.dp).height(2.dp))
            }
            Column(
                Modifier
                    .weight(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.openSettings(true) }
                    .padding(vertical = 4.dp),
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
