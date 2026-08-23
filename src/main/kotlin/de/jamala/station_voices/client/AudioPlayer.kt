package de.jamala.station_voices.client

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import de.jamala.station_voices.ModConfig
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent

import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import net.minecraft.core.BlockPos
import kotlinx.coroutines.delay

object AudioPlayer {
    private val CACHE_DIR: File by lazy {
        val dir = File(Minecraft.getInstance().gameDirectory, "create_station_voices_cache")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val downloadMutex = Mutex()
    private val chunkBuffer = mutableMapOf<java.util.UUID, MutableList<ByteArray?>>()
    
    fun handleAudioChunk(
        pos: BlockPos?, speed: Float, volume: Float, reverb: Boolean, maxRange: Int,
        streamId: java.util.UUID, chunkIndex: Int, totalChunks: Int, chunkData: ByteArray
    ) {
        val list = chunkBuffer.getOrPut(streamId) { MutableList(totalChunks) { null } }
        list[chunkIndex] = chunkData
        if (list.all { it != null }) {
            chunkBuffer.remove(streamId)
            val fullData = list.flatMap { it!!.toList() }.toByteArray()
            playData(pos, speed, volume, reverb, maxRange, fullData)
        }
    }

    private fun playData(pos: BlockPos?, speed: Float, volume: Float, reverb: Boolean, maxRange: Int, wavData: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bais = java.io.ByteArrayInputStream(wavData)
                val audioIn = AudioSystem.getAudioInputStream(bais)
                val format = audioIn.format
                var bytes = audioIn.readAllBytes()
                
                if (reverb) {
                    bytes = applyReverb(bytes, format)
                }

                val newFormat = AudioFormat(
                    format.encoding,
                    format.sampleRate * speed, // Pitch/Speed shift
                    format.sampleSizeInBits,
                    format.channels,
                    format.frameSize,
                    format.frameRate * speed,
                    format.isBigEndian
                )

                val info = javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, newFormat)
                val line = AudioSystem.getLine(info) as SourceDataLine
                line.open(newFormat)
                line.start()
                
                val gainControl = if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl else null
                val panControl = if (line.isControlSupported(FloatControl.Type.PAN)) line.getControl(FloatControl.Type.PAN) as FloatControl else null

                var chunkSize = (newFormat.frameRate * newFormat.frameSize * 0.05).toInt()
                // Ensure chunk size is a multiple of frameSize
                chunkSize -= chunkSize % newFormat.frameSize
                var offset = 0
                
                while (offset < bytes.size) {
                    if (pos != null) {
                        val player = Minecraft.getInstance().player
                        val camera = Minecraft.getInstance().gameRenderer.mainCamera
                        if (player != null && camera.isInitialized) {
                            val dx = pos.x + 0.5 - camera.position.x
                            val dy = pos.y + 0.5 - camera.position.y
                            val dz = pos.z + 0.5 - camera.position.z
                            val distance = Math.sqrt(dx * dx + dy * dy + dz * dz)

                            val maxRangeD = maxRange.toDouble()
                            val startFalloff = maxRangeD / 3.0
                            var volMult = 1.0
                            if (distance > maxRangeD) {
                                volMult = 0.0
                            } else if (distance > startFalloff) {
                                volMult = 1.0 - ((distance - startFalloff) / (maxRangeD - startFalloff))
                            }

                            if (gainControl != null) {
                                val v = (volume * volMult).coerceIn(0.0001, 1.0)
                                gainControl.value = (20.0 * Math.log10(v)).toFloat()
                            }

                            if (panControl != null) {
                                val radYaw = Math.toRadians(camera.yRot.toDouble())
                                val rightX = -Math.cos(radYaw)
                                val rightZ = -Math.sin(radYaw)
                                var pan = 0.0
                                val horizDist = Math.sqrt(dx * dx + dz * dz)
                                if (horizDist > 0.1) {
                                    pan = (dx * rightX + dz * rightZ) / horizDist
                                }
                                panControl.value = pan.toFloat().coerceIn(-1.0f, 1.0f)
                            }
                        }
                    } else {
                        if (gainControl != null) {
                            val v = volume.coerceIn(0.0001f, 1.0f)
                            gainControl.value = (20.0 * Math.log10(v.toDouble())).toFloat()
                        }
                        if (panControl != null) {
                            panControl.value = 0f
                        }
                    }
                    
                    var length = Math.min(chunkSize, bytes.size - offset)
                    // Ensure length written is a multiple of frameSize
                    length -= length % newFormat.frameSize
                    if (length > 0) {
                        line.write(bytes, offset, length)
                        offset += length
                    } else {
                        // Reached the end and not enough bytes for a full frame
                        break
                    }
                }
                
                line.drain()
                line.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun play(pos: BlockPos?, text: String, voice: String, language: String, speed: Float, volume: Float, reverb: Boolean, maxRange: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = getAudioFile(text, voice, language)
                if (file.exists()) {
                    playData(pos, speed, volume, reverb, maxRange, file.readBytes())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyReverb(input: ByteArray, format: AudioFormat): ByteArray {
        if (format.sampleSizeInBits != 16) return input 
        
        val samples = ShortArray(input.size / 2)
        java.nio.ByteBuffer.wrap(input).order(
            if (format.isBigEndian) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN
        ).asShortBuffer().get(samples)
        
        val delaySamples = (format.sampleRate * 0.3f).toInt() // 300ms delay
        val decay = 0.4f
        
        val outSamples = ShortArray(samples.size + delaySamples * 3) // extend for reverb tail
        
        for (i in samples.indices) {
            outSamples[i] = samples[i]
        }
        
        for (i in samples.indices) {
            val s = samples[i]
            for (echo in 1..3) {
                val idx = i + delaySamples * echo
                if (idx < outSamples.size) {
                    val echoVal = (s * Math.pow(decay.toDouble(), echo.toDouble())).toInt()
                    var mixed = outSamples[idx].toInt() + echoVal
                    mixed = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    outSamples[idx] = mixed.toShort()
                }
            }
        }
        
        val outBytes = ByteArray(outSamples.size * 2)
        val bb = java.nio.ByteBuffer.wrap(outBytes).order(
            if (format.isBigEndian) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN
        )
        for (s in outSamples) {
            bb.putShort(s)
        }
        return outBytes
    }

    suspend fun getAudioFile(text: String, voice: String, language: String): File {
        val hash = md5("$text-$voice-$language")
        val file = File(CACHE_DIR, "$hash.wav")

        if (file.exists() && file.length() > 0) {
            return file
        }

        downloadMutex.withLock {
            if (file.exists() && file.length() > 0) {
                return file
            }

            val baseUrl = ModConfig.SERVER.webApiUrl.get()
            if (baseUrl.isBlank()) {
                throw Exception("Web API URL is not configured in create_station_voices-server.toml")
            }
            val url = URI("$baseUrl/api/generate").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JsonObject()
            json.addProperty("text", text)
            json.addProperty("voice", voice)
            json.addProperty("language", language)
            json.addProperty("no-cache", false)

            connection.outputStream.use { os ->
                val input = json.toString().toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (connection.responseCode == 200) {
                val inputStream: InputStream = connection.inputStream
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } else {
                throw Exception("Failed to generate audio. HTTP Code: ${connection.responseCode}")
            }
        }
        
        return file
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
