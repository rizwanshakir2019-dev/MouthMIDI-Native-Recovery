package com.mouthmidi.app

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException

class UsbMidiTransport(
    private val context: Context,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) : MidiTransport {

    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null

    private val handler = Handler(Looper.getMainLooper())

    override var connected = false
        private set

    override fun connect() {
        midiManager =
            context.getSystemService(
                MidiManager::class.java
            )

        val manager = midiManager ?: return

        manager.registerDeviceCallback(
            deviceCallback,
            Handler(Looper.getMainLooper())
        )

        scanDevices()
    }

    private val deviceCallback =
        object : MidiManager.DeviceCallback() {

            override fun onDeviceAdded(device: MidiDeviceInfo) {
                Log.d("MouthMIDI", "USB MIDI added")
                scanDevices()
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                Log.d("MouthMIDI", "USB MIDI removed")
                disconnect()
            }
        }

    private fun scanDevices() {

        val manager = midiManager ?: return

        val usbDevice =
            manager.devices.firstOrNull {
                it.type == MidiDeviceInfo.TYPE_USB
            }

        if (usbDevice == null) {
            connected = false
            onConnectionChanged(false)
            return
        }

        manager.openDevice(
            usbDevice,
            { device ->

                if (device == null) {
                    connected = false
                    onConnectionChanged(false)
                    return@openDevice
                }

                midiDevice = device

                inputPort =
                    device.openInputPort(0)

                if (inputPort != null) {
                    connected = true
                    onConnectionChanged(true)

                    Log.d(
                        "MouthMIDI",
                        "USB MIDI connected"
                    )
                }
            },
            Handler(Looper.getMainLooper())
        )
    }


    override fun disconnect() {

        connected = false
        onConnectionChanged(false)

        inputPort?.close()
        inputPort = null

        midiDevice?.close()
        midiDevice = null
    }


    override fun sendCC(
        channel: Int,
        cc: Int,
        value: Int
    ) {

        val message =
            byteArrayOf(
                (0xB0 or ((channel - 1) and 0x0F)).toByte(),
                cc.toByte(),
                value.toByte()
            )

        try {

            inputPort?.send(
                message,
                0,
                message.size
            )

        } catch (e: IOException) {

            Log.e(
                "MouthMIDI",
                "USB CC send failed",
                e
            )
        }
    }
}
