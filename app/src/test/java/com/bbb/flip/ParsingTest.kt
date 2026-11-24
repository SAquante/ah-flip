package com.bbb.flip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    private val deals = """
    {"house":{"region":"eu","realmId":440484,"auctionHouseId":2,"faction":"alliance","marketLabel":"EU PvE",
      "lastObservedAt":"2026-11-23T16:41:22.000Z","verdict":"current","itemsPriced":7075},
     "under":20,"sort":"saving","cutBps":500,
     "pagination":{"page":1,"limit":50,"hasMore":true,"totalCount":616,"totalPages":13},
     "rows":[{"itemId":273121,"name":"Blueprint: Sewing Machine","quality":"uncommon","minBuyout":859300,
       "listings":9,"quantity":9,"unitsAtMinimum":3,"reference":1703333,"discountPercent":49.55,
       "saving":2532099,"resaleNet":2276598,"vendorSellPrice":120000,"medianQuantity7d":13.5,
       "sold":{"soldPerDay":{"basis":"estimated","value":3.02,"n":25,"confidence":{"tier":"low"}}},
       "resalePerDay":1320427}],
     "vendorFlips":[{"itemId":281666,"name":"Robes of the Blind Cultist","quality":"uncommon",
       "minBuyout":8900,"vendorSellPrice":23118,"unitsAtMinimum":7,"profit":99526,
       "observedAt":"2026-11-23T16:41:22.000Z"}],
     "sales":{"status":"ok","window":"7d"},"minSold":1,
     "soldCounts":{"any":780,"atLeast1":616,"atLeast10":303,"atLeast100":151}}
    """.trimIndent()

    @Test
    fun dealsPayloadParses() {
        val parsed = apiJson.decodeFromString<DealsResponse>(deals)
        assertEquals(616, parsed.pagination?.totalCount)
        val deal = parsed.rows.single()
        assertEquals(13.5, deal.medianQuantity7d!!, 0.0001)
        // resaleNet already has the auction cut taken out
        assertEquals(1417298L, deal.resaleProfit)
        assertEquals(3.02, deal.soldPerDay!!, 0.0001)

        val flip = parsed.vendorFlips.single()
        assertEquals(14218L, flip.unitProfit)
        assertEquals(99526L, flip.profit)
        assertEquals(159.75, flip.roi!!, 0.01)
    }

    @Test
    fun ageCountsFromTheScanTimestamp() {
        val scan = "2026-11-23T22:24:04.000Z"
        val scanMs = java.time.Instant.parse(scan).toEpochMilli()
        // the scan is 12 minutes old, whatever the timezone of the device
        assertEquals("12 мин назад", Money.age(scan, scanMs + 12 * 60_000))
        assertEquals("только что", Money.age(scan, scanMs + 30_000))
        assertEquals("1 ч назад", Money.age(scan, scanMs + 61 * 60_000))
        assertEquals("2 дн назад", Money.age(scan, scanMs + 2 * 24 * 60 * 60_000L))
    }

    @Test
    fun moneyFormatsCopper() {
        assertEquals("0м", Money.full(0))
        assertEquals("12м", Money.full(12))
        assertEquals("1с 50м", Money.full(150))
        assertEquals("1г", Money.full(10_000))
        assertEquals("1г 23с 45м", Money.full(12_345))
        assertEquals("1 234 567г", Money.full(12_345_670_000L))
        assertEquals("–5с", Money.full(-500))
    }

    @Test
    fun searchVendorProfitIsBuyoutMinusNpc() {
        val item = apiJson.decodeFromString<SearchItem>(
            """{"id":1,"name":"X","sell_price":500,"buyout":400,"quantity":3}""",
        )
        assertEquals(100L, item.vendorProfit)
    }

    @Test
    fun craftingRowsParseWithUnknownKeys() {
        val row = apiJson.decodeFromString<CraftRow>(
            """{"recipeId":5714,"professionSlug":"tailoring","result":{"itemId":14342,"name":"Mooncloth","yield":1},
                "reagents":[{"itemId":14256,"name":"Felcloth","quantity":2,"unitPrice":6793}],
                "reagentCost":13586,"sellsFor":519898,"profit":480317,"marginPercent":92.39,
                "vendorFloor":false,"complete":true,"brandNewField":"ignored"}""",
        )
        assertEquals(480317L, row.profit)
        assertEquals(2 * 6793L, row.reagents.single().unitPrice!! * row.reagents.single().quantity)
        assertTrue((row.roi ?: 0.0) > 3000)
    }

    @Test
    fun discountIsMeasuredAgainstMarketValue() {
        val row = apiJson.decodeFromString<SearchItem>(
            """{"id":1,"name":"X","market_value":1000,"effective_price":600,"buyout":600}""",
        )
        assertEquals(40.0, row.discountPercent!!, 0.001)

        // no market value: nothing to compare against
        val bare = apiJson.decodeFromString<SearchItem>("""{"id":2,"name":"Y","buyout":600}""")
        assertNull(bare.discountPercent)

        // a listing above market value is a negative discount, not a crash
        val above = apiJson.decodeFromString<SearchItem>(
            """{"id":3,"name":"Z","market_value":100,"effective_price":250}""",
        )
        assertEquals(-150.0, above.discountPercent!!, 0.001)
    }

    @Test
    fun auctionUrlsCarryEveryFilter() {
        val url = Api.searchUrl(
            region = "eu", realmId = 440484, faction = "alliance",
            query = "луноткань", classId = 7, subclassId = 5, quality = 2,
            page = 3, limit = 100,
        )
        assertTrue(url.startsWith("/api/items/search?gameVersion=forever&region=eu"))
        assertTrue(url.contains("realmId=440484"))
        assertTrue(url.contains("auctionHouseId=2"))       // alliance house
        assertTrue(url.contains("page=3"))
        assertTrue(url.contains("classId=7"))
        assertTrue(url.contains("subclassId=5"))
        assertTrue(url.contains("quality=2"))
        assertTrue(url.contains("q=%D0%BB"))               // cyrillic is url-encoded

        // horde uses its own auction house id
        assertTrue(
            Api.searchUrl("eu", 440484, "horde").contains("auctionHouseId=6"),
        )
    }

    @Test
    fun listUrlsStayUnderTheWebViewBodyLimit() {
        // The WebView reliably delivers ~40 KB and stalls above that, so pages are small.
        assertTrue(Api.disenchantUrl("eu", 440484, "alliance").contains("limit=20"))
        assertTrue(Api.craftingUrl("eu", 440484, "alliance", "tailoring").contains("limit=20"))
    }
}
