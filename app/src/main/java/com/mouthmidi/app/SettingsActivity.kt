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

        midiChannelInput.setText(
            settings.midiChannel.toString()
        )

        midiCCInput.setText(
            settings.midiCC.toString()
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

        settingsRepository.save(settings)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
