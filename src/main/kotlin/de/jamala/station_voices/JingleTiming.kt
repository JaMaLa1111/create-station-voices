package de.jamala.station_voices

enum class JingleTiming(val id: String, val displayName: String) {
    BOTH("BOTH", "Both"),
    BEFORE("BEFORE", "Before"),
    AFTER("AFTER", "After");

    fun next(): JingleTiming = when (this) {
        BOTH -> BEFORE
        BEFORE -> AFTER
        AFTER -> BOTH
    }

    companion object {
        fun fromString(str: String?): JingleTiming {
            return entries.find { it.id.equals(str, ignoreCase = true) } ?: BOTH
        }
    }
}
