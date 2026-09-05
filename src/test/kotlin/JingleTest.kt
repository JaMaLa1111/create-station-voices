import de.jamala.station_voices.Jingle
import de.jamala.station_voices.JingleManager
import de.jamala.station_voices.JingleTiming
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.sound.sampled.AudioFormat

class JingleTest {

    @Test
    fun testJingleCycle() {
        assertEquals(Jingle.DB, Jingle.OFF.next())
        assertEquals(Jingle.SNCF, Jingle.DB.next())
        assertEquals(Jingle.NS, Jingle.SNCF.next())
        assertEquals(Jingle.UK, Jingle.NS.next())
        assertEquals(Jingle.OFF, Jingle.UK.next())
    }

    @Test
    fun testFromString() {
        assertEquals(Jingle.OFF, Jingle.fromString("OFF"))
        assertEquals(Jingle.OFF, Jingle.fromString("off"))
        assertEquals(Jingle.OFF, Jingle.fromString(null))
        assertEquals(Jingle.OFF, Jingle.fromString(""))
        assertEquals(Jingle.OFF, Jingle.fromString("unknown"))

        assertEquals(Jingle.DB, Jingle.fromString("DB"))
        assertEquals(Jingle.DB, Jingle.fromString("db"))
        assertEquals(Jingle.DB, Jingle.fromString("db-gong.mp3"))

        assertEquals(Jingle.SNCF, Jingle.fromString("SNCF"))
        assertEquals(Jingle.SNCF, Jingle.fromString("sncf"))
        assertEquals(Jingle.SNCF, Jingle.fromString("sncf-jingle.wav"))

        assertEquals(Jingle.NS, Jingle.fromString("NS"))
        assertEquals(Jingle.NS, Jingle.fromString("ns"))
        assertEquals(Jingle.NS, Jingle.fromString("ns-omroep.wav"))

        assertEquals(Jingle.UK, Jingle.fromString("UK"))
        assertEquals(Jingle.UK, Jingle.fromString("uk"))
        assertEquals(Jingle.UK, Jingle.fromString("uk-4-chime.wav"))
    }

    @Test
    fun testJingleDurations() {
        assertEquals(0L, JingleManager.getJingleDuration(Jingle.OFF))
        assertEquals(0L, JingleManager.getJingleDuration("OFF"))

        val dbDuration = JingleManager.getJingleDuration(Jingle.DB)
        val sncfDuration = JingleManager.getJingleDuration(Jingle.SNCF)
        val nsDuration = JingleManager.getJingleDuration(Jingle.NS)
        val ukDuration = JingleManager.getJingleDuration(Jingle.UK)

        assertTrue(dbDuration > 0, "DB duration should be > 0, was $dbDuration")
        assertTrue(sncfDuration > 0, "SNCF duration should be > 0, was $sncfDuration")
        assertTrue(nsDuration > 0, "NS duration should be > 0, was $nsDuration")
        assertTrue(ukDuration > 0, "UK duration should be > 0, was $ukDuration")

        // Test timing multipliers
        assertEquals(sncfDuration * 2, JingleManager.getJingleDuration(Jingle.SNCF, 1.0f, JingleTiming.BOTH))
        assertEquals(sncfDuration, JingleManager.getJingleDuration(Jingle.SNCF, 1.0f, JingleTiming.BEFORE))
        assertEquals(sncfDuration, JingleManager.getJingleDuration(Jingle.SNCF, 1.0f, JingleTiming.AFTER))

        // Test String overload
        assertEquals(sncfDuration, JingleManager.getJingleDuration("sncf-jingle.wav"))
        assertEquals(nsDuration, JingleManager.getJingleDuration("ns-omroep.wav"))
        assertEquals(ukDuration, JingleManager.getJingleDuration("uk-4-chime.wav"))

        // Backwards compatibility
        assertEquals(dbDuration, JingleManager.getGongDuration())
    }

    @Test
    fun testJingleAudioBytes() {
        val targetFormat = AudioFormat(22050f, 16, 1, true, false)

        val offBytes = JingleManager.getJingleBytesForFormat(Jingle.OFF, targetFormat)
        assertEquals(0, offBytes.size)

        val dbBytes = JingleManager.getJingleBytesForFormat(Jingle.DB, targetFormat)
        assertTrue(dbBytes.isNotEmpty(), "DB audio bytes should not be empty")

        val sncfBytes = JingleManager.getJingleBytesForFormat(Jingle.SNCF, targetFormat)
        assertTrue(sncfBytes.isNotEmpty(), "SNCF audio bytes should not be empty")

        val nsBytes = JingleManager.getJingleBytesForFormat(Jingle.NS, targetFormat)
        assertTrue(nsBytes.isNotEmpty(), "NS audio bytes should not be empty")

        val ukBytes = JingleManager.getJingleBytesForFormat(Jingle.UK, targetFormat)
        assertTrue(ukBytes.isNotEmpty(), "UK audio bytes should not be empty")
    }
}
