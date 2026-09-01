package de.jamala.station_voices.item

import de.jamala.station_voices.CreateStationVoices
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModCreativeTabs {
    val REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateStationVoices.ID)

    val MAIN_TAB by REGISTRY.register("main") { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_station_voices.main"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon { ModItems.TAB_ICON.defaultInstance }
            .displayItems { _, output ->
                output.accept(ModItems.ANNOUNCER_BLOCK_ITEM)
                output.accept(ModItems.CONFIGURABLE_ANNOUNCER_BLOCK_ITEM)
                output.accept(ModItems.SPEAKER_BLOCK_ITEM)
            }
            .build()
    }
}
