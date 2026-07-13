package com.mouthmidi.app

import android.os.Bundle
import android.widget.Switch
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SettingsRepository

    private lateinit var settings: MouthMidiSettings

    private lateinit var flashlightSwitch: Switch

    private lateinit var keepScreenAwakeSwitch: Switch

    private lateinit var midiChannelInput: EditText

    private lateinit var midiCCInput: EditText

    private lateinit var jawClosedCalibrationInput: EditText

    private lateinit var jawOpenCalibrationInput: EditText

    private lateinit var smoothingInput: EditText

    private lateinit var minCCInput: EditText

    private lateinit var maxCCInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        settingsRepository = SettingsRepository(this)

        settings = settingsRepository.load()

        flashlightSwitch =
            findViewById(R.id.flashlightSwitch)

        keepScreenAwakeSwitch =
            findViewById(R.id.keepScreenAwakeSwitch)

        midiChannelInput =
            findViewById(R.id.midiChannelInput)

        midiCCInput =
            findViewById(R.id.midiCCInput)

        jawClosedCalibrationInput =
            findViewById(R.id.jawClosedCalibrationInput)

        jawOpenCalibrationInput =
            findViewById(R.id.jawOpenCalibrationInput)

        smoothingInput =
            findViewById(R.id.smoothingInput)

        minCCInput =
            findViewById(R.id.minCCInput)

        maxCCInput =
            findViewById(R.id.maxCCInput)

        midiChannelInput.setText(
            settings.midiChannel.toString()
        )

        midiCCInput.setText(
            settings.midiCC.toString()
        )

        jawClosedCalibrationInput.setText(
            settings.jawClosedCalibration.toString()
        )

        jawOpenCalibrationInput.setText(
            settings.jawOpenCalibration.toString()
        )

        smoothingInput.setText(
            settings.smoothing.toString()
        )

        minCCInput.setText(
            settings.minCC.toString()
        )

        maxCCInput.setText(
            settings.maxCC.toString()
        )

        flashlightSwitch.isChecked =
            settings.flashlightEnabled

        keepScreenAwakeSwitch.isChecked =
            settings.keepScreenAwake

        supportActionBar?.title = "⚙ Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onPause() {
        super.onPause()


        settings.midiChannel =
            (midiChannelInput.text.toString().toIntOrNull() ?: 1)
                .coerceIn(1,16)

        settings.midiCC =
            (midiCCInput.text.toString().toIntOrNull() ?: 1)
                .coerceIn(0,127)

        settings.flashlightEnabled =
            flashlightSwitch.isChecked

        settings.keepScreenAwake =
            keepScreenAwakeSwitch.isChecked

        settings.jawClosedCalibration =
            jawClosedCalibrationInput.text.toString()
                .toFloatOrNull() ?: 0.01f

        settings.jawOpenCalibration =
            jawOpenCalibrationInput.text.toString()
                .toFloatOrNull() ?: 0.80f

        settings.smoothing =
            smoothingInput.text.toString()
                .toFloatOrNull() ?: 0f

        settings.minCC =
            (minCCInput.text.toString()
                .toIntOrNull() ?: 0)
                .coerceIn(0,127)

        settings.maxCC =
            (maxCCInput.text.toString()
                .toIntOrNull() ?: 127)
                .coerceIn(0,127)

        settingsRepository.save(settings)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
