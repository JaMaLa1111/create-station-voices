package de.jamala1111.station_voices.block

interface IAnnouncerSource {
    val isPlayingAnnouncement: Boolean
    val currentAnnouncementText: String
}
