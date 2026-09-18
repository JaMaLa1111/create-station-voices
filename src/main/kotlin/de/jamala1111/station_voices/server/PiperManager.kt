package de.jamala.station_voices.server

import de.jamala.station_voices.CreateStationVoices
import de.jamala.station_voices.TextSanitizer
import de.jamala.station_voices.VoiceModelInfo
import com.google.gson.JsonParser
import io.github.jvoiceproject.piperjni.PiperJNI
import io.github.jvoiceproject.piperjni.PiperVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.neoforged.fml.loading.FMLPaths
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object PiperManager {
    private var piperInstance: PiperJNI? = null
    private val piperMutex = Mutex()
    private val loadedVoices = ConcurrentHashMap<String, PiperVoice>()
    private val loadedVoiceModels = ConcurrentHashMap<String, VoiceModelInfo>()

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getPiperDirectory(): File {
        val serverDir = FMLPaths.GAMEDIR.get().toFile()
        val dir = File(serverDir, "create_station_voices_piper")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelsDirectory(): File {
        val dir = File(getPiperDirectory(), "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCacheDirectory(): File {
        val dir = File(getPiperDirectory(), "cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun getInstalledModels(): List<String> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<String>()
            val modelsDir = getModelsDirectory()
            if (modelsDir.exists()) {
                for (langDir in modelsDir.listFiles() ?: emptyArray()) {
                    if (langDir.isDirectory) {
                        for (voiceDir in langDir.listFiles() ?: emptyArray()) {
                            if (voiceDir.isDirectory) {
                                if (voiceDir.listFiles()?.any { it.name.endsWith(".onnx") } == true) {
                                    list.add("${langDir.name}:${voiceDir.name}")
                                }
                            }
                        }
                    }
                }
            }
            list
        }
    }

    suspend fun downloadModel(language: String, voice: String, onnxUrl: String, jsonUrl: String, progressCallback: ((Float) -> Unit)? = null) {
        withContext(Dispatchers.IO) {
            try {
                val modelsDir = getModelsDirectory()
                val langDir = File(modelsDir, language)
                val voiceDir = File(langDir, voice)
                if (!voiceDir.exists()) voiceDir.mkdirs()

                val onnxFile = File(voiceDir, onnxUrl.substringAfterLast("/"))
                val jsonFile = File(voiceDir, jsonUrl.substringAfterLast("/"))

                downloadFile(onnxUrl, onnxFile, progressCallback)
                downloadFile(jsonUrl, jsonFile, null)

                // Invalidate cached voice if re-downloaded
                piperMutex.withLock {
                    val key = "$language:$voice"
                    loadedVoiceModels.remove(key)
                    loadedVoices.remove(key)?.let {
                        try {
                            it.close()
                        } catch (e: Exception) {
                            CreateStationVoices.LOGGER.error("Error closing old voice model $key", e)
                        }
                    }
                }

                CreateStationVoices.LOGGER.info("Downloaded Piper model $language-$voice")
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.error("Failed to download model $language-$voice", e)
            }
        }
    }

    private fun downloadFile(url: String, dest: File, progressCallback: ((Float) -> Unit)?) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (connection.responseCode == 200) {
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastProgress = 0f
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0 && progressCallback != null) {
                            val progress = downloadedBytes.toFloat() / totalBytes
                            if (progress - lastProgress > 0.02f || progress >= 1.0f) {
                                progressCallback.invoke(progress)
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
        }
    }

    fun getVoiceModelInfo(language: String, voice: String): VoiceModelInfo? {
        val key = "$language:$voice"
        loadedVoiceModels[key]?.let { return it }

        val modelsDir = getModelsDirectory()
        val langDir = File(modelsDir, language)
        val voiceDir = File(langDir, voice)

        var jsonFile: File? = null
        if (voiceDir.exists()) {
            voiceDir.walkTopDown().forEach { f ->
                if (f.name.endsWith(".onnx.json")) jsonFile = f
            }
        }

        if (jsonFile != null && jsonFile.exists()) {
            try {
                FileReader(jsonFile).use { reader ->
                    val root = JsonParser.parseReader(reader).asJsonObject
                    val phonemeType = root.get("phoneme_type")?.asString ?: "espeak"
                    val idMapObj = root.getAsJsonObject("phoneme_id_map")
                    val phonemeIds = idMapObj?.keySet() ?: emptySet()
                    val espeakVoice = root.getAsJsonObject("espeak")?.get("voice")?.asString
                    val sampleRate = root.getAsJsonObject("audio")?.get("sample_rate")?.asInt ?: 22050
                    val info = VoiceModelInfo(phonemeType, phonemeIds, espeakVoice, sampleRate)
                    loadedVoiceModels[key] = info
                    return info
                }
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.error("Failed to load model info from ${jsonFile!!.absolutePath}", e)
            }
        }
        return null
    }

    suspend fun setupPiper() {
        withContext(Dispatchers.IO) {
            piperMutex.withLock {
                if (piperInstance == null) {
                    try {
                        CreateStationVoices.LOGGER.info("Initializing native Piper ONNX runtime...")
                        val piper = PiperJNI()
                        piper.initialize(true)
                        piperInstance = piper
                        CreateStationVoices.LOGGER.info("Successfully initialized native Piper ONNX runtime v${piper.piperVersion}.")
                    } catch (e: Exception) {
                        CreateStationVoices.LOGGER.error("Failed to initialize native Piper ONNX runtime", e)
                    }
                }
            }
        }
    }

    suspend fun generateAudio(text: String, voice: String, language: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                if (text.isBlank()) return@withContext null

                // Load voice model metadata if available for guardrails
                val voiceModel = getVoiceModelInfo(language, voice)
                val safeText = TextSanitizer.sanitizeForModel(text, voiceModel, language)
                if (safeText.isNullOrBlank()) {
                    CreateStationVoices.LOGGER.debug("Text sanitized to blank or unspeakable for model $language-$voice: '$text'")
                    return@withContext null
                }

                val hash = md5("$safeText-$voice-$language")
                val cacheFile = File(getCacheDirectory(), "$hash.wav")

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    return@withContext cacheFile.readBytes()
                }

                // Find model
                val modelsDir = getModelsDirectory()
                val langDir = File(modelsDir, language)
                val voiceDir = File(langDir, voice)

                var onnxFile: File? = null
                var jsonFile: File? = null

                if (voiceDir.exists()) {
                    voiceDir.walkTopDown().forEach { f ->
                        if (f.name.endsWith(".onnx")) onnxFile = f
                        if (f.name.endsWith(".onnx.json")) jsonFile = f
                    }
                }

                if (onnxFile == null || jsonFile == null) {
                    CreateStationVoices.LOGGER.error("Piper model for $language-$voice not found in ${modelsDir.absolutePath}")
                    return@withContext null
                }

                piperMutex.withLock {
                    var piper = piperInstance
                    if (piper == null) {
                        piper = PiperJNI()
                        piper.initialize(true)
                        piperInstance = piper
                    }

                    val key = "$language:$voice"
                    var piperVoice = loadedVoices[key]
                    if (piperVoice == null) {
                        piperVoice = piper.loadVoice(onnxFile!!.toPath(), jsonFile!!.toPath())
                        loadedVoices[key] = piperVoice
                    }

                    val samples = piper.textToAudio(piperVoice, safeText)
                    if (samples.isEmpty()) {
                        CreateStationVoices.LOGGER.warn("Piper generated empty audio for '$safeText' (raw: '$text')")
                        return@withContext null
                    }

                    val wavBytes = pcmToWav(samples, piperVoice.sampleRate)
                    cacheFile.writeBytes(wavBytes)
                    return@withContext wavBytes
                }
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.error("Error generating Piper audio", e)
                return@withContext null
            }
        }
    }

    private fun pcmToWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * 2
        val chunkSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(chunkSize)
        buffer.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        buffer.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(16) // 16 for PCM
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(dataSize)
        for (sample in samples) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }
}
