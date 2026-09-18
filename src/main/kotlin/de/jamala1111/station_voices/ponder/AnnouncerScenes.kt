package de.jamala.station_voices.ponder

import com.simibubi.create.AllBlocks
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import de.jamala.station_voices.block.ModBlocks
import de.jamala.station_voices.item.ModItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB

object AnnouncerScenes {

    fun redstoneAnnouncer(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("announcer_block", "Using the Redstone Announcer")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val announcerPos = util.grid().at(2, 1, 2)
        val redstonePos = util.grid().at(2, 1, 3)
        val leverPos = util.grid().at(2, 1, 4)

        val announcerSelect = util.select().position(announcerPos)
        val redstoneSelect = util.select().fromTo(2, 1, 3, 2, 1, 4)

        scene.world().setBlock(
            announcerPos,
            ModBlocks.ANNOUNCER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        scene.world().setBlock(redstonePos, Blocks.REDSTONE_WIRE.defaultBlockState(), false)
        scene.world().setBlock(leverPos, Blocks.LEVER.defaultBlockState(), false)

        scene.world().showSection(announcerSelect, Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(60)
            .text("The Redstone Announcer plays custom text-to-speech voice announcements")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
        scene.idle(70)

        scene.overlay().showControls(util.vector().topOf(announcerPos), Pointing.DOWN, 50).rightClick()
        scene.idle(10)

        scene.overlay().showText(70)
            .text("Right-click the block to configure text, voice, pitch, reverb, and effects")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
        scene.idle(80)

        scene.world().showSection(redstoneSelect, Direction.NORTH)
        scene.idle(20)

        scene.overlay().showText(60)
            .text("Power the Announcer with a Redstone signal to trigger the announcement")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(leverPos))
            .placeNearTarget()
        scene.idle(70)

        scene.world().modifyBlock(leverPos, { s -> s.setValue(LeverBlock.POWERED, true) }, false)
        scene.world().modifyBlock(redstonePos, { s -> s.setValue(RedStoneWireBlock.POWER, 15) }, false)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, true) }, false)
        scene.effects().indicateRedstone(leverPos)
        scene.effects().indicateSuccess(announcerPos)
        scene.idle(15)

        scene.overlay().showText(70)
            .text("While playing, the announcer lights up and broadcasts audio to nearby players")
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(80)

        scene.world().modifyBlock(leverPos, { s -> s.setValue(LeverBlock.POWERED, false) }, false)
        scene.world().modifyBlock(redstonePos, { s -> s.setValue(RedStoneWireBlock.POWER, 0) }, false)
        scene.idle(30)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, false) }, false)
        scene.idle(20)

        scene.overlay().showText(80)
            .text("Optional jingles (such as DB Gong) can be configured to play before, after, or both!")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
            .colored(PonderPalette.BLUE)
        scene.idle(90)
    }

    fun configurableAnnouncer(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("configurable_announcer_block", "Automating Station Announcements")
        scene.configureBasePlate(1, 0, 12)
        scene.scaleSceneView(0.65f)
        scene.setSceneOffsetY(-1f)
        scene.showBasePlate()
        scene.idle(5)

        for (i in 13 downTo 0) {
            scene.world().showSection(util.select().position(i, 1, 6), Direction.DOWN)
            scene.idle(1)
        }

        val stationPos = util.grid().at(8, 1, 3)
        val stationSelect = util.select().position(stationPos)
        val announcerPos = util.grid().at(6, 1, 3)
        val announcerSelect = util.select().position(announcerPos)
        val train = util.select().fromTo(9, 2, 5, 5, 3, 7)

        scene.world().showSection(stationSelect, Direction.DOWN)
        scene.idle(15)

        scene.overlay().showText(70)
            .text("The Configurable Announcer automates announcements for your train network")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(stationPos))
            .placeNearTarget()
        scene.idle(80)

        scene.overlay().showControls(util.vector().topOf(stationPos), Pointing.DOWN, 50)
            .rightClick()
            .withItem(net.minecraft.world.item.ItemStack(ModItems.CONFIGURABLE_ANNOUNCER_BLOCK_ITEM))
        scene.idle(10)
        scene.effects().indicateSuccess(stationPos)

        scene.overlay().showText(70)
            .text("Right-click a Station or Track Observer with the Announcer item to link it")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(stationPos))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(80)

        scene.world().setBlock(
            announcerPos,
            ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        scene.world().showSection(announcerSelect, Direction.DOWN)
        scene.idle(15)

        val announcerBb = AABB(announcerPos)
        val stationBb = AABB(stationPos)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, announcerBb, stationBb, 40)
        scene.idle(45)

        scene.overlay().showControls(util.vector().topOf(announcerPos), Pointing.DOWN, 50).rightClick()
        scene.idle(10)

        scene.overlay().showText(80)
            .text("In the UI, use 'Auto-Fetch' to automatically populate trains scheduled for this station")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
        scene.idle(90)

        scene.overlay().showText(70)
            .text("Each train can have completely customized announcement text, voice, and effects")
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
        scene.idle(80)

        val trainElement = scene.world().showIndependentSection(train, Direction.DOWN)
        scene.world().moveSection(trainElement, util.vector().of(-4.0, 0.0, 0.0), 0)
        val target = util.vector().centerOf(2, 3, 6)
        val birb = scene.special().createBirb(target, net.createmod.ponder.api.element.ParrotPose::FacePointOfInterestPose)
        scene.idle(10)

        scene.world().moveSection(trainElement, util.vector().of(4.0, 0.0, 0.0), 20)
        scene.world().animateBogey(util.grid().at(7, 2, 6), -4f, 20)
        scene.special().moveParrot(birb, util.vector().of(4.0, 0.0, 0.0), 20)
        scene.idle(20)

        scene.world().animateTrainStation(stationPos, true)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, true) }, false)
        scene.effects().indicateSuccess(announcerPos)
        scene.effects().indicateRedstone(stationPos)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("When a scheduled train arrives, the Announcer triggers automatically!")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(90)

        scene.world().animateTrainStation(stationPos, false)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, false) }, false)
        scene.idle(20)
    }

    fun trainAnnouncer(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("train_announcer_block", "Using the Train-Mounted Announcer")
        scene.configureBasePlate(1, 0, 12)
        scene.scaleSceneView(0.65f)
        scene.setSceneOffsetY(-1f)
        scene.showBasePlate()
        scene.idle(5)

        for (i in 13 downTo 0) {
            scene.world().showSection(util.select().position(i, 1, 6), Direction.DOWN)
            scene.idle(1)
        }

        val stationPos = util.grid().at(8, 1, 3)
        val stationSelect = util.select().position(stationPos)
        val train = util.select().fromTo(9, 2, 5, 5, 3, 7)
        val announcerPos = util.grid().at(7, 3, 5)

        scene.world().showSection(stationSelect, Direction.DOWN)
        scene.idle(10)

        val trainElement = scene.world().showIndependentSection(train, Direction.DOWN)
        scene.world().moveSection(trainElement, util.vector().of(-4.0, 0.0, 0.0), 0)
        scene.idle(10)

        scene.world().setBlock(
            announcerPos,
            ModBlocks.TRAIN_ANNOUNCER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        val announcerElement = scene.world().showIndependentSection(util.select().position(announcerPos), Direction.DOWN)
        scene.world().moveSection(announcerElement, util.vector().of(-4.0, 0.0, 0.0), 0)
        scene.idle(15)

        scene.overlay().showText(70)
            .text("The Train-Mounted Announcer is placed directly on train carriages")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(3, 3, 5))
            .placeNearTarget()
        scene.idle(80)

        scene.overlay().showControls(util.vector().topOf(3, 3, 5), Pointing.DOWN, 50).rightClick()
        scene.idle(10)

        scene.overlay().showText(80)
            .text("Right-click to configure announcements for each arriving station, even while assembled")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(3, 3, 5))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(90)

        scene.overlay().showText(70)
            .text("Use 'Auto-Fetch' to automatically load stations from the train schedule")
            .pointAt(util.vector().topOf(3, 3, 5))
            .placeNearTarget()
        scene.idle(80)

        // Move train toward station
        scene.world().moveSection(trainElement, util.vector().of(4.0, 0.0, 0.0), 20)
        scene.world().moveSection(announcerElement, util.vector().of(4.0, 0.0, 0.0), 20)
        scene.world().animateBogey(util.grid().at(7, 2, 6), -4f, 20)
        scene.idle(20)

        scene.world().animateTrainStation(stationPos, true)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, true) }, false)
        scene.effects().indicateSuccess(announcerPos)
        scene.effects().indicateRedstone(stationPos)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("Upon arriving at a configured station, the announcer plays inside the train!")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(90)

        scene.overlay().showText(80)
            .text("Enable 'Train Only' in the effects tab so only passengers on the train hear the broadcast")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
            .colored(PonderPalette.BLUE)
        scene.idle(90)

        scene.world().animateTrainStation(stationPos, false)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, false) }, false)
        scene.idle(20)
    }

    fun speaker(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("speaker_block", "Using Speakers as Audio Relays")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val announcerPos = util.grid().at(1, 1, 2)
        val speakerPos = util.grid().at(3, 1, 2)
        val redstonePos = util.grid().at(1, 1, 3)
        val leverPos = util.grid().at(1, 1, 4)

        val announcerSelect = util.select().position(announcerPos)
        val speakerSelect = util.select().position(speakerPos)
        val redstoneSelect = util.select().fromTo(1, 1, 3, 1, 1, 4)

        scene.world().setBlock(
            announcerPos,
            ModBlocks.ANNOUNCER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        scene.world().setBlock(redstonePos, Blocks.REDSTONE_WIRE.defaultBlockState(), false)
        scene.world().setBlock(leverPos, Blocks.LEVER.defaultBlockState(), false)

        scene.world().showSection(announcerSelect, Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(70)
            .text("Speakers act as secondary audio sources for Announcer blocks")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
        scene.idle(80)

        scene.overlay().showControls(util.vector().topOf(announcerPos), Pointing.DOWN, 50)
            .rightClick()
            .withItem(net.minecraft.world.item.ItemStack(ModItems.SPEAKER_BLOCK_ITEM))
        scene.idle(10)
        scene.effects().indicateSuccess(announcerPos)

        scene.overlay().showText(70)
            .text("Right-click any Announcer with the Speaker item in hand to link them")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(announcerPos))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(80)

        scene.world().setBlock(
            speakerPos,
            ModBlocks.SPEAKER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        scene.world().showSection(speakerSelect, Direction.DOWN)
        scene.idle(15)

        val speakerBb = AABB(speakerPos)
        val announcerBb = AABB(announcerPos)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, speakerBb, announcerBb, 40)
        scene.idle(45)

        scene.world().showSection(redstoneSelect, Direction.NORTH)
        scene.idle(15)

        scene.overlay().showText(60)
            .text("When the Announcer plays, all linked Speakers broadcast the audio simultaneously!")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(speakerPos))
            .placeNearTarget()
        scene.idle(70)

        scene.world().modifyBlock(leverPos, { s -> s.setValue(LeverBlock.POWERED, true) }, false)
        scene.world().modifyBlock(redstonePos, { s -> s.setValue(RedStoneWireBlock.POWER, 15) }, false)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, true) }, false)
        scene.world().modifyBlock(speakerPos, { s -> s.setValue(BlockStateProperties.POWERED, true) }, false)
        scene.effects().indicateRedstone(leverPos)
        scene.effects().indicateSuccess(announcerPos)
        scene.effects().indicateSuccess(speakerPos)
        scene.idle(15)

        scene.overlay().showText(80)
            .text("Distribute speakers around stations and platforms so announcements are heard everywhere")
            .pointAt(util.vector().topOf(speakerPos))
            .placeNearTarget()
            .colored(PonderPalette.GREEN)
        scene.idle(90)

        scene.world().modifyBlock(leverPos, { s -> s.setValue(LeverBlock.POWERED, false) }, false)
        scene.world().modifyBlock(redstonePos, { s -> s.setValue(RedStoneWireBlock.POWER, 0) }, false)
        scene.idle(30)
        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, false) }, false)
        scene.world().modifyBlock(speakerPos, { s -> s.setValue(BlockStateProperties.POWERED, false) }, false)
        scene.idle(20)
    }

    fun displayLink(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("announcer_display_link", "Displaying Announcements on Screens")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val announcerPos = util.grid().at(3, 1, 1)
        val announcerSelect = util.select().position(announcerPos)
        val linkPos = util.grid().at(2, 1, 1)
        val linkSelect = util.select().position(linkPos)
        val board = util.grid().at(3, 2, 3)
        val fullBoard = util.select().fromTo(3, 2, 3, 1, 1, 3)
        val largeCog = util.select().position(3, 0, 5)
        val smallCog = util.select().fromTo(4, 1, 5, 4, 1, 3)

        scene.world().showSection(largeCog, Direction.UP)
        scene.world().showSection(smallCog, Direction.WEST)
        scene.idle(5)
        scene.world().showSection(fullBoard, Direction.NORTH)
        scene.idle(15)

        val targetVec = util.vector().of(3.5, 2.75, 3.25)

        scene.overlay().showText(70)
            .attachKeyFrame()
            .text("Display Links can read announcements from Announcer blocks")
            .pointAt(targetVec.add(-1.0, 0.0, 0.0))
            .placeNearTarget()
        scene.idle(80)

        scene.overlay().showControls(targetVec, Pointing.RIGHT, 50)
            .withItem(com.simibubi.create.AllBlocks.DISPLAY_LINK.asStack())
            .rightClick()
        scene.idle(6)
        scene.overlay().chaseBoundingBoxOutline(
            PonderPalette.OUTPUT,
            linkSelect,
            AABB(board).expandTowards(-2.0, -1.0, 0.0).deflate(0.0, 0.0, 3.0 / 16.0),
            50
        )
        scene.idle(30)
        scene.overlay().showText(60)
            .text("First, right-click the target display board, sign, or nixie tube...")
            .pointAt(targetVec.add(-1.0, 0.0, 0.0))
            .colored(PonderPalette.OUTPUT)
            .attachKeyFrame()
            .placeNearTarget()
        scene.idle(70)

        scene.world().setBlock(
            announcerPos,
            ModBlocks.ANNOUNCER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        scene.world().showSection(announcerSelect, Direction.DOWN)
        scene.idle(10)
        scene.world().showSection(linkSelect, Direction.EAST)
        scene.idle(15)

        scene.overlay().showOutlineWithText(announcerSelect, 60)
            .text("...then place the Display Link against an Announcer block")
            .pointAt(util.vector().centerOf(linkPos))
            .colored(PonderPalette.INPUT)
            .attachKeyFrame()
            .placeNearTarget()
        scene.idle(70)

        scene.overlay().showControls(util.vector().topOf(linkPos), Pointing.DOWN, 50).rightClick()
        scene.idle(10)
        scene.overlay().showText(80)
            .text("In the interface, customize the label and choose between 'While Playing' or 'Keep Displayed'")
            .pointAt(util.vector().centerOf(linkPos))
            .attachKeyFrame()
            .placeNearTarget()
        scene.idle(90)

        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, true) }, false)
        scene.effects().indicateSuccess(announcerPos)
        scene.world().flashDisplayLink(linkPos)
        scene.world().setDisplayBoardText(board, 0, net.minecraft.network.chat.Component.literal("Express Arriving - Tr. 1"))
        scene.idle(15)

        scene.overlay().showText(80)
            .text("When an announcement is played, the fitting text is immediately sent to the display!")
            .pointAt(util.vector().centerOf(board))
            .colored(PonderPalette.GREEN)
            .attachKeyFrame()
            .placeNearTarget()
        scene.idle(90)

        scene.world().modifyBlock(announcerPos, { s -> s.setValue(BlockStateProperties.POWERED, false) }, false)
        scene.idle(20)

        scene.world().setBlock(
            announcerPos,
            ModBlocks.CONFIGURABLE_ANNOUNCER_BLOCK.defaultBlockState().setValue(BlockStateProperties.POWERED, false),
            false
        )
        scene.effects().indicateSuccess(announcerPos)
        scene.idle(15)

        scene.overlay().showText(80)
            .text("This works with Redstone Announcers, Configurable Train Announcers, and linked Speakers")
            .pointAt(util.vector().topOf(announcerPos))
            .colored(PonderPalette.BLUE)
            .attachKeyFrame()
            .placeNearTarget()
        scene.idle(90)
    }
}
