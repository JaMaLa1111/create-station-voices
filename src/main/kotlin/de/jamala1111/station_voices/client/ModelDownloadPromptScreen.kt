package de.jamala1111.station_voices.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ModelDownloadPromptScreen(private val environmentId: String) : Screen(Component.translatable("gui.create_station_voices.model_prompt.title")) {

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2
        
        val message = Component.translatable("gui.create_station_voices.model_prompt.message")
        val lines = minecraft!!.font.split(message, 300)
        val textHeight = lines.size * (minecraft!!.font.lineHeight + 2)
        
        val buttonStartY = (height / 2 - 60) + 20 + textHeight + 20

        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.model_prompt.open_manager")) { _ ->
            minecraft?.setScreen(ModelDownloadScreen())
        }.bounds(centerX - 100, buttonStartY, 200, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.model_prompt.not_now")) { _ ->
            minecraft?.setScreen(null)
        }.bounds(centerX - 100, buttonStartY + 25, 95, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.model_prompt.dont_show_again")) { _ ->
            hideForEnvironment(environmentId)
            minecraft?.setScreen(null)
        }.bounds(centerX + 5, buttonStartY + 25, 95, 20).build())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF)
        
        val message = Component.translatable("gui.create_station_voices.model_prompt.message")
        val lines = font.split(message, 300)
        
        var yOffset = (height / 2 - 60) + 20
        for (line in lines) {
            guiGraphics.drawCenteredString(font, line, width / 2, yOffset, 0xAAAAAA)
            yOffset += font.lineHeight + 2
        }
    }

    companion object {
        private val configFile = File(Minecraft.getInstance().gameDirectory, "config/create_station_voices_hidden_prompts.json")
        private val gson = Gson()

        fun isHiddenForEnvironment(envId: String): Boolean {
            if (!configFile.exists()) return false
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val hiddenEnvs: List<String> = gson.fromJson(configFile.readText(), type) ?: return false
                return hiddenEnvs.contains(envId)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        private fun hideForEnvironment(envId: String) {
            val hiddenEnvs = mutableListOf<String>()
            if (configFile.exists()) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    val existing: List<String>? = gson.fromJson(configFile.readText(), type)
                    if (existing != null) hiddenEnvs.addAll(existing)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (!hiddenEnvs.contains(envId)) {
                hiddenEnvs.add(envId)
                configFile.parentFile.mkdirs()
                configFile.writeText(gson.toJson(hiddenEnvs))
            }
        }
    }
}
