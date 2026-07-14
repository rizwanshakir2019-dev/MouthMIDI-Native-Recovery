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


    private val presetPrefs =
        context.getSharedPreferences(
            "mouthmidi_presets",
            Context.MODE_PRIVATE
        )

    fun getLastPreset(): String {
        return prefs.getString("lastPresetName", "Default") ?: "Default"
    }

    fun saveLastPreset(name: String) {
        prefs.edit()
            .putString("lastPresetName", name)
            .apply()
    }


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
                    "pink"
                ) ?: "pink",

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
                ) ?: "USB",

              wifiHost =
                  prefs.getString(
                  "wifiHost",
                      "192.168.1.100"
                  ) ?: "192.168.1.100",

              wifiPort =
                  prefs.getInt(
                  "wifiPort",
                      5004
                  ),

              wifiSessionName =
                  prefs.getString(
                    "wifiSessionName",
                      "MouthMIDI"
                  ) ?: "MouthMIDI"
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

              .putString(
                  "wifiHost",
                  settings.wifiHost
              )

              .putInt(
                  "wifiPort",
                  settings.wifiPort
              )

              .putString(
                  "wifiSessionName",
                  settings.wifiSessionName
              )

            .apply()
    }


    fun savePreset(
        name: String,
        settings: PresetSettings
    ) {

        presetPrefs.edit()

            .putFloat("${name}_jawClosedCalibration", settings.jawClosedCalibration)
            .putFloat("${name}_jawOpenCalibration", settings.jawOpenCalibration)

            .putInt("${name}_minCC", settings.minCC)
            .putInt("${name}_maxCC", settings.maxCC)

            .putInt("${name}_midiCC", settings.midiCC)
            .putInt("${name}_midiChannel", settings.midiChannel)

            .putFloat("${name}_smoothing", settings.smoothing)
            .putFloat("${name}_deadZone", settings.deadZone)

            .putBoolean("${name}_invert", settings.invert)
            .putBoolean("${name}_holdLastValue", settings.holdLastValue)

            .putString("${name}_themeColor", settings.themeColor)

            .putBoolean("${name}_exists", true)

            .apply()

          println("DEBUG SAVE PRESET: $name midiCC=${settings.midiCC} channel=${settings.midiChannel} smoothing=${settings.smoothing} theme=${settings.themeColor}")
    }


    fun loadPreset(
        name: String
    ): PresetSettings {

          println("DEBUG LOAD PRESET: $name theme=${presetPrefs.getString("${name}_themeColor","missing")}")

        return PresetSettings(

            jawClosedCalibration =
                presetPrefs.getFloat("${name}_jawClosedCalibration",0.01f),

            jawOpenCalibration =
                presetPrefs.getFloat("${name}_jawOpenCalibration",0.80f),

            minCC =
                presetPrefs.getInt("${name}_minCC",0),

            maxCC =
                presetPrefs.getInt("${name}_maxCC",127),

            midiCC =
                presetPrefs.getInt("${name}_midiCC",1),

            midiChannel =
                presetPrefs.getInt("${name}_midiChannel",1),

            smoothing =
                presetPrefs.getFloat("${name}_smoothing",0f),


            deadZone =
                presetPrefs.getFloat("${name}_deadZone",0f),

            invert =
                presetPrefs.getBoolean("${name}_invert",false),

            holdLastValue =
                presetPrefs.getBoolean("${name}_holdLastValue",true),

            themeColor =
                presetPrefs.getString("${name}_themeColor","pink")
                    ?: "pink"
        )
    }



      fun deletePreset(
          name: String
      ) {

          presetPrefs.edit()
              .remove("${name}_jawClosedCalibration")
              .remove("${name}_jawOpenCalibration")
              .remove("${name}_minCC")
              .remove("${name}_maxCC")
              .remove("${name}_midiCC")
              .remove("${name}_midiChannel")
              .remove("${name}_smoothing")
              .remove("${name}_deadZone")
              .remove("${name}_invert")
              .remove("${name}_holdLastValue")
              .remove("${name}_themeColor")
              .remove("${name}_exists")
              .apply()
      }


    fun getPresetNames(): List<String> {

        return presetPrefs.all.keys
            .filter {
                it.endsWith("_exists")
            }
            .map {
                it.removeSuffix("_exists")
            }
    }

}
