package de.jamala.station_voices.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object SpeakerManager {
    private val speakersByDimension = ConcurrentHashMap<String, ConcurrentHashMap<BlockPos, CopyOnWriteArraySet<BlockPos>>>()

    fun registerSpeaker(dimKey: String, announcerPos: BlockPos, speakerPos: BlockPos) {
        val dimMap = speakersByDimension.computeIfAbsent(dimKey) { ConcurrentHashMap() }
        val speakerSet = dimMap.computeIfAbsent(announcerPos) { CopyOnWriteArraySet() }
        speakerSet.add(speakerPos)
    }

    fun registerSpeaker(level: Level, announcerPos: BlockPos, speakerPos: BlockPos) {
        registerSpeaker(getDimKey(level), announcerPos, speakerPos)
    }

    fun unregisterSpeaker(dimKey: String, announcerPos: BlockPos, speakerPos: BlockPos) {
        val dimMap = speakersByDimension[dimKey] ?: return
        val speakerSet = dimMap[announcerPos] ?: return
        speakerSet.remove(speakerPos)
        if (speakerSet.isEmpty()) {
            dimMap.remove(announcerPos)
        }
    }

    fun unregisterSpeaker(level: Level, announcerPos: BlockPos, speakerPos: BlockPos) {
        unregisterSpeaker(getDimKey(level), announcerPos, speakerPos)
    }

    fun getSpeakersFor(dimKey: String, announcerPos: BlockPos): Set<BlockPos> {
        val dimMap = speakersByDimension[dimKey] ?: return emptySet()
        val speakerSet = dimMap[announcerPos] ?: return emptySet()
        return speakerSet
    }

    fun getSpeakersFor(level: Level, announcerPos: BlockPos): Set<BlockPos> {
        return getSpeakersFor(getDimKey(level), announcerPos)
    }

    fun clear() {
        speakersByDimension.clear()
    }

    private fun getDimKey(level: Level): String {
        return level.dimension().location().toString()
    }
}
