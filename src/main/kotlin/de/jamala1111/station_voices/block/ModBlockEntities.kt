package de.jamala.station_voices.block

import de.jamala.station_voices.CreateStationVoices
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import thedarkcolour.kotlinforforge.neoforge.forge.getValue
import net.minecraft.core.registries.Registries
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import net.minecraft.network.chat.Component

object ModBlockEntities {
    val REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateStationVoices.ID)

    val ANNOUNCER_BLOCK_ENTITY by REGISTRY.register("announcer_block") { ->
        BlockEntityType.Builder.of(
            ::AnnouncerBlockEntity,
            ModBlocks.ANNOUNCER_BLOCK
        ).build(null)
    }

    val CONFIGURABLE_ANNOUNCER_BLOCK_ENTITY by REGISTRY.register("configurable_announcer_block") { ->
        BlockEntityType.Builder.of(
            ::ConfigurableAnnouncerBlockEntity,
            ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK
        ).build(null)
    }

    val SPEAKER_BLOCK_ENTITY by REGISTRY.register("speaker_block") { ->
        BlockEntityType.Builder.of(
            ::SpeakerBlockEntity,
            ModBlocks.SPEAKER_BLOCK
        ).build(null)
    }

    val TRAIN_ANNOUNCER_BLOCK_ENTITY by REGISTRY.register("train_announcer_block") { ->
        BlockEntityType.Builder.of(
            ::TrainAnnouncerBlockEntity,
            ModBlocks.TRAIN_ANNOUNCER_BLOCK
        ).build(null)
    }
}

class AnnouncerBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.ANNOUNCER_BLOCK_ENTITY, pos, state), IHaveGoggleInformation, IAnnouncerSource {
    var ttsText: String = "Hello"
    var ttsVoice: String = "amy"
    var ttsLanguage: String = "en_US"
    var ttsSpeed: Float = 1.0f
    var ttsVolume: Float = 1.0f
    var ttsReverb: Boolean = false
    var ttsMaxRange: Int = 32
    var ttsJingle: String = "OFF"
    var ttsJingleTiming: String = "BOTH"
    var ttsRealism: Float = 0.0f

    var isPlaying: Boolean = false
    var wasPoweredByRedstone: Boolean = false
    var audioEndTimeMillis: Long = 0L
    var lastAnnouncementText: String = ""

    override val isPlayingAnnouncement: Boolean
        get() = isPlaying

    override val currentAnnouncementText: String
        get() = if (lastAnnouncementText.isNotBlank()) lastAnnouncementText else ttsText

    fun tick(level: net.minecraft.world.level.Level, pos: BlockPos, state: BlockState) {
        if (level.isClientSide) return

        if (isPlaying && System.currentTimeMillis() >= audioEndTimeMillis) {
            isPlaying = false
            com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level, pos)
            val hasSignal = level.hasNeighborSignal(pos)
            if (!hasSignal && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3)
            }
        }
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putString("TtsText", ttsText)
        tag.putString("TtsVoice", ttsVoice)
        tag.putString("TtsLanguage", ttsLanguage)
        tag.putFloat("TtsSpeed", ttsSpeed)
        tag.putFloat("TtsVolume", ttsVolume)
        tag.putBoolean("TtsReverb", ttsReverb)
        tag.putInt("TtsMaxRange", ttsMaxRange)
        tag.putString("TtsJingle", ttsJingle)
        tag.putString("TtsJingleTiming", ttsJingleTiming)
        tag.putFloat("TtsRealism", ttsRealism)
        tag.putString("LastAnnouncementText", lastAnnouncementText)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("TtsText")) {
            ttsVoice = tag.getString("TtsVoice")
            ttsLanguage = tag.getString("TtsLanguage")
            ttsText = de.jamala.station_voices.TextSanitizer.sanitize(tag.getString("TtsText"), ttsLanguage)
            if (tag.contains("TtsSpeed")) ttsSpeed = tag.getFloat("TtsSpeed")
            if (tag.contains("TtsVolume")) ttsVolume = tag.getFloat("TtsVolume")
            if (tag.contains("TtsReverb")) ttsReverb = tag.getBoolean("TtsReverb")
            if (tag.contains("TtsMaxRange")) ttsMaxRange = tag.getInt("TtsMaxRange")
            if (tag.contains("TtsJingle")) ttsJingle = tag.getString("TtsJingle")
            if (tag.contains("TtsJingleTiming")) ttsJingleTiming = tag.getString("TtsJingleTiming") else ttsJingleTiming = "BOTH"
            if (tag.contains("TtsRealism")) ttsRealism = tag.getFloat("TtsRealism")
        }
        if (tag.contains("LastAnnouncementText")) {
            lastAnnouncementText = tag.getString("LastAnnouncementText")
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        tooltip.add(Component.literal("    Train Announcer").withStyle(net.minecraft.ChatFormatting.GOLD))
        tooltip.add(Component.literal(" Text: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal(ttsText).withStyle(net.minecraft.ChatFormatting.GREEN)))
        tooltip.add(Component.literal(" Voice: ").withStyle(net.minecraft.ChatFormatting.GRAY).append(Component.literal(ttsVoice).withStyle(net.minecraft.ChatFormatting.YELLOW)))
        return true
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
