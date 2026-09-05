import de.jamala.station_voices.block.IAnnouncerSource
import de.jamala.station_voices.block.display.AnnouncementDisplayLogic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementDisplaySourceTest {

    private class MockAnnouncer(
        override var isPlayingAnnouncement: Boolean,
        override var currentAnnouncementText: String
    ) : IAnnouncerSource

    @Test
    fun testNullSource() {
        assertNull(AnnouncementDisplayLogic.getDisplayText(null, 0))
        assertNull(AnnouncementDisplayLogic.getDisplayText(null, 1))
    }

    @Test
    fun testWhilePlayingMode() {
        val announcer = MockAnnouncer(isPlayingAnnouncement = false, currentAnnouncementText = "Train Arriving")

        // In Mode 0 (While Playing), when not playing, displayText should be null
        assertNull(AnnouncementDisplayLogic.getDisplayText(announcer, 0))

        // When playing, displayText should be the announcement text
        announcer.isPlayingAnnouncement = true
        assertEquals("Train Arriving", AnnouncementDisplayLogic.getDisplayText(announcer, 0))

        // When blank text, displayText should be null even if playing
        announcer.currentAnnouncementText = "   "
        assertNull(AnnouncementDisplayLogic.getDisplayText(announcer, 0))
    }

    @Test
    fun testKeepDisplayedMode() {
        val announcer = MockAnnouncer(isPlayingAnnouncement = false, currentAnnouncementText = "Express 404")

        // In Mode 1 (Keep Displayed), even when not playing, displayText should return text
        assertEquals("Express 404", AnnouncementDisplayLogic.getDisplayText(announcer, 1))

        // When playing, displayText should still return text
        announcer.isPlayingAnnouncement = true
        assertEquals("Express 404", AnnouncementDisplayLogic.getDisplayText(announcer, 1))

        // When text is blank, should return null
        announcer.currentAnnouncementText = ""
        assertNull(AnnouncementDisplayLogic.getDisplayText(announcer, 1))
    }

    @Test
    fun testModeSwitching() {
        val announcer = MockAnnouncer(isPlayingAnnouncement = false, currentAnnouncementText = "Central Line")

        // Mode 0: null while not playing
        assertNull(AnnouncementDisplayLogic.getDisplayText(announcer, 0))
        // Mode 1: available even when not playing
        assertEquals("Central Line", AnnouncementDisplayLogic.getDisplayText(announcer, 1))

        // Play
        announcer.isPlayingAnnouncement = true
        assertEquals("Central Line", AnnouncementDisplayLogic.getDisplayText(announcer, 0))
        assertEquals("Central Line", AnnouncementDisplayLogic.getDisplayText(announcer, 1))

        // Stop playing
        announcer.isPlayingAnnouncement = false
        assertNull(AnnouncementDisplayLogic.getDisplayText(announcer, 0))
        assertEquals("Central Line", AnnouncementDisplayLogic.getDisplayText(announcer, 1))
    }

    @Test
    fun testSplitLines() {
        assertTrue(AnnouncementDisplayLogic.splitLines(null).isEmpty())
        assertTrue(AnnouncementDisplayLogic.splitLines("").isEmpty())
        assertTrue(AnnouncementDisplayLogic.splitLines("   ").isEmpty())

        val single = AnnouncementDisplayLogic.splitLines("Single Line")
        assertEquals(listOf("Single Line"), single)

        val multi = AnnouncementDisplayLogic.splitLines("Line 1\nLine 2\nLine 3")
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), multi)
    }
}
