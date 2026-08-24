package de.jamala.station_voices.network

import de.jamala.station_voices.CreateStationVoices
import de.jamala.station_voices.block.ConfigurableAnnouncerBlockEntity
import de.jamala.station_voices.block.TrainProfile
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.PacketDistributor
import com.simibubi.create.content.trains.station.StationBlockEntity
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction
import net.minecraft.server.level.ServerPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import de.jamala.station_voices.server.PiperManager

data class SetConfigurableAnnouncerDataPayload(
    val pos: BlockPos,
    val trainName: String,
    val text: String,
    val voice: String,
    val language: String,
    val speed: Float,
    val volume: Float,
    val reverb: Boolean,
    val maxRange: Int
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetConfigurableAnnouncerDataPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "set_conf_announcer_data"))
        val STREAM_CODEC = StreamCodec.ofMember(SetConfigurableAnnouncerDataPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): SetConfigurableAnnouncerDataPayload {
            return SetConfigurableAnnouncerDataPayload(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readInt()
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeUtf(trainName)
        buf.writeUtf(text)
        buf.writeUtf(voice)
        buf.writeUtf(language)
        buf.writeFloat(speed)
        buf.writeFloat(volume)
        buf.writeBoolean(reverb)
        buf.writeInt(maxRange)
    }
}

data class RequestInstalledModelsPayload(val dummy: Boolean = false) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<RequestInstalledModelsPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "request_installed_models"))
        val STREAM_CODEC = StreamCodec.ofMember(RequestInstalledModelsPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = RequestInstalledModelsPayload(buf.readBoolean())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) = buf.writeBoolean(dummy)
}

data class InstalledModelsPayload(val models: List<String>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<InstalledModelsPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "installed_models"))
        val STREAM_CODEC = StreamCodec.ofMember(InstalledModelsPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf): InstalledModelsPayload {
            val size = buf.readInt()
            val list = mutableListOf<String>()
            for (i in 0 until size) list.add(buf.readUtf())
            return InstalledModelsPayload(list)
        }
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeInt(models.size)
        models.forEach { buf.writeUtf(it) }
    }
}

data class DownloadModelPayload(val language: String, val voice: String, val onnxUrl: String, val jsonUrl: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DownloadModelPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "download_model"))
        val STREAM_CODEC = StreamCodec.ofMember(DownloadModelPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = DownloadModelPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(language)
        buf.writeUtf(voice)
        buf.writeUtf(onnxUrl)
        buf.writeUtf(jsonUrl)
    }
}

data class AutoFetchTrainsRequestPayload(
    val pos: BlockPos
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AutoFetchTrainsRequestPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "auto_fetch_request"))
        val STREAM_CODEC = StreamCodec.ofMember(AutoFetchTrainsRequestPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): AutoFetchTrainsRequestPayload {
            return AutoFetchTrainsRequestPayload(buf.readBlockPos())
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
    }
}

data class AutoFetchTrainsResponsePayload(
    val trainNames: List<String>
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AutoFetchTrainsResponsePayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "auto_fetch_response"))
        val STREAM_CODEC = StreamCodec.ofMember(AutoFetchTrainsResponsePayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): AutoFetchTrainsResponsePayload {
            val size = buf.readInt()
            val list = mutableListOf<String>()
            for (i in 0 until size) {
                list.add(buf.readUtf())
            }
            return AutoFetchTrainsResponsePayload(list)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeInt(trainNames.size)
        for (name in trainNames) {
            buf.writeUtf(name)
        }
    }
}

data class OpenModelDownloadScreenPayload(val dummy: Boolean = false) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<OpenModelDownloadScreenPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "open_model_download"))
        val STREAM_CODEC = StreamCodec.ofMember(OpenModelDownloadScreenPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = OpenModelDownloadScreenPayload(buf.readBoolean())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) = buf.writeBoolean(dummy)
}

data class DownloadModelProgressPayload(val language: String, val voice: String, val progress: Float) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DownloadModelProgressPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "download_model_progress"))
        val STREAM_CODEC = StreamCodec.ofMember(DownloadModelProgressPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = DownloadModelProgressPayload(buf.readUtf(), buf.readUtf(), buf.readFloat())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(language)
        buf.writeUtf(voice)
        buf.writeFloat(progress)
    }
}

object ModNetworkingConfigurable {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CreateStationVoices.ID)

        registrar.playToServer(
            SetConfigurableAnnouncerDataPayload.ID,
            SetConfigurableAnnouncerDataPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player()
                val level = player.level()
                val pos = payload.pos
                
                if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) < 64) {
                    val blockEntity = level.getBlockEntity(pos)
                    if (blockEntity is ConfigurableAnnouncerBlockEntity) {
                        if (payload.text.isBlank()) {
                            blockEntity.profiles.remove(payload.trainName)
                        } else {
                            blockEntity.profiles[payload.trainName] = TrainProfile(
                                payload.text,
                                payload.voice,
                                payload.language,
                                payload.speed,
                                payload.volume,
                                payload.reverb,
                                payload.maxRange
                            )
                        }
                        blockEntity.setChanged()
                        level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, 3)
                    }
                }
            }
        }

        registrar.playToServer(
            RequestInstalledModelsPayload.ID,
            RequestInstalledModelsPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                CoroutineScope(Dispatchers.IO).launch {
                    val models = PiperManager.getInstalledModels()
                    PacketDistributor.sendToPlayer(player, InstalledModelsPayload(models))
                }
            }
        }

        registrar.playToServer(
            DownloadModelPayload.ID,
            DownloadModelPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer
                CoroutineScope(Dispatchers.IO).launch {
                    PiperManager.downloadModel(payload.language, payload.voice, payload.onnxUrl, payload.jsonUrl) { progress ->
                        if (player != null) {
                            PacketDistributor.sendToPlayer(player, DownloadModelProgressPayload(payload.language, payload.voice, progress))
                        }
                    }
                    if (player != null) {
                        val models = PiperManager.getInstalledModels()
                        PacketDistributor.sendToPlayer(player, InstalledModelsPayload(models))
                    }
                }
            }
        }

        registrar.playToClient(
            DownloadModelProgressPayload.ID,
            DownloadModelProgressPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.station_voices.client.ClientHooks.handleModelProgress(payload.language, payload.voice, payload.progress)
                    }
                }
            }
        }

        registrar.playToClient(
            InstalledModelsPayload.ID,
            InstalledModelsPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.station_voices.client.ClientHooks.handleInstalledModels(payload.models)
                    }
                }
            }
        }

        registrar.playToClient(
            OpenModelDownloadScreenPayload.ID,
            OpenModelDownloadScreenPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.station_voices.client.ClientHooks.openModelDownloadScreen()
                    }
                }
            }
        }

        registrar.playToServer(
            AutoFetchTrainsRequestPayload.ID,
            AutoFetchTrainsRequestPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val level = player.level()
                val pos = payload.pos
                
                if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) < 64) {
                    val be = level.getBlockEntity(pos) as? ConfigurableAnnouncerBlockEntity ?: return@enqueueWork
                    val targetPos = be.targetStation ?: return@enqueueWork
                    val targetBe = level.getBlockEntity(targetPos) as? StationBlockEntity ?: return@enqueueWork
                    
                    val station = targetBe.station ?: return@enqueueWork
                    val stationName = station.name ?: return@enqueueWork

                    val foundTrains = mutableSetOf<String>()
                    
                    val presentTrain = station.presentTrain
                    if (presentTrain != null) {
                        val tName = presentTrain.name.string
                        if (tName.isNotBlank()) foundTrains.add(tName)
                    }

                    val trains = com.simibubi.create.Create.RAILWAYS.trains.values
                    for (train in trains) {
                        val schedule = train.runtime?.schedule ?: continue
                        var stopsHere = false
                        for (entry in schedule.entries) {
                            val instruction = entry.instruction
                            if (instruction is DestinationInstruction) {
                                val regexStr = instruction.filterForRegex
                                try {
                                    if (stationName.matches(Regex(regexStr, RegexOption.IGNORE_CASE))) {
                                        stopsHere = true
                                        break
                                    }
                                } catch (e: Exception) {
                                    if (stationName.equals(instruction.filter, ignoreCase = true)) {
                                        stopsHere = true
                                        break
                                    }
                                }
                            }
                        }
                        if (stopsHere) {
                            val tName = train.name.string
                            if (tName.isNotBlank()) foundTrains.add(tName)
                        }
                    }

                    PacketDistributor.sendToPlayer(player, AutoFetchTrainsResponsePayload(foundTrains.toList()))
                }
            }
        }

        registrar.playToClient(
            AutoFetchTrainsResponsePayload.ID,
            AutoFetchTrainsResponsePayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala.station_voices.client.ClientHooks.handleAutoFetchResponse(payload.trainNames)
                    }
                }
            }
        }
    }
}
