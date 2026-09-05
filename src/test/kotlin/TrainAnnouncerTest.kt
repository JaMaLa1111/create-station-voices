import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.jamala.station_voices.block.TrainAnnouncerMovementBehaviour
import de.jamala.station_voices.block.TrainProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrainAnnouncerTest {

    private fun findProfile(profiles: Map<String, TrainProfile>, stationName: String): TrainProfile? {
        profiles[stationName]?.let { return it }
        profiles.entries.find { it.key.equals(stationName, ignoreCase = true) }?.value?.let { return it }
        for ((key, profile) in profiles) {
            try {
                if (stationName.matches(Regex(key, RegexOption.IGNORE_CASE))) {
                    return profile
                }
            } catch (_: Exception) {}
        }
        return null
    }

    @Test
    fun testExactStationMatch() {
        val profiles = mutableMapOf(
            "Central Station" to TrainProfile(text = "Welcome to Central Station"),
            "North Park" to TrainProfile(text = "Arriving at North Park")
        )

        val match = findProfile(profiles, "Central Station")
        assertNotNull(match)
        assertEquals("Welcome to Central Station", match?.text)
    }

    @Test
    fun testCaseInsensitiveStationMatch() {
        val profiles = mutableMapOf(
            "Central Station" to TrainProfile(text = "Welcome to Central Station")
        )

        val match = findProfile(profiles, "central station")
        assertNotNull(match)
        assertEquals("Welcome to Central Station", match?.text)
    }

    @Test
    fun testRegexStationMatch() {
        val profiles = mutableMapOf(
            ".*Airport.*" to TrainProfile(text = "Now arriving at Airport Terminal")
        )

        val match1 = findProfile(profiles, "City Airport Terminal 1")
        assertNotNull(match1)
        assertEquals("Now arriving at Airport Terminal", match1?.text)

        val match2 = findProfile(profiles, "Downtown Central")
        assertNull(match2)
    }

    @Test
    fun testJsonSerializationPersistence() {
        val gson = Gson()
        val originalProfiles = mutableMapOf(
            "Station Alpha" to TrainProfile(
                text = "Next stop Alpha",
                voice = "thorsten",
                language = "de_DE",
                speed = 1.1f,
                volume = 0.8f,
                reverb = true,
                maxRange = 48,
                jingle = "DB",
                jingleTiming = "BEFORE",
                realism = 0.5f,
                contraptionOnly = true
            ),
            "Station Beta" to TrainProfile(
                text = "Next stop Beta",
                voice = "amy",
                language = "en_US",
                contraptionOnly = false
            )
        )

        // Serialize as done in networking and NBT
        val json = gson.toJson(originalProfiles)
        assertNotNull(json)
        assertTrue(json.contains("Station Alpha"))
        assertTrue(json.contains("Next stop Alpha"))
        assertTrue(json.contains("thorsten"))
        assertTrue(json.contains("contraptionOnly"))

        // Deserialize as done on client/server/contraption assembly
        val type = object : TypeToken<MutableMap<String, TrainProfile>>() {}.type
        val loadedProfiles: MutableMap<String, TrainProfile> = gson.fromJson(json, type)

        assertEquals(2, loadedProfiles.size)
        val alpha = loadedProfiles["Station Alpha"]
        assertNotNull(alpha)
        assertEquals("Next stop Alpha", alpha?.text)
        assertEquals("thorsten", alpha?.voice)
        assertEquals("de_DE", alpha?.language)
        assertEquals(1.1f, alpha?.speed)
        assertEquals(0.8f, alpha?.volume)
        assertTrue(alpha?.reverb == true)
        assertEquals(48, alpha?.maxRange)
        assertEquals("DB", alpha?.jingle)
        assertEquals("BEFORE", alpha?.jingleTiming)
        assertEquals(0.5f, alpha?.realism)
        assertTrue(alpha?.contraptionOnly == true)

        val beta = loadedProfiles["Station Beta"]
        assertNotNull(beta)
        assertEquals("Next stop Beta", beta?.text)
        assertEquals("amy", beta?.voice)
        assertFalse(beta?.contraptionOnly == true)
    }
}
