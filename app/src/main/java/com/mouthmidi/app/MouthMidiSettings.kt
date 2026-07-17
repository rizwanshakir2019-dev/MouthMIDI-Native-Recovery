package com.mouthmidi.app

data class MouthMidiSettings(

    var jawClosedCalibration: Float = 0.01f,

    var jawOpenCalibration: Float = 0.80f,

    var minCC: Int = 0,

    var maxCC: Int = 127,

    var midiCC: Int = 1,

    var midiChannel: Int = 1,

    var attackSpeed: Float = 0f,

    var releaseSpeed: Float = 0f,

    var smoothing: Float = 0f,

    var deadZone: Float = 0f,


    var invert: Boolean = false,

    var holdLastValue: Boolean = true,

    var themeColor: String = "orange",

    var transport: String = "USB",

    var wifiHost: String = "192.168.1.100",

    var wifiPort: Int = 5004,

    var wifiSessionName: String = "MouthMIDI",

    var flashlightEnabled: Boolean = false,

    var keepScreenAwake: Boolean = true,
    var cameraResolution: String = "BALANCED"
)
