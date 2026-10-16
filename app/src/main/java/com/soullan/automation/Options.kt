package com.soullan.automation

import android.util.Base64

class Options(
    args: Array<String>,
) {
    private val options = hashMapOf<String, String>()

    init {
        for (arg in args) {
            val (k, v) = arg.split("=", limit = 2)
            options[k] = v
        }
    }

    operator fun get(k: String) = options[k]

    val host: String
        get() = get("host") ?: "automation"

    val quality: Int
        get() = (get("quality") ?: "100").toInt()
        
    val silence: Boolean
        get() = get("silence")?: "" == "true"

    // One-shot: set the focused input field to this base64 encoded utf-8
    // text and exit, instead of serving screenshots.
    val setText: String?
        get() = get("settext")?.let {
            String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8)
        }

    val append: Boolean
        get() = get("append") == "true"
}
