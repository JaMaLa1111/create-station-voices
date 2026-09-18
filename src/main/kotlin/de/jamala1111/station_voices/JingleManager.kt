package de.jamala1111.station_voices

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.mccue.jlayer.decoder.Bitstream
import dev.mccue.jlayer.decoder.Decoder
import dev.mccue.jlayer.decoder.SampleBuffer
import com.jcraft.jogg.Packet
import com.jcraft.jogg.Page
import com.jcraft.jogg.StreamState
import com.jcraft.jogg.SyncState
import com.jcraft.jorbis.Block
import com.jcraft.jorbis.Comment
import com.jcraft.jorbis.DspState
import com.jcraft.jorbis.Info

data class CustomJingle(
    var key: String,
    val fileName: String,
    var enabled: Boolean = true,
    var file: File
) {
    val id: String get() = key
    val displayName: String get() = key
}

object JingleManager {
    var customJinglesDirOverride: File? = null

    private val customJingles = CopyOnWriteArrayList<CustomJingle>()
    private val cachedPcm = ConcurrentHashMap<String, Pair<AudioFormat, ByteArray>>()
    private val cachedDurationMs = ConcurrentHashMap<String, Long>()

    init {
        try {
            reload()
        } catch (e: Throwable) {
            // Ignore during early initialization
        }
    }

    fun getJinglesDirectory(): File {
        customJinglesDirOverride?.let { return it }
        val configDir = try {
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().toFile()
        } catch (e: Throwable) {
            File("config")
        }
        val dir = File(configDir, "create_station_voices/jingles")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @Synchronized
    fun reload(): Int {
        cachedPcm.clear()
        cachedDurationMs.clear()
        customJingles.clear()

        val dir = getJinglesDirectory()
        val configFile = File(dir, "jingles.json")
        val savedConfigs = mutableMapOf<String, Pair<String, Boolean>>() // fileName lowercase -> (key, enabled)

        if (configFile.exists()) {
            try {
                val json = JsonParser.parseString(configFile.readText()).asJsonObject
                val jinglesArray = json.getAsJsonArray("jingles")
                if (jinglesArray != null) {
                    for (element in jinglesArray) {
                        val obj = element.asJsonObject
                        val key = obj.get("key")?.asString ?: continue
                        val fileName = obj.get("fileName")?.asString ?: continue
                        val enabled = if (obj.has("enabled")) obj.get("enabled").asBoolean else true
                        savedConfigs[fileName.lowercase()] = Pair(key, enabled)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val files = dir.listFiles()?.filter { file ->
            file.isFile && (file.name.endsWith(".wav", ignoreCase = true) ||
                    file.name.endsWith(".ogg", ignoreCase = true) ||
                    file.name.endsWith(".mp3", ignoreCase = true))
        }?.sortedBy { it.name.lowercase() } ?: emptyList()

        val baseNameCounts = files.groupingBy { it.nameWithoutExtension.lowercase() }.eachCount()
        val builtInIds = Jingle.entries.map { it.id.lowercase() }.toSet()

        for (file in files) {
            val saved = savedConfigs[file.name.lowercase()]
            val key = if (saved != null) {
                saved.first
            } else {
                val baseName = file.nameWithoutExtension
                val hasCollision = (baseNameCounts[baseName.lowercase()] ?: 0) > 1 || builtInIds.contains(baseName.lowercase())
                if (hasCollision) file.name else baseName
            }
            val enabled = saved?.second ?: true
            customJingles.add(CustomJingle(key, file.name, enabled, file))
        }

        saveConfig()
        return customJingles.size
    }

    @Synchronized
    fun saveConfig() {
        try {
            val dir = getJinglesDirectory()
            val configFile = File(dir, "jingles.json")
            val root = JsonObject()
            val array = JsonArray()
            for (cj in customJingles) {
                val item = JsonObject()
                item.addProperty("key", cj.key)
                item.addProperty("fileName", cj.fileName)
                item.addProperty("enabled", cj.enabled)
                array.add(item)
            }
            root.add("jingles", array)
            val gson = GsonBuilder().setPrettyPrinting().create()
            configFile.writeText(gson.toJson(root))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun setJingleKey(oldKey: String, newKey: String): Boolean {
        val trimmed = newKey.trim()
        if (trimmed.isBlank()) return false
        if (Jingle.entries.any { it.id.equals(trimmed, ignoreCase = true) }) return false
        if (customJingles.any { it.key.equals(trimmed, ignoreCase = true) && !it.key.equals(oldKey.trim(), ignoreCase = true) }) return false

        val cj = findCustomJingle(oldKey) ?: return false
        val prevKey = cj.key
        cj.key = trimmed

        cachedPcm.remove(prevKey)
        cachedPcm.remove(prevKey.lowercase())
        cachedDurationMs.remove(prevKey)
        cachedDurationMs.remove(prevKey.lowercase())

        saveConfig()
        return true
    }

    @Synchronized
    fun setJingleEnabled(key: String, enabled: Boolean): Boolean {
        val cj = findCustomJingle(key) ?: return false
        cj.enabled = enabled
        saveConfig()
        return true
    }

    @Synchronized
    fun deleteCustomJingle(key: String): Boolean {
        val cj = findCustomJingle(key) ?: return false
        customJingles.remove(cj)
        try {
            if (cj.file.exists()) {
                cj.file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedPcm.remove(cj.key)
        cachedPcm.remove(cj.key.lowercase())
        cachedDurationMs.remove(cj.key)
        cachedDurationMs.remove(cj.key.lowercase())

        saveConfig()
        return true
    }

    @Synchronized
    fun addCustomJingle(key: String, fileName: String, bytes: ByteArray): Boolean {
        val cleanName = File(fileName).name
        val lower = cleanName.lowercase()
        if (!lower.endsWith(".wav") && !lower.endsWith(".ogg") && !lower.endsWith(".mp3")) {
            return false
        }

        val dir = getJinglesDirectory()
        val targetFile = File(dir, cleanName)
        try {
            targetFile.writeBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        var validKey = key.trim()
        if (validKey.isBlank() || Jingle.entries.any { it.id.equals(validKey, ignoreCase = true) }) {
            validKey = targetFile.nameWithoutExtension
        }
        if (Jingle.entries.any { it.id.equals(validKey, ignoreCase = true) }) {
            validKey = cleanName
        }

        val existing = customJingles.find { it.fileName.equals(cleanName, ignoreCase = true) }
        if (existing != null) {
            existing.key = validKey
            existing.file = targetFile
            existing.enabled = true
        } else {
            customJingles.add(CustomJingle(validKey, cleanName, true, targetFile))
        }

        cachedPcm.remove(validKey)
        cachedPcm.remove(validKey.lowercase())
        cachedDurationMs.remove(validKey)
        cachedDurationMs.remove(validKey.lowercase())

        saveConfig()
        return true
    }

    fun getCustomJingles(): List<CustomJingle> = customJingles.toList()

    fun getAllJingleIds(): List<String> {
        val list = mutableListOf("OFF", "DB", "SNCF", "NS", "UK")
        for (cj in customJingles) {
            if (cj.enabled && !list.any { it.equals(cj.key, ignoreCase = true) }) {
                list.add(cj.key)
            }
        }
        return list
    }

    fun isOff(jingleStr: String?): Boolean {
        return jingleStr.isNullOrBlank() || jingleStr.trim().equals("OFF", ignoreCase = true)
    }

    fun isJingleEnabled(jingleStr: String?): Boolean = !isOff(jingleStr)

    fun findCustomJingle(query: String?): CustomJingle? {
        if (query.isNullOrBlank()) return null
        val trimmed = query.trim()
        for (cj in customJingles) {
            if (cj.key.equals(trimmed, ignoreCase = true) ||
                cj.fileName.equals(trimmed, ignoreCase = true) ||
                cj.file.name.equals(trimmed, ignoreCase = true) ||
                cj.file.nameWithoutExtension.equals(trimmed, ignoreCase = true)
            ) {
                return cj
            }
        }
        return null
    }

    fun resolveCanonicalKey(jingleStr: String?): String? {
        if (isOff(jingleStr)) return null
        val trimmed = jingleStr!!.trim()

        // 1. Exact match with built-in IDs or fileNames
        for (entry in Jingle.entries) {
            if (entry == Jingle.OFF) continue
            if (entry.id.equals(trimmed, ignoreCase = true) || entry.fileName.equals(trimmed, ignoreCase = true)) {
                return entry.id
            }
        }

        // 2. Custom jingle match
        val custom = findCustomJingle(trimmed)
        if (custom != null) {
            return custom.key
        }

        // 3. Fallback fuzzy match with built-in IDs
        val fuzzy = Jingle.fromString(trimmed)
        if (fuzzy != Jingle.OFF) {
            return fuzzy.id
        }

        // 4. On-demand check if file exists in jingles directory
        val dir = getJinglesDirectory()
        val directFile = File(dir, trimmed).takeIf { it.exists() && it.isFile }
            ?: File(dir, "$trimmed.wav").takeIf { it.exists() && it.isFile }
            ?: File(dir, "$trimmed.ogg").takeIf { it.exists() && it.isFile }
            ?: File(dir, "$trimmed.mp3").takeIf { it.exists() && it.isFile }

        if (directFile != null) {
            val key = directFile.nameWithoutExtension
            val cj = CustomJingle(key, directFile.name, true, directFile)
            if (customJingles.none { it.key.equals(key, ignoreCase = true) }) {
                customJingles.add(cj)
                saveConfig()
            }
            return key
        }

        return null
    }

    fun getDisplayName(jingleStr: String?): String {
        if (isOff(jingleStr)) return "OFF"
        val trimmed = jingleStr!!.trim()

        for (entry in Jingle.entries) {
            if (entry == Jingle.OFF) continue
            if (entry.id.equals(trimmed, ignoreCase = true) || entry.fileName.equals(trimmed, ignoreCase = true)) {
                return entry.displayName
            }
        }

        val custom = findCustomJingle(trimmed)
        if (custom != null) {
            return custom.key
        }

        val fuzzy = Jingle.fromString(trimmed)
        if (fuzzy != Jingle.OFF) {
            return fuzzy.displayName
        }

        return trimmed
    }

    fun getNextJingleId(currentId: String?): String {
        val ids = getAllJingleIds()
        if (ids.isEmpty()) return "OFF"

        val canonical = resolveCanonicalKey(currentId) ?: "OFF"
        val idx = ids.indexOfFirst { it.equals(canonical, ignoreCase = true) }
        val nextIdx = if (idx >= 0) (idx + 1) % ids.size else 0
        return ids[nextIdx]
    }

    @Synchronized
    fun loadPcm(jingleStr: String?): Pair<AudioFormat, ByteArray>? {
        if (isOff(jingleStr)) return null
        val canonicalKey = resolveCanonicalKey(jingleStr) ?: return null
        cachedPcm[canonicalKey]?.let { return it }

        try {
            // Built-in
            val builtIn = Jingle.entries.find { it.id.equals(canonicalKey, ignoreCase = true) && it != Jingle.OFF }
            if (builtIn != null) {
                val fileName = builtIn.fileName
                val stream: InputStream? = JingleManager::class.java.getResourceAsStream("/assets/create_station_voices/sounds/$fileName")
                    ?: JingleManager::class.java.getResourceAsStream("/$fileName")
                    ?: File("assets/create_station_voices/sounds/$fileName").takeIf { it.exists() }?.inputStream()
                    ?: File(fileName).takeIf { it.exists() }?.inputStream()

                if (stream != null) {
                    val bytes = stream.use { it.readAllBytes() }
                    val decoded = decodeAudio(bytes, fileName)
                    if (decoded != null) {
                        val (format, pcmBytes) = decoded
                        val durationMs = ((pcmBytes.size.toDouble() / format.frameSize) / format.frameRate * 1000.0).toLong()
                        cachedDurationMs[canonicalKey] = durationMs
                        cachedPcm[canonicalKey] = decoded
                        return decoded
                    }
                }
                return null
            }

            // Custom
            val custom = findCustomJingle(canonicalKey)
            val file = custom?.file
                ?: File(getJinglesDirectory(), canonicalKey).takeIf { it.exists() }
                ?: File(getJinglesDirectory(), "$canonicalKey.wav").takeIf { it.exists() }
                ?: File(getJinglesDirectory(), "$canonicalKey.ogg").takeIf { it.exists() }
                ?: File(getJinglesDirectory(), "$canonicalKey.mp3").takeIf { it.exists() }

            if (file != null && file.exists()) {
                val bytes = file.readBytes()
                val decoded = decodeAudio(bytes, file.name)
                if (decoded != null) {
                    val (format, pcmBytes) = decoded
                    val durationMs = ((pcmBytes.size.toDouble() / format.frameSize) / format.frameRate * 1000.0).toLong()
                    cachedDurationMs[canonicalKey] = durationMs
                    cachedPcm[canonicalKey] = decoded
                    return decoded
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun loadPcm(jingle: Jingle): Pair<AudioFormat, ByteArray>? {
        if (jingle == Jingle.OFF) return null
        return loadPcm(jingle.id)
    }

    private fun decodeAudio(bytes: ByteArray, fileName: String): Pair<AudioFormat, ByteArray>? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".wav") -> decodeWav(bytes)
            lower.endsWith(".ogg") -> decodeOgg(bytes)
            lower.endsWith(".mp3") -> decodeMp3(bytes)
            else -> {
                if (bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
                    decodeWav(bytes)
                } else if (bytes.size >= 4 && bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()) {
                    decodeOgg(bytes)
                } else {
                    decodeMp3(bytes)
                }
            }
        }
    }

    private fun decodeWav(bytes: ByteArray): Pair<AudioFormat, ByteArray>? {
        return try {
            val bais = ByteArrayInputStream(bytes)
            val audioIn = AudioSystem.getAudioInputStream(bais)
            val baseFormat = audioIn.format

            val targetFormat = if (baseFormat.encoding != AudioFormat.Encoding.PCM_SIGNED || baseFormat.sampleSizeInBits != 16) {
                AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false
                )
            } else {
                baseFormat
            }

            val stream = if (targetFormat != baseFormat) {
                AudioSystem.getAudioInputStream(targetFormat, audioIn)
            } else {
                audioIn
            }

            val pcmBytes = stream.readAllBytes()
            Pair(targetFormat, pcmBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeMp3(bytes: ByteArray): Pair<AudioFormat, ByteArray>? {
        return try {
            val bitstream = Bitstream(ByteArrayInputStream(bytes))
            val decoder = Decoder()
            val baos = ByteArrayOutputStream()

            var sampleRate = 44100f
            var channels = 2

            var header = bitstream.readFrame()
            while (header != null) {
                val sampleBuffer = decoder.decodeFrame(header, bitstream) as SampleBuffer
                sampleRate = sampleBuffer.sampleFrequency.toFloat()
                channels = sampleBuffer.channelCount

                val buffer = sampleBuffer.buffer
                val len = sampleBuffer.bufferLength
                for (i in 0 until len) {
                    val sample = buffer[i].toInt()
                    baos.write(sample and 0xFF)
                    baos.write((sample shr 8) and 0xFF)
                }
                bitstream.closeFrame()
                header = bitstream.readFrame()
            }
            bitstream.close()

            val pcmBytes = baos.toByteArray()
            val format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false
            )
            Pair(format, pcmBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeOgg(bytes: ByteArray): Pair<AudioFormat, ByteArray>? {
        return try {
            val input = ByteArrayInputStream(bytes)
            val syncState = SyncState()
            val streamState = StreamState()
            val page = Page()
            val packet = Packet()

            val info = Info()
            val comment = Comment()
            val dspState = DspState()
            val block = Block(dspState)

            val bufferSize = 4096
            syncState.init()
            val outputStream = ByteArrayOutputStream()

            var headerPacketsRead = 0
            var initializedStream = false

            while (headerPacketsRead < 3) {
                val index = syncState.buffer(bufferSize)
                val buffer = syncState.data
                var count = input.read(buffer, index, bufferSize)
                if (count < 0) count = 0
                syncState.wrote(count)

                while (headerPacketsRead < 3) {
                    val result = syncState.pageout(page)
                    if (result == 0) break
                    if (result < 0) return null

                    if (!initializedStream) {
                        streamState.init(page.serialno())
                        streamState.reset()
                        info.init()
                        comment.init()
                        initializedStream = true
                    }

                    if (streamState.pagein(page) < 0) return null

                    while (headerPacketsRead < 3) {
                        val pResult = streamState.packetout(packet)
                        if (pResult == 0) break
                        if (pResult < 0) return null
                        if (info.synthesis_headerin(comment, packet) < 0) return null
                        headerPacketsRead++
                    }
                }
                if (count == 0 && headerPacketsRead < 3) return null
            }

            dspState.synthesis_init(info)
            block.init(dspState)

            val pcmInfo = Array(1) { Array(0) { FloatArray(0) } }
            val pcmIndex = IntArray(info.channels)

            var eos = false
            while (!eos) {
                while (!eos) {
                    val result = syncState.pageout(page)
                    if (result == 0) break
                    if (result < 0) continue

                    streamState.pagein(page)
                    while (true) {
                        val pResult = streamState.packetout(packet)
                        if (pResult == 0) break
                        if (pResult < 0) continue

                        if (block.synthesis(packet) == 0) {
                            dspState.synthesis_blockin(block)
                        }

                        var samples: Int
                        while (dspState.synthesis_pcmout(pcmInfo, pcmIndex).also { samples = it } > 0) {
                            val pcm = pcmInfo[0]
                            for (i in 0 until samples) {
                                for (c in 0 until info.channels) {
                                    var sample = (pcm[c][pcmIndex[c] + i] * 32767.0f).toInt()
                                    if (sample > 32767) sample = 32767
                                    if (sample < -32768) sample = -32768
                                    outputStream.write(sample and 0xFF)
                                    outputStream.write((sample shr 8) and 0xFF)
                                }
                            }
                            dspState.synthesis_read(samples)
                        }
                    }
                    if (page.eos() != 0) {
                        eos = true
                        break
                    }
                }

                if (!eos) {
                    val index = syncState.buffer(bufferSize)
                    val buffer = syncState.data
                    val count = input.read(buffer, index, bufferSize)
                    if (count <= 0) {
                        eos = true
                    } else {
                        syncState.wrote(count)
                    }
                }
            }

            streamState.clear()
            block.clear()
            dspState.clear()
            info.clear()
            syncState.clear()

            val pcmBytes = outputStream.toByteArray()
            val format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                info.rate.toFloat(),
                16,
                info.channels,
                info.channels * 2,
                info.rate.toFloat(),
                false
            )
            Pair(format, pcmBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getJingleDuration(jingle: Jingle, speed: Float = 1.0f): Long {
        if (jingle == Jingle.OFF) return 0L
        return getJingleDuration(jingle.id, speed)
    }

    fun getJingleDuration(jingleStr: String?, speed: Float = 1.0f): Long {
        if (isOff(jingleStr)) return 0L
        loadPcm(jingleStr)
        val canonicalKey = resolveCanonicalKey(jingleStr) ?: return 0L
        val baseMs = cachedDurationMs[canonicalKey] ?: 0L
        val speedFactor = if (speed > 0.01f) speed.toDouble() else 1.0
        return (baseMs / speedFactor).toLong()
    }

    fun getJingleDuration(jingle: Jingle, speed: Float, timing: JingleTiming): Long {
        if (jingle == Jingle.OFF) return 0L
        return getJingleDuration(jingle.id, speed, timing)
    }

    fun getJingleDuration(jingleStr: String?, speed: Float, timing: JingleTiming): Long {
        if (isOff(jingleStr)) return 0L
        val multiplier = when (timing) {
            JingleTiming.BOTH -> 2
            JingleTiming.BEFORE, JingleTiming.AFTER -> 1
        }
        return getJingleDuration(jingleStr, speed) * multiplier
    }

    fun getJingleBytesForFormat(jingle: Jingle, targetFormat: AudioFormat): ByteArray {
        if (jingle == Jingle.OFF) return ByteArray(0)
        return getJingleBytesForFormat(jingle.id, targetFormat)
    }

    fun getJingleBytesForFormat(jingleStr: String?, targetFormat: AudioFormat): ByteArray {
        if (isOff(jingleStr)) return ByteArray(0)
        val pair = loadPcm(jingleStr) ?: return ByteArray(0)
        val (sourceFormat, pcmBytes) = pair
        if (sourceFormat.matches(targetFormat)) {
            return pcmBytes
        }
        return try {
            val sourceStream = AudioInputStream(
                ByteArrayInputStream(pcmBytes),
                sourceFormat,
                pcmBytes.size.toLong() / sourceFormat.frameSize
            )
            val convertedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream)
            convertedStream.readAllBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            pcmBytes
        }
    }

    fun getGongDuration(speed: Float = 1.0f): Long = getJingleDuration(Jingle.DB, speed)

    fun getGongDuration(speed: Float, timing: JingleTiming): Long = getJingleDuration(Jingle.DB, speed, timing)

    fun getGongBytesForFormat(targetFormat: AudioFormat): ByteArray = getJingleBytesForFormat(Jingle.DB, targetFormat)
}
