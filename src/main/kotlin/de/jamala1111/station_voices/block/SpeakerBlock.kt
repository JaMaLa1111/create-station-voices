package de.jamala.station_voices.block

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class SpeakerBlock(properties: Properties) : Block(properties), EntityBlock {
    init {
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.POWERED)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(BlockStateProperties.POWERED, false)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return SpeakerBlockEntity(pos, state)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos) as? SpeakerBlockEntity
            if (be != null) {
                be.setChanged()
                level.sendBlockUpdated(pos, state, state, 3)
                val target = be.linkedAnnouncer
                if (target != null) {
                    SpeakerManager.registerSpeaker(level, target, pos)
                }
            }
        }
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (!level.isClientSide) {
            BlockEntityTicker { level, pos, state, be ->
                if (be is SpeakerBlockEntity) {
                    be.tick(level, pos, state)
                }
            }
        } else null
    }
}
