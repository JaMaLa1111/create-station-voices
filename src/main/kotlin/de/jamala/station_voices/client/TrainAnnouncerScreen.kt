package de.jamala.station_voices.client

import com.google.gson.Gson
import de.jamala.station_voices.Jingle
import de.jamala.station_voices.JingleTiming
import de.jamala.station_voices.block.TrainProfile
import de.jamala.station_voices.network.AutoFetchStationsRequestPayload
import de.jamala.station_voices.network.SaveTrainAnnouncerProfilesPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

class TrainAnnouncerScreen(
    val entityId: Int?,
    val pos: BlockPos?,
    val localPos: BlockPos?,
    val profiles: MutableMap<String, TrainProfile>
) : Screen(Component.literal("Train-Mounted Announcer Setup")) {

    private val gson = Gson()
    private var selectedStation: String? = null

    // Left Panel
    private lateinit var newStationBox: EditBox
    private lateinit var addStationBtn: Button
    private lateinit var removeStationBtn: Button
    private lateinit var autoPopulateBtn: Button
    private lateinit var stationList: StationList

    // Right Panel
    private var isEffectsTab = false
    private lateinit var textBox: EditBox
    private lateinit var voiceBtn: Button
    private lateinit var genBtn: Button
    private lateinit var speedSlider: AbstractSliderButton
    private lateinit var volumeSlider: AbstractSliderButton
    private lateinit var rangeSlider: AbstractSliderButton
    private lateinit var realismSlider: AbstractSliderButton
    private lateinit var reverbBtn: Button
    private lateinit var jingleBtn: Button
    private lateinit var jingleTimingBtn: Button
    private lateinit var contraptionOnlyBtn: Button
    private lateinit var tabBtn: Button

    // Common
    private lateinit var doneBtn: Button

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2

        // Left Panel (Station List)
        val leftX = centerX - 180
        val rightX = centerX + 10

        newStationBox = EditBox(font, leftX, 35, 100, 20, Component.literal("Station Name"))
        newStationBox.setMaxLength(64)
        addRenderableWidget(newStationBox)

        addStationBtn = addRenderableWidget(Button.builder(Component.literal("Add")) { _ ->
            val name = newStationBox.value.trim()
            if (name.isNotBlank() && !profiles.containsKey(name)) {
                profiles[name] = TrainProfile()
                stationList.updateItems(profiles.keys.toList())
                stationList.selectItem(name)
                newStationBox.value = ""
            }
        }.bounds(leftX + 105, 35, 45, 20).build())

        stationList = StationList(minecraft!!, 150, height, 60, 20)
        stationList.setX(leftX)
        addRenderableWidget(stationList)
        stationList.updateItems(profiles.keys.toList())

        removeStationBtn = addRenderableWidget(Button.builder(Component.literal("Remove")) { _ ->
            val name = selectedStation
            if (name != null) {
                profiles.remove(name)
                selectedStation = null
                stationList.updateItems(profiles.keys.toList())
                updateRightPanel()
            }
        }.bounds(leftX, height - 30, 73, 20).build())

        autoPopulateBtn = addRenderableWidget(Button.builder(Component.literal("Auto-Fetch")) { _ ->
            autoPopulateStations()
        }.bounds(leftX + 77, height - 30, 73, 20).build())

        // Right Panel
        textBox = EditBox(font, rightX, centerY - 52, 200, 20, Component.literal("Announcement Text"))
        textBox.setMaxLength(256)
        textBox.setResponder { text ->
            selectedStation?.let { profiles[it]?.text = text }
        }
        addRenderableWidget(textBox)

        voiceBtn = addRenderableWidget(Button.builder(Component.literal("Voice")) { _ ->
            val profile = selectedStation?.let { profiles[it] }
            if (profile != null) {
                minecraft?.setScreen(VoiceSelectionScreen(this, profile.language, profile.voice) { lang, voice -> 
                    profile.language = lang
                    profile.voice = voice
                    updateRightPanel()
                })
            }
        }.bounds(rightX, centerY - 29, 200, 20).build())

        // Effects Tab
        speedSlider = addRenderableWidget(createSpeedSlider(rightX, centerY - 80, 200, 20))
        volumeSlider = addRenderableWidget(createVolumeSlider(rightX, centerY - 57, 200, 20))
        rangeSlider = addRenderableWidget(createRangeSlider(rightX, centerY - 34, 200, 20))
        realismSlider = addRenderableWidget(createRealismSlider(rightX, centerY - 11, 200, 20))

        reverbBtn = addRenderableWidget(Button.builder(Component.literal("Reverb: OFF")) { btn ->
            val profile = selectedStation?.let { profiles[it] }
            if (profile != null) {
                profile.reverb = !profile.reverb
                btn.message = Component.literal("Reverb: ${if (profile.reverb) "ON" else "OFF"}")
            }
        }.bounds(rightX, centerY + 12, 95, 20).build())

        jingleBtn = addRenderableWidget(Button.builder(Component.literal("Jingle: OFF")) { btn ->
            val profile = selectedStation?.let { profiles[it] }
            if (profile != null) {
                val next = Jingle.fromString(profile.jingle).next()
                profile.jingle = next.id
                btn.message = Component.literal("Jingle: ${next.displayName}")
                jingleTimingBtn.active = next != Jingle.OFF
            }
        }.bounds(rightX + 105, centerY + 12, 95, 20).build())

        jingleTimingBtn = addRenderableWidget(Button.builder(Component.literal("Timing: Both")) { btn ->
            val profile = selectedStation?.let { profiles[it] }
            if (profile != null) {
                val next = JingleTiming.fromString(profile.jingleTiming).next()
                profile.jingleTiming = next.id
                btn.message = Component.literal("Timing: ${next.displayName}")
            }
        }.bounds(rightX, centerY + 35, 95, 20).build())

        contraptionOnlyBtn = addRenderableWidget(Button.builder(Component.literal("Train Only: OFF")) { btn ->
            val profile = selectedStation?.let { profiles[it] }
            if (profile != null) {
                profile.contraptionOnly = !profile.contraptionOnly
                btn.message = Component.literal("Train Only: ${if (profile.contraptionOnly) "ON" else "OFF"}")
            }
        }.bounds(rightX + 105, centerY + 35, 95, 20)
        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Only players on the train (seated and standing) can hear the announcement.\n\nNote: Inaccuracies can occur because of the way Create calculates bounding boxes for trains.")))
        .build())

        // Preview button
        genBtn = addRenderableWidget(Button.builder(Component.literal("Generate & Preview")) { _ ->
            val profile = selectedStation?.let { profiles[it] }
            if (profile != null && profile.text.isNotBlank()) {
                PacketDistributor.sendToServer(de.jamala.station_voices.network.RequestPreviewAudioPayload(
                    profile.text, profile.voice, profile.language, profile.speed, profile.volume, profile.reverb, profile.maxRange, profile.jingle, profile.jingleTiming, profile.realism
                ))
            }
        }.bounds(rightX, centerY + 58, 200, 20).build())

        tabBtn = addRenderableWidget(Button.builder(Component.literal("Tab: Main")) { _ ->
            isEffectsTab = !isEffectsTab
            updateRightPanelVisibility()
        }.bounds(rightX, centerY + 81, 95, 20).build())

        // Done Button (Saves all)
        doneBtn = addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            saveAndClose()
        }.bounds(rightX + 105, centerY + 81, 95, 20).build())

        if (profiles.isNotEmpty()) {
            stationList.selectItem(profiles.keys.first())
        } else {
            updateRightPanel()
        }
    }

    private fun autoPopulateStations() {
        PacketDistributor.sendToServer(AutoFetchStationsRequestPayload(pos, entityId, localPos))
    }

    fun handleAutoFetchResponse(stationNames: List<String>) {
        var addedAny = false
        for (stationName in stationNames) {
            if (!profiles.containsKey(stationName)) {
                profiles[stationName] = TrainProfile()
                addedAny = true
            }
        }

        if (addedAny) {
            stationList.updateItems(profiles.keys.toList())
            if (selectedStation == null && profiles.isNotEmpty()) {
                stationList.selectItem(profiles.keys.first())
            }
        }
    }

    private fun saveAndClose() {
        val json = gson.toJson(profiles)
        PacketDistributor.sendToServer(SaveTrainAnnouncerProfilesPayload(pos, entityId, localPos, json))
        onClose()
    }

    fun onStationSelected(name: String) {
        selectedStation = name
        updateRightPanel()
    }

    private fun updateRightPanel() {
        val profile = selectedStation?.let { profiles[it] }
        val hasSelection = profile != null

        removeStationBtn.active = hasSelection
        tabBtn.active = hasSelection
        doneBtn.active = true

        if (profile != null) {
            textBox.value = profile.text
            voiceBtn.message = Component.literal("Voice: ${profile.voice} (${profile.language})")
            
            setSliderValue(speedSlider, ((profile.speed - 0.1) / 2.9).toDouble())
            setSliderValue(volumeSlider, profile.volume.toDouble())
            setSliderValue(rangeSlider, ((profile.maxRange - 1.0) / 63.0).toDouble())
            setSliderValue(realismSlider, profile.realism.toDouble())
            
            reverbBtn.message = Component.literal("Reverb: ${if (profile.reverb) "ON" else "OFF"}")
            val currentJingle = Jingle.fromString(profile.jingle)
            jingleBtn.message = Component.literal("Jingle: ${currentJingle.displayName}")
            val timing = JingleTiming.fromString(profile.jingleTiming)
            jingleTimingBtn.message = Component.literal("Timing: ${timing.displayName}")
            jingleTimingBtn.active = currentJingle != Jingle.OFF
            contraptionOnlyBtn.message = Component.literal("Train Only: ${if (profile.contraptionOnly) "ON" else "OFF"}")
        } else {
            textBox.value = ""
            voiceBtn.message = Component.literal("Voice: None")
        }

        updateRightPanelVisibility()
    }

    private fun setSliderValue(slider: AbstractSliderButton, newValue: Double) {
        if (slider is CustomSlider) {
            slider.setValue(newValue)
            slider.updateMessage()
        }
    }

    private fun updateRightPanelVisibility() {
        val hasSelection = selectedStation != null
        val profile = selectedStation?.let { profiles[it] }
        
        tabBtn.message = Component.literal(if (isEffectsTab) "Tab: Effects" else "Tab: Main")
        
        textBox.visible = hasSelection && !isEffectsTab
        voiceBtn.visible = hasSelection && !isEffectsTab
        
        speedSlider.visible = hasSelection && isEffectsTab
        volumeSlider.visible = hasSelection && isEffectsTab
        rangeSlider.visible = hasSelection && isEffectsTab
        realismSlider.visible = hasSelection && isEffectsTab
        reverbBtn.visible = hasSelection && isEffectsTab
        jingleBtn.visible = hasSelection && isEffectsTab
        jingleTimingBtn.visible = hasSelection && isEffectsTab
        contraptionOnlyBtn.visible = hasSelection && isEffectsTab
        if (hasSelection && profile != null) {
            jingleTimingBtn.active = Jingle.fromString(profile.jingle) != Jingle.OFF
        }
        genBtn.visible = hasSelection
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
        
        if (selectedStation != null) {
            val rightX = width / 2 + 10
            guiGraphics.drawString(font, "Station: $selectedStation", rightX, height / 2 - 95, 0xAAAAAA)
        }
    }

    override fun isPauseScreen(): Boolean = false

    interface CustomSlider {
        fun setValue(v: Double)
        fun updateMessage()
    }

    private fun createSpeedSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                val act = 0.1 + value * 2.9
                message = Component.literal(String.format(java.util.Locale.US, "Pitch/Speed: %.2fx", act))
            }
            override fun applyValue() {
                selectedStation?.let { profiles[it]?.speed = (0.1 + value * 2.9).toFloat() }
            }
        }
    }

    private fun createVolumeSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                message = Component.literal(String.format(java.util.Locale.US, "Volume: %d%%", (value * 100).toInt()))
            }
            override fun applyValue() {
                selectedStation?.let { profiles[it]?.volume = value.toFloat() }
            }
        }
    }

    private fun createRangeSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                val act = 1 + (value * 63).toInt()
                message = Component.literal("Max Range: $act blocks")
            }
            override fun applyValue() {
                selectedStation?.let { profiles[it]?.maxRange = 1 + (value * 63).toInt() }
            }
        }
    }

    private fun createRealismSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                val pct = (value * 100).toInt()
                message = Component.literal(if (pct == 0) "Realism: OFF" else "Realism: $pct%")
            }
            override fun applyValue() {
                selectedStation?.let { profiles[it]?.realism = value.toFloat() }
            }
        }
    }

    inner class StationList(mc: Minecraft, width: Int, height: Int, y0: Int, itemHeight: Int) : ObjectSelectionList<StationList.StringEntry>(mc, width, height - y0 - 40, y0, itemHeight) {
        
        fun updateItems(items: List<String>) {
            clearEntries()
            for (item in items) {
                addEntry(StringEntry(item))
            }
        }

        fun selectItem(item: String) {
            val entry = children().find { it.text == item }
            super.setSelected(entry)
            if (entry != null) {
                onStationSelected(entry.text)
            }
        }
        
        override fun setSelected(entry: StringEntry?) {
            super.setSelected(entry)
            if (entry != null) {
                onStationSelected(entry.text)
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
                guiGraphics.drawString(minecraft!!.font, text, left + 5, top + 5, color)
            }
            
            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                setSelected(this)
                return true
            }

            override fun getNarration(): Component = Component.literal(text)
        }
    }
}
