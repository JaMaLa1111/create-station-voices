import de.jamala1111.station_voices.server.PiperManager
import de.jamala1111.station_voices.server.getSafePiperPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class PiperSafePathTest {

    @Test
    fun testAsciiPathUnchanged() {
        val asciiPath = Path.of("C:", "Users", "Public", "models", "de_DE-voice.onnx")
        val safePath = getSafePiperPath(asciiPath)
        assertEquals(asciiPath, safePath)
    }

    @Test
    fun testNonExistentNonAsciiPathFallsBackGracefully() {
        val nonAsciiPath = Path.of("C:", "Users", "тест_üöä", "models", "voice.onnx")
        val safePath = getSafePiperPath(nonAsciiPath)
        assertNotNull(safePath)
        // If file doesn't exist, GetShortPathName fails and falls back to original path
        assertEquals(nonAsciiPath, safePath)
    }

    @Test
    fun testPiperManagerDelegation() {
        val path = Path.of("C:", "normal", "path.onnx")
        assertEquals(getSafePiperPath(path), PiperManager.getSafePiperPath(path))
    }

    @Test
    fun testExistingPathShorteningOnWindows() {
        val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        if (!os.contains("win")) {
            return
        }

        // Try creating a temporary file in user temp directory
        val tempDir = Files.createTempDirectory("piper_test_äöü_тест")
        try {
            val testFile = Files.createFile(tempDir.resolve("test_voice.onnx"))
            val safe = getSafePiperPath(testFile)
            assertNotNull(safe)
            assertTrue(Files.exists(safe))
            // If short names are enabled on the volume, safe path will be pure ASCII
            val safeStr = safe.toAbsolutePath().toString()
            println("Original path: $testFile")
            println("Safe path:     $safeStr")
            println("Is pure ASCII: ${safeStr.all { it.code < 128 }}")
        } finally {
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            } catch (_: Exception) {}
        }
    }
}
