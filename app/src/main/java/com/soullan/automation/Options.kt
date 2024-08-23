package com.soullan.automation

class Options(
    args: Array<String>,
) {
    private val options = hashMapOf<String, Any>()

    init {
        for (arg in args) {
            val (k, v) = arg.split("=")
            options[k] = v
        }
    }

    operator fun get(k: String) = options[k]
}
