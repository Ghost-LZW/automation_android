package com.soullan.automation

import android.graphics.Bitmap
import android.net.LocalServerSocket
import android.net.LocalSocket
import com.soullan.automation.tasks.ScreenShotPrivate
import java.nio.charset.Charset

fun main(args: Array<String>) {
    val options = Options(args)

    val server = LocalServerSocket(options.host)
    println("Listen on ${options.host}")

    val client = server.accept()

    println("Client connect")

    // thread {ClientHandler(client).run()}
    ClientHandler(client, screenshotQuality = options.quality).run(options.silence)
}

class ClientHandler(
    private val client: LocalSocket,
    private val screenshotQuality: Int,
) {
    private var running: Boolean = false
    private val screenShot = ScreenShotPrivate()
    private val writer = client.outputStream
    private val reader = client.inputStream

    fun run(silence: Boolean) {
        running = true
        write("a")

        while (running) {
            try {
                val cmd = reader.read()
                if (cmd == 2) {
                    shutdown()
                    continue
                }

                assert(cmd == 1)

                val bitmap = screenShot.takeScreenshot()
                if (!silence) {
                  println("take shot done, with height: ${bitmap.height} width: ${bitmap.width}")
                }

                bitmap.compress(Bitmap.CompressFormat.JPEG, screenshotQuality, writer)

                writer.write("^EOF".toByteArray())
                if (!silence) {
                  println("End write")
                }
            } catch (ex: Exception) {
                // TODO: Implement exception handling
                println(ex.toString())
                ex.printStackTrace()
                shutdown()
            } finally {
            }
        }
    }

    private fun write(message: String) {
        writer.write((message).toByteArray(Charset.defaultCharset()))
    }

    private fun shutdown() {
        running = false
        client.close()
        println("Connection Close")
    }
}
