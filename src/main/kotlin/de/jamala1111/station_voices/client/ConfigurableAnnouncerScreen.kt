package de.jamala1111.station_voices.client

import de.jamala1111.station_voices.Jingle
import de.jamala1111.station_voices.JingleManager
import de.jamala1111.station_voices.JingleTiming
import de.jamala1111.station_voices.block.TrainProfile
import de.jamala1111.station_voices.network.AutoFetchTrainsRequestPayload
import de.jamala1111.station_voices.network.SetConfigurableAnnouncerDataPayload
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
import com.simibubi.create.CreateClient
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction

class ConfigurableAnnouncerScreen(
    val pos: BlockPos,
    val profiles: MutableMap<String, TrainProfile>,
    val targetStation: BlockPos?
) : Screen(Component.translatable("gui.create_station_voices.configurable.title")) {

    private val originalKeys = profiles.keys.toList()
    private var selectedTrain: String? = null

    // Left Panel
    private lateinit var newTrainBox: EditBox
    private lateinit var addTrainBtn: Button
    private lateinit var removeTrainBtn: Button
    private lateinit var autoPopulateBtn: Button
    private lateinit var trainList: TrainList

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
    private lateinit var tabBtn: Button

    // Common
    private lateinit var doneBtn: Button

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2

        // Left Panel (Trains List)
        val leftX = centerX - 180
        val rightX = centerX + 10

        newTrainBox = EditBox(font, leftX, 35, 100, 20, Component.translatable("gui.create_station_voices.configurable.train_name"))
        newTrainBox.setMaxLength(64)
        addRenderableWidget(newTrainBox)

        addTrainBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.add")) { _ ->
            val name = de.jamala1111.station_voices.TextSanitizer.sanitizeLabel(newTrainBox.value)
            if (name.isNotBlank() && !profiles.containsKey(name)) {
                profiles[name] = TrainProfile()
                trainList.updateItems(profiles.keys.toList())
                trainList.selectItem(name)
                newTrainBox.value = ""
            }
        }.bounds(leftX + 105, 35, 45, 20).build())

        trainList = TrainList(minecraft!!, 150, height, 60, 20)
        trainList.setX(leftX)
        addRenderableWidget(trainList)
        trainList.updateItems(profiles.keys.toList())

        removeTrainBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.remove")) { _ ->
            val name = selectedTrain
            if (name != null) {
                profiles.remove(name)
                selectedTrain = null
                trainList.updateItems(profiles.keys.toList())
                updateRightPanel()
            }
        }.bounds(leftX, height - 30, 73, 20).build())

        autoPopulateBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.auto_fetch")) { _ ->
            autoPopulateTrains()
        }.bounds(leftX + 77, height - 30, 73, 20).build())

        val targetBe = targetStation?.let { minecraft?.level?.getBlockEntity(it) }
        val isLinked = targetStation != null && (targetBe == null || targetBe is StationBlockEntity || targetBe is TrackObserverBlockEntity)
        autoPopulateBtn.active = isLinked

        // Right Panel
        textBox = EditBox(font, rightX, centerY - 52, 200, 20, Component.translatable("gui.create_station_voices.announcer.text"))
        textBox.setMaxLength(256)
        textBox.setResponder { text ->
            selectedTrain?.let { profiles[it]?.text = text }
        }
        addRenderableWidget(textBox)

        voiceBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.voice_button")) { _ ->
            val profile = selectedTrain?.let { profiles[it] }
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

        reverbBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.effects.reverb", Component.translatable("gui.create_station_voices.off"))) { btn ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null) {
                profile.reverb = !profile.reverb
                btn.message = Component.translatable("gui.create_station_voices.effects.reverb", Component.translatable(if (profile.reverb) "gui.create_station_voices.on" else "gui.create_station_voices.off"))
            }
        }.bounds(rightX, centerY + 12, 95, 20).build())

        jingleBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.effects.jingle", "OFF")) { btn ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null) {
                val next = JingleManager.getNextJingleId(profile.jingle)
                profile.jingle = next
                btn.message = Component.translatable("gui.create_station_voices.effects.jingle", JingleManager.getDisplayName(next))
                jingleTimingBtn.active = !JingleManager.isOff(next)
            }
        }.bounds(rightX + 105, centerY + 12, 95, 20).build())

        jingleTimingBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.effects.jingle_timing", JingleTiming.BOTH.getComponent())) { btn ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null) {
                val next = JingleTiming.fromString(profile.jingleTiming).next()
                profile.jingleTiming = next.id
                btn.message = Component.translatable("gui.create_station_voices.effects.jingle_timing", next.getComponent())
            }
        }.bounds(rightX, centerY + 35, 200, 20).build())

        // Common
        genBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.generate_preview")) { _ ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null && profile.text.isNotBlank()) {
                val previewText = de.jamala1111.station_voices.TextSanitizer.sanitize(
                    profile.text.replace("{train}", selectedTrain ?: "", ignoreCase = true).replace("{name}", selectedTrain ?: "", ignoreCase = true),
                    profile.language
                )
                if (previewText.isNotBlank()) {
                    PacketDistributor.sendToServer(de.jamala1111.station_voices.network.RequestPreviewAudioPayload(
                        previewText, profile.voice, profile.language, profile.speed, profile.volume, profile.reverb, profile.maxRange, profile.jingle, profile.jingleTiming, profile.realism
                    ))
                }
            }
        }.bounds(rightX, centerY + 58, 200, 20).build())

        tabBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.tab_main")) { _ ->
            isEffectsTab = !isEffectsTab
            updateRightPanelVisibility()
        }.bounds(rightX, centerY + 81, 95, 20).build())

        // Done Button (Saves all)
        doneBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.done")) { _ ->
            saveAndClose()
        }.bounds(rightX + 105, centerY + 81, 95, 20).build())

        if (profiles.isNotEmpty()) {
            trainList.selectItem(profiles.keys.first())
        } else {
            updateRightPanel()
        }
    }

    private fun autoPopulateTrains() {
        val targetPos = targetStation ?: return
        PacketDistributor.sendToServer(AutoFetchTrainsRequestPayload(pos))
    }

    fun handleAutoFetchResponse(trainNames: List<String>) {
        var addedAny = false
        for (trainName in trainNames) {
            if (!profiles.containsKey(trainName)) {
                profiles[trainName] = TrainProfile()
                addedAny = true
            }
        }

        if (addedAny) {
            trainList.updateItems(profiles.keys.toList())
            if (selectedTrain == null && profiles.isNotEmpty()) {
                trainList.selectItem(profiles.keys.first())
            }
        }
    }

    private fun saveAndClose() {
        val keysToSend = mutableSetOf<String>()
        keysToSend.addAll(originalKeys)
        keysToSend.addAll(profiles.keys)

        for (key in keysToSend) {
            val profile = profiles[key]
            val safeKey = de.jamala1111.station_voices.TextSanitizer.sanitizeLabel(key)
            if (profile == null) {
                // Deleted
                PacketDistributor.sendToServer(SetConfigurableAnnouncerDataPayload(pos, safeKey.ifBlank { key }, "", "amy", "en_US", 1.0f, 1.0f, false, 64, "OFF", "BOTH", 0.0f))
            } else {
                // Updated/Created
                val safeText = de.jamala1111.station_voices.TextSanitizer.sanitizeTemplate(profile.text, profile.language)
                PacketDistributor.sendToServer(SetConfigurableAnnouncerDataPayload(pos, safeKey.ifBlank { key }, safeText, profile.voice, profile.language, profile.speed, profile.volume, profile.reverb, profile.maxRange, profile.jingle, profile.jingleTiming, profile.realism))
            }
        }
        onClose()
    }

    fun onTrainSelected(name: String) {
        selectedTrain = name
        updateRightPanel()
    }

    private fun updateRightPanel() {
        val profile = selectedTrain?.let { profiles[it] }
        val hasSelection = profile != null

        removeTrainBtn.active = hasSelection
        tabBtn.active = hasSelection
        doneBtn.active = true // Always can save and close

        if (profile != null) {
            textBox.value = profile.text
            voiceBtn.message = Component.translatable("gui.create_station_voices.voice", profile.voice, profile.language)
            
            setSliderValue(speedSlider, ((profile.speed - 0.1) / 2.9).toDouble())
            setSliderValue(volumeSlider, profile.volume.toDouble())
            setSliderValue(rangeSlider, ((profile.maxRange - 1.0) / 63.0).toDouble())
            setSliderValue(realismSlider, profile.realism.toDouble())
            
            reverbBtn.message = Component.translatable("gui.create_station_voices.effects.reverb", Component.translatable(if (profile.reverb) "gui.create_station_voices.on" else "gui.create_station_voices.off"))
            val jingleName = JingleManager.getDisplayName(profile.jingle)
            jingleBtn.message = Component.translatable("gui.create_station_voices.effects.jingle", jingleName)
            val timing = JingleTiming.fromString(profile.jingleTiming)
            jingleTimingBtn.message = Component.translatable("gui.create_station_voices.effects.jingle_timing", timing.getComponent())
            jingleTimingBtn.active = !JingleManager.isOff(profile.jingle)
        } else {
            textBox.value = ""
            voiceBtn.message = Component.translatable("gui.create_station_voices.voice_none")
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
        val hasSelection = selectedTrain != null
        val profile = selectedTrain?.let { profiles[it] }
        
        tabBtn.message = Component.translatable(if (isEffectsTab) "gui.create_station_voices.tab_effects" else "gui.create_station_voices.tab_main")
        
        textBox.visible = hasSelection && !isEffectsTab
        voiceBtn.visible = hasSelection && !isEffectsTab
        
        speedSlider.visible = hasSelection && isEffectsTab
        volumeSlider.visible = hasSelection && isEffectsTab
        rangeSlider.visible = hasSelection && isEffectsTab
        realismSlider.visible = hasSelection && isEffectsTab
        reverbBtn.visible = hasSelection && isEffectsTab
        jingleBtn.visible = hasSelection && isEffectsTab
        jingleTimingBtn.visible = hasSelection && isEffectsTab
        if (hasSelection && profile != null) {
            jingleTimingBtn.active = !JingleManager.isOff(profile.jingle)
        }
        genBtn.visible = hasSelection
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
        
        if (selectedTrain != null) {
            val rightX = width / 2 + 10
            guiGraphics.drawString(font, Component.translatable("gui.create_station_voices.editing", selectedTrain), rightX, height / 2 - 95, 0xAAAAAA)
        }
    }

    override fun isPauseScreen(): Boolean = false

    interface CustomSlider {
        fun setValue(v: Double)
        fun updateMessage()
    }

    // Needed to subclass AbstractSliderButton and expose setValue
    private fun createSpeedSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                val act = 0.1 + value * 2.9
                message = Component.translatable("gui.create_station_voices.effects.speed", act)
            }
            override fun applyValue() {
                selectedTrain?.let { profiles[it]?.speed = (0.1 + value * 2.9).toFloat() }
            }
        }
    }

    private fun createVolumeSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                message = Component.translatable("gui.create_station_voices.effects.volume", (value * 100).toInt())
            }
            override fun applyValue() {
                selectedTrain?.let { profiles[it]?.volume = value.toFloat() }
            }
        }
    }

    private fun createRangeSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                val act = 1 + (value * 63).toInt()
                message = Component.translatable("gui.create_station_voices.effects.range", act)
            }
            override fun applyValue() {
                selectedTrain?.let { profiles[it]?.maxRange = 1 + (value * 63).toInt() }
            }
        }
    }

    private fun createRealismSlider(x: Int, y: Int, w: Int, h: Int): AbstractSliderButton {
        return object : AbstractSliderButton(x, y, w, h, Component.empty(), 0.0), CustomSlider {
            init { updateMessage() }
            override fun setValue(v: Double) { value = v }
            public override fun updateMessage() {
                val pct = (value * 100).toInt()
                message = if (pct == 0) Component.translatable("gui.create_station_voices.effects.realism_off") else Component.translatable("gui.create_station_voices.effects.realism", pct)
            }
            override fun applyValue() {
                selectedTrain?.let { profiles[it]?.realism = value.toFloat() }
            }
        }
    }

    inner class TrainList(mc: Minecraft, width: Int, height: Int, y0: Int, itemHeight: Int) : ObjectSelectionList<TrainList.StringEntry>(mc, width, height - y0 - 40, y0, itemHeight) {
        
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
                onTrainSelected(entry.text)
            }
        }
        
        override fun setSelected(entry: StringEntry?) {
            super.setSelected(entry)
            if (entry != null) {
                onTrainSelected(entry.text)
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
