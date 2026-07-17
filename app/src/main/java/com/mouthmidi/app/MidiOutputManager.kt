package com.mouthmidi.app

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.Looper
import android.media.midi.MidiManager.DeviceCallback
import android.util.Log
import java.io.IOException

class MidiOutputManager(
    private val context: Context,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) : MidiTransport {

    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null

    private val handler = Handler(Looper.getMainLooper())

    private val monitorRunnable = object : Runnable {
        override fun run() {

            val manager = midiManager

            if (manager != null) {

                val usbPresent =
                    manager.devices.any {
                        it.type == MidiDeviceInfo.TYPE_USB
                    }

                if (!usbPresent && connected) {

                    Log.d(
                        "MouthMIDI",
                        "MIDI watchdog: device missing"
                    )

                    disconnect()
                }
            }

            handler.postDelayed(this, 1000)
        }
    }

    private val deviceCallback =
        object : MidiManager.DeviceCallback() {

            override fun onDeviceAdded(device: MidiDeviceInfo) {
                Log.d("MouthMIDI", "MIDI device added")
                scanDevices()

          handler.post(monitorRunnable)
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                Log.d(
                    "MouthMIDI",
                    "MIDI device removed callback fired"
                )

                disconnect()
            }
        }


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

          handler.post(monitorRunnable)
    }


    private fun scanDevices() {

          handler.post(monitorRunnable)

        val manager = midiManager ?: return



        val devices = manager.devices

        Log.d(
            "MouthMIDI",
            "MIDI scan: devices=${devices.size}"
        )

        val usbDevice =
            devices.firstOrNull {
                it.type == MidiDeviceInfo.TYPE_USB
            }

        if (usbDevice == null) {
            connected = false
            onConnectionChanged(false)
            Log.d("MouthMIDI", "No USB MIDI device")
            return
        }


        manager.openDevice(
            usbDevice,
            { device ->

                if (device == null) {
                    connected = false
                    onConnectionChanged(false)
                    Log.d("MouthMIDI", "MIDI open failed")
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

        Log.d("MouthMIDI", "Disconnecting MIDI")

        connected = false
        onConnectionChanged(false)

        inputPort?.close()
        inputPort = null

        midiDevice?.close()
        midiDevice = null
    }


    fun cleanup() {


          handler.removeCallbacks(monitorRunnable)
        midiManager?.unregisterDeviceCallback(
            deviceCallback
        )

        disconnect()
    }



    override fun sendCC(
        channel: Int,
        cc: Int,
        value: Int
    ) {

        val midiMessage =
            byteArrayOf(
                (0xB0 or ((channel - 1) and 0x0F)).toByte(),
                cc.toByte(),
                value.toByte()
            )

        try {

            inputPort?.send(
                midiMessage,
                0,
                midiMessage.size
            )

        }
        catch (e: IOException) {

            Log.e(
                "MouthMIDI",
                "Failed CC send",
                e
            )
        }
    }
}
