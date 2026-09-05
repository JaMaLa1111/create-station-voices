package de.jamala.station_voices.block

interface IAnnouncerSource {
    val isPlayingAnnouncement: Boolean
    val currentAnnouncementText: String
}
