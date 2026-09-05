package de.jamala.station_voices

enum class Jingle(val id: String, val displayName: String, val fileName: String) {
    OFF("OFF", "OFF", ""),
    DB("DB", "DB", "db-gong.mp3"),
    SNCF("SNCF", "SNCF", "sncf-jingle.wav"),
    NS("NS", "NS", "ns-omroep.wav"),
    UK("UK", "UK", "uk-4-chime.wav");

    fun next(): Jingle = when (this) {
        OFF -> DB
        DB -> SNCF
        SNCF -> NS
        NS -> UK
        UK -> OFF
    }

    companion object {
        fun fromString(str: String?): Jingle {
            if (str.isNullOrBlank()) return OFF
            val trimmed = str.trim()
            for (entry in entries) {
                if (entry.id.equals(trimmed, ignoreCase = true)) return entry
            }
            val lower = trimmed.lowercase()
            return when {
                lower.contains("sncf") -> SNCF
                lower.contains("ns-omroep") || lower.contains("ns_omroep") || lower == "ns" -> NS
                lower.contains("uk") || lower.contains("chime") -> UK
                lower.contains("db") || lower.contains("gong") -> DB
                else -> OFF
            }
        }
    }
}
