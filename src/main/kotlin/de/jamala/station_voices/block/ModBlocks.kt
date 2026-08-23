package de.jamala.station_voices.block

import de.jamala.station_voices.CreateStationVoices
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModBlocks {
    val REGISTRY = DeferredRegister.createBlocks(CreateStationVoices.ID)

    // If you get an "overload resolution ambiguity" error, include the arrow at the start of the closure.
    val ANNOUNCER_BLOCK by REGISTRY.register("announcer_block") { ->
        AnnouncerBlock(BlockBehaviour.Properties.of().lightLevel { 15 }.strength(3.0f))
    }

    val CONFIGURABLE_ANNOUNCER_BLOCK by REGISTRY.register("configurable_announcer_block") { ->
        ConfigurableAnnouncerBlock(BlockBehaviour.Properties.of().lightLevel { 15 }.strength(3.0f))
    }
}
