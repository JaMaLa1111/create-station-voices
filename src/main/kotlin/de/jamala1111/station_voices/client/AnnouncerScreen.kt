package de.jamala1111.station_voices.client

import de.jamala1111.station_voices.Jingle
import de.jamala1111.station_voices.JingleManager
import de.jamala1111.station_voices.JingleTiming
import de.jamala1111.station_voices.network.SetAnnouncerDataPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

class AnnouncerScreen(
    val pos: BlockPos,
    var currentText: String,
    var currentVoice: String,
    var currentLanguage: String,
    var currentSpeed: Float,
    var currentVolume: Float,
    var currentReverb: Boolean,
    var currentMaxRange: Int,
    var currentJingle: String = "OFF",
    var currentJingleTiming: String = "BOTH",
    var currentRealism: Float = 0.0f
) : Screen(Component.translatable("gui.create_station_voices.announcer.title")) {

    private lateinit var textBox: EditBox

    private var isEffectsTab = false

    // Main Tab
    private lateinit var voiceBtn: Button
    private lateinit var genBtn: Button

    // Effects Tab
    private lateinit var speedSlider: AbstractSliderButton
    private lateinit var volumeSlider: AbstractSliderButton
    private lateinit var rangeSlider: AbstractSliderButton
    private lateinit var realismSlider: AbstractSliderButton
    private lateinit var reverbBtn: Button
    private lateinit var jingleBtn: Button
    private lateinit var jingleTimingBtn: Button

    // Common
    private lateinit var tabBtn: Button
    private lateinit var doneBtn: Button

    override fun init() {
        val centerX = width / 2
        val centerY = height / 2

        // Main Tab
        textBox = EditBox(font, centerX - 100, centerY - 52, 200, 20, Component.translatable("gui.create_station_voices.announcer.text"))
        textBox.value = currentText
        textBox.setMaxLength(256)
        addRenderableWidget(textBox)

        voiceBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.voice", currentVoice, currentLanguage)) { _ ->
            minecraft?.setScreen(VoiceSelectionScreen(this, currentLanguage, currentVoice) { lang, voice -> updateVoice(lang, voice) })
        }.bounds(centerX - 100, centerY - 29, 200, 20).build())

        // Effects Tab
        speedSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 80, 200, 20, Component.empty(), (currentSpeed - 0.1) / (3.0 - 0.1)) {
            init { updateMessage() }
            override fun updateMessage() {
                val act = 0.1 + value * 2.9
                message = Component.translatable("gui.create_station_voices.effects.speed", act)
            }
            override fun applyValue() {
                currentSpeed = (0.1 + value * 2.9).toFloat()
            }
        })
        
        volumeSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 57, 200, 20, Component.empty(), currentVolume.toDouble()) {
            init { updateMessage() }
            override fun updateMessage() {
                message = Component.translatable("gui.create_station_voices.effects.volume", (value * 100).toInt())
            }
            override fun applyValue() {
                currentVolume = value.toFloat()
            }
        })
        
        rangeSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 34, 200, 20, Component.empty(), (currentMaxRange - 1.0) / 63.0) {
            init { updateMessage() }
            override fun updateMessage() {
                val act = 1 + (value * 63).toInt()
                message = Component.translatable("gui.create_station_voices.effects.range", act)
            }
            override fun applyValue() {
                currentMaxRange = 1 + (value * 63).toInt()
            }
        })

        realismSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 11, 200, 20, Component.empty(), currentRealism.toDouble()) {
            init { updateMessage() }
            override fun updateMessage() {
                val pct = (value * 100).toInt()
                message = if (pct == 0) Component.translatable("gui.create_station_voices.effects.realism_off") else Component.translatable("gui.create_station_voices.effects.realism", pct)
            }
            override fun applyValue() {
                currentRealism = value.toFloat()
            }
        })

        reverbBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.effects.reverb", Component.translatable(if (currentReverb) "gui.create_station_voices.on" else "gui.create_station_voices.off"))) { btn ->
            currentReverb = !currentReverb
            btn.message = Component.translatable("gui.create_station_voices.effects.reverb", Component.translatable(if (currentReverb) "gui.create_station_voices.on" else "gui.create_station_voices.off"))
        }.bounds(centerX - 100, centerY + 12, 95, 20).build())

        val initName = JingleManager.getDisplayName(currentJingle)
        jingleBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.effects.jingle", initName)) { btn ->
            val next = JingleManager.getNextJingleId(currentJingle)
            currentJingle = next
            btn.message = Component.translatable("gui.create_station_voices.effects.jingle", JingleManager.getDisplayName(next))
            jingleTimingBtn.active = !JingleManager.isOff(next)
        }.bounds(centerX + 5, centerY + 12, 95, 20).build())

        jingleTimingBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.effects.jingle_timing", JingleTiming.fromString(currentJingleTiming).getComponent())) { btn ->
            val next = JingleTiming.fromString(currentJingleTiming).next()
            currentJingleTiming = next.id
            btn.message = Component.translatable("gui.create_station_voices.effects.jingle_timing", next.getComponent())
        }.bounds(centerX - 100, centerY + 35, 200, 20).build())

        // Common
        genBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.generate_preview")) { _ ->
            de.jamala1111.station_voices.client.ClientHooks.testAL()
            val text = de.jamala1111.station_voices.TextSanitizer.sanitize(textBox.value, currentLanguage)
            textBox.value = text
            if (text.isNotBlank()) {
                PacketDistributor.sendToServer(de.jamala1111.station_voices.network.RequestPreviewAudioPayload(
                    text, currentVoice, currentLanguage, currentSpeed, currentVolume, currentReverb, currentMaxRange, currentJingle, currentJingleTiming, currentRealism
                ))
            }
        }.bounds(centerX - 100, centerY + 58, 200, 20).build())

        tabBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.tab_main")) { _ ->
            isEffectsTab = !isEffectsTab
            updateVisibility()
        }.bounds(centerX - 100, centerY + 81, 95, 20).build())

        doneBtn = addRenderableWidget(Button.builder(Component.translatable("gui.create_station_voices.done")) { _ ->
            currentText = de.jamala1111.station_voices.TextSanitizer.sanitize(textBox.value, currentLanguage)
            textBox.value = currentText
            PacketDistributor.sendToServer(SetAnnouncerDataPayload(pos, currentText, currentVoice, currentLanguage, currentSpeed, currentVolume, currentReverb, currentMaxRange, currentJingle, currentJingleTiming, currentRealism))
            onClose()
        }.bounds(centerX + 5, centerY + 81, 95, 20).build())

        updateVisibility()
    }

    fun updateVoice(lang: String, voice: String) {
        currentLanguage = lang
        currentVoice = voice
        voiceBtn.message = Component.translatable("gui.create_station_voices.voice", currentVoice, currentLanguage)
    }

    private fun updateVisibility() {
        tabBtn.message = Component.translatable(if (isEffectsTab) "gui.create_station_voices.tab_effects" else "gui.create_station_voices.tab_main")
        
        textBox.visible = !isEffectsTab
        voiceBtn.visible = !isEffectsTab
        
        speedSlider.visible = isEffectsTab
        volumeSlider.visible = isEffectsTab
        rangeSlider.visible = isEffectsTab
        realismSlider.visible = isEffectsTab
        reverbBtn.visible = isEffectsTab
        jingleBtn.visible = isEffectsTab
        jingleTimingBtn.visible = isEffectsTab
        jingleTimingBtn.active = !JingleManager.isOff(currentJingle)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
    }

    override fun isPauseScreen(): Boolean = false
}
