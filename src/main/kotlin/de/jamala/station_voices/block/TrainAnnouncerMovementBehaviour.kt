package de.jamala.station_voices.block

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.trains.entity.CarriageContraption
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import com.simibubi.create.content.trains.entity.Train
import de.jamala.station_voices.JingleManager
import de.jamala.station_voices.JingleTiming
import de.jamala.station_voices.ModConfig
import de.jamala.station_voices.network.PlayAnnouncerAudioDataChunkPayload
import de.jamala.station_voices.network.PlayAnnouncerAudioPayload
import de.jamala.station_voices.server.PiperManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

class TrainAnnouncerMovementData(
    var profiles: MutableMap<String, TrainProfile> = mutableMapOf(),
    var lastStationId: UUID? = null,
    var isPlaying: Boolean = false,
    var audioEndTimeMillis: Long = 0L,
    var lastAnnouncementText: String = "",
    var initialized: Boolean = false
)

class TrainAnnouncerMovementBehaviour : MovementBehaviour {

    override fun tick(context: MovementContext) {
        val level = context.world as? ServerLevel ?: return
        if (context.contraption !is CarriageContraption) return
        val cce = context.contraption.entity as? CarriageContraptionEntity ?: return
        val carriage = cce.carriage ?: return
        val train = carriage.train ?: return

        if (context.temporaryData !is TrainAnnouncerMovementData) {
            val data = TrainAnnouncerMovementData()
            val tag = context.blockEntityData
            if (tag != null) {
                val json = when {
                    tag.contains("StationProfiles") -> tag.getString("StationProfiles")
                    tag.contains("TrainProfiles") -> tag.getString("TrainProfiles")
                    else -> null
                }
                if (json != null) {
                    val type = object : TypeToken<MutableMap<String, TrainProfile>>() {}.type
                    try {
                        val map: MutableMap<String, TrainProfile>? = Gson().fromJson(json, type)
                        if (map != null) {
                            for ((_, p) in map) {
                                if (p.jingleTiming.isNullOrBlank()) {
                                    p.jingleTiming = "BOTH"
                                }
                            }
                            data.profiles = map
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            // Do not immediately play arrival announcement on fresh assembly if already at a station
            data.lastStationId = train.currentStation
            data.initialized = true
            context.temporaryData = data
        }

        val data = context.temporaryData as TrainAnnouncerMovementData

        if (data.isPlaying) {
            if (System.currentTimeMillis() >= data.audioEndTimeMillis) {
                data.isPlaying = false
            }
        }

        val currentStation = train.getCurrentStation()
        if (currentStation != null) {
            if (data.lastStationId != currentStation.id) {
                data.lastStationId = currentStation.id
                playStationAnnouncement(level, context, cce, train, data, currentStation.name)
            }
        } else {
            data.lastStationId = null
        }
    }

    private fun playStationAnnouncement(
        level: ServerLevel,
        context: MovementContext,
        cce: CarriageContraptionEntity,
        train: Train,
        data: TrainAnnouncerMovementData,
        stationName: String
    ) {
        val profile = findProfile(data.profiles, stationName) ?: return
        if (profile.text.isBlank()) return

        data.isPlaying = true
        data.lastAnnouncementText = profile.text

        if (ModConfig.SERVER.ttsMode.get() == ModConfig.TtsMode.LOCAL_PIPER) {
            CoroutineScope(Dispatchers.IO).launch {
                val audioData = PiperManager.generateAudio(profile.text, profile.voice, profile.language)
                if (audioData != null) {
                    val durationMs = calculateWavDurationMs(audioData, profile.speed, profile.reverb, profile.jingle, profile.jingleTiming)
                    data.audioEndTimeMillis = System.currentTimeMillis() + durationMs

                    val streamId = UUID.randomUUID()
                    val chunkSize = 30000
                    val totalChunks = Math.ceil(audioData.size.toDouble() / chunkSize.toDouble()).toInt()

                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = Math.min(start + chunkSize, audioData.size)
                        val chunk = audioData.copyOfRange(start, end)

                        val chunkPayload = PlayAnnouncerAudioDataChunkPayload(
                            null,
                            profile.speed,
                            profile.volume,
                            profile.reverb,
                            profile.maxRange,
                            profile.jingle,
                            profile.jingleTiming,
                            profile.realism,
                            streamId,
                            i,
                            totalChunks,
                            chunk,
                            cce.id
                        )

                        if (profile.contraptionOnly) {
                            val trainPlayers = getPlayersOnTrain(level, train)
                            for (p in trainPlayers) {
                                PacketDistributor.sendToPlayer(p, chunkPayload)
                            }
                        } else {
                            PacketDistributor.sendToPlayersNear(
                                level,
                                null,
                                cce.x,
                                cce.y,
                                cce.z,
                                profile.maxRange.toDouble().coerceAtLeast(64.0),
                                chunkPayload
                            )

                            val trainPlayers = getPlayersOnTrain(level, train)
                            for (p in trainPlayers) {
                                PacketDistributor.sendToPlayer(p, chunkPayload)
                            }
                        }
                    }
                } else {
                    data.isPlaying = false
                }
            }
        } else {
            val timing = JingleTiming.fromString(profile.jingleTiming)
            val jingleDurationMs = JingleManager.getJingleDuration(profile.jingle, profile.speed, timing)
            val estimatedDurationMs = (profile.text.length * 120L / profile.speed.coerceAtLeast(0.1f).toDouble()).toLong() + 2500L + (if (profile.reverb) 900L else 0L) + jingleDurationMs
            data.audioEndTimeMillis = System.currentTimeMillis() + estimatedDurationMs

            val payload = PlayAnnouncerAudioPayload(
                null,
                profile.text,
                profile.voice,
                profile.language,
                profile.speed,
                profile.volume,
                profile.reverb,
                profile.maxRange,
                profile.jingle,
                profile.jingleTiming,
                profile.realism,
                cce.id
            )

            if (profile.contraptionOnly) {
                val trainPlayers = getPlayersOnTrain(level, train)
                for (p in trainPlayers) {
                    PacketDistributor.sendToPlayer(p, payload)
                }
            } else {
                PacketDistributor.sendToPlayersNear(
                    level,
                    null,
                    cce.x,
                    cce.y,
                    cce.z,
                    profile.maxRange.toDouble().coerceAtLeast(64.0),
                    payload
                )

                val trainPlayers = getPlayersOnTrain(level, train)
                for (p in trainPlayers) {
                    PacketDistributor.sendToPlayer(p, payload)
                }
            }
        }
    }

    private fun isPlayerOnTrain(player: ServerPlayer, train: Train): Boolean {
        for (carriage in train.carriages) {
            val ce = carriage.anyAvailableEntity() ?: continue
            if (ce.level() != player.level()) continue

            // 1. Seated
            if (ce.passengers.contains(player) || player.vehicle == ce || player.rootVehicle == ce) {
                return true
            }

            // 2. Standing on collision
            if (ce.collidingEntities.containsKey(player)) {
                return true
            }

            // 3. Inside entity bounding box (with vertical leeway)
            if (ce.boundingBox.inflate(0.5, 1.0, 0.5).contains(player.position())) {
                return true
            }

            // 4. Inside contraption local block volume
            val contraption = ce.contraption
            if (contraption?.bounds != null) {
                val localPos = ce.toLocalVector(player.position(), 0f)
                if (contraption.bounds.inflate(0.5, 1.0, 0.5).contains(localPos)) {
                    return true
                }
            }
        }
        return false
    }

    private fun getPlayersOnTrain(level: ServerLevel, train: Train): Set<ServerPlayer> {
        val trainPlayers = mutableSetOf<ServerPlayer>()
        for (player in level.players()) {
            if (isPlayerOnTrain(player, train)) {
                trainPlayers.add(player)
            }
        }
        return trainPlayers
    }

    private fun findProfile(profiles: Map<String, TrainProfile>, stationName: String): TrainProfile? {
        profiles[stationName]?.let { return it }
        profiles.entries.find { it.key.equals(stationName, ignoreCase = true) }?.value?.let { return it }
        for ((key, profile) in profiles) {
            try {
                if (stationName.matches(Regex(key, RegexOption.IGNORE_CASE))) {
                    return profile
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun calculateWavDurationMs(audioData: ByteArray, speed: Float, reverb: Boolean, jingle: String, jingleTiming: String = "BOTH"): Long {
        try {
            val bais = java.io.ByteArrayInputStream(audioData)
            val audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(bais)
            val format = audioIn.format
            val frameLength = audioData.size.toLong() / format.frameSize
            val durationSeconds = frameLength.toDouble() / format.frameRate.toDouble()
            val speedFactor = if (speed > 0.01f) speed.toDouble() else 1.0
            val adjustedMs = ((durationSeconds / speedFactor) * 1000.0).toLong()
            val reverbTailMs = if (reverb) 900L else 0L
            val timing = JingleTiming.fromString(jingleTiming)
            val jingleMs = JingleManager.getJingleDuration(jingle, speed, timing)
            return adjustedMs + reverbTailMs + jingleMs
        } catch (e: Exception) {
            return 2000L
        }
    }
}
