package de.jamala.station_voices

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import dev.mccue.jlayer.decoder.Bitstream
import dev.mccue.jlayer.decoder.Decoder
import dev.mccue.jlayer.decoder.SampleBuffer

object JingleManager {
    private val cachedPcm = ConcurrentHashMap<Jingle, Pair<AudioFormat, ByteArray>>()
    private val cachedDurationMs = ConcurrentHashMap<Jingle, Long>()

    @Synchronized
    private fun loadPcm(jingle: Jingle): Pair<AudioFormat, ByteArray>? {
        if (jingle == Jingle.OFF) return null
        cachedPcm[jingle]?.let { return it }

        try {
            val fileName = jingle.fileName
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
                    cachedDurationMs[jingle] = durationMs
                    cachedPcm[jingle] = decoded
                    return decoded
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun decodeAudio(bytes: ByteArray, fileName: String): Pair<AudioFormat, ByteArray>? {
        if (fileName.endsWith(".wav", ignoreCase = true)) {
            return decodeWav(bytes)
        }
        return decodeMp3(bytes)
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

    fun getJingleDuration(jingle: Jingle, speed: Float = 1.0f): Long {
        if (jingle == Jingle.OFF) return 0L
        loadPcm(jingle)
        val baseMs = cachedDurationMs[jingle] ?: 0L
        val speedFactor = if (speed > 0.01f) speed.toDouble() else 1.0
        return (baseMs / speedFactor).toLong()
    }

    fun getJingleDuration(jingleStr: String?, speed: Float = 1.0f): Long {
        return getJingleDuration(Jingle.fromString(jingleStr), speed)
    }

    fun getJingleDuration(jingle: Jingle, speed: Float, timing: JingleTiming): Long {
        if (jingle == Jingle.OFF) return 0L
        val multiplier = when (timing) {
            JingleTiming.BOTH -> 2
            JingleTiming.BEFORE, JingleTiming.AFTER -> 1
        }
        return getJingleDuration(jingle, speed) * multiplier
    }

    fun getJingleDuration(jingleStr: String?, speed: Float, timing: JingleTiming): Long {
        return getJingleDuration(Jingle.fromString(jingleStr), speed, timing)
    }

    fun getJingleBytesForFormat(jingle: Jingle, targetFormat: AudioFormat): ByteArray {
        if (jingle == Jingle.OFF) return ByteArray(0)
        val pair = loadPcm(jingle) ?: return ByteArray(0)
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

    fun getJingleBytesForFormat(jingleStr: String?, targetFormat: AudioFormat): ByteArray {
        return getJingleBytesForFormat(Jingle.fromString(jingleStr), targetFormat)
    }

    fun getGongDuration(speed: Float = 1.0f): Long = getJingleDuration(Jingle.DB, speed)

    fun getGongDuration(speed: Float, timing: JingleTiming): Long = getJingleDuration(Jingle.DB, speed, timing)

    fun getGongBytesForFormat(targetFormat: AudioFormat): ByteArray = getJingleBytesForFormat(Jingle.DB, targetFormat)
}
