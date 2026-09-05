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
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import de.jamala.station_voices.ModConfig
import de.jamala.station_voices.server.PiperManager
import de.jamala.station_voices.network.PlayAnnouncerAudioDataChunkPayload
import de.jamala.station_voices.JingleTiming
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
        val hasSignal = context.level.hasNeighborSignal(context.clickedPos)
        return defaultBlockState().setValue(BlockStateProperties.POWERED, hasSignal)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return AnnouncerBlockEntity(pos, state)
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (!level.isClientSide) {
            BlockEntityTicker { level, pos, state, be ->
                if (be is AnnouncerBlockEntity) {
                    be.tick(level, pos, state)
                }
            }
        } else null
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
                            be.ttsMaxRange,
                            be.ttsJingle,
                            be.ttsJingleTiming,
                            be.ttsRealism
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
            val be = level.getBlockEntity(pos) as? AnnouncerBlockEntity ?: return
            val hasSignal = level.hasNeighborSignal(pos)
            val isPoweredState = state.getValue(BlockStateProperties.POWERED)

            if (hasSignal && !be.wasPoweredByRedstone) {
                be.wasPoweredByRedstone = true
                if (!isPoweredState) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), 3)
                }
                triggerAudio(level, pos, be)
            } else if (!hasSignal && be.wasPoweredByRedstone) {
                be.wasPoweredByRedstone = false
                if (!be.isPlaying && isPoweredState) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), 3)
                }
            }
        }
    }

    private fun triggerAudio(level: Level, pos: BlockPos, be: AnnouncerBlockEntity) {
        be.isPlaying = true
        be.lastAnnouncementText = be.ttsText
        be.setChanged()
        com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level, pos)
        if (ModConfig.SERVER.ttsMode.get() == ModConfig.TtsMode.LOCAL_PIPER) {
            CoroutineScope(Dispatchers.IO).launch {
                val audioData = PiperManager.generateAudio(be.ttsText, be.ttsVoice, be.ttsLanguage)
                if (audioData != null) {
                    val durationMs = calculateWavDurationMs(audioData, be.ttsSpeed, be.ttsReverb, be.ttsJingle, be.ttsJingleTiming)
                    be.audioEndTimeMillis = System.currentTimeMillis() + durationMs

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
                            be.ttsJingle,
                            be.ttsJingleTiming,
                            be.ttsRealism,
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

                    val speakers = SpeakerManager.getSpeakersFor(level, pos)
                    for (speakerPos in speakers) {
                        val speakerStreamId = UUID.randomUUID()
                        for (i in 0 until totalChunks) {
                            val start = i * chunkSize
                            val end = Math.min(start + chunkSize, audioData.size)
                            val chunk = audioData.copyOfRange(start, end)

                            val chunkPayload = PlayAnnouncerAudioDataChunkPayload(
                                speakerPos,
                                be.ttsSpeed,
                                be.ttsVolume,
                                be.ttsReverb,
                                be.ttsMaxRange,
                                be.ttsJingle,
                                be.ttsJingleTiming,
                                be.ttsRealism,
                                speakerStreamId,
                                i,
                                totalChunks,
                                chunk
                            )

                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(
                                level as net.minecraft.server.level.ServerLevel,
                                null,
                                speakerPos.x + 0.5,
                                speakerPos.y + 0.5,
                                speakerPos.z + 0.5,
                                be.ttsMaxRange.toDouble(),
                                chunkPayload
                            )
                        }

                        (level as? net.minecraft.server.level.ServerLevel)?.server?.execute {
                            val speakerBe = level.getBlockEntity(speakerPos) as? SpeakerBlockEntity
                            if (speakerBe != null && speakerBe.linkedAnnouncer == pos) {
                                speakerBe.isPlaying = true
                                speakerBe.audioEndTimeMillis = System.currentTimeMillis() + durationMs
                                val st = level.getBlockState(speakerPos)
                                if (st.hasProperty(BlockStateProperties.POWERED) && !st.getValue(BlockStateProperties.POWERED)) {
                                    level.setBlock(speakerPos, st.setValue(BlockStateProperties.POWERED, true), 3)
                                }
                                com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level, speakerPos)
                            }
                        }
                    }
                } else {
                    be.isPlaying = false
                    val hasSignal = level.hasNeighborSignal(pos)
                    val state = level.getBlockState(pos)
                    if (!hasSignal && state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), 3)
                    }
                    (level as? net.minecraft.server.level.ServerLevel)?.server?.execute {
                        com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level, pos)
                    }
                }
            }
        } else {
            val timing = JingleTiming.fromString(be.ttsJingleTiming)
            val jingleDurationMs = de.jamala.station_voices.JingleManager.getJingleDuration(be.ttsJingle, be.ttsSpeed, timing)
            val estimatedDurationMs = (be.ttsText.length * 120L / be.ttsSpeed.coerceAtLeast(0.1f).toDouble()).toLong() + 2500L + (if (be.ttsReverb) 900L else 0L) + jingleDurationMs
            be.audioEndTimeMillis = System.currentTimeMillis() + estimatedDurationMs

            val payload = de.jamala.station_voices.network.PlayAnnouncerAudioPayload(
                pos,
                be.ttsText, 
                be.ttsVoice, 
                be.ttsLanguage, 
                be.ttsSpeed, 
                be.ttsVolume, 
                be.ttsReverb, 
                be.ttsMaxRange, 
                be.ttsJingle, 
                be.ttsJingleTiming, 
                be.ttsRealism
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

            val speakers = SpeakerManager.getSpeakersFor(level, pos)
            for (speakerPos in speakers) {
                val speakerPayload = de.jamala.station_voices.network.PlayAnnouncerAudioPayload(
                    speakerPos,
                    be.ttsText, 
                    be.ttsVoice, 
                    be.ttsLanguage, 
                    be.ttsSpeed, 
                    be.ttsVolume, 
                    be.ttsReverb, 
                    be.ttsMaxRange, 
                    be.ttsJingle, 
                    be.ttsJingleTiming, 
                    be.ttsRealism
                )
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(
                    level as net.minecraft.server.level.ServerLevel, 
                    null, 
                    speakerPos.x.toDouble() + 0.5, 
                    speakerPos.y.toDouble() + 0.5, 
                    speakerPos.z.toDouble() + 0.5, 
                    be.ttsMaxRange.toDouble(), 
                    speakerPayload
                )

                val speakerBe = level.getBlockEntity(speakerPos) as? SpeakerBlockEntity
                if (speakerBe != null) {
                    speakerBe.isPlaying = true
                    speakerBe.audioEndTimeMillis = System.currentTimeMillis() + estimatedDurationMs
                    val st = level.getBlockState(speakerPos)
                    if (st.hasProperty(BlockStateProperties.POWERED) && !st.getValue(BlockStateProperties.POWERED)) {
                        level.setBlock(speakerPos, st.setValue(BlockStateProperties.POWERED, true), 3)
                    }
                    com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level, speakerPos)
                }
            }
        }
    }

    private fun calculateWavDurationMs(audioData: ByteArray, speed: Float, reverb: Boolean, jingle: String, jingleTiming: String = "BOTH"): Long {
        try {
            val bais = java.io.ByteArrayInputStream(audioData)
            val audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(bais)
            val format = audioIn.format
            val frameLength = audioData.size.toLong() / format.frameSize
            val durationSeconds = frameLength.toDouble() / format.frameRate.toDouble()
            val speedFactor = if (speed > 0.01f) speed.toDouble() else 1.0
            val adjustedMs = ((durationSeconds / speedFactor) * 1000.0).toLong()
            val reverbTailMs = if (reverb) 900L else 0L
            val timing = JingleTiming.fromString(jingleTiming)
            val jingleMs = de.jamala.station_voices.JingleManager.getJingleDuration(jingle, speed, timing)
            return adjustedMs + reverbTailMs + jingleMs
        } catch (e: Exception) {
            return 2000L
        }
    }
}
