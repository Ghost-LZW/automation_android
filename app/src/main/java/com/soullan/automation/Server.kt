package com.soullan.automation

import java.net.ServerSocket
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val options = Options(args)

    val server = ServerSocket((options["port"]?:8888) as Int)

    val client = server.accept()

    println("Client connected: ${client.inetAddress.hostAddress}")

    // thread {ClientHandler(client).run()}
}