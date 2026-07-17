package com.mouthmidi.app

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


class RtpMidiTransport(
    private val host: String,
    private val port: Int,
    private val sessionName: String,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) : MidiTransport {


    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null


    override var connected = false
        private set


    override fun connect() {

        try {

            address = InetAddress.getByName(host)

            socket = DatagramSocket()

            connected = true

            onConnectionChanged(true)

            Log.d(
                "MouthMIDI",
                "RTP MIDI connected $host:$port session=$sessionName"
            )

        } catch (e: Exception) {

            connected = false

            onConnectionChanged(false)

            Log.e(
                "MouthMIDI",
                "RTP MIDI connection failed",
                e
            )
        }
    }


    override fun disconnect() {

        socket?.close()

        socket = null

        connected = false

        onConnectionChanged(false)

        Log.d(
            "MouthMIDI",
            "RTP MIDI disconnected"
        )
    }


    override fun sendCC(
        channel: Int,
        cc: Int,
        value: Int
    ) {

        if (!connected) return


        try {

            val midi =
                byteArrayOf(
                    (0xB0 or ((channel - 1) and 0x0F)).toByte(),
                    cc.toByte(),
                    value.toByte()
                )


            val packet =
                DatagramPacket(
                    midi,
                    midi.size,
                    address,
                    port
                )


            socket?.send(packet)


        } catch (e: Exception) {

            Log.e(
                "MouthMIDI",
                "RTP MIDI CC send failed",
                e
            )
        }
    }
}
