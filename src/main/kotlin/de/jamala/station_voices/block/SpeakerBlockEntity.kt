package de.jamala.station_voices.block

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class SpeakerBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.SPEAKER_BLOCK_ENTITY, pos, state), IHaveGoggleInformation {
    var linkedAnnouncer: BlockPos? = null
        set(value) {
            val old = field
            field = value
            val lvl = level
            if (lvl != null && !lvl.isClientSide) {
                if (old != null) SpeakerManager.unregisterSpeaker(lvl, old, blockPos)
                if (value != null) SpeakerManager.registerSpeaker(lvl, value, blockPos)
            }
        }

    var isPlaying: Boolean = false
    var audioEndTimeMillis: Long = 0L

    override fun onLoad() {
        super.onLoad()
        val target = linkedAnnouncer
        val lvl = level
        if (target != null && lvl != null && !lvl.isClientSide) {
            SpeakerManager.registerSpeaker(lvl, target, blockPos)
        }
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        val target = linkedAnnouncer
        if (target != null && !level.isClientSide) {
            SpeakerManager.registerSpeaker(level, target, blockPos)
        }
    }

    override fun setRemoved() {
        val target = linkedAnnouncer
        val lvl = level
        if (target != null && lvl != null && !lvl.isClientSide) {
            SpeakerManager.unregisterSpeaker(lvl, target, blockPos)
        }
        super.setRemoved()
    }

    override fun clearRemoved() {
        super.clearRemoved()
        val target = linkedAnnouncer
        val lvl = level
        if (target != null && lvl != null && !lvl.isClientSide) {
            SpeakerManager.registerSpeaker(lvl, target, blockPos)
        }
    }

    fun tick(level: Level, pos: BlockPos, state: BlockState) {
        if (level.isClientSide) return

        val target = linkedAnnouncer
        if (target != null) {
            SpeakerManager.registerSpeaker(level, target, pos)
        }

        if (isPlaying) {
            if (System.currentTimeMillis() >= audioEndTimeMillis) {
                isPlaying = false
                if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), 3)
                }
            }
        } else {
            if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), 3)
            }
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        tooltip.add(Component.literal("    Speaker").withStyle(net.minecraft.ChatFormatting.GOLD))
        if (linkedAnnouncer != null) {
            val targetBe = level?.getBlockEntity(linkedAnnouncer!!)
            val type = if (targetBe is AnnouncerBlockEntity) {
                "Redstone Announcer"
            } else if (targetBe is ConfigurableAnnouncerBlockEntity) {
                "Configurable Announcer"
            } else {
                "Announcer"
            }
            tooltip.add(Component.literal(" Linked to: ").withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(Component.literal("$type at ${linkedAnnouncer!!.x}, ${linkedAnnouncer!!.y}, ${linkedAnnouncer!!.z}").withStyle(net.minecraft.ChatFormatting.GREEN)))
        } else {
            tooltip.add(Component.literal(" Linked to: ").withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(Component.literal("None").withStyle(net.minecraft.ChatFormatting.RED)))
        }
        return true
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        linkedAnnouncer?.let {
            val announcerTag = CompoundTag()
            announcerTag.putInt("X", it.x)
            announcerTag.putInt("Y", it.y)
            announcerTag.putInt("Z", it.z)
            tag.put("LinkedAnnouncer", announcerTag)
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("LinkedAnnouncer")) {
            val announcerTag = tag.getCompound("LinkedAnnouncer")
            linkedAnnouncer = BlockPos(announcerTag.getInt("X"), announcerTag.getInt("Y"), announcerTag.getInt("Z"))
        } else {
            linkedAnnouncer = null
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
