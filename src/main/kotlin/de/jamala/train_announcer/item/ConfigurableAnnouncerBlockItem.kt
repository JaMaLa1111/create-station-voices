package de.jamala.train_announcer.item

import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity
import de.jamala.train_announcer.block.ModBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block

class ConfigurableAnnouncerBlockItem(block: Block, properties: Item.Properties) : BlockItem(block, properties) {
    
    override fun onItemUseFirst(stack: net.minecraft.world.item.ItemStack, context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val player = context.player
        
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos)
            if (be is StationBlockEntity || be is TrackObserverBlockEntity) {
                val customData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)
                val tag = customData.copyTag()
                
                val stationTag = net.minecraft.nbt.CompoundTag()
                stationTag.putInt("X", pos.x)
                stationTag.putInt("Y", pos.y)
                stationTag.putInt("Z", pos.z)
                tag.put("LinkedStation", stationTag)
                // Add the id so the game knows it's a valid BlockEntity tag
                tag.putString("id", "train_announcer:configurable_announcer_block")
                
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag))
                
                val type = if (be is StationBlockEntity) "Station" else "Observer"
                player?.displayClientMessage(Component.literal("Linked to $type at ${pos.x}, ${pos.y}, ${pos.z}"), true)
                return InteractionResult.SUCCESS
            }
        } else {
            // Client side needs to return SUCCESS to prevent block placement packet
            val be = level.getBlockEntity(pos)
            if (be is StationBlockEntity || be is TrackObserverBlockEntity) {
                return InteractionResult.SUCCESS
            }
        }
        
        return super.onItemUseFirst(stack, context)
    }
}
