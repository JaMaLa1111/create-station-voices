package de.jamala.train_announcer.client

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import de.jamala.train_announcer.ModConfig
import de.jamala.train_announcer.network.DownloadModelPayload
import de.jamala.train_announcer.network.RequestInstalledModelsPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

class ModelDownloadScreen : Screen(Component.literal("Piper Models Downloader")) {

    private var availableData = mutableMapOf<String, List<String>>()
    private var piperManifestData = mutableMapOf<String, JsonObject>() // voiceKey -> data
    private var installedModels = setOf<String>()
    
    private lateinit var langList: StringList
    private lateinit var voiceList: StringList
    
    private var selectedLang: String = ""
    private var selectedVoice: String = ""

    private var downloadBtn: Button? = null
    private var refetchBtn: Button? = null

    override fun init() {
        val centerX = width / 2
        val listWidth = 120

        langList = StringList(minecraft!!, listWidth, height, 40, 36)
        langList.setX(centerX - listWidth - 5)
        addRenderableWidget(langList)

        voiceList = StringList(minecraft!!, listWidth, height, 40, 36)
        voiceList.setX(centerX + 5)
        addRenderableWidget(voiceList)

        addRenderableWidget(Button.builder(Component.literal("Close")) { _ ->
            minecraft?.setScreen(null)
        }.bounds(centerX - 50, height - 30, 100, 20).build())

        downloadBtn = addRenderableWidget(Button.builder(Component.literal("Download to Server")) { _ ->
            downloadSelectedModel()
        }.bounds(centerX + listWidth + 15, height / 2, 120, 20).build())
        downloadBtn?.active = false

        refetchBtn = addRenderableWidget(Button.builder(Component.literal("Refresh Manifest")) { _ ->
            fetchData()
        }.bounds(width - 110, 10, 100, 20).build())
        
        PacketDistributor.sendToServer(RequestInstalledModelsPayload())
        fetchData()
    }

    private fun downloadSelectedModel() {
        val voices = availableData[selectedLang] ?: emptyList()
        if (voices.contains(selectedVoice)) {
            val key = piperManifestData.keys.find { piperManifestData[it]?.get("language")?.asString == selectedLang && piperManifestData[it]?.get("voice")?.asString == selectedVoice }
            if (key != null) {
                val data = piperManifestData[key]!!
                val onnxUrl = data.get("download_url_onnx").asString
                val jsonUrl = data.get("download_url_json").asString
                PacketDistributor.sendToServer(DownloadModelPayload(selectedLang, selectedVoice, onnxUrl, jsonUrl))
                downloadBtn?.active = false
                downloadBtn?.message = Component.literal("Downloading...")
            }
        }
    }

    fun updateInstalledModels(models: List<String>) {
        installedModels = models.toSet()
        updateVoicesList()
    }

    private fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URI("https://huggingface.co/Ja-Ma-La1111/piper-voice-mirror/resolve/main/manifest.json").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val root = JsonParser.parseReader(InputStreamReader(connection.inputStream)).asJsonObject
                    val data = mutableMapOf<String, MutableList<String>>()
                    piperManifestData.clear()

                    for (key in root.keySet()) {
                        val entry = root.getAsJsonObject(key)
                        piperManifestData[key] = entry
                        val lang = entry.get("language").asString
                        val voice = entry.get("voice").asString
                        val list = data.getOrPut(lang) { mutableListOf() }
                        if (!list.contains(voice)) list.add(voice)
                    }
                    
                    Minecraft.getInstance().execute {
                        availableData.clear()
                        availableData.putAll(data)
                        
                        langList.updateItems(availableData.keys.toList())
                        if (!availableData.keys.contains(selectedLang) && availableData.keys.isNotEmpty()) {
                            selectedLang = availableData.keys.first()
                        }
                        langList.selectItem(selectedLang)
                        
                        updateVoicesList()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onLangSelected(lang: String) {
        selectedLang = lang
        updateVoicesList()
    }

    private fun updateVoicesList() {
        val voices = availableData[selectedLang] ?: emptyList()
        voiceList.updateItems(voices)
        if (!voices.contains(selectedVoice) && voices.isNotEmpty()) {
            selectedVoice = voices[0]
        }
        voiceList.selectItem(selectedVoice)
        
        val isInstalled = installedModels.contains("$selectedLang:$selectedVoice")
        if (isInstalled) {
            downloadBtn?.active = false
            downloadBtn?.message = Component.literal("Installed")
        } else {
            downloadBtn?.active = true
            downloadBtn?.message = Component.literal("Download to Server")
        }
    }

    fun onVoiceSelected(voice: String) {
        selectedVoice = voice
        updateVoicesList()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
        
        guiGraphics.drawCenteredString(font, Component.literal("Language"), width / 2 - 60, 25, 0xAAAAAA)
        guiGraphics.drawCenteredString(font, Component.literal("Voice"), width / 2 + 60, 25, 0xAAAAAA)
    }

    inner class StringList(mc: Minecraft, width: Int, height: Int, y0: Int, itemHeight: Int) : ObjectSelectionList<StringList.StringEntry>(mc, width, height - y0 - 40, y0, itemHeight) {
        
        fun updateItems(items: List<String>) {
            clearEntries()
            for (item in items) {
                addEntry(StringEntry(item))
            }
        }

        fun selectItem(item: String) {
            val entry = children().find { it.text == item }
            super.setSelected(entry)
        }
        
        override fun setSelected(entry: StringEntry?) {
            super.setSelected(entry)
            if (entry != null) {
                if (this == langList) onLangSelected(entry.text)
                if (this == voiceList) onVoiceSelected(entry.text)
            }
        }

        override fun getScrollbarPosition(): Int {
            return x + width - 6
        }

        override fun getRowWidth(): Int {
            return width - 10
        }

        inner class StringEntry(val text: String) : ObjectSelectionList.Entry<StringEntry>() {
            override fun render(guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, isMouseOver: Boolean, partialTick: Float) {
                val color = if (selected == this) 0x00FF00 else 0xFFFFFF
                guiGraphics.drawString(minecraft!!.font, text, left + 5, top + 2, color)
            }
            
            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                setSelected(this)
                return true
            }

            override fun getNarration(): Component = Component.literal(text)
        }
    }
}
