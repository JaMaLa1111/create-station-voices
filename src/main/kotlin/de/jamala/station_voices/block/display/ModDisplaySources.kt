package de.jamala.station_voices.block.display

import com.simibubi.create.api.behaviour.display.DisplaySource
import com.simibubi.create.api.registry.CreateRegistries
import de.jamala.station_voices.CreateStationVoices
import thedarkcolour.kotlinforforge.neoforge.forge.getValue
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.DeferredHolder

object ModDisplaySources {
    val REGISTRY: DeferredRegister<DisplaySource> = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, CreateStationVoices.ID)

    val ANNOUNCEMENT_ENTRY: DeferredHolder<DisplaySource, AnnouncementDisplaySource> = REGISTRY.register("announcement") { ->
        AnnouncementDisplaySource()
    }

    val ANNOUNCEMENT by ANNOUNCEMENT_ENTRY
}
