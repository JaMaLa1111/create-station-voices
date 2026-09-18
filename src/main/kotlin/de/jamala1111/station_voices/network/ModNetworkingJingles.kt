package de.jamala1111.station_voices.network

import de.jamala1111.station_voices.CreateStationVoices
import de.jamala1111.station_voices.JingleManager
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

data class JingleNetworkData(
    val key: String,
    val fileName: String,
    val enabled: Boolean,
    val durationMs: Long
)

data class OpenJingleManagerScreenPayload(val dummy: Boolean = false) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<OpenJingleManagerScreenPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "open_jingle_manager"))
        val STREAM_CODEC = StreamCodec.ofMember(OpenJingleManagerScreenPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = OpenJingleManagerScreenPayload(buf.readBoolean())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) = buf.writeBoolean(dummy)
}

data class RequestJingleListPayload(val dummy: Boolean = false) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<RequestJingleListPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "request_jingle_list"))
        val STREAM_CODEC = StreamCodec.ofMember(RequestJingleListPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = RequestJingleListPayload(buf.readBoolean())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) = buf.writeBoolean(dummy)
}

data class SyncJingleListPayload(val jingles: List<JingleNetworkData>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SyncJingleListPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "sync_jingle_list"))
        val STREAM_CODEC = StreamCodec.ofMember(SyncJingleListPayload::write, ::read)

        fun read(buf: RegistryFriendlyByteBuf): SyncJingleListPayload {
            val size = buf.readInt()
            val list = mutableListOf<JingleNetworkData>()
            for (i in 0 until size) {
                list.add(JingleNetworkData(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readLong()))
            }
            return SyncJingleListPayload(list)
        }
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeInt(jingles.size)
        for (item in jingles) {
            buf.writeUtf(item.key)
            buf.writeUtf(item.fileName)
            buf.writeBoolean(item.enabled)
            buf.writeLong(item.durationMs)
        }
    }
}

data class UpdateJingleKeyPayload(val oldKey: String, val newKey: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<UpdateJingleKeyPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "update_jingle_key"))
        val STREAM_CODEC = StreamCodec.ofMember(UpdateJingleKeyPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = UpdateJingleKeyPayload(buf.readUtf(), buf.readUtf())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(oldKey)
        buf.writeUtf(newKey)
    }
}

data class SetJingleEnabledPayload(val key: String, val enabled: Boolean) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetJingleEnabledPayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "set_jingle_enabled"))
        val STREAM_CODEC = StreamCodec.ofMember(SetJingleEnabledPayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = SetJingleEnabledPayload(buf.readUtf(), buf.readBoolean())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(key)
        buf.writeBoolean(enabled)
    }
}

data class DeleteJinglePayload(val key: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DeleteJinglePayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "delete_jingle"))
        val STREAM_CODEC = StreamCodec.ofMember(DeleteJinglePayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = DeleteJinglePayload(buf.readUtf())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(key)
    }
}

data class AddJinglePayload(val key: String, val fileName: String, val data: ByteArray) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AddJinglePayload>(ResourceLocation.fromNamespaceAndPath(CreateStationVoices.ID, "add_jingle"))
        val STREAM_CODEC = StreamCodec.ofMember(AddJinglePayload::write, ::read)
        fun read(buf: RegistryFriendlyByteBuf) = AddJinglePayload(buf.readUtf(), buf.readUtf(), buf.readByteArray())
    }
    override fun type() = ID
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(key)
        buf.writeUtf(fileName)
        buf.writeByteArray(data)
    }
}

object ModNetworkingJingles {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CreateStationVoices.ID)

        registrar.playToClient(
            OpenJingleManagerScreenPayload.ID,
            OpenJingleManagerScreenPayload.STREAM_CODEC
        ) { _, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala1111.station_voices.client.ClientHooks.openJingleManagerScreen()
                    }
                }
            }
        }

        registrar.playToServer(
            RequestJingleListPayload.ID,
            RequestJingleListPayload.STREAM_CODEC
        ) { _, context ->
            val player = context.player() as net.minecraft.server.level.ServerPlayer
            val list = JingleManager.getCustomJingles().map {
                JingleNetworkData(it.key, it.fileName, it.enabled, JingleManager.getJingleDuration(it.key))
            }
            PacketDistributor.sendToPlayer(player, SyncJingleListPayload(list))
        }

        registrar.playToClient(
            SyncJingleListPayload.ID,
            SyncJingleListPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                net.neoforged.fml.loading.FMLEnvironment.dist.let {
                    if (it.isClient) {
                        de.jamala1111.station_voices.client.ClientHooks.handleSyncJingles(payload.jingles)
                    }
                }
            }
        }

        registrar.playToServer(
            UpdateJingleKeyPayload.ID,
            UpdateJingleKeyPayload.STREAM_CODEC
        ) { payload, context ->
            val player = context.player()
            if (!player.hasPermissions(2)) return@playToServer
            context.enqueueWork {
                JingleManager.setJingleKey(payload.oldKey, payload.newKey)
                broadcastJingleList()
            }
        }

        registrar.playToServer(
            SetJingleEnabledPayload.ID,
            SetJingleEnabledPayload.STREAM_CODEC
        ) { payload, context ->
            val player = context.player()
            if (!player.hasPermissions(2)) return@playToServer
            context.enqueueWork {
                JingleManager.setJingleEnabled(payload.key, payload.enabled)
                broadcastJingleList()
            }
        }

        registrar.playToServer(
            DeleteJinglePayload.ID,
            DeleteJinglePayload.STREAM_CODEC
        ) { payload, context ->
            val player = context.player()
            if (!player.hasPermissions(2)) return@playToServer
            context.enqueueWork {
                JingleManager.deleteCustomJingle(payload.key)
                broadcastJingleList()
            }
        }

        registrar.playToServer(
            AddJinglePayload.ID,
            AddJinglePayload.STREAM_CODEC
        ) { payload, context ->
            val player = context.player()
            if (!player.hasPermissions(2)) return@playToServer
            context.enqueueWork {
                JingleManager.addCustomJingle(payload.key, payload.fileName, payload.data)
                broadcastJingleList()
            }
        }
    }

    private fun broadcastJingleList() {
        val list = JingleManager.getCustomJingles().map {
            JingleNetworkData(it.key, it.fileName, it.enabled, JingleManager.getJingleDuration(it.key))
        }
        PacketDistributor.sendToAllPlayers(SyncJingleListPayload(list))
    }
}
