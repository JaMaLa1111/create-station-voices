package de.jamala.station_voices.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simibubi.create.Create
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import com.simibubi.create.content.trains.graph.EdgePointType
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction
import de.jamala.station_voices.CreateStationVoices
import de.jamala.station_voices.block.TrainAnnouncerBlockEntity
import de.jamala.station_voices.block.TrainAnnouncerMovementData
import de.jamala.station_voices.block.TrainProfile
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

data class OpenTrainAnnouncerScreenPayload(
    val entityId: Int?,
    val pos: BlockPos?,
    val localPos: BlockPos?,
    val profilesJson: String
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<OpenTrainAnnouncerScreenPayload>(
            ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "open_train_announcer_screen")
        )
        val STREAM_CODEC = StreamCodec.ofMember(OpenTrainAnnouncerScreenPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): OpenTrainAnnouncerScreenPayload {
            val hasEntity = buf.readBoolean()
            val entityId = if (hasEntity) buf.readVarInt() else null
            val hasPos = buf.readBoolean()
            val pos = if (hasPos) buf.readBlockPos() else null
            val hasLocalPos = buf.readBoolean()
            val localPos = if (hasLocalPos) buf.readBlockPos() else null
            val profilesJson = buf.readUtf()
            return OpenTrainAnnouncerScreenPayload(entityId, pos, localPos, profilesJson)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(entityId != null)
        if (entityId != null) buf.writeVarInt(entityId)
        buf.writeBoolean(pos != null)
        if (pos != null) buf.writeBlockPos(pos)
        buf.writeBoolean(localPos != null)
        if (localPos != null) buf.writeBlockPos(localPos)
        buf.writeUtf(profilesJson)
    }
}

data class SaveTrainAnnouncerProfilesPayload(
    val pos: BlockPos?,
    val entityId: Int?,
    val localPos: BlockPos?,
    val profilesJson: String
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SaveTrainAnnouncerProfilesPayload>(
            ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "save_train_announcer_profiles")
        )
        val STREAM_CODEC = StreamCodec.ofMember(SaveTrainAnnouncerProfilesPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): SaveTrainAnnouncerProfilesPayload {
            val hasPos = buf.readBoolean()
            val pos = if (hasPos) buf.readBlockPos() else null
            val hasEntity = buf.readBoolean()
            val entityId = if (hasEntity) buf.readVarInt() else null
            val hasLocalPos = buf.readBoolean()
            val localPos = if (hasLocalPos) buf.readBlockPos() else null
            val profilesJson = buf.readUtf()
            return SaveTrainAnnouncerProfilesPayload(pos, entityId, localPos, profilesJson)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(pos != null)
        if (pos != null) buf.writeBlockPos(pos)
        buf.writeBoolean(entityId != null)
        if (entityId != null) buf.writeVarInt(entityId)
        buf.writeBoolean(localPos != null)
        if (localPos != null) buf.writeBlockPos(localPos)
        buf.writeUtf(profilesJson)
    }
}

data class AutoFetchStationsRequestPayload(
    val pos: BlockPos?,
    val entityId: Int?,
    val localPos: BlockPos?
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AutoFetchStationsRequestPayload>(
            ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "auto_fetch_stations_request")
        )
        val STREAM_CODEC = StreamCodec.ofMember(AutoFetchStationsRequestPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): AutoFetchStationsRequestPayload {
            val hasPos = buf.readBoolean()
            val pos = if (hasPos) buf.readBlockPos() else null
            val hasEntity = buf.readBoolean()
            val entityId = if (hasEntity) buf.readVarInt() else null
            val hasLocalPos = buf.readBoolean()
            val localPos = if (hasLocalPos) buf.readBlockPos() else null
            return AutoFetchStationsRequestPayload(pos, entityId, localPos)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(pos != null)
        if (pos != null) buf.writeBlockPos(pos)
        buf.writeBoolean(entityId != null)
        if (entityId != null) buf.writeVarInt(entityId)
        buf.writeBoolean(localPos != null)
        if (localPos != null) buf.writeBlockPos(localPos)
    }
}

data class AutoFetchStationsResponsePayload(
    val stationNames: List<String>
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AutoFetchStationsResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "auto_fetch_stations_response")
        )
        val STREAM_CODEC = StreamCodec.ofMember(AutoFetchStationsResponsePayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): AutoFetchStationsResponsePayload {
            val size = buf.readInt()
            val list = mutableListOf<String>()
            for (i in 0 until size) list.add(buf.readUtf())
            return AutoFetchStationsResponsePayload(list)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeInt(stationNames.size)
        for (name in stationNames) {
            buf.writeUtf(name)
        }
    }
}

object ModNetworkingTrainAnnouncer {
    private val gson = Gson()

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CreateStationVoices.ID)

        registrar.playToClient(
            OpenTrainAnnouncerScreenPayload.ID,
            OpenTrainAnnouncerScreenPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        val type = object : TypeToken<MutableMap<String, TrainProfile>>() {}.type
                        val profiles: MutableMap<String, TrainProfile> = try {
                            gson.fromJson(payload.profilesJson, type) ?: mutableMapOf()
                        } catch (e: Exception) {
                            mutableMapOf()
                        }
                        de.jamala.station_voices.client.ClientHooks.openTrainAnnouncerScreen(
                            payload.entityId,
                            payload.pos,
                            payload.localPos,
                            profiles
                        )
                    }
                }
            }
        }

        registrar.playToServer(
            SaveTrainAnnouncerProfilesPayload.ID,
            SaveTrainAnnouncerProfilesPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val level = player.serverLevel()

                val type = object : TypeToken<MutableMap<String, TrainProfile>>() {}.type
                val rawMap: MutableMap<String, TrainProfile> = try {
                    gson.fromJson(payload.profilesJson, type) ?: mutableMapOf()
                } catch (e: Exception) {
                    mutableMapOf()
                }
                val map = mutableMapOf<String, TrainProfile>()
                for ((key, profile) in rawMap) {
                    val sanitizedKey = de.jamala.station_voices.TextSanitizer.sanitizeLabel(key)
                    if (sanitizedKey.isNotBlank()) {
                        profile.text = de.jamala.station_voices.TextSanitizer.sanitizeTemplate(profile.text, profile.language)
                        map[sanitizedKey] = profile
                    }
                }
                val sanitizedJson = gson.toJson(map)

                if (payload.pos != null) {
                    val pos = payload.pos
                    if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) < 64) {
                        val be = level.getBlockEntity(pos) as? TrainAnnouncerBlockEntity
                        if (be != null) {
                            be.profiles = map
                            be.setChanged()
                            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
                        }
                    }
                } else if (payload.entityId != null && payload.localPos != null) {
                    val entity = level.getEntity(payload.entityId) as? CarriageContraptionEntity ?: return@enqueueWork
                    if (player.distanceToSqr(entity) < 128) {
                        val contraption = entity.contraption
                        val localPos = payload.localPos
                        val blockInfo = contraption.getBlocks()[localPos]
                        if (blockInfo != null) {
                            var tag = blockInfo.nbt()
                            if (tag == null) {
                                tag = CompoundTag()
                                val newInfo = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                                    blockInfo.pos(),
                                    blockInfo.state(),
                                    tag
                                )
                                entity.setBlock(localPos, newInfo)
                            }
                            tag.putString("StationProfiles", sanitizedJson)

                            for (actor in contraption.actors) {
                                val ctx = actor.right
                                if (ctx != null && ctx.localPos == localPos) {
                                    ctx.blockEntityData?.putString("StationProfiles", sanitizedJson)
                                    val movementData = ctx.temporaryData as? TrainAnnouncerMovementData
                                    if (movementData != null) {
                                        movementData.profiles = map
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        registrar.playToServer(
            AutoFetchStationsRequestPayload.ID,
            AutoFetchStationsRequestPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val level = player.serverLevel()
                val stationNames = mutableSetOf<String>()

                if (payload.entityId != null) {
                    val entity = level.getEntity(payload.entityId) as? CarriageContraptionEntity
                    val train = entity?.carriage?.train
                    if (train != null) {
                        val schedule = train.runtime?.schedule
                        if (schedule != null) {
                            for (entry in schedule.entries) {
                                val instruction = entry.instruction
                                if (instruction is DestinationInstruction) {
                                    val filter = instruction.filter
                                    if (filter.isNotBlank()) {
                                        stationNames.add(filter)
                                    }
                                }
                            }
                        }
                        val graph = train.graph
                        if (graph != null) {
                            val stations = graph.getPoints(EdgePointType.STATION)
                            for (station in stations) {
                                if (station.name.isNotBlank()) {
                                    stationNames.add(station.name)
                                }
                            }
                        }
                    }
                }

                if (stationNames.isEmpty()) {
                    for (train in Create.RAILWAYS.trains.values) {
                        val graph = train.graph ?: continue
                        for (station in graph.getPoints(EdgePointType.STATION)) {
                            if (station.name.isNotBlank()) stationNames.add(station.name)
                        }
                    }
                    for (graph in Create.RAILWAYS.trackNetworks.values) {
                        for (station in graph.getPoints(EdgePointType.STATION)) {
                            if (station.name.isNotBlank()) stationNames.add(station.name)
                        }
                    }
                }

                PacketDistributor.sendToPlayer(player, AutoFetchStationsResponsePayload(stationNames.toList().sorted()))
            }
        }

        registrar.playToClient(
            AutoFetchStationsResponsePayload.ID,
            AutoFetchStationsResponsePayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.station_voices.client.ClientHooks.handleAutoFetchStationsResponse(payload.stationNames)
                    }
                }
            }
        }
    }
}
