package de.jamala1111.station_voices

import net.minecraft.network.chat.Component

enum class JingleTiming(val id: String, val displayName: String) {
    BOTH("BOTH", "Both"),
    BEFORE("BEFORE", "Before"),
    AFTER("AFTER", "After");

    fun getComponent(): Component = Component.translatable("gui.create_station_voices.timing.${id.lowercase()}")

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
