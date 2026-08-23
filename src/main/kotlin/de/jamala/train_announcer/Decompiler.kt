package de.jamala.train_announcer

object Decompiler {
    @JvmStatic
    fun main(args: Array<String>) {
        val stream = com.simibubi.create.content.trains.observer.TrackObserverBlockEntity::class.java.getResourceAsStream("TrackObserverBlockEntity.class")
        if (stream == null) {
            println("Class stream not found")
            return
        }
        val bytes = stream.readBytes()
        println("Bytes length: ${bytes.size}")
        
        // I can just check if passingTrainUUID is ever modified
    }
}
