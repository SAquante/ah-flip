package com.bbb.flip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val apiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

// ---------------------------------------------------------------- shared pieces

@Serializable
data class House(
    val region: String = "eu",
    val realmId: Long = 0,
    val auctionHouseId: Int = 2,
    val faction: String? = null,
    val marketLabel: String = "",
    val lastObservedAt: String? = null,
    val sweeps7d: Int = 0,
    val itemsPriced: Int = 0,
    val verdict: String? = null,
    val referenceWindowDays: Int = 7,
)

@Serializable
data class Pagination(
    val page: Int = 1,
    val limit: Int = 50,
    val hasMore: Boolean = false,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class Confidence(val tier: String = "", val reasons: List<String> = emptyList())

@Serializable
data class Estimate(
    val basis: String = "",
    val value: Double = 0.0,
    val low: Double? = null,
    val high: Double? = null,
    val n: Int = 0,
    val confidence: Confidence = Confidence(),
)

@Serializable
data class Speed(
    val basis: String = "",
    val value: String = "",
    val daysOfSupply: Double? = null,
    val n: Int = 0,
    val confidence: Confidence = Confidence(),
)

@Serializable
data class Sold(
    val soldPerDay: Estimate = Estimate(),
    val speed: Speed = Speed(),
    val relistShare: Estimate? = null,
)

@Serializable
data class Range(val low: Long = 0, val point: Long = 0)

// ---------------------------------------------------------------- vendor flips

/** A listing you can buy and hand to an NPC vendor for more than you paid. */
@Serializable
data class FlipRow(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val quality: String? = null,
    val minBuyout: Long? = null,
    val vendorSellPrice: Long? = null,
    val unitsAtMinimum: Int = 1,
    val depthState: String? = null,
    val profit: Long = 0,
    val observedAt: String? = null,
) {
    val unitProfit: Long get() = (vendorSellPrice ?: 0) - (minBuyout ?: 0)
    val roi: Double? get() = Money.roi(unitProfit, minBuyout ?: 0)
}

// ---------------------------------------------------------------- deals

@Serializable
data class DealRow(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val quality: String? = null,
    val minBuyout: Long? = null,
    val observedAt: String? = null,
    val listings: Int = 0,
    val quantity: Int = 0,
    val unitsAtMinimum: Int = 0,
    val nextUnitPrice: Long? = null,
    val depthState: String? = null,
    val reference: Long? = null,
    val referenceKind: String? = null,
    val referenceWindowDays: Int = 7,
    val discountPercent: Double = 0.0,
    val saving: Long = 0,
    val resaleNet: Long? = null,
    val vendorSellPrice: Long? = null,
    val sevenDayLow: Long? = null,
    val medianQuantity7d: Double? = null,
    val supplySurge: Boolean = false,
    val sold: Sold? = null,
    val resalePerDay: Long? = null,
) {
    val resaleProfit: Long? get() = resaleNet?.let { it - (minBuyout ?: 0) }
    val soldPerDay: Double? get() = sold?.soldPerDay?.value
}

@Serializable
data class SoldCounts(
    val any: Int = 0,
    val atLeast1: Int = 0,
    val atLeast10: Int = 0,
    val atLeast100: Int = 0,
)

@Serializable
data class DealsResponse(
    val house: House? = null,
    val under: Int = 20,
    val sort: String = "",
    val cutBps: Int = 500,
    val pagination: Pagination? = null,
    val rows: List<DealRow> = emptyList(),
    val vendorFlips: List<FlipRow> = emptyList(),
    val relaxation: String? = null,
    val minSold: Int = 0,
    val soldCounts: SoldCounts? = null,
    val sales: SalesStatus? = null,
)

@Serializable
data class SalesStatus(val status: String = "", val window: String? = null)

// ---------------------------------------------------------------- disenchant

@Serializable
data class DeResult(
    val itemId: Long = 0,
    val name: String = "",
    val probability: Double = 0.0,
    val quantityMean: Double = 0.0,
    val unitPrice: Long? = null,
    val evidence: String? = null,
)

@Serializable
data class DeBracket(
    val quality: Int = 0,
    val itemClass: String = "",
    val minItemLevel: Int = 0,
    val maxItemLevel: Int = 0,
)

@Serializable
data class DeRow(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val quality: String? = null,
    val itemLevel: Int = 0,
    val itemClass: String? = null,
    val minBuyout: Long? = null,
    val observedAt: String? = null,
    val bracket: DeBracket? = null,
    val results: List<DeResult> = emptyList(),
    val value: Long? = null,
    val netValue: Long? = null,
    val margin: Long? = null,
    val pricedShare: Double = 1.0,
    val partiallyPriced: Boolean = false,
    val vendorSellPrice: Long? = null,
    val vendorBeatsDisenchant: Boolean = false,
    val impliedUnitCost: Long? = null,
    val impliedUnitCostMaterialId: Long? = null,
) {
    val roi: Double? get() = Money.roi(margin ?: 0, minBuyout ?: 0)
}

@Serializable
data class DeMaterial(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val minBuyout: Long? = null,
    val observedAt: String? = null,
)

@Serializable
data class DisenchantResponse(
    val house: House? = null,
    val sort: String = "margin",
    val cutBps: Int = 500,
    val material: Long? = null,
    val materials: List<DeMaterial> = emptyList(),
    val pagination: Pagination? = null,
    val rows: List<DeRow> = emptyList(),
)

// ---------------------------------------------------------------- crafting

@Serializable
data class ItemRef(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val quality: String? = null,
    val yield: Int = 1,
)

@Serializable
data class Reagent(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val quantity: Int = 0,
    val unitPrice: Long? = null,
    val observedAt: String? = null,
    val unitsInBook: Int? = null,
    val listings: Int? = null,
    val marketValue: Long? = null,
    val vendorPrice: Long? = null,
    val vendorPriced: Boolean = false,
)

@Serializable
data class ListedNow(val listings: Int = 0, val quantity: Int = 0)

@Serializable
data class CraftRow(
    val recipeId: Long = 0,
    val professionId: Int = 0,
    val professionSlug: String = "",
    val professionName: String = "",
    val skillRequired: Int = 0,
    val result: ItemRef = ItemRef(),
    val reagents: List<Reagent> = emptyList(),
    val reagentCost: Long? = null,
    val sellsFor: Long? = null,
    val sellsForObservedAt: String? = null,
    val listedNow: ListedNow? = null,
    val profit: Long? = null,
    val marginPercent: Double? = null,
    val marginAtMarketValue: Long? = null,
    val marginAtMarketValuePercent: Double? = null,
    val thinReagent: Boolean = false,
    val vendorSellPrice: Long? = null,
    val vendorFloor: Boolean = false,
    val pricedObservedAt: String? = null,
    val complete: Boolean = false,
    val missingReagents: List<Long> = emptyList(),
    val sold: Sold? = null,
    val profitPerDay: Range? = null,
) {
    val soldPerDay: Double? get() = sold?.soldPerDay?.value
    val roi: Double? get() = Money.roi(profit ?: 0, reagentCost ?: 0)
}

@Serializable
data class CraftTotals(val ranked: Int = 0, val unranked: Int = 0)

@Serializable
data class CraftingResponse(
    val house: House? = null,
    val sort: String = "perDay",
    val profession: String? = null,
    val cutBps: Int = 500,
    val totals: CraftTotals? = null,
    val pagination: Pagination? = null,
    val rows: List<CraftRow> = emptyList(),
    val sales: SalesStatus? = null,
)

// ---------------------------------------------------------------- item search

@Serializable
data class SearchItem(
    val id: Long = 0,
    @SerialName("blizzard_id") val blizzardId: Long? = null,
    val name: String = "",
    val quality: String? = null,
    val level: Int = 0,
    @SerialName("item_class_name") val itemClass: String? = null,
    @SerialName("item_subclass_name") val itemSubclass: String? = null,
    @SerialName("sell_price") val sellPrice: Long? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val buyout: Long? = null,
    @SerialName("market_value") val marketValue: Long? = null,
    @SerialName("effective_price") val effectivePrice: Long? = null,
    val quantity: Int = 0,
    @SerialName("num_auctions") val numAuctions: Int = 0,
    @SerialName("last_scan_at") val lastScanAt: String? = null,
    val listedNow: Boolean = false,
) {
    /** Buying from the AH and selling to an NPC vendor. */
    val vendorProfit: Long? get() = sellPrice?.let { s -> buyout?.let { s - it } }
}

@Serializable
data class SearchResponse(
    val data: List<SearchItem> = emptyList(),
    val pagination: Pagination? = null,
)

// ---------------------------------------------------------------- realms

@Serializable
data class RealmGroup(
    val id: Long = 0,
    val slug: String = "",
    val label: String = "",
    val hasRecentData: Boolean = false,
    val pricedItemCount: Int = 0,
)

@Serializable
data class RealmResponse(val data: List<RealmGroup> = emptyList())

// ---------------------------------------------------------------- categories

@Serializable
data class Category(
    val id: Int = 0,
    val name: String = "",
    val itemCount: Int = 0,
    val subcategories: List<SubCategory> = emptyList(),
)

@Serializable
data class SubCategory(
    val id: Int = 0,
    val name: String = "",
    val itemCount: Int = 0,
)

@Serializable
data class CategoriesResponse(val categories: List<Category> = emptyList())

// ---------------------------------------------------------------- trending

@Serializable
data class TrendItem(
    val itemId: Long = 0,
    val name: String = "",
    val iconUrl: String? = null,
    val marketValueCopper: Long = 0,
    val deltaPercent: Double = 0.0,
    val quality: String? = null,
    val numAuctions: Int = 0,
)

@Serializable
data class TrendingResponse(
    val items: List<TrendItem> = emptyList(),
    val generatedAt: String? = null,
    val window: String? = null,
)

/** API error envelope: `{"status":400,"title":"Validation failed",...}`. */
@Serializable
data class ErrorEnvelope(
    val status: Int = 0,
    val title: String? = null,
    val detail: String? = null,
)

// ---------------------------------------------------------------- endpoints

object Api {
    const val BASE = "https://bootybaybroker.com"

    const val AH_ALLIANCE = 2
    const val AH_HORDE = 6

    val DEAL_THRESHOLDS = listOf(20, 25, 50)
    val PROFESSIONS = listOf(
        "tailoring" to "Портняжное дело",
        "blacksmithing" to "Кузнечное дело",
        "leatherworking" to "Кожевничество",
        "alchemy" to "Алхимия",
        "engineering" to "Инженерное дело",
        "enchanting" to "Наложение чар",
        "cooking" to "Кулинария",
        "first-aid" to "Первая помощь",
    )

    fun ahId(faction: String) = if (faction == "horde") AH_HORDE else AH_ALLIANCE

    fun house(region: String, realmId: Long, faction: String) =
        "region=$region&realmId=$realmId&auctionHouseId=${ahId(faction)}"

    fun dealsUrl(region: String, realmId: Long, faction: String, under: Int, limit: Int = 50) =
        "/api/forever/deals?${house(region, realmId, faction)}&under=$under&sort=saving&page=1&limit=$limit"

    // WebView reliably delivers ~40 KB bodies and stalls above that, so this list is paged
    // at 20 rows. sort=margin is ROI-ordered and every profitable row sits up front, so the
    // caller stops as soon as a page has nothing in the black.
    fun disenchantUrl(region: String, realmId: Long, faction: String, page: Int = 1, limit: Int = 20) =
        "/api/forever/crafting/disenchant?${house(region, realmId, faction)}&sort=margin&page=$page&limit=$limit"

    fun craftingUrl(region: String, realmId: Long, faction: String, profession: String, page: Int = 1, limit: Int = 20) =
        "/api/forever/crafting?${house(region, realmId, faction)}&profession=$profession&sort=perDay&sold=1&page=$page&limit=$limit"

    fun searchUrl(
        region: String,
        realmId: Long,
        faction: String,
        query: String = "",
        classId: Int? = null,
        subclassId: Int? = null,
        minLevel: Int? = null,
        maxLevel: Int? = null,
        quality: Int? = null,
        sortBy: String = "quantity",
        sortOrder: String = "desc",
        page: Int = 1,
        limit: Int = 100,
    ): String {
        val sb = StringBuilder("/api/items/search?gameVersion=forever&region=$region")
        sb.append("&page=$page&limit=$limit&realmId=$realmId&auctionHouseId=${ahId(faction)}")
        sb.append("&sortBy=$sortBy&sortOrder=$sortOrder")
        if (query.isNotBlank()) sb.append("&q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
        classId?.let { sb.append("&classId=$it") }
        subclassId?.let { sb.append("&subclassId=$it") }
        minLevel?.let { sb.append("&minLevel=$it") }
        maxLevel?.let { sb.append("&maxLevel=$it") }
        quality?.let { sb.append("&quality=$it") }
        return sb.toString()
    }

    fun categoriesUrl(region: String, realmId: Long, faction: String) =
        "/api/categories?gameVersion=forever&region=$region&realmId=$realmId&auctionHouseId=${ahId(faction)}"

    fun trendingUrl(region: String, realmId: Long, faction: String) =
        "/api/trending?version=forever&region=$region&realmId=$realmId&auctionHouseId=${ahId(faction)}"

    /** Item classes as the site's own category list names them. */
    val QUALITIES = listOf(
        0 to "Серое", 1 to "Белое", 2 to "Зелёное", 3 to "Синее", 4 to "Эпик", 5 to "Легендарное",
    )

    fun realmsUrl(region: String) = "/api/realms?gameVersion=forever&region=$region"
}
