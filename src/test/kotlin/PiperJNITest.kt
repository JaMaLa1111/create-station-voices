import io.github.jvoiceproject.piperjni.PiperJNI
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class PiperJNITest {
    @Test
    fun testInit() {
        val piper = PiperJNI()
        println("Piper JNI version: " + piper.piperVersion)
        assertNotNull(piper.piperVersion)
        piper.initialize(true)
        assertTrue(piper.isInitialized)
        piper.terminate()
    }
}
