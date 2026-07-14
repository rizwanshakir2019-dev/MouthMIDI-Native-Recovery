package com.mouthmidi.app

data class PresetSettings(

    var jawClosedCalibration: Float = 0.01f,

    var jawOpenCalibration: Float = 0.80f,

    var minCC: Int = 0,

    var maxCC: Int = 127,

    var midiCC: Int = 1,

    var midiChannel: Int = 1,

    var smoothing: Float = 0f,

    var deadZone: Float = 0f,


    var invert: Boolean = false,

    var holdLastValue: Boolean = true,

    var themeColor: String = "pink"
)
