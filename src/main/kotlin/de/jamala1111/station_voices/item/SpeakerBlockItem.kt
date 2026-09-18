package de.jamala.station_voices.item

import de.jamala.station_voices.block.AnnouncerBlockEntity
import de.jamala.station_voices.block.ConfigurableAnnouncerBlockEntity
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block

class SpeakerBlockItem(block: Block, properties: Item.Properties) : BlockItem(block, properties) {

    override fun onItemUseFirst(stack: ItemStack, context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val player = context.player

        val be = level.getBlockEntity(pos)
        if (be is AnnouncerBlockEntity || be is ConfigurableAnnouncerBlockEntity) {
            if (!level.isClientSide) {
                val customData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)
                val tag = customData.copyTag()

                val announcerTag = CompoundTag()
                announcerTag.putInt("X", pos.x)
                announcerTag.putInt("Y", pos.y)
                announcerTag.putInt("Z", pos.z)
                tag.put("LinkedAnnouncer", announcerTag)
                tag.putString("id", "create_station_voices:speaker_block")

                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag))

                val type = if (be is AnnouncerBlockEntity) "Redstone Announcer" else "Configurable Train Announcer"
                player?.displayClientMessage(Component.literal("Linked to $type at ${pos.x}, ${pos.y}, ${pos.z}"), true)
            }
            return InteractionResult.SUCCESS
        }

        return super.onItemUseFirst(stack, context)
    }

    override fun appendHoverText(stack: ItemStack, context: Item.TooltipContext, tooltipComponents: MutableList<Component>, tooltipFlag: TooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
        val customData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)
        val tag = customData.copyTag()
        if (tag.contains("LinkedAnnouncer")) {
            val announcerTag = tag.getCompound("LinkedAnnouncer")
            val x = announcerTag.getInt("X")
            val y = announcerTag.getInt("Y")
            val z = announcerTag.getInt("Z")
            tooltipComponents.add(Component.literal("Linked to Announcer at $x, $y, $z").withStyle(net.minecraft.ChatFormatting.GOLD))
        } else {
            tooltipComponents.add(Component.literal("Not linked (Right-click an Announcer to link)").withStyle(net.minecraft.ChatFormatting.GRAY))
        }
    }
}
