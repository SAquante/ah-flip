package com.bbb.flip

/** WoW copper formatting: 1 gold = 100 silver = 10 000 copper. */
object Money {
    private const val S = 100L
    private const val G = 10_000L

    private fun group(n: Long): String =
        n.toString().reversed().chunked(3).joinToString(" ").reversed()

    fun full(copper: Long?): String {
        if (copper == null) return "—"
        if (copper == 0L) return "0м"
        val neg = copper < 0
        var v = if (neg) -copper else copper
        val g = v / G; v %= G
        val s = v / S
        val c = v % S
        val sb = StringBuilder()
        if (neg) sb.append('–')
        when {
            g > 0 -> {
                sb.append(group(g)).append('г')
                if (s > 0) sb.append(' ').append(s).append('с')
                if (c > 0 && g < 1000) sb.append(' ').append(c).append('м')
            }
            s > 0 -> {
                sb.append(s).append('с')
                if (c > 0) sb.append(' ').append(c).append('м')
            }
            else -> sb.append(c).append('м')
        }
        return sb.toString()
    }

    /** Short form for headers and totals. */
    fun short(copper: Long?): String {
        if (copper == null) return "—"
        val g = copper / G
        return when {
            g >= 1_000_000 -> "${fmt(g / 1_000_000.0, 1)} млн г"
            g >= 100_000 -> "${g / 1_000}к г"
            g >= 10_000 -> "${fmt(g / 1000.0, 1)}к г"
            else -> full(copper)
        }
    }

    fun fmt(value: Double, digits: Int): String =
        String.format(java.util.Locale.US, "%.${digits}f", value).replace('.', ',')

    /** "12,5 / 88 / 375" — per day, per week, per month from a per-day estimate. */
    fun salesPerPeriod(perDay: Double?): String? {
        if (perDay == null) return null
        val month = perDay * 30
        return when {
            perDay >= 100 -> "${fmt(perDay, 0)} / ${fmt(perDay * 7, 0)} / ${fmt(month, 0)}"
            perDay >= 10 -> "${fmt(perDay, 1)} / ${fmt(perDay * 7, 1)} / ${fmt(month, 0)}"
            else -> "${fmt(perDay, 2)} / ${fmt(perDay * 7, 1)} / ${fmt(month, 1)}"
        }
    }

    fun percent(value: Double?): String =
        if (value == null) "—" else "${fmt(value, 1)}%"

    fun roi(profit: Long, cost: Long): Double? =
        if (cost <= 0) null else profit * 100.0 / cost

    /** "2 дн. назад" style age of a scan timestamp. */
    fun age(iso: String?, now: Long = System.currentTimeMillis()): String {
        if (iso == null) return "—"
        val t = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return "—"
        val min = ((now - t) / 60_000).coerceAtLeast(0)
        return when {
            min < 2 -> "только что"
            min < 60 -> "$min мин назад"
            min < 60 * 24 -> "${min / 60} ч назад"
            else -> "${min / (60 * 24)} дн назад"
        }
    }
}
