package de.jamala.train_announcer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.lwjgl.openal.AL

object ALThreadTest {
    @JvmStatic
    fun main(args: Array<String>) {
        // Can't run in standalone because we need MC to initialize OpenAL.
    }
}
