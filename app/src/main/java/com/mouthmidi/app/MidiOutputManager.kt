package com.mouthmidi.app

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException

class MidiOutputManager(
    private val context: Context,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) {

    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null


    var connected = false
        private set


    fun connect() {

        midiManager =
            context.getSystemService(
                MidiManager::class.java
            )

        val manager = midiManager ?: return

        val devices = manager.devices

        if (devices.isEmpty()) {
            connected = false
              onConnectionChanged(false)
            Log.d("MouthMIDI", "No MIDI device")
            return
        }


        val usbDevice =
            devices.firstOrNull {
                it.type == MidiDeviceInfo.TYPE_USB
            }
            ?: devices.first()


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


    fun disconnect() {

        connected = false
        onConnectionChanged(false)

        inputPort?.close()
        inputPort = null

        midiDevice?.close()
        midiDevice = null
    }


    fun sendCC(
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
