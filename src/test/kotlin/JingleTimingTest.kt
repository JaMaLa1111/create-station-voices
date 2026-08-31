import de.jamala.station_voices.JingleTiming
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JingleTimingTest {

    @Test
    fun testEnumCycle() {
        assertEquals(JingleTiming.BEFORE, JingleTiming.BOTH.next())
        assertEquals(JingleTiming.AFTER, JingleTiming.BEFORE.next())
        assertEquals(JingleTiming.BOTH, JingleTiming.AFTER.next())
    }

    @Test
    fun testFromString() {
        assertEquals(JingleTiming.BOTH, JingleTiming.fromString("BOTH"))
        assertEquals(JingleTiming.BOTH, JingleTiming.fromString("both"))
        assertEquals(JingleTiming.BEFORE, JingleTiming.fromString("BEFORE"))
        assertEquals(JingleTiming.BEFORE, JingleTiming.fromString("before"))
        assertEquals(JingleTiming.AFTER, JingleTiming.fromString("AFTER"))
        assertEquals(JingleTiming.AFTER, JingleTiming.fromString("after"))
        assertEquals(JingleTiming.BOTH, JingleTiming.fromString(null))
        assertEquals(JingleTiming.BOTH, JingleTiming.fromString("invalid"))
    }
}
