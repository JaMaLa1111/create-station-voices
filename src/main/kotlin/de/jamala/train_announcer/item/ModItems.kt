package de.jamala.train_announcer.item

import de.jamala.train_announcer.TrainAnnouncer
import de.jamala.train_announcer.block.ModBlocks
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModItems {
    val REGISTRY = DeferredRegister.createItems(TrainAnnouncer.ID)

    val ANNOUNCER_BLOCK_ITEM by REGISTRY.register("announcer_block") { ->
        BlockItem(ModBlocks.ANNOUNCER_BLOCK, Item.Properties())
    }

    val CONFIGURABLE_ANNOUNCER_BLOCK_ITEM by REGISTRY.register("configurable_announcer_block") { ->
        ConfigurableAnnouncerBlockItem(ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK, Item.Properties())
    }
}
