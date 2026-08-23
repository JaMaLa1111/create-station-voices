package de.jamala.train_announcer.item

import de.jamala.train_announcer.TrainAnnouncer
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModCreativeTabs {
    val REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrainAnnouncer.ID)

    val MAIN_TAB by REGISTRY.register("main") { ->
        CreativeModeTab.builder()
            .title(Component.literal("Train Announcer"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon { ModItems.CONFIGURABLE_ANNOUNCER_BLOCK_ITEM.defaultInstance }
            .displayItems { _, output ->
                output.accept(ModItems.ANNOUNCER_BLOCK_ITEM)
                output.accept(ModItems.CONFIGURABLE_ANNOUNCER_BLOCK_ITEM)
            }
            .build()
    }
}
