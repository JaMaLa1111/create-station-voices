package de.jamala.station_voices.client

import javax.sound.sampled.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object RealismFilter {

    class Biquad {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = if (y.isFinite()) y else 0.0
            return y1
        }

        fun setHighpass(sampleRate: Double, cutoff: Double, q: Double = 0.707) {
            val w0 = 2.0 * Math.PI * (cutoff.coerceIn(10.0, sampleRate * 0.49)) / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosw0) / 2.0) / a0
            b1 = (-(1.0 + cosw0)) / a0
            b2 = ((1.0 + cosw0) / 2.0) / a0
            a1 = (-2.0 * cosw0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun setLowpass(sampleRate: Double, cutoff: Double, q: Double = 0.707) {
            val w0 = 2.0 * Math.PI * (cutoff.coerceIn(10.0, sampleRate * 0.49)) / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosw0) / 2.0) / a0
            b1 = (1.0 - cosw0) / a0
            b2 = ((1.0 - cosw0) / 2.0) / a0
            a1 = (-2.0 * cosw0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun setPeaking(sampleRate: Double, freq: Double, gainDb: Double, q: Double = 1.5) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * Math.PI * (freq.coerceIn(10.0, sampleRate * 0.49)) / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val a0 = 1.0 + alpha / a
            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * cosw0) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * cosw0) / a0
            a2 = (1.0 - alpha / a) / a0
        }
    }

    fun apply(input: ByteArray, format: AudioFormat, realism: Float): ByteArray {
        if (realism <= 0.001f || format.sampleSizeInBits != 16) return input

        val r = realism.coerceIn(0.0f, 1.0f).toDouble()
        val numSamples = input.size / 2
        val channels = format.channels
        val sampleRate = format.sampleRate.toDouble()

        val rawShorts = ShortArray(numSamples)
        ByteBuffer.wrap(input).order(
            if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        ).asShortBuffer().get(rawShorts)

        // Filters per channel
        // HP: 40Hz -> 500Hz
        val hpCutoff = 40.0 + r * 460.0
        // LP: 18000Hz -> 3400Hz
        val lpCutoff = 18000.0 - r * 14600.0
        // Resonant Horn Megaphone Peak around 2000Hz (+0dB -> +8dB)
        val peakGain = r * 8.0

        val hpFilters = Array(channels) { Biquad().apply { setHighpass(sampleRate, hpCutoff, 0.8) } }
        val lpFilters = Array(channels) { Biquad().apply { setLowpass(sampleRate, lpCutoff, 0.8) } }
        val peakFilters = Array(channels) { Biquad().apply { setPeaking(sampleRate, 2000.0, peakGain, 1.4) } }

        val outShorts = ShortArray(numSamples)
        var compEnvelope = 0.0

        val drive = 1.0 + r * 2.2
        val noiseAmp = r * 0.003
        val humAmp = r * 0.004
        val random = java.util.Random(42)

        for (i in 0 until numSamples) {
            val ch = i % channels
            val frameIndex = i / channels
            val originalVal = rawShorts[i] / 32768.0

            // 1. Bandpass & Peaking EQ
            var filtered = hpFilters[ch].process(originalVal)
            filtered = lpFilters[ch].process(filtered)
            filtered = peakFilters[ch].process(filtered)

            // 2. Drive & Soft Saturation / Overdrive (tanh distortion)
            var driven = filtered * drive
            // Slight asymmetrical diode characteristic
            driven += 0.15 * r * driven * driven * sign(driven)
            val saturated = tanh(driven)

            // 3. Compressor
            val absVal = abs(saturated)
            compEnvelope = 0.995 * compEnvelope + 0.005 * absVal
            val compGain = if (compEnvelope > 0.25) {
                (0.25 + (compEnvelope - 0.25) * 0.3) / compEnvelope
            } else {
                1.0 + (0.25 - compEnvelope) * r * 0.8
            }
            var compressed = saturated * compGain

            // 4. PA Background Line Noise & 50Hz Hum
            val noise = (random.nextDouble() * 2.0 - 1.0) * noiseAmp
            val hum = sin(2.0 * Math.PI * 50.0 * frameIndex / sampleRate) * humAmp
            compressed += noise + hum

            // 5. Dry / Wet Blend
            val blended = (1.0 - r) * originalVal + r * compressed
            val clamped = blended.coerceIn(-1.0, 1.0)
            outShorts[i] = (clamped * 32767.0).toInt().toShort()
        }

        val outBytes = ByteArray(numSamples * 2)
        val bb = ByteBuffer.wrap(outBytes).order(
            if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        )
        for (s in outShorts) {
            bb.putShort(s)
        }
        return outBytes
    }
}
