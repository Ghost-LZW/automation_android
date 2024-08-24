package com.soullan.automation.tasks

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

class ScreenShot {
    lateinit var instrumentation: Instrumentation
    lateinit var uiDevice: UiDevice
    val cacheDir = File("/data/local/tmp")

    fun takeScreenShot(): ByteArray {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        uiDevice = UiDevice.getInstance(instrumentation)
        val file = File.createTempFile("screenshot", "png", cacheDir)
        uiDevice.takeScreenshot(file)
        return file.readBytes()
    }
}
