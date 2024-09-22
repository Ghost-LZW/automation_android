package com.soullan.automation

class Options(
    args: Array<String>,
) {
    private val options = hashMapOf<String, String>()

    init {
        for (arg in args) {
            val (k, v) = arg.split("=")
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
}
