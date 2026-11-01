package de.jamala1111.station_voices.client

import de.jamala1111.station_voices.ModConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class NetworkConsentScreen(private val nextScreen: Screen) : Screen(Component.translatable("gui.create_station_voices.network_consent.title")) {

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2
        
        val message = Component.translatable("gui.create_station_voices.network_consent.message")
        val lines = minecraft!!.font.split(message, 300)
        val textHeight = lines.size * (minecraft!!.font.lineHeight + 2)
        
        val buttonStartY = (height / 2 - 60) + 20 + textHeight + 20

        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.network_consent.allow")) { _ ->
            ModConfig.CLIENT.allowNetworkUsage.set(true)
            ModConfig.CLIENT.allowNetworkUsage.save()
            minecraft?.setScreen(nextScreen)
        }.bounds(centerX - 105, buttonStartY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.network_consent.deny")) { _ ->
            minecraft?.setScreen(null)
        }.bounds(centerX + 5, buttonStartY, 100, 20).build())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF)
        
        val message = Component.translatable("gui.create_station_voices.network_consent.message")
        val lines = font.split(message, 300)
        
        var yOffset = (height / 2 - 60) + 20
        for (line in lines) {
            guiGraphics.drawCenteredString(font, line, width / 2, yOffset, 0xAAAAAA)
            yOffset += font.lineHeight + 2
        }
    }
}
