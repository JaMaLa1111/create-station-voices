package de.jamala1111.station_voices.server

import de.jamala1111.station_voices.CreateStationVoices
import de.jamala1111.station_voices.TextSanitizer
import de.jamala1111.station_voices.VoiceModelInfo
import com.google.gson.JsonParser
import com.sun.jna.platform.win32.Kernel32
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
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface ExtKernel32 : com.sun.jna.Library {
    fun SetDllDirectoryW(path: com.sun.jna.WString?): Boolean
}

fun getSafePiperPath(path: Path): Path {
    val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
    if (!os.contains("win")) {
        return path
    }

    val absolutePath = path.toAbsolutePath().toString()

    // If the path is already pure ASCII, no need to convert
    if (absolutePath.all { it.code < 128 }) {
        return path
    }

    return try {
        val buffer = CharArray(260)
        val len = Kernel32.INSTANCE.GetShortPathName(absolutePath, buffer, buffer.size)
        if (len > 0 && len <= buffer.size) {
            Path.of(String(buffer, 0, len))
        } else if (len > buffer.size) {
            // If the buffer was too small, allocate required size and retry
            val largeBuffer = CharArray(len)
            val retryLen = Kernel32.INSTANCE.GetShortPathName(absolutePath, largeBuffer, largeBuffer.size)
            if (retryLen > 0 && retryLen <= largeBuffer.size) {
                Path.of(String(largeBuffer, 0, retryLen))
            } else {
                path
            }
        } else {
            path
        }
    } catch (e: Throwable) {
        // Fallback if 8.3 generation is disabled on the drive
        path
    }
}

object PiperManager {
    fun getSafePiperPath(path: Path): Path = de.jamala1111.station_voices.server.getSafePiperPath(path)

    private var piperInstance: PiperJNI? = null
    @Volatile
    private var setupAttempted = false
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

    fun getNativeLibraryDirectory(): File {
        val dir = File(getPiperDirectory(), "native")
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

    private fun configurePiperNativeExtraction() {
        try {
            val nativeDir = getNativeLibraryDirectory()
            val nativePath = nativeDir.toPath()

            // Deploy the bundled Visual C++ runtime DLLs so that onnxruntime.dll's
            // DllMain can find vcruntime140.dll / msvcp140.dll / vcruntime140_1.dll
            // without requiring the user to install the VC++ Redistributable.
            ensureVCRuntimeDeployed(nativeDir)

            // Add the native directory to the Windows DLL search path so that
            // onnxruntime.dll's DllMain can locate its companion DLLs
            // (e.g. onnxruntime_providers_shared.dll) by name.
            addToDllSearchPath(nativeDir)

            // Load NativeUtils through PiperJNI's classloader (NeoForge serves piper-jni
            // from a separate plugin layer) so we patch the very class piper-jni uses,
            // not a copy on PiperManager's own loader.
            val nativeUtilsClass = Class.forName(
                "io.github.jvoiceproject.piperjni.internal.NativeUtils",
                false,
                PiperJNI::class.java.classLoader
            )
            val field = nativeUtilsClass.getDeclaredField("temporaryDir")
            field.isAccessible = true
            val current = field.get(null) as? Path
            if (current == null || current.toAbsolutePath() != nativePath.toAbsolutePath()) {
                field.set(null, nativePath)
                CreateStationVoices.LOGGER.info("Configured Piper native library extraction directory: ${nativePath.toAbsolutePath()}")
            }
        } catch (e: Throwable) {
            CreateStationVoices.LOGGER.warn("Could not configure Piper native extraction directory; falling back to default temp dir", e)
        }
    }

    private fun addToDllSearchPath(dir: File) {
        val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        if (!os.contains("win")) return
        try {
            val extKernel32 = com.sun.jna.Native.load("kernel32", ExtKernel32::class.java) as ExtKernel32
            val result = extKernel32.SetDllDirectoryW(com.sun.jna.WString(dir.absolutePath))
            if (result) {
                CreateStationVoices.LOGGER.info("Added native directory to DLL search path: ${dir.absolutePath}")
            } else {
                val err = com.sun.jna.Native.getLastError()
                CreateStationVoices.LOGGER.warn("SetDllDirectoryW failed (Win32 error $err) for ${dir.absolutePath}")
            }
        } catch (e: Throwable) {
            CreateStationVoices.LOGGER.warn("Could not add native directory to DLL search path", e)
        }
    }

    private fun ensureVCRuntimeDeployed(nativeDir: File) {
        val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        if (!os.contains("win")) return
        val vcrtNames = listOf("vcruntime140.dll", "msvcp140.dll", "vcruntime140_1.dll")
        for (name in vcrtNames) {
            val resourcePath = "/create_station_voices_native/vcrt/$name"
            val target = File(nativeDir, name)
            try {
                val bytes = PiperManager::class.java.getResourceAsStream(resourcePath)?.use { it.readBytes() }
                if (bytes == null) {
                    CreateStationVoices.LOGGER.warn("Bundled VC++ runtime resource not found: $resourcePath")
                    continue
                }
                if (target.exists() && target.length() == bytes.size.toLong()) continue
                target.writeBytes(bytes)
                CreateStationVoices.LOGGER.info("Deployed VC++ runtime DLL: ${target.absolutePath}")
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.warn("Failed to deploy VC++ runtime DLL $name", e)
            }
        }
    }

    private fun createPiperJNI(): PiperJNI {
        configurePiperNativeExtraction()
        return PiperJNI()
    }

    suspend fun setupPiper() {
        withContext(Dispatchers.IO) {
            piperMutex.withLock {
                if (piperInstance != null || setupAttempted) return@withLock
                setupAttempted = true
                try {
                    CreateStationVoices.LOGGER.info("Initializing native Piper ONNX runtime...")
                    val piper = createPiperJNI()
                    piper.initialize(true)
                    piperInstance = piper
                    CreateStationVoices.LOGGER.info("Successfully initialized native Piper ONNX runtime v${piper.piperVersion}.")
                } catch (e: Throwable) {
                    CreateStationVoices.LOGGER.error("Failed to initialize native Piper ONNX runtime. " +
                        "This is often caused by missing Visual C++ Redistributable on the server, " +
                        "or by antivirus/permissions blocking DLL loading.", e)
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
                        if (setupAttempted) {
                            CreateStationVoices.LOGGER.error("Piper runtime initialization previously failed; skipping audio generation. Check earlier logs for the initialization error.")
                            return@withContext null
                        }
                        piper = createPiperJNI()
                        piper.initialize(true)
                        piperInstance = piper
                    }

                    val key = "$language:$voice"
                    var piperVoice = loadedVoices[key]
                    if (piperVoice == null) {
                        val safeOnnxPath = getSafePiperPath(onnxFile!!.toPath())
                        val safeJsonPath = getSafePiperPath(jsonFile!!.toPath())
                        piperVoice = piper.loadVoice(safeOnnxPath, safeJsonPath)
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
