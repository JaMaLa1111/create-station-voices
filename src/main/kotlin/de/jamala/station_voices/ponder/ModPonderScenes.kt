package de.jamala.station_voices.ponder

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags
import de.jamala.station_voices.block.ModBlocks
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ItemLike

object ModPonderScenes {
    fun register(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        val itemHelper = helper.withKeyFunction { itemLike: ItemLike ->
            BuiltInRegistries.ITEM.getKey(itemLike.asItem())
        }

        itemHelper.forComponents(ModBlocks.ANNOUNCER_BLOCK)
            .addStoryBoard(
                ResourceLocation.fromNamespaceAndPath("create", "redstone_link"),
                AnnouncerScenes::redstoneAnnouncer,
                AllCreatePonderTags.REDSTONE
            )

        itemHelper.forComponents(ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK)
            .addStoryBoard(
                ResourceLocation.fromNamespaceAndPath("create", "train_station/schedule"),
                AnnouncerScenes::configurableAnnouncer,
                AllCreatePonderTags.TRAIN_RELATED,
                AllCreatePonderTags.REDSTONE
            )

        itemHelper.forComponents(ModBlocks.SPEAKER_BLOCK)
            .addStoryBoard(
                ResourceLocation.fromNamespaceAndPath("create", "redstone_link"),
                AnnouncerScenes::speaker,
                AllCreatePonderTags.REDSTONE,
                AllCreatePonderTags.TRAIN_RELATED
            )
    }
}
