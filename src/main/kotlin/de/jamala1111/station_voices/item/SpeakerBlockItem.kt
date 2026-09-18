package de.jamala1111.station_voices.item

import de.jamala1111.station_voices.block.AnnouncerBlockEntity
import de.jamala1111.station_voices.block.ConfigurableAnnouncerBlockEntity
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

                val typeComp = if (be is AnnouncerBlockEntity) {
                    Component.translatable("block.create_station_voices.announcer_block")
                } else {
                    Component.translatable("block.create_station_voices.configurable_announcer_block")
                }
                player?.displayClientMessage(Component.translatable("gui.create_station_voices.item.linked_to_coords", typeComp, pos.x, pos.y, pos.z), true)
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
            tooltipComponents.add(Component.translatable("gui.create_station_voices.item.speaker.linked", x, y, z).withStyle(net.minecraft.ChatFormatting.GOLD))
        } else {
            tooltipComponents.add(Component.translatable("gui.create_station_voices.item.speaker.not_linked").withStyle(net.minecraft.ChatFormatting.GRAY))
        }
    }
}
