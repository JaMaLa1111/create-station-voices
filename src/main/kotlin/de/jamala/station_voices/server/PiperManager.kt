package de.jamala.station_voices.server

import de.jamala.station_voices.CreateStationVoices
import net.minecraft.server.MinecraftServer
import net.neoforged.fml.loading.FMLPaths
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neoforged.fml.ModList
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes

import java.security.MessageDigest

object PiperManager {
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

    private fun getBinaryDirectory(): File {
        val dir = File(getPiperDirectory(), "bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheDirectory(): File {
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
                                // check if .onnx exists
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

    suspend fun downloadModel(language: String, voice: String, onnxUrl: String, jsonUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                val modelsDir = getModelsDirectory()
                val langDir = File(modelsDir, language)
                val voiceDir = File(langDir, voice)
                if (!voiceDir.exists()) voiceDir.mkdirs()

                val onnxFile = File(voiceDir, onnxUrl.substringAfterLast("/"))
                val jsonFile = File(voiceDir, jsonUrl.substringAfterLast("/"))

                downloadFile(onnxUrl, onnxFile)
                downloadFile(jsonUrl, jsonFile)
                CreateStationVoices.LOGGER.info("Downloaded Piper model $language-$voice")
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.error("Failed to download model $language-$voice", e)
            }
        }
    }

    private fun downloadFile(url: String, dest: File) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (connection.responseCode == 200) {
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun setupPiper() {
        withContext(Dispatchers.IO) {
            val binDir = getBinaryDirectory()
            val piperDir = File(binDir, "piper")
            
            if (!piperDir.exists() || (piperDir.list()?.size ?: 0) < 5) {
                piperDir.mkdirs()
                CreateStationVoices.LOGGER.info("Extracting bundled Piper TTS binaries...")
                
                try {
                    val modFile = ModList.get().getModFileById(CreateStationVoices.ID).file
                    val resourcePath = modFile.findResource("piper", "piper")
                    
                    if (Files.exists(resourcePath)) {
                        Files.walk(resourcePath).forEach { path ->
                            if (path.isRegularFile()) {
                                val relPath = resourcePath.relativize(path).toString().replace("\\", "/")
                                val targetFile = File(piperDir, relPath)
                                targetFile.parentFile.mkdirs()
                                Files.copy(path, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                
                                // Make executables runnable
                                if (targetFile.name == "piper" || targetFile.name == "piper.exe") {
                                    targetFile.setExecutable(true)
                                }
                            }
                        }
                        CreateStationVoices.LOGGER.info("Successfully extracted Piper TTS.")
                    } else {
                        CreateStationVoices.LOGGER.error("Bundled Piper TTS not found in jar!")
                    }
                } catch (e: Exception) {
                    CreateStationVoices.LOGGER.error("Failed to extract Piper TTS", e)
                }
            }
            
            val piperExec = if (System.getProperty("os.name").lowercase().contains("win")) "piper.exe" else "piper"
            val piperFile = File(piperDir, piperExec)
            if (!piperFile.exists()) {
                CreateStationVoices.LOGGER.warn("Piper executable not found at ${piperFile.absolutePath}. Local TTS will fail unless Piper is installed.")
            }
        }
    }

    suspend fun generateAudio(text: String, voice: String, language: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val hash = md5("$text-$voice-$language")
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
                
                val binDir = getBinaryDirectory()
                val piperExec = if (System.getProperty("os.name").lowercase().contains("win")) "piper.exe" else "piper"
                val piperFile = File(binDir, "piper/$piperExec")
                
                if (!piperFile.exists()) {
                    CreateStationVoices.LOGGER.error("Piper executable not found at ${piperFile.absolutePath}")
                    return@withContext null
                }

                val outputFile = File.createTempFile("piper_out", ".wav", getPiperDirectory())
                
                val processBuilder = ProcessBuilder(
                    piperFile.absolutePath,
                    "--model", onnxFile!!.absolutePath,
                    "--output_file", outputFile.absolutePath
                )
                processBuilder.directory(File(binDir, "piper"))
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(text)
                    writer.flush()
                }
                
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val output = process.inputStream.bufferedReader().readText()
                    CreateStationVoices.LOGGER.error("Piper process failed with code $exitCode: $output")
                    outputFile.delete()
                    return@withContext null
                }
                
                val bytes = outputFile.readBytes()
                outputFile.copyTo(cacheFile, overwrite = true)
                outputFile.delete()
                
                return@withContext bytes
            } catch (e: Exception) {
                CreateStationVoices.LOGGER.error("Error generating Piper audio", e)
                return@withContext null
            }
        }
    }
}