package de.jamala.station_voices

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import dev.mccue.jlayer.decoder.Bitstream
import dev.mccue.jlayer.decoder.Decoder
import dev.mccue.jlayer.decoder.SampleBuffer

object JingleManager {
    private var cachedGongPcm: Pair<AudioFormat, ByteArray>? = null
    private var cachedGongDurationMs: Long = 0L

    @Synchronized
    private fun loadGongPcm(): Pair<AudioFormat, ByteArray>? {
        if (cachedGongPcm != null) return cachedGongPcm

        try {
            val stream: InputStream? = JingleManager::class.java.getResourceAsStream("/assets/create_station_voices/sounds/db-gong.mp3")
                ?: JingleManager::class.java.getResourceAsStream("/db-gong.mp3")
                ?: File("db-gong.mp3").takeIf { it.exists() }?.inputStream()

            if (stream != null) {
                stream.use { input ->
                    val bitstream = Bitstream(input)
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

                    val bytes = baos.toByteArray()
                    val format = AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate,
                        16,
                        channels,
                        channels * 2,
                        sampleRate,
                        false
                    )
                    cachedGongDurationMs = ((bytes.size.toDouble() / format.frameSize) / format.frameRate * 1000.0).toLong()
                    cachedGongPcm = Pair(format, bytes)
                    return cachedGongPcm
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getGongDuration(speed: Float = 1.0f): Long {
        loadGongPcm()
        val speedFactor = if (speed > 0.01f) speed.toDouble() else 1.0
        return (cachedGongDurationMs / speedFactor).toLong()
    }

    fun getGongBytesForFormat(targetFormat: AudioFormat): ByteArray {
        val gong = loadGongPcm() ?: return ByteArray(0)
        val (gongFormat, gongBytes) = gong
        if (gongFormat.matches(targetFormat)) {
            return gongBytes
        }
        return try {
            val gongStream = AudioInputStream(
                ByteArrayInputStream(gongBytes),
                gongFormat,
                gongBytes.size.toLong() / gongFormat.frameSize
            )
            val convertedStream = AudioSystem.getAudioInputStream(targetFormat, gongStream)
            convertedStream.readAllBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            gongBytes
        }
    }
}
