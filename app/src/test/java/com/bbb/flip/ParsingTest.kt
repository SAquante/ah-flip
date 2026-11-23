package com.bbb.flip

import org.junit.Assert.assertEquals
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
}
