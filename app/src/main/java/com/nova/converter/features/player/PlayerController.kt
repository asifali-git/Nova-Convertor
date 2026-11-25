package com.nova.converter.features.player

import androidx.compose.runtime.mutableStateOf

/**
 * Global in-app player controller — kahin se bhi (result card, history, metadata…)
 * PlayerController.open(path, mime) call karo, full-screen player overlay khul jayega.
 */
object PlayerController {

    private val current_ = mutableStateOf<Pair<String, String>?>(null)
    val current: androidx.compose.runtime.State<Pair<String, String>?> = current_

    fun open(path: String, mime: String) {
        current_.value = path to mime
    }

    fun close() {
        current_.value = null
    }
}
