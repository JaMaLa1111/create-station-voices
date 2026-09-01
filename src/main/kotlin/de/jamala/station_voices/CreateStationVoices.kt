package de.jamala.station_voices

import de.jamala.station_voices.block.ModBlocks
import de.jamala.station_voices.item.ModItems
import de.jamala.station_voices.block.ModBlockEntities
import de.jamala.station_voices.item.ModCreativeTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.fml.config.ModConfig.Type
import net.neoforged.fml.ModLoadingContext
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import kotlin.time.Duration.Companion.seconds
import net.neoforged.neoforge.common.NeoForge
import de.jamala.station_voices.network.ModNetworking
import de.jamala.station_voices.network.ModNetworkingConfigurable
import de.jamala.station_voices.command.ModCommands

/**
 * Main mod class. Should be an `object` declaration annotated with `@Mod`.
 * The mod_id should be declared in this object and should match the modId entry
 * in neoforge.mods.toml.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(CreateStationVoices.ID)
object CreateStationVoices {
    const val ID = "create_station_voices"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        ModLoadingContext.get().activeContainer.registerConfig(Type.SERVER, ModConfig.SERVER_SPEC)
        
        LOGGER.log(Level.INFO, "Hello world!")

        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.REGISTRY.register(MOD_BUS)
        ModItems.REGISTRY.register(MOD_BUS)
        ModBlockEntities.REGISTRY.register(MOD_BUS)
        ModCreativeTabs.REGISTRY.register(MOD_BUS)

        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(ModNetworking::register)
        MOD_BUS.addListener(ModNetworkingConfigurable::register)
        
        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands)

        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)
                "test"
            })

        println(obj)

        @Serializable
        data class MySerializedThing(
            val name: String,
            val number: Int
        )
        val testObject = MySerializedThing("KotlinForForge", 712)
        val json = Json.encodeToString(testObject)
        LOGGER.log(Level.INFO, "--- JSON: $json")

        CoroutineScope(Dispatchers.Default).launch {
            LOGGER.log(Level.INFO, "Before delay")
            delay(5.seconds)
            LOGGER.log(Level.INFO, "After 5 seconds")
        }
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
        net.createmod.ponder.foundation.PonderIndex.addPlugin(de.jamala.station_voices.ponder.ModPonderPlugin())
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
        CoroutineScope(Dispatchers.IO).launch {
            de.jamala.station_voices.server.PiperManager.setupPiper()
        }
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
        CoroutineScope(Dispatchers.IO).launch {
            de.jamala.station_voices.server.PiperManager.setupPiper()
        }
    }
}