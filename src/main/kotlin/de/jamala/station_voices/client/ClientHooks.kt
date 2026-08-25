package de.jamala.station_voices.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.api.distmarker.OnlyIn

import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11

object ClientHooks {
    fun testAL() {
        try {
            val err = AL10.alGetError()
            println("AL Error before: $err")
            val buffer = AL10.alGenBuffers()
            println("Gen buffer: $buffer")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun openAnnouncerScreen(
        pos: BlockPos, 
        currentText: String, 
        currentVoice: String, 
        currentLanguage: String, 
        currentSpeed: Float,
        currentVolume: Float,
        currentReverb: Boolean,
        currentMaxRange: Int,
        currentJingle: String,
        currentRealism: Float = 0.0f
    ) {
        Minecraft.getInstance().setScreen(AnnouncerScreen(pos, currentText, currentVoice, currentLanguage, currentSpeed, currentVolume, currentReverb, currentMaxRange, currentJingle, currentRealism))
    }
    
    fun openConfigurableAnnouncerScreen(pos: BlockPos, be: de.jamala.station_voices.block.ConfigurableAnnouncerBlockEntity) {
        val profilesMap = be.profiles.mapValues { it.value.copy() }.toMutableMap()
        Minecraft.getInstance().setScreen(ConfigurableAnnouncerScreen(pos, profilesMap, be.targetStation))
    }

    fun handleAutoFetchResponse(trainNames: List<String>) {
        val screen = Minecraft.getInstance().screen
        if (screen is ConfigurableAnnouncerScreen) {
            screen.handleAutoFetchResponse(trainNames)
        }
    }
    
    fun openModelDownloadScreen() {
        Minecraft.getInstance().setScreen(ModelDownloadScreen())
    }
    
    fun handleInstalledModels(models: List<String>) {
        val screen = Minecraft.getInstance().screen
        if (screen is VoiceSelectionScreen) {
            screen.updateInstalledModels(models)
        } else if (screen is ModelDownloadScreen) {
            screen.updateInstalledModels(models)
        }
    }

    fun handleModelProgress(language: String, voice: String, progress: Float) {
        val screen = Minecraft.getInstance().screen
        if (screen is ModelDownloadScreen) {
            screen.updateDownloadProgress(language, voice, progress)
        }
    }
}
