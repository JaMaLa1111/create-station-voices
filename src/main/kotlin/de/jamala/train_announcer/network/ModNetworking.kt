package de.jamala.train_announcer.network

import de.jamala.train_announcer.TrainAnnouncer
import de.jamala.train_announcer.block.AnnouncerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

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
        val ID = CustomPacketPayload.Type<SetAnnouncerDataPayload>(ResourceLocation.fromNamespaceAndPath(TrainAnnouncer.ID, "set_announcer_data"))
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
        val ID = CustomPacketPayload.Type<PlayAnnouncerAudioPayload>(ResourceLocation.fromNamespaceAndPath(TrainAnnouncer.ID, "play_announcer_audio"))
        val STREAM_CODEC = StreamCodec.ofMember(PlayAnnouncerAudioPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): PlayAnnouncerAudioPayload {
            return PlayAnnouncerAudioPayload(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt())
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

data class PlayAnnouncerAudioDataChunkPayload(
    val pos: BlockPos,
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
        val ID = CustomPacketPayload.Type<PlayAnnouncerAudioDataChunkPayload>(ResourceLocation.fromNamespaceAndPath(TrainAnnouncer.ID, "play_announcer_audio_chunk"))
        val STREAM_CODEC = StreamCodec.ofMember(PlayAnnouncerAudioDataChunkPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): PlayAnnouncerAudioDataChunkPayload {
            val pos = buf.readBlockPos()
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
        buf.writeBlockPos(pos)
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

object ModNetworking {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(TrainAnnouncer.ID)

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

        registrar.playToClient(
            PlayAnnouncerAudioDataChunkPayload.ID,
            PlayAnnouncerAudioDataChunkPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.train_announcer.client.AudioPlayer.handleAudioChunk(
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
                de.jamala.train_announcer.client.AudioPlayer.play(
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
