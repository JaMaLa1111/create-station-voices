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
    fun testConfigurableAnnouncerFindProfileWildcardAndPlaceholders() {
        val profiles = mutableMapOf(
            "Express" to TrainProfile(text = "The {train} is arriving"),
            "*" to TrainProfile(text = "Attention: Train {train} is passing by without stopping")
        )

        fun findConfigurableProfile(trainName: String): TrainProfile? {
            val trimmed = trainName.trim()
            profiles[trimmed]?.let { return it }
            profiles.entries.find { it.key.trim().equals(trimmed, ignoreCase = true) }?.value?.let { return it }
            for ((key, profile) in profiles) {
                val k = key.trim()
                if (k == "*" || k.equals("default", ignoreCase = true) || k.equals("all", ignoreCase = true)) {
                    continue
                }
                try {
                    if (trimmed.matches(Regex(k, RegexOption.IGNORE_CASE))) {
                        return profile
                    }
                } catch (_: Exception) {}
            }
            for ((key, profile) in profiles) {
                val k = key.trim()
                if (k == "*" || k.equals("default", ignoreCase = true) || k.equals("all", ignoreCase = true)) {
                    return profile
                }
            }
            return null
        }

        // Exact match takes priority over wildcard
        val expressProfile = findConfigurableProfile("Express")
        assertNotNull(expressProfile)
        assertEquals("The {train} is arriving", expressProfile?.text)
        val expressText = expressProfile!!.text.replace("{train}", "Express", ignoreCase = true)
        assertEquals("The Express is arriving", expressText)

        // Case-insensitive match takes priority over wildcard
        val expressLowerProfile = findConfigurableProfile("express")
        assertNotNull(expressLowerProfile)
        assertEquals("The {train} is arriving", expressLowerProfile?.text)

        // Unknown train falls back to wildcard
        val cargoProfile = findConfigurableProfile("Heavy Freight 42")
        assertNotNull(cargoProfile)
        assertEquals("Attention: Train {train} is passing by without stopping", cargoProfile?.text)
        val cargoText = cargoProfile!!.text.replace("{train}", "Heavy Freight 42", ignoreCase = true)
        assertEquals("Attention: Train Heavy Freight 42 is passing by without stopping", cargoText)
    }

    @Test
    fun testObserverDetectionLogic() {
        // Simulates the observer detection mechanism
        val trainAId = java.util.UUID.randomUUID()
        val trainBId = java.util.UUID.randomUUID()
        val trainNames = mutableMapOf(
            trainAId to "Regional Express",
            trainBId to "Freight Carrier"
        )

        var lastPresentTrain: java.util.UUID? = null
        val playedAnnouncements = mutableListOf<String>()

        fun onObserverTick(currentTrainId: java.util.UUID?) {
            val trainId = currentTrainId
            if (trainId != null) {
                if (lastPresentTrain != trainId) {
                    lastPresentTrain = trainId
                    val trainName = trainNames[trainId]
                    if (trainName != null) {
                        playedAnnouncements.add(trainName)
                    }
                }
            } else {
                lastPresentTrain = null
            }
        }

        // Tick 1: Train A enters observer
        onObserverTick(trainAId)
        assertEquals(1, playedAnnouncements.size)
        assertEquals("Regional Express", playedAnnouncements.last())

        // Tick 2-5: Train A is still passing over observer
        onObserverTick(trainAId)
        onObserverTick(trainAId)
        onObserverTick(trainAId)
        assertEquals(1, playedAnnouncements.size, "Should not re-trigger while train is still passing")

        // Tick 6: Train A leaves observer (currentTrain becomes null)
        onObserverTick(null)
        assertNull(lastPresentTrain)
        assertEquals(1, playedAnnouncements.size)

        // Tick 7: Train B arrives
        onObserverTick(trainBId)
        assertEquals(2, playedAnnouncements.size)
        assertEquals("Freight Carrier", playedAnnouncements.last())

        // Tick 8: Train B leaves
        onObserverTick(null)

        // Tick 9: Train A returns (same train as earlier)
        onObserverTick(trainAId)
        assertEquals(3, playedAnnouncements.size)
        assertEquals("Regional Express", playedAnnouncements.last())
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
