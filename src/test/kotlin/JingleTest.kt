import de.jamala1111.station_voices.Jingle
import de.jamala1111.station_voices.JingleManager
import de.jamala1111.station_voices.JingleTiming
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFormat

class JingleTest {

    @BeforeEach
    fun setUp() {
        JingleManager.customJinglesDirOverride = File("src/test/resources/custom_jingles")
        JingleManager.reload()
    }

    @AfterEach
    fun tearDown() {
        JingleManager.customJinglesDirOverride = null
        JingleManager.reload()
    }

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

    @Test
    fun testCustomJingleLoading() {
        val customJingles = JingleManager.getCustomJingles()
        assertEquals(3, customJingles.size, "Should find 3 custom jingles (.wav, .ogg, .mp3)")

        val ids = JingleManager.getAllJingleIds()
        assertTrue(ids.contains("OFF"))
        assertTrue(ids.contains("DB"))
        assertTrue(ids.contains("SNCF"))
        assertTrue(ids.contains("NS"))
        assertTrue(ids.contains("UK"))
        assertTrue(ids.contains("custom-chime"))
        assertTrue(ids.contains("custom-gong"))
        assertTrue(ids.contains("custom-omroep"))
    }

    @Test
    fun testJingleCycleWithCustomJingles() {
        assertEquals("DB", JingleManager.getNextJingleId("OFF"))
        assertEquals("SNCF", JingleManager.getNextJingleId("DB"))
        assertEquals("NS", JingleManager.getNextJingleId("SNCF"))
        assertEquals("UK", JingleManager.getNextJingleId("NS"))
        assertEquals("custom-chime", JingleManager.getNextJingleId("UK"))
        assertEquals("custom-gong", JingleManager.getNextJingleId("custom-chime"))
        assertEquals("custom-omroep", JingleManager.getNextJingleId("custom-gong"))
        assertEquals("OFF", JingleManager.getNextJingleId("custom-omroep"))

        // Case-insensitivity and extension resolving in next cycle
        assertEquals("custom-omroep", JingleManager.getNextJingleId("CUSTOM-GONG"))
        assertEquals("custom-omroep", JingleManager.getNextJingleId("custom-gong.mp3"))

        // Display names
        assertEquals("OFF", JingleManager.getDisplayName("OFF"))
        assertEquals("DB", JingleManager.getDisplayName("DB"))
        assertEquals("custom-chime", JingleManager.getDisplayName("custom-chime"))
        assertEquals("custom-gong", JingleManager.getDisplayName("custom-gong.mp3"))
        assertEquals("custom-omroep", JingleManager.getDisplayName("CUSTOM-OMROEP"))

        // isOff and isJingleEnabled
        assertTrue(JingleManager.isOff("OFF"))
        assertTrue(JingleManager.isOff(null))
        assertTrue(JingleManager.isOff(""))
        assertFalse(JingleManager.isOff("custom-chime"))
        assertTrue(JingleManager.isJingleEnabled("custom-chime"))
        assertFalse(JingleManager.isJingleEnabled("OFF"))
    }

    @Test
    fun testCustomJingleDurations() {
        val chimeDuration = JingleManager.getJingleDuration("custom-chime")
        val gongDuration = JingleManager.getJingleDuration("custom-gong")
        val omroepDuration = JingleManager.getJingleDuration("custom-omroep")

        assertTrue(chimeDuration > 0, "Chime WAV duration should be > 0, was $chimeDuration")
        assertTrue(gongDuration > 0, "Gong MP3 duration should be > 0, was $gongDuration")
        assertTrue(omroepDuration > 0, "Omroep OGG duration should be > 0, was $omroepDuration")

        // Overloads with file extensions
        assertEquals(chimeDuration, JingleManager.getJingleDuration("custom-chime.wav"))
        assertEquals(gongDuration, JingleManager.getJingleDuration("custom-gong.mp3"))
        assertEquals(omroepDuration, JingleManager.getJingleDuration("custom-omroep.ogg"))

        // Timing multiplier
        assertEquals(omroepDuration * 2, JingleManager.getJingleDuration("custom-omroep", 1.0f, JingleTiming.BOTH))
        assertEquals(omroepDuration, JingleManager.getJingleDuration("custom-omroep", 1.0f, JingleTiming.BEFORE))
        assertEquals(omroepDuration, JingleManager.getJingleDuration("custom-omroep", 1.0f, JingleTiming.AFTER))

        // Speed adjustment
        val doubleSpeedDuration = JingleManager.getJingleDuration("custom-omroep", 2.0f, JingleTiming.BEFORE)
        assertEquals(omroepDuration / 2, doubleSpeedDuration)
    }

    @Test
    fun testCustomJingleAudioBytes() {
        val targetFormat = AudioFormat(22050f, 16, 1, true, false)

        val wavBytes = JingleManager.getJingleBytesForFormat("custom-chime", targetFormat)
        assertTrue(wavBytes.isNotEmpty(), "WAV custom jingle bytes should not be empty")

        val mp3Bytes = JingleManager.getJingleBytesForFormat("custom-gong", targetFormat)
        assertTrue(mp3Bytes.isNotEmpty(), "MP3 custom jingle bytes should not be empty")

        val oggBytes = JingleManager.getJingleBytesForFormat("custom-omroep", targetFormat)
        assertTrue(oggBytes.isNotEmpty(), "OGG custom jingle bytes should not be empty")

        // Format with file extensions
        val oggBytesWithExt = JingleManager.getJingleBytesForFormat("custom-omroep.ogg", targetFormat)
        assertEquals(oggBytes.size, oggBytesWithExt.size)
    }

    @Test
    fun testCustomJingleReload() {
        val tempDir = Files.createTempDirectory("test_jingles").toFile()
        try {
            JingleManager.customJinglesDirOverride = tempDir
            assertEquals(0, JingleManager.reload())

            val file1 = File(tempDir, "first.wav")
            File("uk-4-chime.wav").copyTo(file1)
            assertEquals(1, JingleManager.reload())
            assertEquals(listOf("OFF", "DB", "SNCF", "NS", "UK", "first"), JingleManager.getAllJingleIds())

            val file2 = File(tempDir, "second.ogg")
            File("src/test/resources/custom_jingles/custom-omroep.ogg").copyTo(file2)
            assertEquals(2, JingleManager.reload())
            assertEquals(listOf("OFF", "DB", "SNCF", "NS", "UK", "first", "second"), JingleManager.getAllJingleIds())

            file1.delete()
            assertEquals(1, JingleManager.reload())
            assertEquals(listOf("OFF", "DB", "SNCF", "NS", "UK", "second"), JingleManager.getAllJingleIds())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSetJingleKey() {
        val tempDir = Files.createTempDirectory("test_jingles_key").toFile()
        try {
            JingleManager.customJinglesDirOverride = tempDir
            val file1 = File(tempDir, "bell.wav")
            File("uk-4-chime.wav").copyTo(file1)
            JingleManager.reload()

            assertEquals("bell", JingleManager.getDisplayName("bell"))

            // Valid key change
            assertTrue(JingleManager.setJingleKey("bell", "MY_BELL"))
            assertEquals("MY_BELL", JingleManager.getDisplayName("MY_BELL"))
            assertTrue(JingleManager.getAllJingleIds().contains("MY_BELL"))
            assertFalse(JingleManager.getAllJingleIds().contains("bell"))

            // Rejected changes
            assertFalse(JingleManager.setJingleKey("MY_BELL", "")) // blank
            assertFalse(JingleManager.setJingleKey("MY_BELL", "DB")) // built-in conflict
            assertFalse(JingleManager.setJingleKey("MY_BELL", "OFF")) // OFF conflict

            // Persistence across reload
            JingleManager.reload()
            assertEquals("MY_BELL", JingleManager.getDisplayName("MY_BELL"))
            assertTrue(JingleManager.getAllJingleIds().contains("MY_BELL"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSetJingleEnabled() {
        val tempDir = Files.createTempDirectory("test_jingles_enable").toFile()
        try {
            JingleManager.customJinglesDirOverride = tempDir
            val file1 = File(tempDir, "chime.wav")
            File("uk-4-chime.wav").copyTo(file1)
            JingleManager.reload()

            assertTrue(JingleManager.getAllJingleIds().contains("chime"))

            // Disable jingle
            assertTrue(JingleManager.setJingleEnabled("chime", false))
            assertFalse(JingleManager.getAllJingleIds().contains("chime"), "Disabled jingle should not be in active jingle IDs")
            assertEquals("OFF", JingleManager.getNextJingleId("UK"), "Cycle should skip disabled jingle and wrap to OFF")

            // But duration and bytes can still be queried
            assertTrue(JingleManager.getJingleDuration("chime") > 0)

            // Re-enable jingle
            assertTrue(JingleManager.setJingleEnabled("chime", true))
            assertTrue(JingleManager.getAllJingleIds().contains("chime"))
            assertEquals("chime", JingleManager.getNextJingleId("UK"))

            // Persistence
            JingleManager.setJingleEnabled("chime", false)
            JingleManager.reload()
            assertFalse(JingleManager.getAllJingleIds().contains("chime"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteCustomJingle() {
        val tempDir = Files.createTempDirectory("test_jingles_del").toFile()
        try {
            JingleManager.customJinglesDirOverride = tempDir
            val file1 = File(tempDir, "temp_sound.wav")
            File("uk-4-chime.wav").copyTo(file1)
            JingleManager.reload()

            assertEquals(1, JingleManager.getCustomJingles().size)
            assertTrue(file1.exists())

            // Delete
            assertTrue(JingleManager.deleteCustomJingle("temp_sound"))
            assertEquals(0, JingleManager.getCustomJingles().size)
            assertFalse(file1.exists(), "Underlying file should be deleted")

            // Reload should still be 0
            JingleManager.reload()
            assertEquals(0, JingleManager.getCustomJingles().size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAddCustomJingle() {
        val tempDir = Files.createTempDirectory("test_jingles_add").toFile()
        try {
            JingleManager.customJinglesDirOverride = tempDir
            JingleManager.reload()
            assertEquals(0, JingleManager.getCustomJingles().size)

            val bytes = File("uk-4-chime.wav").readBytes()
            assertTrue(JingleManager.addCustomJingle("NEW_CHIME", "new_chime.wav", bytes))

            assertEquals(1, JingleManager.getCustomJingles().size)
            assertTrue(JingleManager.getAllJingleIds().contains("NEW_CHIME"))
            assertTrue(File(tempDir, "new_chime.wav").exists())
            assertTrue(JingleManager.getJingleDuration("NEW_CHIME") > 0)

            // Reject invalid extension
            assertFalse(JingleManager.addCustomJingle("TEXT", "test.txt", "hello".toByteArray()))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

