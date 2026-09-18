package de.jamala.station_voices.client

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import de.jamala.station_voices.ModConfig
import de.jamala.station_voices.network.DownloadModelPayload
import de.jamala.station_voices.network.RequestInstalledModelsPayload
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
    private var playSampleBtn: Button? = null
    private var stopSampleBtn: Button? = null
    private var refetchBtn: Button? = null

    private val downloadingModels = mutableMapOf<String, Float>()
    private var activeUrl: String? = null
    private var urlRect: java.awt.Rectangle? = null
    private var currentPlayer: dev.mccue.jlayer.player.Player? = null

    override fun init() {
        val centerX = width / 2
        val listWidth = 120
        val panelX = centerX + listWidth + 15
        val panelY = 40

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
        }.bounds(panelX, panelY + 120, 150, 20).build())
        downloadBtn?.active = false

        playSampleBtn = addRenderableWidget(Button.builder(Component.literal("Listen")) { _ ->
            playSelectedSample()
        }.bounds(panelX, panelY + 95, 100, 20).build())
        playSampleBtn?.active = false
        
        stopSampleBtn = addRenderableWidget(Button.builder(Component.literal("Stop")) { _ ->
            stopSelectedSample()
        }.bounds(panelX + 105, panelY + 95, 45, 20).build())
        stopSampleBtn?.active = false

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
                val progressKey = "$selectedLang:$selectedVoice"
                downloadingModels[progressKey] = 0f
                PacketDistributor.sendToServer(DownloadModelPayload(selectedLang, selectedVoice, onnxUrl, jsonUrl))
                updateVoicesList()
            }
        }
    }

    private fun playSelectedSample() {
        val key = piperManifestData.keys.find { piperManifestData[it]?.get("language")?.asString == selectedLang && piperManifestData[it]?.get("voice")?.asString == selectedVoice }
        if (key != null) {
            val data = piperManifestData[key]!!
            val sampleUrlObj = data.getAsJsonObject("sample_url")
            if (sampleUrlObj != null) {
                val samples = mutableMapOf<String, String>()
                for (k in sampleUrlObj.keySet()) {
                    samples[k] = sampleUrlObj.get(k).asString
                }
                
                if (samples.size == 1) {
                    playSampleUrl(samples.values.first())
                } else if (samples.size > 1) {
                    minecraft?.setScreen(SampleSelectionScreen(this, samples) { url ->
                        playSampleUrl(url)
                    })
                }
            }
        }
    }

    private fun playSampleUrl(url: String) {
        currentPlayer?.close()
        currentPlayer = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                if (connection.responseCode == 200) {
                    val player = dev.mccue.jlayer.player.Player(connection.inputStream)
                    currentPlayer = player
                    player.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopSelectedSample() {
        currentPlayer?.close()
        currentPlayer = null
    }

    override fun onClose() {
        currentPlayer?.close()
        super.onClose()
    }

    fun updateInstalledModels(models: List<String>) {
        installedModels = models.toSet()
        models.forEach { downloadingModels.remove(it) }
        updateVoicesList()
    }

    fun updateDownloadProgress(language: String, voice: String, progress: Float) {
        val progressKey = "$language:$voice"
        downloadingModels[progressKey] = progress
        if (language == selectedLang && voice == selectedVoice) {
            updateVoicesList()
        }
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
        
        val progressKey = "$selectedLang:$selectedVoice"
        val isDownloading = downloadingModels.containsKey(progressKey)
        val isInstalled = installedModels.contains(progressKey)
        
        if (isDownloading) {
            downloadBtn?.active = false
            downloadBtn?.message = Component.literal("Downloading...")
        } else if (isInstalled) {
            downloadBtn?.active = false
            downloadBtn?.message = Component.literal("Installed")
        } else {
            downloadBtn?.active = true
            downloadBtn?.message = Component.literal("Download to Server")
        }

        val centerX = width / 2
        val listWidth = 120
        val panelX = centerX + listWidth + 15
        val panelY = 40
        val maxWidth = width - panelX - 10
        var metadataHeight = 0

        val key = piperManifestData.keys.find { piperManifestData[it]?.get("language")?.asString == selectedLang && piperManifestData[it]?.get("voice")?.asString == selectedVoice }
        if (key != null) {
            val data = piperManifestData[key]!!
            val modelCard = data.getAsJsonObject("model_card")
            if (modelCard != null) {
                fun getWrappedHeight(prefix: String, value: String): Int {
                    val text = "$prefix$value"
                    val lines = minecraft!!.font.split(Component.literal(text), maxWidth)
                    return lines.size * (minecraft!!.font.lineHeight + 2)
                }
                
                if (modelCard.has("quality")) metadataHeight += getWrappedHeight("Quality: ", modelCard.get("quality").asString)
                if (modelCard.has("speakers")) metadataHeight += getWrappedHeight("Speakers: ", modelCard.get("speakers").asString)
                if (modelCard.has("samplerate")) metadataHeight += getWrappedHeight("Sample Rate: ", modelCard.get("samplerate").asString)
                if (modelCard.has("url")) metadataHeight += getWrappedHeight("URL: ", modelCard.get("url").asString)
                if (modelCard.has("license")) metadataHeight += getWrappedHeight("License: ", modelCard.get("license").asString)
            }
            
            val sampleUrlObj = data.getAsJsonObject("sample_url")
            if (sampleUrlObj != null && sampleUrlObj.keySet().isNotEmpty()) {
                playSampleBtn?.active = true
                stopSampleBtn?.active = true
            } else {
                playSampleBtn?.active = false
                stopSampleBtn?.active = false
            }
        } else {
            playSampleBtn?.active = false
            stopSampleBtn?.active = false
        }
        
        var buttonsY = panelY + 15 + metadataHeight + 10
        if (buttonsY < panelY + 95) buttonsY = panelY + 95
        
        playSampleBtn?.setY(buttonsY)
        stopSampleBtn?.setY(buttonsY)
        downloadBtn?.setY(buttonsY + 25)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        
        if (button == 0 && activeUrl != null && urlRect != null) {
            if (urlRect!!.contains(mouseX.toInt(), mouseY.toInt())) {
                try {
                    net.minecraft.Util.getPlatform().openUri(URI(activeUrl!!))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }
        }
        return false
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

        val centerX = width / 2
        val listWidth = 120
        val panelX = centerX + listWidth + 15
        val panelY = 40
        val maxWidth = width - panelX - 10

        activeUrl = null
        urlRect = null

        val key = piperManifestData.keys.find { piperManifestData[it]?.get("language")?.asString == selectedLang && piperManifestData[it]?.get("voice")?.asString == selectedVoice }
        if (key != null) {
            val data = piperManifestData[key]!!
            guiGraphics.drawString(font, Component.literal("Metadata"), panelX, panelY, 0xFFFF55)
            
            var yOffset = panelY + 15
            val modelCard = data.getAsJsonObject("model_card")
            if (modelCard != null) {
                fun drawWrapped(prefix: String, value: String, isUrl: Boolean = false) {
                    val text = "$prefix$value"
                    val lines = font.split(Component.literal(text), maxWidth)
                    
                    if (isUrl) {
                        urlRect = java.awt.Rectangle(panelX, yOffset, maxWidth, lines.size * (font.lineHeight + 2))
                        activeUrl = value
                    }
                    
                    for (line in lines) {
                        val color = if (isUrl) 0x5555FF else 0xAAAAAA
                        guiGraphics.drawString(font, line, panelX, yOffset, color)
                        yOffset += font.lineHeight + 2
                    }
                }
                
                if (modelCard.has("quality")) drawWrapped("Quality: ", modelCard.get("quality").asString)
                if (modelCard.has("speakers")) drawWrapped("Speakers: ", modelCard.get("speakers").asString)
                if (modelCard.has("samplerate")) drawWrapped("Sample Rate: ", modelCard.get("samplerate").asString)
                if (modelCard.has("url")) drawWrapped("URL: ", modelCard.get("url").asString, true)
                if (modelCard.has("license")) drawWrapped("License: ", modelCard.get("license").asString)
            }
        }

        val progressKey = "$selectedLang:$selectedVoice"
        if (downloadingModels.containsKey(progressKey)) {
            val progress = downloadingModels[progressKey] ?: 0f
            val barWidth = 150
            val barHeight = 10
            val barX = panelX
            val barY = (downloadBtn?.y ?: (panelY + 120)) + 25
            
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555.toInt())
            guiGraphics.fill(barX, barY, barX + (barWidth * progress).toInt(), barY + barHeight, 0xFF00FF00.toInt())
            guiGraphics.drawCenteredString(font, Component.literal("${(progress * 100).toInt()}%"), barX + barWidth / 2, barY + 1, 0xFFFFFF)
        }
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
                
                // Draw progress indicator for downloading items
                val progressKey = if (this@StringList == langList) {
                    val voices = availableData[text] ?: emptyList()
                    if (voices.isNotEmpty()) "$text:${voices[0]}" else "" // fallback, not perfect for lang
                } else {
                    "$selectedLang:$text"
                }

                if (this@StringList == voiceList && downloadingModels.containsKey(progressKey)) {
                    val progress = downloadingModels[progressKey] ?: 0f
                    guiGraphics.fill(left, top, left + (width * progress).toInt(), top + height, 0x4400FF00.toInt())
                }

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
