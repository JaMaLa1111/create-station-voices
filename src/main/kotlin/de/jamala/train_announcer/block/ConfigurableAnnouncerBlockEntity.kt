package de.jamala.train_announcer.block

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import net.minecraft.network.chat.Component
import de.jamala.train_announcer.network.PlayAnnouncerAudioPayload
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
import de.jamala.train_announcer.ModConfig
import de.jamala.train_announcer.server.PiperManager
import de.jamala.train_announcer.network.PlayAnnouncerAudioDataChunkPayload
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
    var maxRange: Int = 32
)

class ConfigurableAnnouncerBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.CONFIGURABLE_ANNOUNCER_BLOCK_ENTITY, pos, state), IHaveGoggleInformation {
    var profiles: MutableMap<String, TrainProfile> = mutableMapOf()
    var targetStation: BlockPos? = null
    private var lastPresentTrain: UUID? = null

    private val gson = Gson()

    fun tick() {
        val level = level ?: return
        if (level.isClientSide) return

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
            if (ModConfig.SERVER.ttsMode.get() == ModConfig.TtsMode.LOCAL_PIPER) {
                CoroutineScope(Dispatchers.IO).launch {
                    val audioData = PiperManager.generateAudio(profile.text, profile.voice, profile.language)
                    if (audioData != null) {
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
                    }
                }
            } else {
                val payload = PlayAnnouncerAudioPayload(
                    blockPos,
                    profile.text,
                    profile.voice,
                    profile.language,
                    profile.speed,
                    profile.volume,
                    profile.reverb,
                    profile.maxRange
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
            }
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
