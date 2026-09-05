package de.jamala.station_voices.block.display

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity
import com.simibubi.create.content.trains.display.FlapDisplayLayout
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder
import de.jamala.station_voices.block.IAnnouncerSource
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

class AnnouncementDisplaySource : SingleLineDisplaySource() {

    private fun getDisplayText(context: DisplayLinkContext): String? {
        val be = context.sourceBlockEntity as? IAnnouncerSource
        val mode = context.sourceConfig().getInt("DisplayMode")
        return AnnouncementDisplayLogic.getDisplayText(be, mode)
    }

    override fun provideLine(context: DisplayLinkContext, stats: DisplayTargetStats): MutableComponent {
        val text = getDisplayText(context) ?: return EMPTY_LINE
        val firstLine = if (text.contains("\n")) text.substringBefore("\n") else text
        return Component.literal(firstLine)
    }

    override fun provideText(context: DisplayLinkContext, stats: DisplayTargetStats): List<MutableComponent> {
        val rawText = getDisplayText(context) ?: return EMPTY
        val lines = AnnouncementDisplayLogic.splitLines(rawText)
        val label = if (allowsLabeling(context)) context.sourceConfig().getString("Label") else ""

        return lines.mapIndexed { index, lineStr ->
            var comp: MutableComponent = Component.literal(lineStr)
            if (index == 0 && label.isNotEmpty()) {
                comp = Component.literal("$label ").append(comp)
            }
            comp
        }
    }

    override fun provideFlapDisplayText(context: DisplayLinkContext, stats: DisplayTargetStats): List<List<MutableComponent>> {
        val rawText = getDisplayText(context) ?: return super.provideFlapDisplayText(context, stats)
        val lines = AnnouncementDisplayLogic.splitLines(rawText)
        val label = if (allowsLabeling(context)) context.sourceConfig().getString("Label") else ""

        if (lines.size <= 1) {
            val line = provideLine(context, stats)
            if (label.isNotEmpty()) {
                return listOf(listOf(Component.literal("$label "), line))
            }
            return listOf(listOf(line))
        }

        return lines.mapIndexed { index, lineStr ->
            if (index == 0 && label.isNotEmpty()) {
                listOf(Component.literal("$label "), Component.literal(lineStr))
            } else {
                listOf(Component.literal(lineStr))
            }
        }
    }

    override fun loadFlapDisplayLayout(
        context: DisplayLinkContext,
        flapDisplay: FlapDisplayBlockEntity,
        layout: FlapDisplayLayout,
        lineIndex: Int
    ) {
        if (lineIndex == 0) {
            super.loadFlapDisplayLayout(context, flapDisplay, layout, lineIndex)
        } else {
            if (!layout.isLayout("Default")) {
                layout.loadDefault(flapDisplay.maxCharCount)
            }
        }
    }

    override fun allowsLabeling(context: DisplayLinkContext): Boolean = true

    override fun getTranslationKey(): String = "announcement"

    @OnlyIn(Dist.CLIENT)
    override fun initConfigurationWidgets(context: DisplayLinkContext, builder: ModularGuiLineBuilder, isFirstLine: Boolean) {
        super.initConfigurationWidgets(context, builder, isFirstLine)
        if (isFirstLine) return

        builder.addSelectionScrollInput(0, 120, { si, _ ->
            si.forOptions(listOf(
                Component.translatable("create_station_voices.display_source.announcement.while_playing"),
                Component.translatable("create_station_voices.display_source.announcement.keep_displayed")
            )).titled(Component.translatable("create_station_voices.display_source.announcement.display_mode"))
        }, "DisplayMode")
    }

    override fun populateData(context: DisplayLinkContext) {
        val conf = context.sourceConfig()
        if (!conf.contains("DisplayMode")) {
            conf.putInt("DisplayMode", 0)
        }
    }
}
