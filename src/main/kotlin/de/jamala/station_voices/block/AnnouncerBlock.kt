package de.jamala.station_voices.block

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import de.jamala.station_voices.ModConfig
import de.jamala.station_voices.server.PiperManager
import de.jamala.station_voices.network.PlayAnnouncerAudioDataChunkPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class AnnouncerBlock(properties: Properties) : Block(properties), EntityBlock {
    init {
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.POWERED)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(BlockStateProperties.POWERED, context.level.hasNeighborSignal(context.clickedPos))
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return AnnouncerBlockEntity(pos, state)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) {
            val be = level.getBlockEntity(pos) as? AnnouncerBlockEntity
            if (be != null) {
                runForDist(
                    clientTarget = {
                        de.jamala.station_voices.client.ClientHooks.openAnnouncerScreen(
                            pos, 
                            be.ttsText, 
                            be.ttsVoice, 
                            be.ttsLanguage,
                            be.ttsSpeed,
                            be.ttsVolume,
                            be.ttsReverb,
                            be.ttsMaxRange
                        )
                    },
                    serverTarget = {}
                )
            }
        }
        return InteractionResult.SUCCESS
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        fromPos: BlockPos,
        isMoving: Boolean
    ) {
        if (!level.isClientSide) {
            val isPowered = state.getValue(BlockStateProperties.POWERED)
            val hasSignal = level.hasNeighborSignal(pos)

            if (hasSignal && !isPowered) {
                val be = level.getBlockEntity(pos) as? AnnouncerBlockEntity
                if (be != null) {
                    if (ModConfig.SERVER.ttsMode.get() == ModConfig.TtsMode.LOCAL_PIPER) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val audioData = PiperManager.generateAudio(be.ttsText, be.ttsVoice, be.ttsLanguage)
                            if (audioData != null) {
                                val streamId = UUID.randomUUID()
                                val chunkSize = 30000
                                val totalChunks = Math.ceil(audioData.size.toDouble() / chunkSize.toDouble()).toInt()
                                
                                for (i in 0 until totalChunks) {
                                    val start = i * chunkSize
                                    val end = Math.min(start + chunkSize, audioData.size)
                                    val chunk = audioData.copyOfRange(start, end)
                                    
                                    val chunkPayload = PlayAnnouncerAudioDataChunkPayload(
                                        pos,
                                        be.ttsSpeed,
                                        be.ttsVolume,
                                        be.ttsReverb,
                                        be.ttsMaxRange,
                                        streamId,
                                        i,
                                        totalChunks,
                                        chunk
                                    )
                                    
                                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(
                                        level as net.minecraft.server.level.ServerLevel,
                                        null,
                                        pos.x + 0.5,
                                        pos.y + 0.5,
                                        pos.z + 0.5,
                                        be.ttsMaxRange.toDouble(),
                                        chunkPayload
                                    )
                                }
                            }
                        }
                    } else {
                        val payload = de.jamala.station_voices.network.PlayAnnouncerAudioPayload(
                            pos,
                            be.ttsText, 
                            be.ttsVoice, 
                            be.ttsLanguage,
                            be.ttsSpeed,
                            be.ttsVolume,
                            be.ttsReverb,
                            be.ttsMaxRange
                        )
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(
                            level as net.minecraft.server.level.ServerLevel, 
                            null, 
                            pos.x.toDouble() + 0.5, 
                            pos.y.toDouble() + 0.5, 
                            pos.z.toDouble() + 0.5, 
                            be.ttsMaxRange.toDouble(), 
                            payload
                        )
                    }
                }
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), 3)
            } else if (!hasSignal && isPowered) {
                // Unpower
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), 3)
            }
        }
    }
}
