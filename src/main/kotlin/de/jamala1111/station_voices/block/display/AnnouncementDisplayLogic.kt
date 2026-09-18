package de.jamala.station_voices.block.display

import de.jamala.station_voices.block.IAnnouncerSource

object AnnouncementDisplayLogic {
    fun getDisplayText(be: IAnnouncerSource?, mode: Int): String? {
        if (be == null) return null
        if (mode == 0 && !be.isPlayingAnnouncement) {
            return null
        }
        val text = be.currentAnnouncementText
        if (text.isBlank()) {
            return null
        }
        return text
    }

    fun splitLines(rawText: String?): List<String> {
        if (rawText.isNullOrBlank()) return emptyList()
        return rawText.split("\n")
    }
}
