package com.mouthmidi.app

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue


class RtpMidiTransport(
    private val host: String,
    private val port: Int,
    private val sessionName: String,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) : MidiTransport {


    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null

    private var packetCount = 0

    private var sendCallCount = 0

    private var lastError = "none"

    private val sendQueue: BlockingQueue<DatagramPacket> =
        LinkedBlockingQueue()

    private var senderThread: Thread? = null

    private var senderRunning = false


    fun getLastError(): String {
        return lastError
    }



    fun getSendCallCount(): Int {
        return sendCallCount
    }

    fun isActuallyConnected(): Boolean {
        return connected
    }


    fun getPacketCount(): Int {
        return packetCount
    }

    fun getQueueSize(): Int {
        return sendQueue.size
    }



    override var connected = false
        private set


    override fun connect() {

        try {

            address = InetAddress.getByName(host)

            socket = DatagramSocket()

            senderRunning = true

            senderThread = Thread {
                while (senderRunning) {
                    try {
                        val packet = sendQueue.take()
                        socket?.send(packet)
                        packetCount++

                        if (packetCount % 100 == 0) {
                            Log.d(
                                "MouthMIDI",
                                "RTP packets sent: $packetCount"
                            )
                        }

                    } catch (e: Exception) {
                        lastError = e.javaClass.simpleName
                    }
                }
            }

            senderThread?.start()

            connected = true

            onConnectionChanged(true)

            Log.d(
                "MouthMIDI",
                "RTP MIDI connected $host:$port session=$sessionName"
            )

        } catch (e: Exception) {

            connected = false

            onConnectionChanged(false)

                lastError = e.javaClass.simpleName

            Log.e(
                "MouthMIDI",
                "RTP MIDI connection failed",
                e
            )
        }
    }


    override fun disconnect() {


          senderRunning = false
          senderThread?.interrupt()
          senderThread = null
          sendQueue.clear()
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


        sendCallCount++

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


              sendQueue.offer(packet)

                Log.d(
                    "MouthMIDI",
                    "WiFi queue size=${sendQueue.size}"
                )


        } catch (e: Exception) {

                lastError = e.javaClass.simpleName

            Log.e(
                "MouthMIDI",
                "RTP MIDI CC send failed",
                e
            )
        }
    }
}
