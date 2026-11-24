package com.bbb.flip

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class UiState(
    val loading: Boolean = false,
    val phase: String = "",
    val error: String? = null,
    val tab: Int = 0,
    val flipsTab: Int = 0,
    val region: String = "eu",
    val realmId: Long = 440484,
    val realmLabel: String = "EU PvE",
    val faction: String = "alliance",
    val profession: String = "tailoring",
    val under: Int = 20,
    val minProfit: Long = 0,
    val onlyProfitable: Boolean = true,
    val settingsOpen: Boolean = false,
    val realms: List<RealmGroup> = emptyList(),
    val house: House? = null,
    val flips: List<FlipRow> = emptyList(),
    val deals: List<DealRow> = emptyList(),
    val dealsTotal: Int = 0,
    val disenchant: List<DeRow> = emptyList(),
    val disenchantTotal: Int = 0,
    val crafts: List<CraftRow> = emptyList(),
    val craftsTotal: Int = 0,
    val searchQuery: String = "",
    val searchResults: List<SearchItem> = emptyList(),
    val searching: Boolean = false,
    val lastUpdated: Long = 0,
    // --- auction browser
    val ahQuery: String = "",
    val ahClassId: Int? = null,
    val ahSubclassId: Int? = null,
    val ahQuality: Int? = null,
    val ahSort: String = "best",      // best | price | priceDesc | name | quantity | vendor
    val ahPage: Int = 1,
    val ahItems: List<SearchItem> = emptyList(),
    val ahTotal: Int = 0,
    val ahLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    // --- statistics
    val trending: List<TrendItem> = emptyList(),
    val trendingWindow: String? = null,
    val statsLoading: Boolean = false,
    /** True when the last refresh could not fetch every section; data shown may be older. */
    val refreshFailed: Boolean = false,
) {
    val factionLabel: String get() = if (faction == "horde") "Орда" else "Альянс"
    val professionLabel: String
        get() = Api.PROFESSIONS.firstOrNull { it.first == profession }?.second ?: profession
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val client = SiteClient()
    private val prefs = app.getSharedPreferences("flip", Application.MODE_PRIVATE)
    private val translateGate = kotlinx.coroutines.sync.Mutex()
    private val _state = MutableStateFlow(loadPrefs())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        RuNames.init(app)
        restoreCache()          // show the last scan immediately, then refresh in background
        viewModelScope.launch { translateLoaded() }
    }

    // ---------------------------------------------------------------- disk cache

    /** A refresh takes minutes (each API call needs a fresh WebView page), so the last good
     *  payload is kept on disk and shown at once while the network refresh runs. */
    private fun cacheFile(key: String) = java.io.File(getApplication<Application>().cacheDir, "scan-$key.json")

    private fun readCache(key: String): String? =
        runCatching { cacheFile(key).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun writeCache(key: String, body: String) {
        runCatching { cacheFile(key).writeText(body) }
    }

    private fun cacheKey(s: UiState, suffix: String) = "${s.region}-${s.realmId}-${s.faction}-$suffix"

    private fun restoreCache() {
        val s = _state.value
        val dealsBody = readCache(cacheKey(s, "deals-${s.under}")) ?: return
        runCatching {
            val deals = apiJson.decodeFromString<DealsResponse>(dealsBody)
            val disenchant = readCache(cacheKey(s, "disenchant"))
                ?.let { apiJson.decodeFromString<DisenchantResponse>(it) }
            val crafting = readCache(cacheKey(s, "craft-${s.profession}"))
                ?.let { apiJson.decodeFromString<CraftingResponse>(it) }
            update {
                it.copy(
                    house = deals.house,
                    flips = deals.vendorFlips,
                    deals = deals.rows,
                    dealsTotal = deals.pagination?.totalCount ?: deals.rows.size,
                    disenchant = disenchant?.rows ?: it.disenchant,
                    disenchantTotal = disenchant?.pagination?.totalCount ?: it.disenchantTotal,
                    crafts = crafting?.rows ?: it.crafts,
                    craftsTotal = crafting?.pagination?.totalCount ?: it.craftsTotal,
                    // Age of the cached scan, so "Обновлено" tells the truth until refresh ends.
                    lastUpdated = cacheFile(cacheKey(s, "deals-${s.under}")).lastModified(),
                )
            }
        }
    }

    /** Resolves Russian names for every item currently on screen, then re-renders. */
    private fun translateLoaded() {
        viewModelScope.launch {
            translateGate.withLock {
                val s = _state.value
                val ids = buildList {
                    addAll(s.flips.map { it.itemId })
                    addAll(s.deals.map { it.itemId })
                    addAll(s.disenchant.map { it.itemId })
                    addAll(s.crafts.map { it.result.itemId })
                    addAll(s.crafts.flatMap { c -> c.reagents.map { it.itemId } })
                    addAll(s.ahItems.map { it.blizzardId })
                    addAll(s.searchResults.map { it.blizzardId })
                    addAll(s.trending.map { it.itemId })
                }
                RuNames.resolve(ids)   // RuNames.version drives recomposition
            }
        }
    }

    private fun loadPrefs(): UiState = UiState(
        region = prefs.getString("region", "eu") ?: "eu",
        realmId = prefs.getLong("realmId", 440484L),
        realmLabel = prefs.getString("realmLabel", "EU PvE") ?: "EU PvE",
        faction = prefs.getString("faction", "alliance") ?: "alliance",
        profession = prefs.getString("profession", "tailoring") ?: "tailoring",
        under = prefs.getInt("under", 20),
        minProfit = prefs.getLong("minProfit", 0L),
        onlyProfitable = prefs.getBoolean("onlyProfitable", true),
    )

    private fun save(s: UiState) = prefs.edit()
        .putString("region", s.region)
        .putLong("realmId", s.realmId)
        .putString("realmLabel", s.realmLabel)
        .putString("faction", s.faction)
        .putString("profession", s.profession)
        .putInt("under", s.under)
        .putLong("minProfit", s.minProfit)
        .putBoolean("onlyProfitable", s.onlyProfitable)
        .apply()

    private fun update(block: (UiState) -> UiState) {
        _state.value = block(_state.value)
    }

    fun bindWebView(view: WebView) {
        client.bind(view)
        // Always refresh on start: the cache only fills the screen while this runs.
        if (!_state.value.loading) refresh()
    }

    fun selectTab(index: Int) = update { it.copy(tab = index) }

    fun selectFlipsTab(index: Int) = update { it.copy(flipsTab = index) }

    fun openSettings(open: Boolean) = update { it.copy(settingsOpen = open) }

    fun setFaction(value: String) {
        update { it.copy(faction = value) }
        save(_state.value)
        refresh()
    }

    fun setRealm(group: RealmGroup) {
        update { it.copy(realmId = group.id, realmLabel = group.label) }
        save(_state.value)
        refresh()
    }

    fun setRegion(value: String) {
        update { it.copy(region = value, realms = emptyList()) }
        save(_state.value)
        refresh()
    }

    fun setProfession(value: String) {
        update { it.copy(profession = value) }
        save(_state.value)
        refresh()
    }

    fun setUnder(value: Int) {
        update { it.copy(under = value) }
        save(_state.value)
        refresh()
    }

    fun setMinProfit(value: Long) {
        update { it.copy(minProfit = value) }
        save(_state.value)
    }

    fun setOnlyProfitable(value: Boolean) {
        update { it.copy(onlyProfitable = value) }
        save(_state.value)
    }

    fun refresh() {
        if (_state.value.loading) return
        android.util.Log.d("BBB", "refresh: started")
        viewModelScope.launch {
            val s0 = _state.value
            update { it.copy(loading = true, error = null, phase = "Подключаюсь к аукциону…") }
            val failures = mutableListOf<String>()

            suspend fun <T> loadWith(phase: String, block: suspend () -> T): T? {
                update { it.copy(phase = phase) }
                return try {
                    block()
                } catch (e: Exception) {
                    android.util.Log.w("BBB", "load failed: $phase", e)
                    failures += (e.message ?: "ошибка").take(120)
                    null
                }
            }

            suspend fun <T> load(phase: String, url: String, cache: String? = null, decode: (String) -> T): T? =
                loadWith(phase) {
                    val body = client.get(url)
                    if (cache != null) writeCache(cache, body)
                    decode(body)
                }

            val deals = load(
                "Считаю скидки…",
                Api.dealsUrl(s0.region, s0.realmId, s0.faction, s0.under),
                cacheKey(s0, "deals-${s0.under}"),
            ) { apiJson.decodeFromString<DealsResponse>(it) }
            update { it.copy(phase = "Считаю распыление…") }
            val disenchant = runCatching {
                // Rows are ROI-ordered, so one page already holds every profitable row.
                val parsed = apiJson.decodeFromString<DisenchantResponse>(
                    client.get(Api.disenchantUrl(s0.region, s0.realmId, s0.faction, 1)),
                )
                val merged = DisenchantResponse(
                    rows = parsed.rows.sortedByDescending { it.margin ?: Long.MIN_VALUE },
                    pagination = parsed.pagination,
                )
                writeCache(cacheKey(s0, "disenchant"), apiJson.encodeToString(DisenchantResponse.serializer(), merged))
                merged
            }.onFailure {
                android.util.Log.w("BBB", "load failed: disenchant", it)
                failures += (it.message ?: "ошибка").take(120)
            }.getOrNull()
            update { it.copy(phase = "Считаю крафт…") }
            val crafting = runCatching {
                // Ranked by est. profit/day, so the first page is the whole "worth showing" set.
                val parsed = apiJson.decodeFromString<CraftingResponse>(
                    client.get(Api.craftingUrl(s0.region, s0.realmId, s0.faction, s0.profession, 1)),
                )
                writeCache(cacheKey(s0, "craft-${s0.profession}"), apiJson.encodeToString(CraftingResponse.serializer(), parsed))
                parsed
            }.onFailure {
                android.util.Log.w("BBB", "load failed: craft", it)
                failures += (it.message ?: "ошибка").take(120)
            }.getOrNull()
            // Realms share the WebView, so they run here rather than racing a refresh.
            val realms = if (_state.value.realms.isEmpty()) {
                load("Загружаю миры…", Api.realmsUrl(s0.region)) {
                    apiJson.decodeFromString<RealmResponse>(it).data
                }
            } else null
            // The auction tab must show the same fresh scan, not whatever it loaded earlier.
            val auction = loadWith("Обновляю аукцион…") { fetchAuction(s0) }

            // Freshness is only claimed when every section actually refreshed.
            val everythingOk = deals != null && disenchant != null && crafting != null && auction != null
            update {
                it.copy(
                    loading = false,
                    phase = "",
                    error = if (deals == null && disenchant == null && crafting == null) {
                        failures.firstOrNull() ?: "Не удалось загрузить данные"
                    } else null,
                    house = deals?.house ?: disenchant?.house ?: crafting?.house ?: it.house,
                    flips = deals?.vendorFlips ?: it.flips,
                    deals = deals?.rows ?: it.deals,
                    dealsTotal = deals?.pagination?.totalCount ?: it.dealsTotal,
                    disenchant = disenchant?.rows ?: it.disenchant,
                    disenchantTotal = disenchant?.pagination?.totalCount ?: it.disenchantTotal,
                    crafts = crafting?.rows ?: it.crafts,
                    craftsTotal = crafting?.pagination?.totalCount ?: it.craftsTotal,
                    realms = realms ?: it.realms,
                    ahItems = auction?.let { r -> sortedRows(r.data, s0.ahSort) } ?: it.ahItems,
                    ahTotal = auction?.pagination?.totalCount ?: it.ahTotal,
                    trending = it.trending,
                    trendingWindow = it.trendingWindow,
                    // Never claim freshness for a partial refresh: keep the old stamp so the
                    // "Обновлено" line stays honest and the stale data is obvious.
                    lastUpdated = if (everythingOk) System.currentTimeMillis() else it.lastUpdated,
                    refreshFailed = !everythingOk,
                )
            }
            translateLoaded()
        }
    }

    // ---------------------------------------------------------------- auction browser

    fun setAhQuery(value: String) = update { it.copy(ahQuery = value) }

    fun setAhClass(id: Int?) = update {
        it.copy(ahClassId = id, ahSubclassId = null, ahPage = 1)
    }

    fun setAhSubclass(id: Int?) = update { it.copy(ahSubclassId = id, ahPage = 1) }

    fun setAhQuality(q: Int?) = update { it.copy(ahQuality = q, ahPage = 1) }

    fun setAhSort(sort: String) = update { it.copy(ahSort = sort, ahPage = 1) }

    fun setAhPage(page: Int) {
        update { it.copy(ahPage = page.coerceAtLeast(1)) }
        loadAuction()
    }

    /** Sorts the API supports are limited, so price ordering is done on the fetched page. */
    private fun sortedRows(items: List<SearchItem>, sort: String): List<SearchItem> = when (sort) {
        "price" -> items.sortedWith(compareBy(nullsLast()) { it.effectivePrice ?: it.buyout })
        "priceDesc" -> items.sortedWith(compareByDescending(nullsFirst()) { it.effectivePrice ?: it.buyout })
        "vendor" -> items.sortedByDescending { it.vendorProfit ?: Long.MIN_VALUE }
        // "Лучшие предложения": biggest gap between the cheapest listing and the market price.
        "best" -> items.sortedByDescending { it.discountPercent ?: Double.MIN_VALUE }
        "name" -> items.sortedBy { RuNames.get(it.blizzardId) ?: it.name }
        else -> items
    }

    /** Fetches one page of the auction browser with the current filters. */
    private suspend fun fetchAuction(s: UiState): SearchResponse = apiJson.decodeFromString(
        client.get(
            Api.searchUrl(
                s.region, s.realmId, s.faction,
                query = s.ahQuery,
                classId = s.ahClassId,
                subclassId = s.ahSubclassId,
                quality = s.ahQuality,
                sortBy = if (s.ahSort == "name") "name" else "quantity",
                sortOrder = if (s.ahSort == "name") "asc" else "desc",
                page = s.ahPage,
                limit = 100,
            ),
        ),
    )

    fun loadAuction() {
        if (_state.value.ahLoading) return
        viewModelScope.launch {
            val s = _state.value
            update { it.copy(ahLoading = true, error = null) }
            try {
                val parsed = fetchAuction(s)
                update {
                    it.copy(
                        ahLoading = false,
                        ahItems = sortedRows(parsed.data, s.ahSort),
                        ahTotal = parsed.pagination?.totalCount ?: parsed.data.size,
                    )
                }
                translateLoaded()
            } catch (e: Exception) {
                update { it.copy(ahLoading = false, error = e.message ?: "Аукцион не загрузился") }
            }
        }
    }

    fun loadCategories() {
        if (_state.value.categories.isNotEmpty()) return
        viewModelScope.launch {
            runCatching {
                val s = _state.value
                apiJson.decodeFromString<CategoriesResponse>(
                    client.get(Api.categoriesUrl(s.region, s.realmId, s.faction)),
                ).categories
            }.onSuccess { cats -> update { it.copy(categories = cats) } }
        }
    }

    // ---------------------------------------------------------------- statistics

    fun loadTrending() {
        if (_state.value.statsLoading) return
        viewModelScope.launch {
            val s = _state.value
            update { it.copy(statsLoading = true) }
            runCatching {
                apiJson.decodeFromString<TrendingResponse>(
                    client.get(Api.trendingUrl(s.region, s.realmId, s.faction)),
                )
            }.onSuccess { t ->
                update { it.copy(statsLoading = false, trending = t.items, trendingWindow = t.window) }
                translateLoaded()
            }.onFailure { e ->
                val msg = e.message ?: "Статистика не загрузилась"
                update { it.copy(statsLoading = false, error = msg) }
            }
        }
    }

    fun search(query: String) {
        update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val s = _state.value
            update { it.copy(searching = true, error = null) }
            try {
                val body = client.get(Api.searchUrl(s.region, s.realmId, s.faction, query))
                val parsed = apiJson.decodeFromString<SearchResponse>(body)
                update { it.copy(searching = false, searchResults = parsed.data) }
                translateLoaded()
            } catch (e: Exception) {
                update { it.copy(searching = false, error = e.message ?: "Поиск не удался") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
