package de.jamala.station_voices.block

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class TrainAnnouncerBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.TRAIN_ANNOUNCER_BLOCK_ENTITY, pos, state), IHaveGoggleInformation, IAnnouncerSource {
    var profiles: MutableMap<String, TrainProfile> = mutableMapOf()
    var isPlaying: Boolean = false
    var audioEndTimeMillis: Long = 0L
    var lastAnnouncementText: String = ""

    override val isPlayingAnnouncement: Boolean
        get() = isPlaying

    override val currentAnnouncementText: String
        get() = lastAnnouncementText

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
                com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level, blockPos)
            }
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        tooltip.add(Component.literal("    Train-Mounted Announcer").withStyle(net.minecraft.ChatFormatting.GOLD))
        tooltip.add(Component.literal(" Mount onto a train to announce arriving stations.").withStyle(net.minecraft.ChatFormatting.GRAY))
        tooltip.add(Component.literal(" Station profiles configured: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal("${profiles.size}").withStyle(net.minecraft.ChatFormatting.YELLOW)))
        return true
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        val json = gson.toJson(profiles)
        tag.putString("StationProfiles", json)
        tag.putString("LastAnnouncementText", lastAnnouncementText)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("LastAnnouncementText")) {
            lastAnnouncementText = tag.getString("LastAnnouncementText")
        }
        val json = when {
            tag.contains("StationProfiles") -> tag.getString("StationProfiles")
            tag.contains("TrainProfiles") -> tag.getString("TrainProfiles")
            else -> null
        }
        if (json != null) {
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
