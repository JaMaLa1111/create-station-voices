package de.jamala.station_voices.ponder

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags
import de.jamala.station_voices.block.ModBlocks
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ItemLike

object ModPonderTags {
    fun register(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        val itemHelper = helper.withKeyFunction { itemLike: ItemLike ->
            BuiltInRegistries.ITEM.getKey(itemLike.asItem())
        }

        itemHelper.addToTag(AllCreatePonderTags.REDSTONE)
            .add(ModBlocks.ANNOUNCER_BLOCK)
            .add(ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK)
            .add(ModBlocks.SPEAKER_BLOCK)

        itemHelper.addToTag(AllCreatePonderTags.TRAIN_RELATED)
            .add(ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK)
            .add(ModBlocks.TRAIN_ANNOUNCER_BLOCK)
            .add(ModBlocks.SPEAKER_BLOCK)

        itemHelper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES)
            .add(ModBlocks.ANNOUNCER_BLOCK)
            .add(ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK)
            .add(ModBlocks.SPEAKER_BLOCK)
    }
}
