package com.mouthmidi.app

interface MidiTransport {

    fun connect()

    fun disconnect()

    fun sendCC(
        channel: Int,
        cc: Int,
        value: Int
    )

    val connected: Boolean
}
