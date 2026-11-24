package com.bbb.flip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ auction browser

@Composable
fun AuctionScreen(state: UiState, vm: AppViewModel) {
    var query by remember { mutableStateOf(state.ahQuery) }
    LaunchedEffect(Unit) {
        vm.loadCategories()
        if (state.ahItems.isEmpty()) vm.loadAuction()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Поиск по аукциону", fontSize = 12.sp) },
            )
            TextButton(
                onClick = {
                    vm.setAhQuery(query)
                    vm.setAhPage(1)
                },
                enabled = !state.ahLoading,
            ) {
                if (state.ahLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                } else {
                    Text("Найти", color = Gold)
                }
            }
        }

        // category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                FilterChip(
                    selected = state.ahClassId == null,
                    onClick = { vm.setAhClass(null); vm.loadAuction() },
                    label = { Text("Всё", fontSize = 11.sp) },
                )
            }
            items(state.categories, key = { it.id }) { cat ->
                FilterChip(
                    selected = state.ahClassId == cat.id,
                    onClick = {
                        vm.setAhClass(if (state.ahClassId == cat.id) null else cat.id)
                        vm.loadAuction()
                    },
                    label = { Text("${CategoryRu.of(cat.name)} ${cat.itemCount}", fontSize = 11.sp) },
                )
            }
        }

        // subcategory + quality + sort
        val subcats = state.categories.firstOrNull { it.id == state.ahClassId }?.subcategories.orEmpty()
        if (subcats.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.ahSubclassId == null,
                        onClick = { vm.setAhSubclass(null); vm.loadAuction() },
                        label = { Text("Все типы", fontSize = 11.sp) },
                    )
                }
                items(subcats, key = { it.id }) { sub ->
                    FilterChip(
                        selected = state.ahSubclassId == sub.id,
                        onClick = {
                            vm.setAhSubclass(if (state.ahSubclassId == sub.id) null else sub.id)
                            vm.loadAuction()
                        },
                        label = { Text("${CategoryRu.of(sub.name)} ${sub.itemCount}", fontSize = 11.sp) },
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(SORTS) { (key, label) ->
                FilterChip(
                    selected = state.ahSort == key,
                    onClick = { vm.setAhSort(key); vm.loadAuction() },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }

        PullRefresh(refreshing = state.ahLoading || state.loading, onRefresh = { vm.loadAuction() }) {
            if (state.ahItems.isEmpty()) {
                EmptyState(if (state.ahLoading) "Загружаю аукцион…" else "Ничего не найдено")
            } else {
                LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Caption("Найдено ${state.ahTotal} предметов · страница ${state.ahPage}")
                    }
                    items(state.ahItems, key = { it.id }) { AuctionRow(it) }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TextButton(onClick = { vm.setAhPage(state.ahPage - 1) }, enabled = state.ahPage > 1) {
                                Text("‹ Назад", color = if (state.ahPage > 1) Gold else Dim)
                            }
                            Text("${state.ahPage}", color = Dim, fontSize = 13.sp)
                            TextButton(onClick = { vm.setAhPage(state.ahPage + 1) }) {
                                Text("Вперёд ›", color = Gold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** API windows look like "7d"; show them in Russian. */
private fun windowLabel(window: String?): String = when (window) {
    null -> "сутки"
    "1d" -> "сутки"
    "7d" -> "7 дней"
    "30d" -> "30 дней"
    else -> window
}

private val SORTS = listOf(
    "best" to "Лучшие",
    "quantity" to "По количеству",
    "price" to "Цена ↑",
    "priceDesc" to "Цена ↓",
    "vendor" to "НПС-прибыль",
    "name" to "По имени",
)

@Composable
private fun AuctionRow(row: SearchItem) {
    val name = ruName(row.blizzardId, row.name)
    val vendorProfit = row.vendorProfit
    PanelCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(row.iconUrl, row.quality)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        color = qualityColor(row.quality),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Caption(
                        listOfNotNull(
                            CategoryRu.of(row.itemSubclass.orEmpty()).takeIf { it.isNotBlank() },
                            "ур. ${row.level}".takeIf { row.level > 0 },
                            "${row.numAuctions} лотов",
                            "${row.quantity} шт",
                        ).joinToString(" · "),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Money(row.effectivePrice ?: row.buyout, Gold, bold = true, fontSize = 14)
                    val discount = row.discountPercent
                    if (discount != null && discount >= 5.0) {
                        Caption("−${Money.percent(discount)} к рынку", Good)
                    } else if (vendorProfit != null) {
                        Caption(
                            "НПС ${if (vendorProfit >= 0) "+" else "−"}${Money.full(kotlin.math.abs(vendorProfit))}",
                            if (vendorProfit > 0) Good else Dim,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Caption("Рыночная цена")
                    Money(row.marketValue, fontSize = 12)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Caption("Цена НПС")
                    Money(row.sellPrice, fontSize = 12)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Caption("Скан")
                    Caption(Money.age(row.lastScanAt))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ statistics

@Composable
fun StatsScreen(state: UiState, vm: AppViewModel) {
    LaunchedEffect(Unit) { if (state.trending.isEmpty()) vm.loadTrending() }

    PullRefresh(refreshing = state.statsLoading, onRefresh = { vm.loadTrending() }) {
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { StatsSummary(state) }
            item { ListHeader("Движения рынка за ${windowLabel(state.trendingWindow)}") }
            if (state.trending.isEmpty()) {
                item { EmptyState(if (state.statsLoading) "Считаю статистику…" else "Нет данных") }
            } else {
                items(state.trending, key = { it.itemId }) { TrendRow(it) }
            }
            item { ListHeader("Сводка по рынку") }
            item { MarketStatsCard(state) }
        }
    }
}

@Composable
private fun StatsSummary(state: UiState) {
    val flipsTotal = state.flips.sumOf { it.profit }
    val bestDe = state.disenchant.firstOrNull()
    val bestCraft = state.crafts.maxByOrNull { it.profitPerDay?.point ?: Long.MIN_VALUE }
    PanelCard {
        Column {
            SectionTitle("Итоги рынка")
            Spacer(Modifier.height(6.dp))
            StatRow("Предметов с ценами", state.house?.itemsPriced?.toString() ?: "—")
            StatRow("Скидок под рынком", "${state.dealsTotal}")
            StatRow("НПС-флипов", "${state.flips.size} на ${Money.short(flipsTotal)}", Good)
            StatRow("Прибыльных распылов", "${state.disenchant.count { (it.margin ?: 0) > 0 }}")
            StatRow("Лучший распыл", bestDe?.let { "${ruName(it.itemId, it.name).take(22)} · ${Money.short(it.margin)}" } ?: "—")
            StatRow(
                "Лучший крафт в день",
                bestCraft?.let { "${ruName(it.result.itemId, it.result.name).take(20)} · ${Money.short(it.profitPerDay?.point)}" } ?: "—",
            )
            StatRow("Обновлено", if (state.lastUpdated > 0) Money.age(java.time.Instant.ofEpochMilli(state.lastUpdated).toString()) else "—")
        }
    }
}

@Composable
private fun TrendRow(t: TrendItem) {
    val up = t.deltaPercent >= 0
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(t.iconUrl, t.quality)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ruName(t.itemId, t.name),
                    color = qualityColor(t.quality),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Caption("${t.numAuctions} лотов · цена ${Money.full(t.marketValueCopper)}")
            }
            Pill(
                "${if (up) "+" else ""}${Money.fmt(t.deltaPercent, 1)}%",
                if (up) Good else Bad,
            )
        }
    }
}

@Composable
private fun MarketStatsCard(state: UiState) {
    val house = state.house
    PanelCard {
        Column {
            StatRow("Мир", state.realmLabel)
            StatRow("Фракция", state.factionLabel)
            StatRow("Регион", state.region.uppercase())
            StatRow("Предметов с ценами", house?.itemsPriced?.toString() ?: "—")
            StatRow("Сканов за 7 дней", house?.sweeps7d?.toString() ?: "—")
            StatRow("Последний скан", Money.age(house?.lastObservedAt))
            StatRow("Комиссия аукциона", "5%")
            Spacer(Modifier.height(6.dp))
            Caption("Все суммы — в золоте, серебре и меди (г/с/м). Данные: bootybaybroker.com.")
        }
    }
}

/** English category/subclass labels from the site, shown in Russian. */
object CategoryRu {
    private val map = mapOf(
        "Armor" to "Броня", "Weapon" to "Оружие", "Consumable" to "Расходники",
        "Container" to "Сумки", "Trade Goods" to "Ресурсы", "Recipe" to "Рецепты",
        "Quest" to "Квестовые", "Miscellaneous" to "Разное", "Projectile" to "Боеприпасы",
        "Quiver" to "Колчаны", "Key" to "Ключи", "Reagent" to "Реагенты", "Gem" to "Камни",
        "Reagent Bag" to "Сумка для реагентов", "Soul Bag" to "Сумка для душ",
        "Enchanting Bag" to "Сумка зачарователя", "Herb Bag" to "Сумка для трав",
        "Mining Bag" to "Сумка рудокопа", "Leather Bag" to "Сумка кожевника",
        "Cloth" to "Ткань", "Leather" to "Кожа", "Mail" to "Кольчуга", "Plate" to "Латы",
        "Shield" to "Щиты", "Misc" to "Прочее", "Axe" to "Топоры", "Sword" to "Мечи",
        "Mace" to "Дробящее", "Dagger" to "Кинжалы", "Staff" to "Посохи", "Polearm" to "Древковое",
        "Bow" to "Луки", "Crossbow" to "Арбалеты", "Gun" to "Огнестрельное", "Wand" to "Жезлы",
        "Thrown" to "Метательное", "Fist Weapon" to "Кистевое", "Bag" to "Сумки",
        "Bandages" to "Бинты", "Potion" to "Зелья", "Elixir" to "Эликсиры", "Flask" to "Настои",
        "Food & Drink" to "Еда и питьё", "Scroll" to "Свитки", "Herb" to "Травы", "Ore" to "Руда",
        "Metal & Stone" to "Металл и камень", "Elemental" to "Первоэлементы", "Enchanting" to "Наложение чар",
        "Alchemy" to "Алхимия", "Blacksmithing" to "Кузнечное дело", "Tailoring" to "Портняжное дело",
        "Leatherworking" to "Кожевничество", "Engineering" to "Инженерное дело", "Cooking" to "Кулинария",
        "First Aid" to "Первая помощь", "Junk" to "Мусор", "Arrow" to "Стрелы", "Bullet" to "Пули",
        "Head" to "Голова", "Chest" to "Грудь", "Legs" to "Ноги", "Feet" to "Ступни",
        "Hands" to "Кисти", "Waist" to "Пояс", "Wrist" to "Запястья", "Back" to "Спина",
        "Shoulder" to "Плечи", "Finger" to "Кольца", "Trinket" to "Аксессуары", "Neck" to "Шея",
        "Off Hand" to "Левая рука", "Held In Off-hand" to "Левая рука", "Two-Hand" to "Двуручное",
        "Main Hand" to "Правая рука", "One-Hand" to "Одноручное", "Shirt" to "Рубашки",
        "Tabard" to "Гербовые накидки", "Cloak" to "Плащи", "Relic" to "Реликвии",
    )

    fun of(label: String): String {
        if (label.isBlank()) return ""
        return map[label] ?: map.entries.firstOrNull { it.key.equals(label, ignoreCase = true) }?.value ?: label
    }
}
