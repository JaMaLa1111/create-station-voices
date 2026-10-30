package de.jamala1111.station_voices.client

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import de.jamala1111.station_voices.ModConfig
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import de.jamala1111.station_voices.Jingle
import de.jamala1111.station_voices.JingleManager
import de.jamala1111.station_voices.JingleTiming

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
        pos: BlockPos?, speed: Float, volume: Float, reverb: Boolean, maxRange: Int, jingle: String, jingleTiming: String, realism: Float,
        streamId: java.util.UUID, chunkIndex: Int, totalChunks: Int, chunkData: ByteArray,
        entityId: Int? = null
    ) {
        val list = chunkBuffer.getOrPut(streamId) { MutableList(totalChunks) { null } }
        list[chunkIndex] = chunkData
        if (list.all { it != null }) {
            chunkBuffer.remove(streamId)
            val fullData = list.flatMap { it!!.toList() }.toByteArray()
            playData(pos, speed, volume, reverb, maxRange, fullData, jingle, jingleTiming, realism, entityId)
        }
    }

    private fun playData(
        pos: BlockPos?, speed: Float, volume: Float, reverb: Boolean, maxRange: Int, wavData: ByteArray,
        jingle: String = "OFF", jingleTiming: String = "BOTH", realism: Float = 0.0f,
        entityId: Int? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bais = java.io.ByteArrayInputStream(wavData)
                val audioIn = AudioSystem.getAudioInputStream(bais)
                val format = audioIn.format
                var bytes = audioIn.readAllBytes()
                
                if (realism > 0.001f) {
                    bytes = RealismFilter.apply(bytes, format, realism)
                }

                if (reverb) {
                    bytes = applyReverb(bytes, format)
                }

                val audioSegments = mutableListOf<ByteArray>()
                if (!JingleManager.isOff(jingle)) {
                    var jingleBytes = JingleManager.getJingleBytesForFormat(jingle, format)
                    if (realism > 0.001f && jingleBytes.isNotEmpty()) {
                        jingleBytes = RealismFilter.apply(jingleBytes, format, realism)
                    }
                    if (reverb && jingleBytes.isNotEmpty()) {
                        jingleBytes = applyReverb(jingleBytes, format)
                    }
                    val timing = JingleTiming.fromString(jingleTiming)
                    if ((timing == JingleTiming.BOTH || timing == JingleTiming.BEFORE) && jingleBytes.isNotEmpty()) {
                        audioSegments.add(jingleBytes)
                    }
                    audioSegments.add(bytes)
                    if ((timing == JingleTiming.BOTH || timing == JingleTiming.AFTER) && jingleBytes.isNotEmpty()) {
                        audioSegments.add(jingleBytes)
                    }
                } else {
                    audioSegments.add(bytes)
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

                for (segment in audioSegments) {
                    var offset = 0
                    while (offset < segment.size) {
                        val player = Minecraft.getInstance().player
                        val camera = Minecraft.getInstance().gameRenderer.mainCamera
                        val level = Minecraft.getInstance().level

                        var isInsideTrain = false
                        var soundSourcePos: net.minecraft.world.phys.Vec3? = pos?.let { net.minecraft.world.phys.Vec3.atCenterOf(it) }

                        if (entityId != null && level != null) {
                            val entity = level.getEntity(entityId)
                            if (entity != null) {
                                soundSourcePos = entity.position()
                                if (player != null && entity is com.simibubi.create.content.trains.entity.CarriageContraptionEntity) {
                                    val train = entity.carriage?.train
                                    val carriages = train?.carriages ?: listOfNotNull(entity.carriage)
                                    for (c in carriages) {
                                        val ce = c.anyAvailableEntity() ?: continue
                                        if (player.vehicle == ce || player.rootVehicle == ce || ce.passengers.contains(player)) {
                                            isInsideTrain = true
                                            break
                                        }
                                        if (ce.collidingEntities.containsKey(player)) {
                                            isInsideTrain = true
                                            break
                                        }
                                        if (ce.boundingBox.inflate(0.5, 1.0, 0.5).contains(player.position())) {
                                            isInsideTrain = true
                                            break
                                        }
                                        val contraption = ce.contraption
                                        if (contraption?.bounds != null) {
                                            val localPos = ce.toLocalVector(player.position(), 0f)
                                            if (contraption.bounds.inflate(0.5, 1.0, 0.5).contains(localPos)) {
                                                isInsideTrain = true
                                                break
                                            }
                                        }
                                    }
                                } else if (player != null) {
                                    if (player.vehicle == entity || player.rootVehicle == entity || entity.boundingBox.inflate(2.0).contains(player.position())) {
                                        isInsideTrain = true
                                    }
                                }
                            }
                        }

                        if (isInsideTrain) {
                            if (gainControl != null) {
                                val v = volume.coerceIn(0.0001f, 1.0f)
                                gainControl.value = (20.0 * Math.log10(v.toDouble())).toFloat()
                            }
                            if (panControl != null) {
                                panControl.value = 0f
                            }
                        } else if (soundSourcePos != null && player != null && camera.isInitialized) {
                            val dx = soundSourcePos.x - camera.position.x
                            val dy = soundSourcePos.y - camera.position.y
                            val dz = soundSourcePos.z - camera.position.z
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
                        } else {
                            if (gainControl != null) {
                                val v = volume.coerceIn(0.0001f, 1.0f)
                                gainControl.value = (20.0 * Math.log10(v.toDouble())).toFloat()
                            }
                            if (panControl != null) {
                                panControl.value = 0f
                            }
                        }
                        
                        var length = Math.min(chunkSize, segment.size - offset)
                        // Ensure length written is a multiple of frameSize
                        length -= length % newFormat.frameSize
                        if (length > 0) {
                            line.write(segment, offset, length)
                            offset += length
                        } else {
                            // Reached the end and not enough bytes for a full frame
                            break
                        }
                    }
                }
                
                line.drain()
                line.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (pos != null) {
                    try {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            de.jamala1111.station_voices.network.AnnouncerAudioFinishedPayload(pos)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun play(pos: BlockPos?, text: String, voice: String, language: String, speed: Float, volume: Float, reverb: Boolean, maxRange: Int, jingle: String = "OFF", jingleTiming: String = "BOTH", realism: Float = 0.0f, entityId: Int? = null) {
        val safeText = de.jamala1111.station_voices.TextSanitizer.sanitizeForModel(text, null, language)
        if (safeText.isNullOrBlank()) {
            if (pos != null) {
                try {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        de.jamala1111.station_voices.network.AnnouncerAudioFinishedPayload(pos)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = getAudioFile(safeText, voice, language)
                if (file.exists()) {
                    playData(pos, speed, volume, reverb, maxRange, file.readBytes(), jingle, jingleTiming, realism, entityId)
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
        val safeTextCache = de.jamala1111.station_voices.TextSanitizer.sanitizeForModel(text, null, language) ?: text.trim()
        val hash = md5("$safeTextCache-$voice-$language")
        val file = File(CACHE_DIR, "$hash.wav")

        if (file.exists() && file.length() > 0) {
            return file
        }

        downloadMutex.withLock {
            if (file.exists() && file.length() > 0) {
                return file
            }

            data class TextPart(val text: String, val silenceSeconds: Int)
            val parsedParts = mutableListOf<TextPart>()
            val matcher = java.util.regex.Pattern.compile("\\[s:(\\d+)\\]").matcher(text)
            var lastEnd = 0
            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    parsedParts.add(TextPart(text.substring(lastEnd, matcher.start()), 0))
                }
                parsedParts.add(TextPart("", matcher.group(1).toInt()))
                lastEnd = matcher.end()
            }
            if (lastEnd < text.length) {
                val remaining = text.substring(lastEnd)
                if (remaining.isNotBlank()) {
                    parsedParts.add(TextPart(remaining, 0))
                }
            }

            val fetchedChunks = mutableListOf<ByteArray>()

            val baseUrl = ModConfig.SERVER.webApiUrl.get()
            if (baseUrl.isBlank()) {
                throw Exception("Web API URL is not configured in create_station_voices-server.toml")
            }

            for (part in parsedParts) {
                if (part.silenceSeconds > 0) {
                    // For Web API, we don't immediately know the sample rate, but generally it's 22050.
                    // We will generate a dummy WAV for the silence and concatenate.
                    // Instead, we just pad the fetchedChunks with silence byte arrays!
                    val sampleRate = 22050
                    val numChannels = 1
                    val bitsPerSample = 16
                    val sampleCount = part.silenceSeconds * sampleRate
                    val dataSize = sampleCount * (bitsPerSample / 8) * numChannels
                    val silenceData = ByteArray(dataSize) // all zeros
                    
                    val header = ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    header.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
                    header.putInt(36 + dataSize)
                    header.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
                    header.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
                    header.putInt(16)
                    header.putShort(1.toShort())
                    header.putShort(numChannels.toShort())
                    header.putInt(sampleRate)
                    header.putInt(sampleRate * numChannels * (bitsPerSample / 8))
                    header.putShort((numChannels * (bitsPerSample / 8)).toShort())
                    header.putShort(bitsPerSample.toShort())
                    header.put("data".toByteArray(StandardCharsets.US_ASCII))
                    header.putInt(dataSize)
                    
                    val combined = ByteArray(44 + dataSize)
                    System.arraycopy(header.array(), 0, combined, 0, 44)
                    System.arraycopy(silenceData, 0, combined, 44, dataSize)
                    
                    fetchedChunks.add(combined)
                } else {
                    val safePart = de.jamala1111.station_voices.TextSanitizer.sanitizeForModel(part.text, null, language)
                    if (!safePart.isNullOrBlank()) {
                        val cleanSafePart = safePart.replace(Regex("\\[s:\\d+\\]"), "")
                        if (cleanSafePart.isNotBlank()) {
                            val url = URI("$baseUrl/api/generate").toURL()
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.doOutput = true

                            val json = JsonObject()
                            json.addProperty("text", cleanSafePart)
                            json.addProperty("voice", voice)
                            json.addProperty("language", language)
                            json.addProperty("no-cache", false)

                            connection.outputStream.use { os ->
                                val input = json.toString().toByteArray(StandardCharsets.UTF_8)
                                os.write(input, 0, input.size)
                            }

                            if (connection.responseCode == 200) {
                                fetchedChunks.add(connection.inputStream.readAllBytes())
                            } else {
                                throw Exception("Failed to generate audio part. HTTP Code: ${connection.responseCode}")
                            }
                        }
                    }
                }
            }

            if (fetchedChunks.isNotEmpty()) {
                if (fetchedChunks.size == 1) {
                    file.writeBytes(fetchedChunks[0])
                } else {
                    // Read all WAV chunks, extract their PCM data, and write a new combined WAV
                    var totalDataSize = 0
                    var sampleRate = 22050
                    var numChannels = 1
                    var bitsPerSample = 16

                    val pcmDataList = mutableListOf<ByteArray>()
                    for (chunk in fetchedChunks) {
                        try {
                            val bais = java.io.ByteArrayInputStream(chunk)
                            val ais = javax.sound.sampled.AudioSystem.getAudioInputStream(bais)
                            sampleRate = ais.format.sampleRate.toInt()
                            numChannels = ais.format.channels
                            bitsPerSample = ais.format.sampleSizeInBits
                            val pcmBytes = ais.readAllBytes()
                            pcmDataList.add(pcmBytes)
                            totalDataSize += pcmBytes.size
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val combinedFormatBytes = ByteBuffer.allocate(44 + totalDataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    combinedFormatBytes.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
                    combinedFormatBytes.putInt(36 + totalDataSize)
                    combinedFormatBytes.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
                    combinedFormatBytes.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
                    combinedFormatBytes.putInt(16)
                    combinedFormatBytes.putShort(1.toShort())
                    combinedFormatBytes.putShort(numChannels.toShort())
                    combinedFormatBytes.putInt(sampleRate)
                    combinedFormatBytes.putInt(sampleRate * numChannels * (bitsPerSample / 8))
                    combinedFormatBytes.putShort((numChannels * (bitsPerSample / 8)).toShort())
                    combinedFormatBytes.putShort(bitsPerSample.toShort())
                    combinedFormatBytes.put("data".toByteArray(StandardCharsets.US_ASCII))
                    combinedFormatBytes.putInt(totalDataSize)
                    
                    for (pcm in pcmDataList) {
                        combinedFormatBytes.put(pcm)
                    }
                    
                    file.writeBytes(combinedFormatBytes.array())
                }
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
