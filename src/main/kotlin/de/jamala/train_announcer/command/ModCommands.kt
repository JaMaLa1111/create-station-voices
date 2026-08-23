package de.jamala.train_announcer.command

import com.mojang.brigadier.CommandDispatcher
import de.jamala.train_announcer.TrainAnnouncer
import de.jamala.train_announcer.network.OpenModelDownloadScreenPayload
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.network.PacketDistributor

object ModCommands {
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("train_announcer")
                .then(Commands.literal("piper_models")
                    .requires { it.hasPermission(2) }
                    .executes { context ->
                        val player = context.source.playerOrException
                        PacketDistributor.sendToPlayer(player, OpenModelDownloadScreenPayload())
                        1
                    }
                )
        )
    }
}
