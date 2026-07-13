package com.mouthmidi.app

import android.content.Context

class SettingsRepository(
    context: Context
) {

    private val prefs =
        context.getSharedPreferences(
            "mouthmidi_settings",
            Context.MODE_PRIVATE
        )

    fun load(): MouthMidiSettings {

        return MouthMidiSettings(

            jawClosedCalibration =
                prefs.getFloat(
                    "jawClosedCalibration",
                    0.01f
                ),

            jawOpenCalibration =
                prefs.getFloat(
                    "jawOpenCalibration",
                    0.80f
                ),

            minCC =
                prefs.getInt(
                    "minCC",
                    0
                ),

            maxCC =
                prefs.getInt(
                    "maxCC",
                    127
                ),

            midiCC =
                prefs.getInt(
                    "midiCC",
                    1
                ),

            midiChannel =
                prefs.getInt(
                    "midiChannel",
                    1
                ),

            smoothing =
                prefs.getFloat(
                    "smoothing",
                    0f
                ),

            sensitivity =
                prefs.getFloat(
                    "sensitivity",
                    1.0f
                ),

            deadZone =
                prefs.getFloat(
                    "deadZone",
                    0f
                ),

            invert =
                prefs.getBoolean(
                    "invert",
                    false
                ),

            holdLastValue =
                prefs.getBoolean(
                    "holdLastValue",
                    true
                ),

            themeColor =
                prefs.getString(
                    "themeColor",
                    "orange"
                ) ?: "orange",

            flashlightEnabled =
                prefs.getBoolean(
                    "flashlightEnabled",
                    false
                ),

            keepScreenAwake =
                prefs.getBoolean(
                    "keepScreenAwake",
                    true
                ),

            transport =
                prefs.getString(
                    "transport",
                    "USB"
                ) ?: "USB"
        )
    }

    fun save(settings: MouthMidiSettings) {

        prefs.edit()

            .putFloat(
                "jawClosedCalibration",
                settings.jawClosedCalibration
            )

            .putFloat(
                "jawOpenCalibration",
                settings.jawOpenCalibration
            )

            .putInt(
                "minCC",
                settings.minCC
            )

            .putInt(
                "maxCC",
                settings.maxCC
            )

            .putInt(
                "midiCC",
                settings.midiCC
            )

            .putInt(
                "midiChannel",
                settings.midiChannel
            )

            .putFloat(
                "smoothing",
                settings.smoothing
            )

            .putFloat(
                "sensitivity",
                settings.sensitivity
            )

            .putFloat(
                "deadZone",
                settings.deadZone
            )

            .putBoolean(
                "invert",
                settings.invert
            )

            .putBoolean(
                "holdLastValue",
                settings.holdLastValue
            )

            .putString(
                "themeColor",
                settings.themeColor
            )

            .putBoolean(
                "flashlightEnabled",
                settings.flashlightEnabled
            )

            .putBoolean(
                "keepScreenAwake",
                settings.keepScreenAwake
            )

            .putString(
                "transport",
                settings.transport
            )

            .apply()
    }
}
