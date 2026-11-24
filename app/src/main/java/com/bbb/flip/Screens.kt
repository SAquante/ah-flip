package com.bbb.flip

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

// ------------------------------------------------------------------ summary

@Composable
fun SummaryScreen(state: UiState, vm: AppViewModel) {
    PullRefresh(refreshing = state.loading, onRefresh = { vm.refresh() }) {
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { MarketCard(state) }
            item { PotentialCard(state) }

            if (state.flips.isNotEmpty()) {
                item { ListHeader("Топ НПС-флипов") }
                items(state.flips.take(3), key = { "f${it.itemId}" }) { FlipCard(it) }
            }
            if (state.disenchant.isNotEmpty()) {
                item { ListHeader("Лучшее распыление") }
                items(state.disenchant.take(3), key = { "d${it.itemId}" }) { DeCard(it) }
            }
            if (state.crafts.isNotEmpty()) {
                item { ListHeader("Лучший крафт (${state.professionLabel})") }
                items(state.crafts.take(3), key = { "c${it.recipeId}" }) { CraftCard(it) }
            }
        }
    }
}

@Composable
private fun MarketCard(state: UiState) {
    val house = state.house
    PanelCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Рынок")
                Spacer(Modifier.weight(1f))
                if (house != null) Pill(verdictLabel(house.verdict), if (house.verdict == "current") Good else Dim)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${state.realmLabel} · ${state.factionLabel} · ${state.region.uppercase()}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            StatRow("Предметов с ценами", house?.itemsPriced?.toString() ?: "—")
            StatRow("Скан аукциона", Money.age(house?.lastObservedAt))
            StatRow("Скидки под рынком", "${state.dealsTotal} шт")
            StatRow("Предметов на распыл", "${state.disenchantTotal} шт")
            StatRow("Рецептов", "${state.craftsTotal} шт")
            StatRow("Прибыльных распылов", "${state.disenchant.count { (it.margin ?: 0) > 0 }} шт", Good)
            if (state.lastUpdated > 0) {
                StatRow(
                    "Обновлено",
                    Money.age(java.time.Instant.ofEpochMilli(state.lastUpdated).toString()),
                    if (state.refreshFailed) Bad else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state.refreshFailed) {
                Caption("Не удалось обновить — показаны прошлые данные", Bad)
            }
        }
    }
}

@Composable
private fun PotentialCard(state: UiState) {
    val flipTotal = state.flips.sumOf { it.profit }
    val flipCount = state.flips.count { it.unitProfit > 0 }
    val bestDe = state.disenchant.maxByOrNull { it.margin ?: Long.MIN_VALUE }
    val bestCraft = state.crafts.maxByOrNull { it.profitPerDay?.point ?: Long.MIN_VALUE }
    PanelCard {
        Column {
            SectionTitle("Потенциал")
            Spacer(Modifier.height(6.dp))
            StatRow("НПС-флипы: всего", Money.short(flipTotal), Good)
            Caption("$flipCount позиций дешевле, чем платит НПС")
            Spacer(Modifier.height(6.dp))
            StatRow("Лучший распыл", bestDe?.let { "${ruName(it.itemId, it.name).take(24)} · ${Money.short(it.margin)}" } ?: "—", Good)
            Spacer(Modifier.height(6.dp))
            StatRow(
                "Лучший крафт в день",
                bestCraft?.let { "${ruName(it.result.itemId, it.result.name).take(22)} · ${Money.short(it.profitPerDay?.point)}" } ?: "—",
                Good,
            )
        }
    }
}

// ------------------------------------------------------------------ flips

@Composable
fun FlipsScreen(state: UiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Freshness + explicit refresh: flips age fast, so the age is always on screen.
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Caption("Скан аукциона: ${Money.age(state.house?.lastObservedAt)}")
                Caption(
                    "Обновлено: ${if (state.lastUpdated > 0) Money.age(java.time.Instant.ofEpochMilli(state.lastUpdated).toString()) else "—"}",
                    if (state.refreshFailed) Bad else Dim,
                )
                if (state.refreshFailed) Caption("Не удалось обновить — показаны прошлые данные", Bad)
            }
            TextButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                } else {
                    Text("⟳ Обновить", color = Gold, fontSize = 12.sp)
                }
            }
        }
        ChipsRow {
            FilterChip(
                selected = state.flipsTab == 0,
                onClick = { vm.selectFlipsTab(0) },
                label = { Text("НПС-флипы") },
            )
            FilterChip(
                selected = state.flipsTab == 1,
                onClick = { vm.selectFlipsTab(1) },
                label = { Text("Скидки рынка") },
            )
        }
        ChipsRow {
            Text("Мин. прибыль:", color = Dim, fontSize = 11.sp)
            listOf(0L, 100L, 10_000L, 100_000L).forEach { threshold ->
                FilterChip(
                    selected = state.minProfit == threshold,
                    onClick = { vm.setMinProfit(threshold) },
                    label = { Text(if (threshold == 0L) "все" else Money.short(threshold)) },
                )
            }
        }
        PullRefresh(refreshing = state.loading, onRefresh = { vm.refresh() }) {
            if (state.flipsTab == 0) FlipsList(state) else DealsList(state)
        }
    }
}

@Composable
private fun FlipsList(state: UiState) {
    val rows = state.flips.filter { it.unitProfit >= state.minProfit }
    if (rows.isEmpty()) {
        EmptyState("Нет позиций: рынок пуст или фильтр слишком строгий")
        return
    }
    LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Caption(
                "Купить на аукционе и сдать НПС. Прибыль — за все лоты по минимальной цене.",
            )
        }
        items(rows, key = { it.itemId }) { FlipCard(it) }
    }
}

@Composable
private fun FlipCard(row: FlipRow) {
    var open by remember { mutableStateOf(false) }
    PanelCard(Modifier.clickable { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(row.iconUrl, row.quality)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ruName(row.itemId, row.name),
                        color = qualityColor(row.quality),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Caption("Купить ${Money.full(row.minBuyout)} · НПС ${Money.full(row.vendorSellPrice)}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Money(row.profit, Good, bold = true, fontSize = 14)
                    Caption("${Money.full(row.unitProfit)}/шт")
                }
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = PanelAlt)
                Spacer(Modifier.height(6.dp))
                StatRow("Цена за штуку", Money.full(row.minBuyout))
                StatRow("Цена НПС", Money.full(row.vendorSellPrice))
                StatRow("Прибыль за штуку", Money.full(row.unitProfit), Good)
                StatRow("Доступно лотов", "${row.unitsAtMinimum} шт")
                StatRow("Итого прибыль", Money.full(row.profit), Good)
                StatRow("Окупаемость", row.roi?.let { Money.percent(it) } ?: "—")
                StatRow("Скан", Money.age(row.observedAt))
            }
        }
    }
}

@Composable
private fun DealsList(state: UiState) {
    val rows = state.deals.filter { (it.resaleProfit ?: it.saving) >= state.minProfit }
    if (rows.isEmpty()) {
        EmptyState("Скидок под рынком не найдено")
        return
    }
    LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Caption("Лоты дешевле рыночной цены. Перепродажа с учётом комиссии аукциона.")
        }
        items(rows, key = { it.itemId }) { DealCard(it) }
    }
}

@Composable
private fun DealCard(row: DealRow) {
    var open by remember { mutableStateOf(false) }
    PanelCard(Modifier.clickable { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(row.iconUrl, row.quality)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ruName(row.itemId, row.name),
                        color = qualityColor(row.quality),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Caption("Сейчас ${Money.full(row.minBuyout)} · рынок ${Money.full(row.reference)}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Pill("−${Money.percent(row.discountPercent)}", Good)
                    Spacer(Modifier.height(2.dp))
                    Caption("выгода ${Money.short(row.saving)}")
                }
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = PanelAlt)
                Spacer(Modifier.height(6.dp))
                StatRow("Минимальная цена", Money.full(row.minBuyout))
                StatRow("Ориентир рынка", Money.full(row.reference))
                StatRow("Перепродажа (нетто)", Money.full(row.resaleNet), Good)
                StatRow("Прибыль с перепродажи", Money.full(row.resaleProfit), Good)
                StatRow("Продаж в день", row.soldPerDay?.let { Money.fmt(it, 1) } ?: "нет данных")
                StatRow("Лотов / штук", "${row.listings} / ${row.quantity}")
                StatRow("Штук по мин. цене", row.unitsAtMinimum.toString())
                StatRow("Минимум за 7 дней", Money.full(row.sevenDayLow))
                StatRow("Скан", Money.age(row.observedAt))
            }
        }
    }
}

// ------------------------------------------------------------------ disenchant

@Composable
fun DisenchantScreen(state: UiState, vm: AppViewModel) {
    val rows = state.disenchant
        .filter { !state.onlyProfitable || (it.margin ?: 0) > 0 }
        .filter { (it.margin ?: 0) >= state.minProfit }
    Column(Modifier.fillMaxSize()) {
        ChipsRow {
            FilterChip(
                selected = state.onlyProfitable,
                onClick = { vm.setOnlyProfitable(!state.onlyProfitable) },
                label = { Text("Только в плюс") },
            )
            FilterChip(
                selected = state.minProfit == 10_000L,
                onClick = { vm.setMinProfit(if (state.minProfit == 10_000L) 0 else 10_000L) },
                label = { Text("от 1г") },
            )
        }
        PullRefresh(refreshing = state.loading, onRefresh = { vm.refresh() }) {
            if (rows.isEmpty()) {
                EmptyState("Нет выгодных распылов при текущем фильтре")
            } else {
                LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Caption("Купить зелёный/синий предмет, распылить и продать материалы (за вычетом комиссии).") }
                    items(rows, key = { it.itemId }) { DeCard(it) }
                }
            }
        }
    }
}

@Composable
private fun DeCard(row: DeRow) {
    var open by remember { mutableStateOf(false) }
    PanelCard(Modifier.clickable { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(row.iconUrl, row.quality)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ruName(row.itemId, row.name),
                        color = qualityColor(row.quality),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Caption("Купить ${Money.full(row.minBuyout)} · распыл ≈ ${Money.full(row.netValue)}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Money(row.margin, if ((row.margin ?: 0) > 0) Good else Bad, bold = true, fontSize = 14)
                    Caption(row.roi?.let { "ROI ${Money.percent(it)}" } ?: "—")
                }
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = PanelAlt)
                Spacer(Modifier.height(6.dp))
                StatRow("Уровень предмета", "ур. ${row.itemLevel}")
                StatRow("Цена покупки", Money.full(row.minBuyout))
                StatRow("Ожидаемая стоимость", Money.full(row.value))
                StatRow("Нетто после комиссии", Money.full(row.netValue))
                StatRow("Прибыль", Money.full(row.margin), if ((row.margin ?: 0) > 0) Good else Bad)
                StatRow("Цена НПС", Money.full(row.vendorSellPrice))
                if (row.partiallyPriced) Caption("Часть материалов без цен — оценка неполная", Bad)
                Spacer(Modifier.height(6.dp))
                SectionTitle("Что выпадает")
                row.results.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Caption(
                            "${ruName(r.itemId, r.name)} · ${Money.fmt(r.probability * 100, 0)}% · ~${Money.fmt(r.quantityMean, 1)} шт",
                        )
                        Money(r.unitPrice?.let { (it * r.quantityMean).toLong() }, fontSize = 11)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ crafting

@Composable
fun CraftScreen(state: UiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(Api.PROFESSIONS) { (slug, label) ->
                FilterChip(
                    selected = state.profession == slug,
                    onClick = { vm.setProfession(slug) },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
        val rows = state.crafts
            .filter { !state.onlyProfitable || ((it.profit ?: 0) > 0) }
            .filter { (it.profit ?: 0) >= state.minProfit }
        PullRefresh(refreshing = state.loading, onRefresh = { vm.refresh() }) {
            if (rows.isEmpty()) {
                EmptyState("Прибыльных рецептов нет")
            } else {
                LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Caption("Прибыль — после комиссии аукциона, по текущим ценам реагентов.") }
                    items(rows, key = { it.recipeId }) { CraftCard(it) }
                }
            }
        }
    }
}

@Composable
private fun CraftCard(row: CraftRow) {
    var open by remember { mutableStateOf(false) }
    PanelCard(Modifier.clickable { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(row.result.iconUrl, row.result.quality)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ruName(row.result.itemId, row.result.name) + if (row.result.yield > 1) " ×${row.result.yield}" else "",
                        color = qualityColor(row.result.quality),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Caption("Себестоимость ${Money.full(row.reagentCost)} · продажа ${Money.full(row.sellsFor)}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Money(row.profit, if ((row.profit ?: 0) > 0) Good else Bad, bold = true, fontSize = 14)
                    Caption("маржа ${Money.percent(row.marginPercent)}")
                }
            }
            if (row.soldPerDay != null || row.profitPerDay != null) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.soldPerDay?.let { Pill("продаж/день ${Money.fmt(it, 1)}", Dim) }
                    row.profitPerDay?.point?.let { Pill("в день ${Money.short(it)}", Good) }
                    if (row.vendorFloor) Pill("ниже цены НПС", Bad)
                }
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = PanelAlt)
                Spacer(Modifier.height(6.dp))
                StatRow("Навык", row.skillRequired.toString())
                StatRow("Реагенты", Money.full(row.reagentCost))
                StatRow("Продажа", Money.full(row.sellsFor))
                StatRow("Прибыль", Money.full(row.profit), if ((row.profit ?: 0) > 0) Good else Bad)
                StatRow("Прибыль в день", Money.full(row.profitPerDay?.point), Good)
                StatRow("Продаж в день", row.soldPerDay?.let { Money.fmt(it, 1) } ?: "нет данных")
                StatRow("Цена НПС за результат", Money.full(row.vendorSellPrice))
                Spacer(Modifier.height(6.dp))
                SectionTitle("Реагенты")
                row.reagents.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ruName(r.itemId, r.name), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1)
                            Caption("×${r.quantity} · ${Money.full(r.unitPrice)}/шт" + if (r.vendorPrice != null) " · НПС ${Money.full(r.vendorPrice)}" else "")
                        }
                        Money(r.unitPrice?.let { it * r.quantity }, fontSize = 12)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ search

@Composable
fun SearchScreen(state: UiState, vm: AppViewModel) {
    var query by remember { mutableStateOf(state.searchQuery) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Предмет", fontSize = 12.sp) },
            )
            TextButton(onClick = { vm.search(query) }, enabled = !state.searching) {
                if (state.searching) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                } else {
                    Text("Найти", color = Gold)
                }
            }
        }
        // Pulling down repeats the last search, so results can be re-priced without retyping.
        PullRefresh(refreshing = state.searching, onRefresh = { if (state.searchQuery.isNotBlank()) vm.search(state.searchQuery) }) {
            if (state.searchResults.isEmpty()) {
                EmptyState(
                    if (state.searchQuery.isBlank()) "Введите название предмета — проверю цену на ауке и у НПС"
                    else "Ничего не найдено",
                )
            } else {
                LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.searchResults, key = { it.id }) { SearchCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(row: SearchItem) {
    val vendorProfit = row.vendorProfit
    PanelCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(row.iconUrl, row.quality)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ruName(row.blizzardId, row.name),
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
                        ).joinToString(" · "),
                    )
                }
                if (vendorProfit != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Money(vendorProfit, if (vendorProfit > 0) Good else Bad, bold = true, fontSize = 14)
                        Caption("НПС − аук")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Caption("Аукцион")
                    Money(row.buyout, fontSize = 13)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Caption("Рынок")
                    Money(row.marketValue, fontSize = 13)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Caption("НПС")
                    Money(row.sellPrice, fontSize = 13)
                }
            }
            Spacer(Modifier.height(4.dp))
            Caption("В наличии ${row.quantity} шт · скан ${Money.age(row.lastScanAt)}")
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Настройки", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.openSettings(false) }) { Text("Готово", color = Gold) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionTitle("Регион")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("eu" to "EU", "us" to "US").forEach { (value, label) ->
                        FilterChip(
                            selected = state.region == value,
                            onClick = { vm.setRegion(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                SectionTitle("Игровой мир")
                if (state.realms.isEmpty()) {
                    Caption("Загрузка списка миров…")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.realms.forEach { realm ->
                            val selected = realm.id == state.realmId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) PanelAlt else Panel, RoundedCornerShape(10.dp))
                                    .clickable { vm.setRealm(realm) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(realm.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Caption("${realm.slug} · ${realm.pricedItemCount} предметов")
                                }
                                if (selected) Text("✓", color = Gold)
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("Фракция")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("alliance" to "Альянс", "horde" to "Орда").forEach { (value, label) ->
                        FilterChip(
                            selected = state.faction == value,
                            onClick = { vm.setFaction(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                SectionTitle("Профессия для крафта")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Api.PROFESSIONS.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { (slug, label) ->
                                FilterChip(
                                    selected = state.profession == slug,
                                    onClick = { vm.setProfession(slug) },
                                    label = { Text(label, fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("Порог скидки для списка «Скидки рынка»")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Api.DEAL_THRESHOLDS.forEach { value ->
                        FilterChip(
                            selected = state.under == value,
                            onClick = { vm.setUnder(value) },
                            label = { Text("−$value%") },
                        )
                    }
                }
            }
            item {
                SectionTitle("Данные")
                Caption(
                    "Цены: bootybaybroker.com (WoW Forever, регион ${state.region.uppercase()}). " +
                        "Комиссия аукциона учтена в расчётах сайта. Скан обновляется по мере сканов игроков.",
                )
            }
        }
    }
}

