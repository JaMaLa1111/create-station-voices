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
    fun LoadLibraryExW(lpFileName: com.sun.jna.WString?, hFile: com.sun.jna.Pointer?, dwFlags: Int): com.sun.jna.Pointer?
    fun FreeLibrary(hModule: com.sun.jna.Pointer?): Boolean
    fun IsProcessorFeaturePresent(feature: Int): Boolean
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
        val vcrtNames = listOf("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll", "msvcp140_atomic_wait.dll")
        for (name in vcrtNames) {
            val resourcePath = "/create_station_voices_native/vcrt/$name"
            val target = File(nativeDir, name)
            try {
                val bytes = PiperManager::class.java.getResourceAsStream(resourcePath)?.use { it.readBytes() }
                if (bytes == null) {
                    CreateStationVoices.LOGGER.warn("Bundled VC++ runtime resource not found: $resourcePath")
                    continue
                }
                if (target.exists() && target.length() == bytes.size.toLong()) {
                    CreateStationVoices.LOGGER.info("VC++ runtime DLL already deployed: ${target.name} (${target.length()} bytes)")
                    continue
                }
                target.writeBytes(bytes)
                CreateStationVoices.LOGGER.info("Deployed VC++ runtime DLL: ${target.absolutePath}")
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.warn("Failed to deploy VC++ runtime DLL $name", e)
            }
        }
    }

    private fun patchJvmVcRuntime() {
        val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        if (!os.contains("win")) return
        val javaHome = System.getProperty("java.home") ?: return
        val jvmBin = File(javaHome, "bin")
        if (!jvmBin.isDirectory) return

        val vcrtNames = listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll")
        var anyRenamed = false
        var anyFailed = false

        for (name in vcrtNames) {
            val jvmDll = File(jvmBin, name)
            if (!jvmDll.exists()) continue
            val backup = File(jvmBin, "$name.bak")
            if (backup.exists()) continue

            try {
                if (jvmDll.renameTo(backup)) {
                    CreateStationVoices.LOGGER.warn("Renamed JVM $name to $name.bak — Windows will use the system-installed version on next restart")
                    System.setProperty("de.jamala1111.station_voices.jvmVcrtPatched", "true")
                    anyRenamed = true
                } else {
                    CreateStationVoices.LOGGER.warn("Could not rename JVM $name — permission denied. See the generated fix-vc-runtime.bat script.")
                    anyFailed = true
                }
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.warn("Could not rename JVM $name", e)
                anyFailed = true
            }
        }

        if (anyFailed) {
            generateVcRuntimeFixScript(jvmBin, vcrtNames)
        }
    }

    private fun generateVcRuntimeFixScript(jvmBin: File, dllNames: List<String>) {
        try {
            val script = File(FMLPaths.GAMEDIR.get().toFile(), "fix-vc-runtime.bat")
            val sb = StringBuilder()
            sb.appendLine("@echo off")
            sb.appendLine("echo Fixing JVM VC++ Runtime for Piper TTS...")
            sb.appendLine("echo This script renames the JVM's bundled VC++ runtime DLLs so Windows")
            sb.appendLine("echo uses the newer system-installed versions instead.")
            sb.appendLine("echo.")
            sb.appendLine("echo JVM bin: ${jvmBin.absolutePath}")
            sb.appendLine("echo.")
            for (name in dllNames) {
                sb.appendLine("if exist \"${jvmBin.absolutePath}\\$name\" (")
                sb.appendLine("  if not exist \"${jvmBin.absolutePath}\\$name.bak\" (")
                sb.appendLine("    ren \"${jvmBin.absolutePath}\\$name\" $name.bak")
                sb.appendLine("    echo Renamed $name to $name.bak")
                sb.appendLine("  ) else (")
                sb.appendLine("    echo $name already renamed")
                sb.appendLine("  )")
                sb.appendLine(") else (")
                sb.appendLine("  echo $name not found in JVM bin")
                sb.appendLine(")")
            }
            sb.appendLine("echo.")
            sb.appendLine("echo Done! Please restart your Minecraft server.")
            sb.appendLine("pause")
            script.writeText(sb.toString())
            CreateStationVoices.LOGGER.error("Could not auto-patch JVM VC++ runtime. A fix script has been generated: ${script.absolutePath}")
            CreateStationVoices.LOGGER.error("Please run fix-vc-runtime.bat as administrator, then restart the server.")
        } catch (e: Exception) {
            CreateStationVoices.LOGGER.error("Could not generate VC++ runtime fix script", e)
        }
    }

    private fun preloadVCRuntime(nativeDir: File) {
        val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        if (!os.contains("win")) return
        val vcrtNames = listOf("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll", "msvcp140_atomic_wait.dll")
        for (name in vcrtNames) {
            val dll = File(nativeDir, name)
            if (!dll.exists()) {
                CreateStationVoices.LOGGER.warn("VC++ runtime DLL not found for preloading: ${dll.absolutePath}")
                continue
            }
            try {
                System.load(dll.absolutePath)
                CreateStationVoices.LOGGER.info("Preloaded VC++ runtime DLL: ${dll.absolutePath}")
            } catch (e: Throwable) {
                CreateStationVoices.LOGGER.warn("Could not preload VC++ runtime DLL ${dll.name} (may already be loaded)", e)
            }
        }
    }

    private fun diagnoseOnnxRuntime(nativeDir: File) {
        val os = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        if (!os.contains("win")) return

        CreateStationVoices.LOGGER.info("[Diagnostic] os.arch: ${System.getProperty("os.arch")}, os.name: ${System.getProperty("os.name")}, os.version: ${System.getProperty("os.version")}")
        CreateStationVoices.LOGGER.info("[Diagnostic] java.version: ${System.getProperty("java.version")}, java.home: ${System.getProperty("java.home")}")
        CreateStationVoices.LOGGER.info("[Diagnostic] native dir: ${nativeDir.absolutePath}")

        // Check if the JVM bundles its own VC++ runtime DLLs — if they're older than
        // what onnxruntime.dll 1.22 needs, Windows will use the JVM's copies (already
        // loaded in the process) instead of ours, causing onnxruntime's DllMain to fail.
        val javaHome = System.getProperty("java.home")
        if (javaHome != null) {
            val jvmBin = File(javaHome, "bin")
            val jvmVcrtNames = listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll", "msvcp140_1.dll")
            for (name in jvmVcrtNames) {
                val jvmDll = File(jvmBin, name)
                if (jvmDll.exists()) {
                    CreateStationVoices.LOGGER.warn("[Diagnostic] JVM bundles $name in ${jvmDll.absolutePath} (${jvmDll.length()} bytes) — this version takes priority over our bundled copy")
                }
            }
        }

        // List files in native dir
        CreateStationVoices.LOGGER.info("[Diagnostic] Files in native dir: ${nativeDir.listFiles()?.joinToString { "${it.name}(${it.length()})" } ?: "none"}")

        try {
            val k32 = com.sun.jna.Native.load("kernel32", ExtKernel32::class.java) as ExtKernel32

            // Check CPU features
            val features = mapOf(
                10 to "PF_XMMI (SSE)",
                12 to "PF_SSE3",
                39 to "PF_AVX",
                40 to "PF_AVX2",
                41 to "PF_AVX512F"
            )
            for ((id, name) in features) {
                try {
                    val supported = k32.IsProcessorFeaturePresent(id)
                    CreateStationVoices.LOGGER.info("[Diagnostic] CPU $name: $supported")
                } catch (e: Throwable) {
                    CreateStationVoices.LOGGER.info("[Diagnostic] CPU $name: error ($e)")
                }
            }

            // Try loading onnxruntime.dll with LoadLibraryExW and free it afterward.
            // We DON'T pre-load it permanently because piper-jni needs to extract and
            // load it itself, and a loaded DLL can't be overwritten on Windows.
            val onnxrtFile = File(nativeDir, "onnxruntime.dll")
            if (onnxrtFile.exists()) {
                val LOAD_WITH_ALTERED_SEARCH_PATH = 0x8
                com.sun.jna.Native.setLastError(0)
                val handle = k32.LoadLibraryExW(com.sun.jna.WString(onnxrtFile.absolutePath), null, LOAD_WITH_ALTERED_SEARCH_PATH)
                val winErr = com.sun.jna.Native.getLastError()
                if (handle != null) {
                    CreateStationVoices.LOGGER.info("[Diagnostic] onnxruntime.dll loaded OK via LoadLibraryExW (Win32 err $winErr)")
                    k32.FreeLibrary(handle)
                } else {
                    CreateStationVoices.LOGGER.error("[Diagnostic] onnxruntime.dll FAILED via LoadLibraryExW, Win32 error: $winErr (0x${winErr.toString(16)})")
                    val errDesc = when (winErr) {
                        126 -> "ERROR_MOD_NOT_FOUND — a dependency DLL is missing"
                        127 -> "ERROR_PROC_NOT_FOUND — a required export is missing"
                        193 -> "ERROR_BAD_EXE_FORMAT — wrong architecture (x86 vs x64)"
                        1114 -> "ERROR_DLL_INIT_FAILED — DllMain returned FALSE (likely version mismatch between JVM's VC++ runtime and what onnxruntime needs)"
                        5 -> "ERROR_ACCESS_DENIED — permission or AV blocking"
                        else -> "unknown"
                    }
                    CreateStationVoices.LOGGER.error("[Diagnostic] onnxruntime.dll error meaning: $errDesc")
                }
            } else {
                CreateStationVoices.LOGGER.info("[Diagnostic] onnxruntime.dll not yet extracted — piper-jni will extract it")
            }
        } catch (e: Throwable) {
            CreateStationVoices.LOGGER.warn("[Diagnostic] Could not run diagnostics", e)
        }
    }

    private fun createPiperJNI(): PiperJNI {
        configurePiperNativeExtraction()
        val nativeDir = getNativeLibraryDirectory()
        // Patch the JVM's bundled VC++ runtime DLLs with our newer versions.
        // The JVM may ship older msvcp140.dll / vcruntime140.dll that lack exports
        // needed by onnxruntime.dll 1.22, causing DllMain to fail (error 1114).
        // This must happen before any DLL is loaded.
        patchJvmVcRuntime()
        preloadVCRuntime(nativeDir)
        diagnoseOnnxRuntime(nativeDir)
        return PiperJNI()
    }

    private var jvmPatchedThisRun = false

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
                    val patched = System.getProperty("de.jamala1111.station_voices.jvmVcrtPatched") == "true"
                    val msg = if (patched && !jvmPatchedThisRun) {
                        "Failed to initialize native Piper ONNX runtime. " +
                            "The JVM's bundled Visual C++ runtime was outdated and has been replaced with a newer version. " +
                            "Please RESTART the server — the fix will take effect on next launch."
                    } else {
                        "Failed to initialize native Piper ONNX runtime. " +
                            "This is often caused by the JVM shipping an outdated Visual C++ runtime. " +
                            "If this is the first run after updating the mod, please restart the server."
                    }
                    CreateStationVoices.LOGGER.error(msg, e)
                    jvmPatchedThisRun = patched
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
