package de.jamala.station_voices.network

import de.jamala.station_voices.CreateStationVoices
import de.jamala.station_voices.block.AnnouncerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import kotlinx.coroutines.launch

import java.util.UUID

data class SetAnnouncerDataPayload(
    val pos: BlockPos, 
    val text: String, 
    val voice: String, 
    val language: String, 
    val speed: Float,
    val volume: Float,
    val reverb: Boolean,
    val maxRange: Int
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetAnnouncerDataPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "set_announcer_data"))
        val STREAM_CODEC = StreamCodec.ofMember(SetAnnouncerDataPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): SetAnnouncerDataPayload {
            return SetAnnouncerDataPayload(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt())
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeUtf(text)
        buf.writeUtf(voice)
        buf.writeUtf(language)
        buf.writeFloat(speed)
        buf.writeFloat(volume)
        buf.writeBoolean(reverb)
        buf.writeInt(maxRange)
    }
}

data class PlayAnnouncerAudioPayload(
    val pos: BlockPos?,
    val text: String, 
    val voice: String, 
    val language: String, 
    val speed: Float,
    val volume: Float,
    val reverb: Boolean,
    val maxRange: Int
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PlayAnnouncerAudioPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "play_announcer_audio"))
        val STREAM_CODEC = StreamCodec.ofMember(PlayAnnouncerAudioPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): PlayAnnouncerAudioPayload {
            val hasPos = buf.readBoolean()
            val pos = if (hasPos) buf.readBlockPos() else null
            return PlayAnnouncerAudioPayload(pos, buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt())
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(pos != null)
        if (pos != null) {
            buf.writeBlockPos(pos)
        }
        buf.writeUtf(text)
        buf.writeUtf(voice)
        buf.writeUtf(language)
        buf.writeFloat(speed)
        buf.writeFloat(volume)
        buf.writeBoolean(reverb)
        buf.writeInt(maxRange)
    }
}

data class PlayAnnouncerAudioDataChunkPayload(
    val pos: BlockPos?,
    val speed: Float,
    val volume: Float,
    val reverb: Boolean,
    val maxRange: Int,
    val streamId: UUID,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkData: ByteArray
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PlayAnnouncerAudioDataChunkPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "play_announcer_audio_chunk"))
        val STREAM_CODEC = StreamCodec.ofMember(PlayAnnouncerAudioDataChunkPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): PlayAnnouncerAudioDataChunkPayload {
            val hasPos = buf.readBoolean()
            val pos = if (hasPos) buf.readBlockPos() else null
            val speed = buf.readFloat()
            val volume = buf.readFloat()
            val reverb = buf.readBoolean()
            val maxRange = buf.readInt()
            val streamId = buf.readUUID()
            val chunkIndex = buf.readInt()
            val totalChunks = buf.readInt()
            val dataLen = buf.readInt()
            val chunkData = ByteArray(dataLen)
            buf.readBytes(chunkData)
            return PlayAnnouncerAudioDataChunkPayload(pos, speed, volume, reverb, maxRange, streamId, chunkIndex, totalChunks, chunkData)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(pos != null)
        if (pos != null) {
            buf.writeBlockPos(pos)
        }
        buf.writeFloat(speed)
        buf.writeFloat(volume)
        buf.writeBoolean(reverb)
        buf.writeInt(maxRange)
        buf.writeUUID(streamId)
        buf.writeInt(chunkIndex)
        buf.writeInt(totalChunks)
        buf.writeInt(chunkData.size)
        buf.writeBytes(chunkData)
    }
}

data class RequestPreviewAudioPayload(
    val text: String,
    val voice: String,
    val language: String,
    val speed: Float,
    val volume: Float,
    val reverb: Boolean,
    val maxRange: Int
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<RequestPreviewAudioPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "request_preview_audio"))
        val STREAM_CODEC = StreamCodec.ofMember(RequestPreviewAudioPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): RequestPreviewAudioPayload {
            return RequestPreviewAudioPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt())
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(text)
        buf.writeUtf(voice)
        buf.writeUtf(language)
        buf.writeFloat(speed)
        buf.writeFloat(volume)
        buf.writeBoolean(reverb)
        buf.writeInt(maxRange)
    }
}

data class AnnouncerAudioFinishedPayload(val pos: BlockPos) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AnnouncerAudioFinishedPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "announcer_audio_finished"))
        val STREAM_CODEC = StreamCodec.ofMember(AnnouncerAudioFinishedPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): AnnouncerAudioFinishedPayload {
            return AnnouncerAudioFinishedPayload(buf.readBlockPos())
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
    }
}

object ModNetworking {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CreateStationVoices.ID)

        registrar.playToServer(
            AnnouncerAudioFinishedPayload.ID,
            AnnouncerAudioFinishedPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player()
                val level = player.level()
                val pos = payload.pos
                val be = level.getBlockEntity(pos)
                if (be is AnnouncerBlockEntity && be.isPlaying) {
                    be.isPlaying = false
                    val state = level.getBlockState(pos)
                    val hasSignal = level.hasNeighborSignal(pos)
                    if (!hasSignal && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                        level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3)
                    }
                } else if (be is de.jamala.station_voices.block.ConfigurableAnnouncerBlockEntity && be.isPlaying) {
                    be.isPlaying = false
                    val state = level.getBlockState(pos)
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                        level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3)
                    }
                }
            }
        }

        registrar.playToServer(
            SetAnnouncerDataPayload.ID,
            SetAnnouncerDataPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player()
                val level = player.level()
                val pos = payload.pos
                
                if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) < 64) {
                    val blockEntity = level.getBlockEntity(pos)
                    if (blockEntity is AnnouncerBlockEntity) {
                        blockEntity.ttsText = payload.text
                        blockEntity.ttsVoice = payload.voice
                        blockEntity.ttsLanguage = payload.language
                        blockEntity.ttsSpeed = payload.speed
                        blockEntity.ttsVolume = payload.volume
                        blockEntity.ttsReverb = payload.reverb
                        blockEntity.ttsMaxRange = payload.maxRange
                        blockEntity.setChanged()
                        level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, 3)
                    }
                }
            }
        }

        registrar.playToServer(
            RequestPreviewAudioPayload.ID,
            RequestPreviewAudioPayload.STREAM_CODEC
        ) { payload, context ->
            val player = context.player() as net.minecraft.server.level.ServerPlayer
            
            if (de.jamala.station_voices.ModConfig.SERVER.ttsMode.get() == de.jamala.station_voices.ModConfig.TtsMode.LOCAL_PIPER) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val audioData = de.jamala.station_voices.server.PiperManager.generateAudio(payload.text, payload.voice, payload.language)
                    if (audioData != null) {
                        val streamId = UUID.randomUUID()
                        val chunkSize = 30000
                        val totalChunks = Math.ceil(audioData.size.toDouble() / chunkSize.toDouble()).toInt()
                        
                        for (i in 0 until totalChunks) {
                            val start = i * chunkSize
                            val end = Math.min(start + chunkSize, audioData.size)
                            val chunk = audioData.copyOfRange(start, end)
                            
                            val chunkPayload = PlayAnnouncerAudioDataChunkPayload(
                                null, // preview implies no position
                                payload.speed,
                                payload.volume,
                                payload.reverb,
                                payload.maxRange,
                                streamId,
                                i,
                                totalChunks,
                                chunk
                            )
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, chunkPayload)
                        }
                    }
                }
            } else {
                val outPayload = PlayAnnouncerAudioPayload(
                    null, // preview implies no position
                    payload.text,
                    payload.voice,
                    payload.language,
                    payload.speed,
                    payload.volume,
                    payload.reverb,
                    payload.maxRange
                )
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, outPayload)
            }
        }

        registrar.playToClient(
            PlayAnnouncerAudioDataChunkPayload.ID,
            PlayAnnouncerAudioDataChunkPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.station_voices.client.AudioPlayer.handleAudioChunk(
                            payload.pos,
                            payload.speed,
                            payload.volume,
                            payload.reverb,
                            payload.maxRange,
                            payload.streamId,
                            payload.chunkIndex,
                            payload.totalChunks,
                            payload.chunkData
                        )
                    }
                }
            }
        }

        registrar.playToClient(
            PlayAnnouncerAudioPayload.ID,
            PlayAnnouncerAudioPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                de.jamala.station_voices.client.AudioPlayer.play(
                    payload.pos,
                    payload.text, 
                    payload.voice, 
                    payload.language, 
                    payload.speed,
                    payload.volume,
                    payload.reverb,
                    payload.maxRange
                )
            }
        }
    }
}
