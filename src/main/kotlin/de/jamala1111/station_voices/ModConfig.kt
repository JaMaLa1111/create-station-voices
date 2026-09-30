package de.jamala1111.station_voices

import net.minecraft.commands.CommandSourceStack
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
        val commandPermissionLevel: ModConfigSpec.IntValue

        init {
            builder.push("tts")
            ttsMode = builder
                .comment("Mode for Text-to-Speech generation. WEB_API uses an external endpoint, LOCAL_PIPER runs Piper TTS locally on the server.")
                .defineEnum("ttsMode", TtsMode.LOCAL_PIPER)
            webApiUrl = builder
                .comment("URL for the Web API TTS. Used if ttsMode is WEB_API. Required if using Web API. Trailing slash should not be included (e.g., https://my-tts-api.com).")
                .define("webApiUrl", "")
            builder.pop()

            builder.push("permissions")
            commandPermissionLevel = builder
                .comment(
                    "Permission level required to use /create_station_voices commands and to edit custom jingles.",
                    "0 = everyone, 1 = moderators, 2 = game masters (default), 3 = admins, 4 = owners.",
                    "Always allowed on non-dedicated (singleplayer / LAN host) servers."
                )
                .defineInRange("commandPermissionLevel", 2, 0, 4)
            builder.pop()
        }
    }

    /** Whether [source] may use the mod's management commands / screens, per [Server.commandPermissionLevel]. */
    fun hasManagePermission(source: CommandSourceStack): Boolean =
        source.hasPermission(SERVER.commandPermissionLevel.get()) || !source.server.isDedicatedServer

    enum class TtsMode {
        WEB_API, LOCAL_PIPER
    }
}
