package de.jamala.station_voices.block

import com.simibubi.create.content.trains.station.StationBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

object TrainStationUtils {
    fun getAdjacentStationName(level: Level, pos: BlockPos): String? {
        for (dir in Direction.values()) {
            val be = level.getBlockEntity(pos.relative(dir))
            if (be is StationBlockEntity) {
                val station = be.station
                if (station != null && station.name.isNotBlank()) {
                    return station.name
                }
            }
        }
        return null
    }
}
