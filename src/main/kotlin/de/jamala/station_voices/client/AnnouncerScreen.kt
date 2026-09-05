package de.jamala.station_voices.client

import de.jamala.station_voices.Jingle
import de.jamala.station_voices.JingleTiming
import de.jamala.station_voices.network.SetAnnouncerDataPayload
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
) : Screen(Component.literal("Announcer Setup")) {

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
        textBox = EditBox(font, centerX - 100, centerY - 52, 200, 20, Component.literal("Text"))
        textBox.value = currentText
        textBox.setMaxLength(256)
        addRenderableWidget(textBox)

        voiceBtn = addRenderableWidget(Button.builder(Component.literal("Voice: $currentVoice ($currentLanguage)")) { _ ->
            minecraft?.setScreen(VoiceSelectionScreen(this, currentLanguage, currentVoice) { lang, voice -> updateVoice(lang, voice) })
        }.bounds(centerX - 100, centerY - 29, 200, 20).build())

        // Effects Tab
        speedSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 80, 200, 20, Component.empty(), (currentSpeed - 0.1) / (3.0 - 0.1)) {
            init { updateMessage() }
            override fun updateMessage() {
                val act = 0.1 + value * 2.9
                message = Component.literal(String.format(java.util.Locale.US, "Pitch/Speed: %.2fx", act))
            }
            override fun applyValue() {
                currentSpeed = (0.1 + value * 2.9).toFloat()
            }
        })
        
        volumeSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 57, 200, 20, Component.empty(), currentVolume.toDouble()) {
            init { updateMessage() }
            override fun updateMessage() {
                message = Component.literal(String.format(java.util.Locale.US, "Volume: %d%%", (value * 100).toInt()))
            }
            override fun applyValue() {
                currentVolume = value.toFloat()
            }
        })
        
        rangeSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 34, 200, 20, Component.empty(), (currentMaxRange - 1.0) / 63.0) {
            init { updateMessage() }
            override fun updateMessage() {
                val act = 1 + (value * 63).toInt()
                message = Component.literal("Max Range: $act blocks")
            }
            override fun applyValue() {
                currentMaxRange = 1 + (value * 63).toInt()
            }
        })

        realismSlider = addRenderableWidget(object : AbstractSliderButton(centerX - 100, centerY - 11, 200, 20, Component.empty(), currentRealism.toDouble()) {
            init { updateMessage() }
            override fun updateMessage() {
                val pct = (value * 100).toInt()
                message = Component.literal(if (pct == 0) "Realism: OFF" else "Realism: $pct%")
            }
            override fun applyValue() {
                currentRealism = value.toFloat()
            }
        })

        reverbBtn = addRenderableWidget(Button.builder(Component.literal("Reverb: ${if (currentReverb) "ON" else "OFF"}")) { btn ->
            currentReverb = !currentReverb
            btn.message = Component.literal("Reverb: ${if (currentReverb) "ON" else "OFF"}")
        }.bounds(centerX - 100, centerY + 12, 95, 20).build())

        val initJingle = Jingle.fromString(currentJingle)
        jingleBtn = addRenderableWidget(Button.builder(Component.literal("Jingle: ${initJingle.displayName}")) { btn ->
            val next = Jingle.fromString(currentJingle).next()
            currentJingle = next.id
            btn.message = Component.literal("Jingle: ${next.displayName}")
            jingleTimingBtn.active = next != Jingle.OFF
        }.bounds(centerX + 5, centerY + 12, 95, 20).build())

        jingleTimingBtn = addRenderableWidget(Button.builder(Component.literal("Jingle Timing: ${JingleTiming.fromString(currentJingleTiming).displayName}")) { btn ->
            val next = JingleTiming.fromString(currentJingleTiming).next()
            currentJingleTiming = next.id
            btn.message = Component.literal("Jingle Timing: ${next.displayName}")
        }.bounds(centerX - 100, centerY + 35, 200, 20).build())

        // Common
        genBtn = addRenderableWidget(Button.builder(Component.literal("Generate & Preview")) { _ ->
            de.jamala.station_voices.client.ClientHooks.testAL()
            val text = textBox.value
            if (text.isNotBlank()) {
                PacketDistributor.sendToServer(de.jamala.station_voices.network.RequestPreviewAudioPayload(
                    text, currentVoice, currentLanguage, currentSpeed, currentVolume, currentReverb, currentMaxRange, currentJingle, currentJingleTiming, currentRealism
                ))
            }
        }.bounds(centerX - 100, centerY + 58, 200, 20).build())

        tabBtn = addRenderableWidget(Button.builder(Component.literal("Tab: Main")) { _ ->
            isEffectsTab = !isEffectsTab
            updateVisibility()
        }.bounds(centerX - 100, centerY + 81, 95, 20).build())

        doneBtn = addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            currentText = textBox.value
            PacketDistributor.sendToServer(SetAnnouncerDataPayload(pos, currentText, currentVoice, currentLanguage, currentSpeed, currentVolume, currentReverb, currentMaxRange, currentJingle, currentJingleTiming, currentRealism))
            onClose()
        }.bounds(centerX + 5, centerY + 81, 95, 20).build())

        updateVisibility()
    }

    fun updateVoice(lang: String, voice: String) {
        currentLanguage = lang
        currentVoice = voice
        voiceBtn.message = Component.literal("Voice: $currentVoice ($currentLanguage)")
    }

    private fun updateVisibility() {
        tabBtn.message = Component.literal(if (isEffectsTab) "Tab: Effects" else "Tab: Main")
        
        textBox.visible = !isEffectsTab
        voiceBtn.visible = !isEffectsTab
        
        speedSlider.visible = isEffectsTab
        volumeSlider.visible = isEffectsTab
        rangeSlider.visible = isEffectsTab
        realismSlider.visible = isEffectsTab
        reverbBtn.visible = isEffectsTab
        jingleBtn.visible = isEffectsTab
        jingleTimingBtn.visible = isEffectsTab
        jingleTimingBtn.active = Jingle.fromString(currentJingle) != Jingle.OFF
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
    }

    override fun isPauseScreen(): Boolean = false
}
