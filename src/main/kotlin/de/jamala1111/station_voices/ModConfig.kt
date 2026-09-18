package de.jamala.station_voices

import net.neoforged.neoforge.common.ModConfigSpec

object ModConfig {
    val SERVER_SPEC: ModConfigSpec
    val SERVER: Server

    init {
        val specPair = ModConfigSpec.Builder().configure(::Server)
        SERVER_SPEC = specPair.right
        SERVER = specPair.left
    }

    class Server(builder: ModConfigSpec.Builder) {
        val ttsMode: ModConfigSpec.EnumValue<TtsMode>
        val webApiUrl: ModConfigSpec.ConfigValue<String>

        init {
            builder.push("tts")
            ttsMode = builder
                .comment("Mode for Text-to-Speech generation. WEB_API uses an external endpoint, LOCAL_PIPER runs Piper TTS locally on the server.")
                .defineEnum("ttsMode", TtsMode.LOCAL_PIPER)
            webApiUrl = builder
                .comment("URL for the Web API TTS. Used if ttsMode is WEB_API. Required if using Web API. Trailing slash should not be included (e.g., https://my-tts-api.com).")
                .define("webApiUrl", "")
            builder.pop()
        }
    }

    enum class TtsMode {
        WEB_API, LOCAL_PIPER
    }
}
