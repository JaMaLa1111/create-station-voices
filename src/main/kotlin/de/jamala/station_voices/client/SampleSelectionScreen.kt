package de.jamala.station_voices.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class SampleSelectionScreen(val parent: Screen, val samples: Map<String, String>, val onSampleSelected: (String) -> Unit) : Screen(Component.literal("Select Sample")) {
    private lateinit var sampleList: StringList
    private var selectedSample: String? = null

    override fun init() {
        val centerX = width / 2
        val listWidth = 120

        sampleList = StringList(minecraft!!, listWidth, height, 40, 36)
        sampleList.setX(centerX - listWidth / 2)
        addRenderableWidget(sampleList)

        sampleList.updateItems(samples.keys.toList())
        if (samples.isNotEmpty()) {
            selectedSample = samples.keys.first()
            sampleList.selectItem(selectedSample!!)
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel")) { _ ->
            minecraft?.setScreen(parent)
        }.bounds(centerX - 105, height - 30, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Play")) { _ ->
            if (selectedSample != null) {
                onSampleSelected(samples[selectedSample!!]!!)
            }
            minecraft?.setScreen(parent)
        }.bounds(centerX + 5, height - 30, 100, 20).build())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
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
                selectedSample = entry.text
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
                guiGraphics.drawString(minecraft!!.font, "Sample $text", left + 5, top + 2, color)
            }
            
            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                setSelected(this)
                return true
            }

            override fun getNarration(): Component = Component.literal(text)
        }
    }
}
