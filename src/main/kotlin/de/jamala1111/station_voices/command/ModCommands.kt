package de.jamala1111.station_voices.command

import com.mojang.brigadier.CommandDispatcher
import de.jamala1111.station_voices.CreateStationVoices
import de.jamala1111.station_voices.JingleManager
import de.jamala1111.station_voices.network.OpenJingleManagerScreenPayload
import de.jamala1111.station_voices.network.OpenModelDownloadScreenPayload
import de.jamala1111.station_voices.network.ReloadJinglesPayload
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.network.PacketDistributor

object ModCommands {
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("create_station_voices")
                .then(Commands.literal("piper_models")
                    .requires { it.hasPermission(2) }
                    .executes { context ->
                        val player = context.source.playerOrException
                        PacketDistributor.sendToPlayer(player, OpenModelDownloadScreenPayload())
                        1
                    }
                )
                .then(Commands.literal("jingle_manager")
                    .requires { it.hasPermission(2) }
                    .executes { context ->
                        val player = context.source.playerOrException
                        PacketDistributor.sendToPlayer(player, OpenJingleManagerScreenPayload())
                        1
                    }
                )
                .then(Commands.literal("jingles")
                    .requires { it.hasPermission(2) }
                    .executes { context ->
                        val player = context.source.playerOrException
                        PacketDistributor.sendToPlayer(player, OpenJingleManagerScreenPayload())
                        1
                    }
                )
                .then(Commands.literal("reload_jingles")
                    .requires { it.hasPermission(2) || !it.server.isDedicatedServer }
                    .executes { context ->
                        val count = JingleManager.reload()
                        PacketDistributor.sendToAllPlayers(ReloadJinglesPayload())
                        context.source.sendSuccess({
                            Component.translatable("command.create_station_voices.reload_jingles.success", count)
                        }, true)
                        count
                    }
                )
        )
    }
}
