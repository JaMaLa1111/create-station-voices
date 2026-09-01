package de.jamala.station_voices.ponder

import de.jamala.station_voices.CreateStationVoices
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation

class ModPonderPlugin : PonderPlugin {
    override fun getModId(): String = CreateStationVoices.ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        ModPonderScenes.register(helper)
    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        ModPonderTags.register(helper)
    }
}
