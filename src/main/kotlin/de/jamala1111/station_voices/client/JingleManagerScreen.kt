package de.jamala1111.station_voices.client

import de.jamala1111.station_voices.Jingle
import de.jamala1111.station_voices.JingleManager
import de.jamala1111.station_voices.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

class JingleManagerScreen : Screen(Component.translatable("gui.create_station_voices.jingle_manager.title")) {
    private var jingles: List<JingleNetworkData> = emptyList()
    private var selectedKey: String? = null

    private lateinit var jingleList: JingleList
    private var keyBox: EditBox? = null
    private var saveKeyBtn: Button? = null
    private var toggleEnabledBtn: Button? = null
    private var deleteBtn: Button? = null
    private var playBtn: Button? = null
    private var stopBtn: Button? = null

    private var activeLine: SourceDataLine? = null
    private var playbackJob: Job? = null
    private var isPlaying = false

    private var statusMessage: String? = null
    private var statusColor: Int = 0x00FF00

    override fun init() {
        val listWidth = 170
        val leftX = 20
        val panelX = leftX + listWidth + 20
        val panelY = 45

        jingleList = JingleList(minecraft!!, listWidth, height, 40, 26)
        jingleList.setX(leftX)
        addRenderableWidget(jingleList)

        val isLocalWorld = minecraft?.isLocalServer() == true

        // Buttons under list
        if (isLocalWorld) {
            addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.add_jingle")) { _ ->
                addJingleFromFile()
            }.bounds(leftX, height - 60, 80, 20).build())

            addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.open_folder")) { _ ->
                net.minecraft.Util.getPlatform().openFile(JingleManager.getJinglesDirectory())
            }.bounds(leftX + 85, height - 60, 85, 20).build())
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.add_jingle")) { _ ->
                addJingleFromFile()
            }.bounds(leftX, height - 60, 170, 20).build())
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.reload")) { _ ->
            JingleManager.reload()
            PacketDistributor.sendToServer(RequestJingleListPayload())
            statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.reloaded").string
            statusColor = 0x55FF55
            refreshFromLocal()
        }.bounds(leftX, height - 35, 170, 20).build())

        // Right details panel widgets
        keyBox = EditBox(font, panelX, panelY + 30, 140, 20, Component.translatable("gui.create_station_voices.jingle_manager.key_label"))
        keyBox?.setMaxLength(32)
        addRenderableWidget(keyBox!!)

        saveKeyBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.save_key")) { _ ->
            saveNewKey()
        }.bounds(panelX + 145, panelY + 30, 70, 20).build())

        playBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.preview")) { _ ->
            playPreview()
        }.bounds(panelX, panelY + 115, 80, 20).build())

        stopBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.stop")) { _ ->
            stopPlayback()
        }.bounds(panelX + 85, panelY + 115, 60, 20).build())

        toggleEnabledBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.disable")) { _ ->
            toggleEnabled()
        }.bounds(panelX, panelY + 140, 100, 20).build())

        deleteBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.jingle_manager.delete")) { _ ->
            deleteSelected()
        }.bounds(panelX + 105, panelY + 140, 75, 20).build())

        // Bottom right close button
        addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.close")) { _ ->
            minecraft?.setScreen(null)
        }.bounds(width - 100, height - 35, 80, 20).build())

        // Initial local data
        refreshFromLocal()
        PacketDistributor.sendToServer(RequestJingleListPayload())
    }

    private fun saveNewKey() {
        val currentKey = selectedKey ?: return
        val newKey = keyBox?.value?.trim() ?: ""
        if (newKey.isBlank()) {
            statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.key_empty").string
            statusColor = 0xFF5555
            return
        }
        if (Jingle.entries.any { it.id.equals(newKey, ignoreCase = true) }) {
            statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.key_conflict_builtin", newKey).string
            statusColor = 0xFF5555
            return
        }
        if (jingles.any { it.key.equals(newKey, ignoreCase = true) && !it.key.equals(currentKey, ignoreCase = true) }) {
            statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.key_in_use", newKey).string
            statusColor = 0xFF5555
            return
        }

        stopPlayback()
        PacketDistributor.sendToServer(UpdateJingleKeyPayload(currentKey, newKey))
        JingleManager.setJingleKey(currentKey, newKey)
        selectedKey = newKey
        statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.key_updated", newKey).string
        statusColor = 0x55FF55
        refreshFromLocal()
    }

    private fun toggleEnabled() {
        val key = selectedKey ?: return
        val current = jingles.find { it.key.equals(key, ignoreCase = true) } ?: return
        val newEnabled = !current.enabled
        PacketDistributor.sendToServer(SetJingleEnabledPayload(key, newEnabled))
        JingleManager.setJingleEnabled(key, newEnabled)
        statusMessage = if (newEnabled) {
            Component.translatable("gui.create_station_voices.jingle_manager.jingle_enabled", key).string
        } else {
            Component.translatable("gui.create_station_voices.jingle_manager.jingle_disabled", key).string
        }
        statusColor = if (newEnabled) 0x55FF55 else 0xFFAA00
        refreshFromLocal()
    }

    private fun deleteSelected() {
        val key = selectedKey ?: return
        stopPlayback()
        PacketDistributor.sendToServer(DeleteJinglePayload(key))
        JingleManager.deleteCustomJingle(key)
        selectedKey = null
        statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.jingle_deleted", key).string
        statusColor = 0xFF5555
        refreshFromLocal()
    }

    private fun addJingleFromFile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dialogTitle = Component.translatable("gui.create_station_voices.jingle_manager.file_dialog_title").string
                val filePath = org.lwjgl.system.MemoryStack.stackPush().use { stack ->
                    val filterPatterns = stack.mallocPointer(3)
                    filterPatterns.put(stack.UTF8("*.wav"))
                    filterPatterns.put(stack.UTF8("*.ogg"))
                    filterPatterns.put(stack.UTF8("*.mp3"))
                    filterPatterns.flip()
                    org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        dialogTitle,
                        "",
                        filterPatterns,
                        "Audio files (*.wav, *.ogg, *.mp3)",
                        false
                    )
                }

                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists() && file.isFile) {
                        val ext = file.extension.lowercase()
                        if (ext != "wav" && ext != "ogg" && ext != "mp3") {
                            Minecraft.getInstance().execute {
                                statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.unsupported_format", ext).string
                                statusColor = 0xFF5555
                            }
                            return@launch
                        }

                        val bytes = file.readBytes()
                        val fileName = file.name
                        val key = file.nameWithoutExtension

                        PacketDistributor.sendToServer(AddJinglePayload(key, fileName, bytes))
                        JingleManager.addCustomJingle(key, fileName, bytes)

                        Minecraft.getInstance().execute {
                            selectedKey = key
                            statusMessage = Component.translatable("gui.create_station_voices.jingle_manager.jingle_added", fileName).string
                            statusColor = 0x55FF55
                            refreshFromLocal()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playPreview() {
        stopPlayback()
        val key = selectedKey ?: return
        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val pair = JingleManager.loadPcm(key)
                if (pair != null) {
                    isPlaying = true
                    val (format, bytes) = pair
                    val info = DataLine.Info(SourceDataLine::class.java, format)
                    val line = AudioSystem.getLine(info) as SourceDataLine
                    activeLine = line
                    line.open(format)
                    line.start()
                    line.write(bytes, 0, bytes.size)
                    line.drain()
                    line.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaying = false
                activeLine = null
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            activeLine?.stop()
            activeLine?.close()
        } catch (e: Exception) {
            // ignore
        }
        activeLine = null
        isPlaying = false
    }

    fun updateJingles(newJingles: List<JingleNetworkData>) {
        jingles = newJingles
        jingleList.updateItems(jingles)
        if (selectedKey == null || !jingles.any { it.key.equals(selectedKey, ignoreCase = true) }) {
            selectedKey = jingles.firstOrNull()?.key
        }
        jingleList.selectItem(selectedKey)
        updateDetailsPanel()
    }

    private fun refreshFromLocal() {
        val localData = JingleManager.getCustomJingles().map {
            JingleNetworkData(it.key, it.fileName, it.enabled, JingleManager.getJingleDuration(it.key))
        }
        updateJingles(localData)
    }

    private fun updateDetailsPanel() {
        val selected = jingles.find { it.key.equals(selectedKey, ignoreCase = true) }
        val hasSelection = selected != null

        keyBox?.visible = hasSelection
        saveKeyBtn?.visible = hasSelection
        playBtn?.visible = hasSelection
        stopBtn?.visible = hasSelection
        toggleEnabledBtn?.visible = hasSelection
        deleteBtn?.visible = hasSelection

        if (selected != null) {
            keyBox?.value = selected.key
            toggleEnabledBtn?.message = Component.translatable(if (selected.enabled) "gui.create_station_voices.jingle_manager.disable" else "gui.create_station_voices.jingle_manager.enable")
        }
    }

    override fun onClose() {
        stopPlayback()
        super.onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF)

        val listWidth = 170
        val leftX = 20
        val panelX = leftX + listWidth + 20
        val panelY = 45

        guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.custom_jingles_count", jingles.size), leftX, 28, 0xAAAAAA)

        val selected = jingles.find { it.key.equals(selectedKey, ignoreCase = true) }
        if (selected != null) {
            guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.settings_header"), panelX, panelY, 0xFFFFFF)
            guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.effects_key_header"), panelX, panelY + 18, 0xAAAAAA)

            guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.file_label", selected.fileName), panelX, panelY + 58, 0xCCCCCC)
            val ext = selected.fileName.substringAfterLast('.', "").uppercase()
            guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.format_label", ext), panelX, panelY + 70, 0xCCCCCC)
            val sec = String.format(java.util.Locale.US, "%.2f", selected.durationMs / 1000.0)
            guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.duration_label", sec, selected.durationMs), panelX, panelY + 82, 0xCCCCCC)

            val statusComp = if (selected.enabled) {
                Component.translatable("gui.create_station_voices.jingle_manager.status_enabled")
            } else {
                Component.translatable("gui.create_station_voices.jingle_manager.status_disabled")
            }
            val statusCol = if (selected.enabled) 0x55FF55 else 0xFFAA00
            guiGraphics.drawString(font, statusComp, panelX, panelY + 96, statusCol)

            if (isPlaying) {
                guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.playing_preview"), panelX + 155, panelY + 120, 0x00FF00)
            }
        } else {
            if (jingles.isEmpty()) {
                guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.no_jingles"), panelX, panelY + 20, 0xAAAAAA)
                val isLocalWorld = minecraft?.isLocalServer() == true
                val hint = if (isLocalWorld) {
                    Component.translatable("gui.create_station_voices.jingle_manager.hint_local")
                } else {
                    Component.translatable("gui.create_station_voices.jingle_manager.hint_remote")
                }
                guiGraphics.drawString(font, hint, panelX, panelY + 35, 0x888888)
                guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.supported_formats"), panelX, panelY + 50, 0x888888)
            } else {
                guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.jingle_manager.select_hint"), panelX, panelY + 20, 0xAAAAAA)
            }
        }

        statusMessage?.let { msg ->
            guiGraphics.drawString(font, msg, panelX, height - 30, statusColor)
        }
    }

    override fun isPauseScreen(): Boolean = false

    inner class JingleList(mc: Minecraft, width: Int, height: Int, y0: Int, itemHeight: Int) :
        ObjectSelectionList<JingleList.JingleEntryWidget>(mc, width, height - y0 - 70, y0, itemHeight) {

        fun updateItems(items: List<JingleNetworkData>) {
            clearEntries()
            for (item in items) {
                addEntry(JingleEntryWidget(item))
            }
        }

        fun selectItem(key: String?) {
            val entry = children().find { it.data.key.equals(key, ignoreCase = true) }
            super.setSelected(entry)
        }

        override fun setSelected(entry: JingleEntryWidget?) {
            super.setSelected(entry)
            if (entry != null) {
                selectedKey = entry.data.key
                updateDetailsPanel()
            }
        }

        override fun getScrollbarPosition(): Int = x + width - 6
        override fun getRowWidth(): Int = width - 10

        inner class JingleEntryWidget(val data: JingleNetworkData) : ObjectSelectionList.Entry<JingleEntryWidget>() {
            override fun render(
                guiGraphics: GuiGraphics,
                index: Int,
                top: Int,
                left: Int,
                width: Int,
                height: Int,
                mouseX: Int,
                mouseY: Int,
                isMouseOver: Boolean,
                partialTick: Float
            ) {
                val isSelected = selected == this
                val titleColor = if (isSelected) 0x00FF00 else if (data.enabled) 0xFFFFFF else 0x888888
                guiGraphics.drawString(minecraft!!.font, data.key, left + 4, top + 2, titleColor)

                val subText = data.fileName
                guiGraphics.drawString(minecraft!!.font, subText, left + 4, top + 13, 0x777777)

                val badge = if (data.enabled) "ON" else "OFF"
                val badgeColor = if (data.enabled) 0x55FF55 else 0xFF5555
                guiGraphics.drawString(minecraft!!.font, badge, left + width - 24, top + 2, badgeColor)
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                setSelected(this)
                return true
            }

            override fun getNarration(): Component = Component.literal(data.key)
        }
    }
}
