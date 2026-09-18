package de.jamala.station_voices.block

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import de.jamala.station_voices.network.OpenTrainAnnouncerScreenPayload
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.network.PacketDistributor

class TrainAnnouncerInteractionBehaviour : MovingInteractionBehaviour() {
    override fun handlePlayerInteraction(
        player: Player,
        activeHand: InteractionHand,
        localPos: BlockPos,
        contraptionEntity: AbstractContraptionEntity
    ): Boolean {
        if (contraptionEntity !is CarriageContraptionEntity) return false

        if (player.level().isClientSide) {
            // Client side confirms interaction, prompting network packet to server
            return true
        }

        val serverPlayer = player as? ServerPlayer ?: return true
        val contraption = contraptionEntity.contraption
        val blockInfo = contraption.getBlocks()[localPos] ?: return true
        val tag = blockInfo.nbt() ?: CompoundTag()

        val json = when {
            tag.contains("StationProfiles") -> tag.getString("StationProfiles")
            tag.contains("TrainProfiles") -> tag.getString("TrainProfiles")
            else -> "{}"
        }

        PacketDistributor.sendToPlayer(
            serverPlayer,
            OpenTrainAnnouncerScreenPayload(
                entityId = contraptionEntity.id,
                pos = null,
                localPos = localPos,
                profilesJson = json
            )
        )
        return true
    }
}
