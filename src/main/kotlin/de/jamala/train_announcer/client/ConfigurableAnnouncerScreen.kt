package de.jamala.train_announcer.client

import de.jamala.train_announcer.block.TrainProfile
import de.jamala.train_announcer.network.AutoFetchTrainsRequestPayload
import de.jamala.train_announcer.network.SetConfigurableAnnouncerDataPayload
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
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction

class ConfigurableAnnouncerScreen(
    val pos: BlockPos,
    val profiles: MutableMap<String, TrainProfile>,
    val targetStation: BlockPos?
) : Screen(Component.literal("Configurable Announcer Setup")) {

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
    private lateinit var reverbBtn: Button
    private lateinit var tabBtn: Button

    // Common
    private lateinit var doneBtn: Button

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2

        // Left Panel (Trains List)
        val leftX = centerX - 180
        val rightX = centerX + 10

        newTrainBox = EditBox(font, leftX, 35, 100, 20, Component.literal("Train Name"))
        newTrainBox.setMaxLength(64)
        addRenderableWidget(newTrainBox)

        addTrainBtn = addRenderableWidget(Button.builder(Component.literal("Add")) { _ ->
            val name = newTrainBox.value
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

        removeTrainBtn = addRenderableWidget(Button.builder(Component.literal("Remove")) { _ ->
            val name = selectedTrain
            if (name != null) {
                profiles.remove(name)
                selectedTrain = null
                trainList.updateItems(profiles.keys.toList())
                updateRightPanel()
            }
        }.bounds(leftX, height - 30, 73, 20).build())

        autoPopulateBtn = addRenderableWidget(Button.builder(Component.literal("Auto-Fetch")) { _ ->
            autoPopulateTrains()
        }.bounds(leftX + 77, height - 30, 73, 20).build())

        val isLinkedToStation = targetStation != null && (minecraft?.level?.getBlockEntity(targetStation) is StationBlockEntity)
        autoPopulateBtn.active = isLinkedToStation

        // Right Panel
        textBox = EditBox(font, rightX, centerY - 60, 200, 20, Component.literal("Text"))
        textBox.setMaxLength(256)
        textBox.setResponder { text ->
            selectedTrain?.let { profiles[it]?.text = text }
        }
        addRenderableWidget(textBox)

        voiceBtn = addRenderableWidget(Button.builder(Component.literal("Voice")) { _ ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null) {
                minecraft?.setScreen(VoiceSelectionScreen(this, profile.language, profile.voice) { lang, voice -> 
                    profile.language = lang
                    profile.voice = voice
                    updateRightPanel()
                })
            }
        }.bounds(rightX, centerY - 35, 200, 20).build())

        // Effects Tab
        speedSlider = addRenderableWidget(createSpeedSlider(rightX, centerY - 60, 200, 20))
        
        volumeSlider = addRenderableWidget(createVolumeSlider(rightX, centerY - 35, 200, 20))
        
        rangeSlider = addRenderableWidget(createRangeSlider(rightX, centerY - 10, 200, 20))

        reverbBtn = addRenderableWidget(Button.builder(Component.literal("Reverb: OFF")) { btn ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null) {
                profile.reverb = !profile.reverb
                btn.message = Component.literal("Reverb: ${if (profile.reverb) "ON" else "OFF"}")
            }
        }.bounds(rightX, centerY + 15, 200, 20).build())

        // Common
        genBtn = addRenderableWidget(Button.builder(Component.literal("Generate & Preview")) { _ ->
            val profile = selectedTrain?.let { profiles[it] }
            if (profile != null && profile.text.isNotBlank()) {
                de.jamala.train_announcer.client.AudioPlayer.play(null, profile.text, profile.voice, profile.language, profile.speed, profile.volume, profile.reverb, profile.maxRange)
            }
        }.bounds(rightX, centerY + 40, 200, 20).build())

        tabBtn = addRenderableWidget(Button.builder(Component.literal("Tab: Main")) { _ ->
            isEffectsTab = !isEffectsTab
            updateRightPanelVisibility()
        }.bounds(rightX, centerY + 65, 95, 20).build())

        // Done Button (Saves all)
        doneBtn = addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            saveAndClose()
        }.bounds(rightX + 105, centerY + 65, 95, 20).build())

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
            if (profile == null) {
                // Deleted
                PacketDistributor.sendToServer(SetConfigurableAnnouncerDataPayload(pos, key, "", "amy", "en_US", 1.0f, 1.0f, false, 64))
            } else {
                // Updated/Created
                PacketDistributor.sendToServer(SetConfigurableAnnouncerDataPayload(pos, key, profile.text, profile.voice, profile.language, profile.speed, profile.volume, profile.reverb, profile.maxRange))
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
            voiceBtn.message = Component.literal("Voice: ${profile.voice} (${profile.language})")
            
            // Set slider values (need reflection or direct var assignment if slider allows, else recreate or ignore warning. Wait, value is protected in AbstractSliderButton in some mappings, but public in others. We'll use a hack or recreate sliders.)
            // AbstractSliderButton has `value` field. If it's protected, we might get compiler error. 
            // In Neoforge 1.21, value is `protected double value`.
            // Let's check how we accessed it in AnnouncerScreen. We accessed `value` inside the anonymous class.
            // Outside, we might need a setter method in our anonymous class. Let's cast and set.
            // Wait, we can't easily access `value` from outside in Kotlin without a setter.
            setSliderValue(speedSlider, ((profile.speed - 0.1) / 2.9).toDouble())
            setSliderValue(volumeSlider, profile.volume.toDouble())
            setSliderValue(rangeSlider, ((profile.maxRange - 1.0) / 63.0).toDouble())
            
            reverbBtn.message = Component.literal("Reverb: ${if (profile.reverb) "ON" else "OFF"}")
        } else {
            textBox.value = ""
            voiceBtn.message = Component.literal("Voice: None")
        }

        updateRightPanelVisibility()
    }

    private fun setSliderValue(slider: AbstractSliderButton, newValue: Double) {
        // Reflection to set value, or just recreate. Better to add an interface.
        if (slider is CustomSlider) {
            slider.setValue(newValue)
            slider.updateMessage()
        }
    }

    private fun updateRightPanelVisibility() {
        val hasSelection = selectedTrain != null
        
        tabBtn.message = Component.literal(if (isEffectsTab) "Tab: Effects" else "Tab: Main")
        
        textBox.visible = hasSelection && !isEffectsTab
        voiceBtn.visible = hasSelection && !isEffectsTab
        
        speedSlider.visible = hasSelection && isEffectsTab
        volumeSlider.visible = hasSelection && isEffectsTab
        rangeSlider.visible = hasSelection && isEffectsTab
        reverbBtn.visible = hasSelection && isEffectsTab
        genBtn.visible = hasSelection
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
        
        if (selectedTrain != null) {
            val rightX = width / 2 + 10
            guiGraphics.drawString(font, "Editing: $selectedTrain", rightX, height / 2 - 80, 0xAAAAAA)
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
                message = Component.literal(String.format(java.util.Locale.US, "Pitch/Speed: %.2fx", act))
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
                message = Component.literal(String.format(java.util.Locale.US, "Volume: %d%%", (value * 100).toInt()))
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
                message = Component.literal("Max Range: $act blocks")
            }
            override fun applyValue() {
                selectedTrain?.let { profiles[it]?.maxRange = 1 + (value * 63).toInt() }
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
