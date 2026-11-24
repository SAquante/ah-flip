package com.bbb.flip

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Russian item names for Blizzard item ids, fetched from Wowhead's public tooltip API and
 * cached on disk, so each item is translated once.
 *
 * Items this private server invented (ids in the 28xxxx range) do not exist in any game
 * database — the market site itself returns `localizedName: null` for them — so they keep
 * their English name, which is also what the game client shows.
 */
object RuNames {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0"
    private const val NO_TRANSLATION = ""   // cached "asked, nothing exists" marker
    private const val MAX_PER_PASS = 60

    private val map = HashMap<Long, String>()
    private val asked = HashSet<Long>()
    private val semaphore = Semaphore(10)
    private var prefs: android.content.SharedPreferences? = null

    /** Snapshot state so any screen showing a name recomposes when translations arrive. */
    var version by mutableStateOf(0)
        private set

    /** Non-null while names are being fetched: (done, total). */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("ru-names", Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) ->
            val id = k.toLongOrNull() ?: return@forEach
            asked.add(id)
            if (v is String && v.isNotBlank()) map[id] = v
        }
    }

    fun get(id: Long?): String? = id?.let { map[it] }

    /** Fetches the names still missing among [ids]. */
    suspend fun resolve(ids: Collection<Long?>) {
        val missing = ids.filterNotNull().distinct()
            .filter { !asked.contains(it) }
            // Cap per pass: translating hundreds of rows in one go keeps the UI busy for minutes
            // and looks like a freeze. The rest is picked up on the next screen change.
            .take(MAX_PER_PASS)
        if (missing.isEmpty()) return
        progress = 0 to missing.size
        var done = 0
        var added = 0
        var lastReport = 0L
        coroutineScope {
            for (id in missing) launch {
                val name = fetch(id)
                synchronized(this@RuNames) {
                    asked.add(id)
                    if (name != null) {
                        map[id] = name
                        added++
                    }
                    done++
                }
                // Report at most ~4 times a second: one update per item recomposes every list.
                val now = System.currentTimeMillis()
                if (now - lastReport > 250 || done == missing.size) {
                    lastReport = now
                    progress = done to missing.size
                }
            }
        }
        progress = null
        save()
        if (added > 0) version++
    }

    private fun save() {
        val p = prefs ?: return
        p.edit().apply {
            asked.forEach { id ->
                putString(id.toString(), map[id] ?: NO_TRANSLATION)
            }
        }.apply()
    }

    private suspend fun fetch(id: Long): String? = semaphore.withPermit {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(12_000) {
                runCatching {
                    val url = URL("https://nether.wowhead.com/tooltip/item/$id?dataEnv=1&locale=ruRU")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("user-agent", UA)
                        setRequestProperty("accept", "application/json")
                    }
                    try {
                        // 404 means this server's custom item: nothing to translate.
                        if (conn.responseCode != 200) return@runCatching null
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val name = JSONObject(body).optString("name").takeIf { it.isNotBlank() }
                        // Wowhead answers in English when it has no Russian name.
                        name?.takeIf { it.any { c -> c in 'А'..'я' || c == 'Ё' || c == 'ё' } }
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()
            }
        }
    }
}
