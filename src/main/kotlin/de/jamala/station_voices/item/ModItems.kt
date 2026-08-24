package de.jamala.station_voices.item

import de.jamala.station_voices.CreateStationVoices
import de.jamala.station_voices.block.ModBlocks
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModItems {
    val REGISTRY = DeferredRegister.createItems(CreateStationVoices.ID)

    val ANNOUNCER_BLOCK_ITEM by REGISTRY.register("announcer_block") { ->
        BlockItem(ModBlocks.ANNOUNCER_BLOCK, Item.Properties())
    }

    val CONFIGURABLE_ANNOUNCER_BLOCK_ITEM by REGISTRY.register("configurable_announcer_block") { ->
        ConfigurableAnnouncerBlockItem(ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK, Item.Properties())
    }

    val TAB_ICON by REGISTRY.register("tab_icon") { ->
        Item(Item.Properties())
    }
}
