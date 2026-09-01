package de.jamala.station_voices.block

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import net.minecraft.network.chat.Component
import de.jamala.station_voices.network.PlayAnnouncerAudioPayload
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.network.PacketDistributor
import de.jamala.station_voices.ModConfig
import de.jamala.station_voices.server.PiperManager
import de.jamala.station_voices.network.PlayAnnouncerAudioDataChunkPayload
import de.jamala.station_voices.JingleTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

data class TrainProfile(
    var text: String = "",
    var voice: String = "amy",
    var language: String = "en_US",
    var speed: Float = 1.0f,
    var volume: Float = 1.0f,
    var reverb: Boolean = false,
    var maxRange: Int = 32,
    var jingle: String = "OFF",
    var jingleTiming: String = "BOTH",
    var realism: Float = 0.0f
)

class ConfigurableAnnouncerBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.CONFIGURABLE_ANNOUNCER_BLOCK_ENTITY, pos, state), IHaveGoggleInformation {
    var profiles: MutableMap<String, TrainProfile> = mutableMapOf()
    var targetStation: BlockPos? = null
    private var lastPresentTrain: UUID? = null

    var isPlaying: Boolean = false
    var audioEndTimeMillis: Long = 0L

    private val gson = Gson()

    fun tick() {
        val level = level ?: return
        if (level.isClientSide) return

        if (isPlaying) {
            if (System.currentTimeMillis() >= audioEndTimeMillis) {
                isPlaying = false
                val state = level.getBlockState(blockPos)
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                    level.setBlock(blockPos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3)
                }
            }
        } else {
            val state = level.getBlockState(blockPos)
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                level.setBlock(blockPos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3)
            }
        }

        val targetBe = targetStation?.let { level.getBlockEntity(it) }

        if (targetBe is StationBlockEntity) {
            val station = targetBe.station
            if (station != null) {
                val presentTrain = station.presentTrain
                if (presentTrain != null) {
                    if (lastPresentTrain != presentTrain.id) {
                        lastPresentTrain = presentTrain.id
                        playTrainAudio(presentTrain.name.string)
                    }
                } else {
                    lastPresentTrain = null
                }
            }
        } else if (targetBe is TrackObserverBlockEntity) {
            val trainId = targetBe.passingTrainUUID
            if (trainId != null) {
                if (lastPresentTrain != trainId) {
                    lastPresentTrain = trainId
                    val train = com.simibubi.create.Create.RAILWAYS.trains[trainId]
                    if (train != null) {
                        playTrainAudio(train.name.string)
                    }
                }
            } else {
                lastPresentTrain = null
            }
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        tooltip.add(Component.literal("    Configurable Train Announcer").withStyle(net.minecraft.ChatFormatting.GOLD))
        
        if (targetStation != null) {
            val targetBe = level?.getBlockEntity(targetStation!!)
            val type = if (targetBe is StationBlockEntity) "Station" else if (targetBe is TrackObserverBlockEntity) "Observer" else "Unknown"
            tooltip.add(Component.literal(" Linked to: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("$type at ${targetStation!!.x}, ${targetStation!!.y}, ${targetStation!!.z}").withStyle(net.minecraft.ChatFormatting.GREEN)))
        } else {
            tooltip.add(Component.literal(" Linked to: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("None").withStyle(net.minecraft.ChatFormatting.RED)))
        }
        
        tooltip.add(Component.literal(" Profiles configured: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("${profiles.size}").withStyle(net.minecraft.ChatFormatting.YELLOW)))
        
        return true
    }

    private fun playTrainAudio(trainName: String) {
        val level = level ?: return
        val profile = profiles[trainName]
        if (profile != null && profile.text.isNotBlank()) {
            isPlaying = true
            val st = level.getBlockState(blockPos)
            if (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && !st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                level.setBlock(blockPos, st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, true), 3)
            }

            if (ModConfig.SERVER.ttsMode.get() == ModConfig.TtsMode.LOCAL_PIPER) {
                CoroutineScope(Dispatchers.IO).launch {
                    val audioData = PiperManager.generateAudio(profile.text, profile.voice, profile.language)
                    if (audioData != null) {
                        val durationMs = calculateWavDurationMs(audioData, profile.speed, profile.reverb, profile.jingle, profile.jingleTiming)
                        audioEndTimeMillis = System.currentTimeMillis() + durationMs

                        val streamId = UUID.randomUUID()
                        val chunkSize = 30000
                        val totalChunks = Math.ceil(audioData.size.toDouble() / chunkSize.toDouble()).toInt()
                        
                        for (i in 0 until totalChunks) {
                            val start = i * chunkSize
                            val end = Math.min(start + chunkSize, audioData.size)
                            val chunk = audioData.copyOfRange(start, end)
                            
                            val chunkPayload = PlayAnnouncerAudioDataChunkPayload(
                                blockPos,
                                profile.speed,
                                profile.volume,
                                profile.reverb,
                                profile.maxRange,
                                profile.jingle,
                                profile.jingleTiming,
                                profile.realism,
                                streamId,
                                i,
                                totalChunks,
                                chunk
                            )
                            
                            PacketDistributor.sendToPlayersNear(
                                level as ServerLevel,
                                null,
                                blockPos.x + 0.5,
                                blockPos.y + 0.5,
                                blockPos.z + 0.5,
                                profile.maxRange.toDouble(),
                                chunkPayload
                            )
                        }

                        val speakers = SpeakerManager.getSpeakersFor(level, blockPos)
                        for (speakerPos in speakers) {
                            val speakerStreamId = UUID.randomUUID()
                            for (i in 0 until totalChunks) {
                                val start = i * chunkSize
                                val end = Math.min(start + chunkSize, audioData.size)
                                val chunk = audioData.copyOfRange(start, end)

                                val chunkPayload = PlayAnnouncerAudioDataChunkPayload(
                                    speakerPos,
                                    profile.speed,
                                    profile.volume,
                                    profile.reverb,
                                    profile.maxRange,
                                    profile.jingle,
                                    profile.jingleTiming,
                                    profile.realism,
                                    speakerStreamId,
                                    i,
                                    totalChunks,
                                    chunk
                                )

                                PacketDistributor.sendToPlayersNear(
                                    level as ServerLevel,
                                    null,
                                    speakerPos.x + 0.5,
                                    speakerPos.y + 0.5,
                                    speakerPos.z + 0.5,
                                    profile.maxRange.toDouble(),
                                    chunkPayload
                                )
                            }

                            (level as? ServerLevel)?.server?.execute {
                                val speakerBe = level.getBlockEntity(speakerPos) as? SpeakerBlockEntity
                                if (speakerBe != null && speakerBe.linkedAnnouncer == blockPos) {
                                    speakerBe.isPlaying = true
                                    speakerBe.audioEndTimeMillis = System.currentTimeMillis() + durationMs
                                    val st = level.getBlockState(speakerPos)
                                    if (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && !st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                                        level.setBlock(speakerPos, st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, true), 3)
                                    }
                                }
                            }
                        }
                    } else {
                        isPlaying = false
                        val curSt = level.getBlockState(blockPos)
                        if (curSt.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && curSt.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                            level.setBlock(blockPos, curSt.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3)
                        }
                    }
                }
            } else {
                val timing = JingleTiming.fromString(profile.jingleTiming)
                val jingleDurationMs = if (profile.jingle.equals("DB", ignoreCase = true)) de.jamala.station_voices.JingleManager.getGongDuration(profile.speed, timing) else 0L
                val estimatedDurationMs = (profile.text.length * 120L / profile.speed.coerceAtLeast(0.1f).toDouble()).toLong() + 2500L + (if (profile.reverb) 900L else 0L) + jingleDurationMs
                audioEndTimeMillis = System.currentTimeMillis() + estimatedDurationMs

                val payload = PlayAnnouncerAudioPayload(
                    blockPos,
                    profile.text,
                    profile.voice,
                    profile.language,
                    profile.speed,
                    profile.volume,
                    profile.reverb,
                    profile.maxRange,
                    profile.jingle,
                    profile.jingleTiming,
                    profile.realism
                )
                PacketDistributor.sendToPlayersNear(
                    level as ServerLevel,
                    null,
                    blockPos.x + 0.5,
                    blockPos.y + 0.5,
                    blockPos.z + 0.5,
                    profile.maxRange.toDouble(),
                    payload
                )

                val speakers = SpeakerManager.getSpeakersFor(level, blockPos)
                for (speakerPos in speakers) {
                    val speakerPayload = PlayAnnouncerAudioPayload(
                        speakerPos,
                        profile.text,
                        profile.voice,
                        profile.language,
                        profile.speed,
                        profile.volume,
                        profile.reverb,
                        profile.maxRange,
                        profile.jingle,
                        profile.jingleTiming,
                        profile.realism
                    )
                    PacketDistributor.sendToPlayersNear(
                        level as ServerLevel,
                        null,
                        speakerPos.x + 0.5,
                        speakerPos.y + 0.5,
                        speakerPos.z + 0.5,
                        profile.maxRange.toDouble(),
                        speakerPayload
                    )

                    val speakerBe = level.getBlockEntity(speakerPos) as? SpeakerBlockEntity
                    if (speakerBe != null && speakerBe.linkedAnnouncer == blockPos) {
                        speakerBe.isPlaying = true
                        speakerBe.audioEndTimeMillis = System.currentTimeMillis() + estimatedDurationMs
                        val st = level.getBlockState(speakerPos)
                        if (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && !st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                            level.setBlock(speakerPos, st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, true), 3)
                        }
                    }
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
            val jingleMs = if (jingle.equals("DB", ignoreCase = true)) de.jamala.station_voices.JingleManager.getGongDuration(speed, timing) else 0L
            return adjustedMs + reverbTailMs + jingleMs
        } catch (e: Exception) {
            return 2000L
        }
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        val json = gson.toJson(profiles)
        tag.putString("TrainProfiles", json)
        targetStation?.let {
            val stationTag = CompoundTag()
            stationTag.putInt("X", it.x)
            stationTag.putInt("Y", it.y)
            stationTag.putInt("Z", it.z)
            tag.put("LinkedStation", stationTag)
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("TrainProfiles")) {
            val json = tag.getString("TrainProfiles")
            val type = object : TypeToken<MutableMap<String, TrainProfile>>() {}.type
            try {
                val map: MutableMap<String, TrainProfile>? = gson.fromJson(json, type)
                if (map != null) {
                    for ((_, p) in map) {
                        if (p.jingleTiming.isNullOrBlank()) {
                            p.jingleTiming = "BOTH"
                        }
                    }
                    profiles = map
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (tag.contains("LinkedStation")) {
            val stationTag = tag.getCompound("LinkedStation")
            targetStation = BlockPos(stationTag.getInt("X"), stationTag.getInt("Y"), stationTag.getInt("Z"))
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = super.getUpdateTag(registries)
        saveAdditional(tag, registries)
        return tag
    }
}
