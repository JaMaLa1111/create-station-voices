package de.jamala.station_voices.block

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class ConfigurableAnnouncerBlock(properties: Properties) : Block(properties), EntityBlock {
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
        return ConfigurableAnnouncerBlockEntity(pos, state)
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (!level.isClientSide) {
            BlockEntityTicker { _, _, _, be ->
                if (be is ConfigurableAnnouncerBlockEntity) {
                    be.tick()
                }
            }
        } else null
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) {
            val be = level.getBlockEntity(pos) as? ConfigurableAnnouncerBlockEntity
            if (be != null) {
                runForDist(
                    clientTarget = {
                        de.jamala.station_voices.client.ClientHooks.openConfigurableAnnouncerScreen(pos, be)
                    },
                    serverTarget = {}
                )
            }
        }
        return InteractionResult.SUCCESS
    }
}
