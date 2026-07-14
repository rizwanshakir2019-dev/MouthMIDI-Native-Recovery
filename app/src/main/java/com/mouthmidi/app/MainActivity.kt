package com.mouthmidi.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.graphics.Paint
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.AdapterView
import android.widget.Switch
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.util.Log
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.app.AlertDialog
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceStatus: TextView
    private lateinit var midiStatus: TextView
    private lateinit var outputStatus: TextView
    private lateinit var ccValue: TextView
    private lateinit var ccMeter: MouthMeterView

    private lateinit var cameraButton: Button
    private lateinit var startButton: Button

    private lateinit var settingsButton: Button
    private lateinit var recalibrateButton: Button
    private lateinit var defaultCalibrationButton: Button
    private lateinit var calibrationStatusText: TextView
    private lateinit var settingsScrollView: ScrollView

    private lateinit var presetSpinner: Spinner
    private var updatingPresetSpinner = false
    private lateinit var savePresetButton: Button
    private lateinit var loadPresetButton: Button

    private lateinit var themeOrangeButton: Button
    private lateinit var themeYellowButton: Button
    private lateinit var themeGreenButton: Button
    private lateinit var themeBlueButton: Button
    private lateinit var themePurpleButton: Button

    private lateinit var settingsContainer: FrameLayout
    private var settingsView: View? = null

    private lateinit var flashlightSwitch: Switch
    private lateinit var keepScreenAwakeSwitch: Switch
    private lateinit var midiChannelInput: EditText
    private lateinit var midiCCInput: EditText
    private lateinit var jawClosedCalibrationInput: TextView
    private lateinit var jawOpenCalibrationInput: TextView
    private lateinit var smoothingSeekBar: SeekBar
    private lateinit var minCCSeekBar: SeekBar
    private lateinit var maxCCSeekBar: SeekBar
    private lateinit var deadZoneSeekBar: SeekBar

    private lateinit var smoothingValueText: TextView
    private lateinit var minCCValueText: TextView
    private lateinit var maxCCValueText: TextView
    private lateinit var deadZoneValueText: TextView
    private lateinit var invertSwitch: Switch
    private lateinit var holdLastValueSwitch: Switch
    private lateinit var usbTransportRadio: RadioButton
    private lateinit var wifiTransportRadio: RadioButton

      private lateinit var wifiSettingsContainer: View
      private lateinit var wifiHostInput: EditText
      private lateinit var wifiPortInput: EditText
      private lateinit var wifiSessionInput: EditText

    private lateinit var cameraExecutor: ExecutorService

    private var faceLandmarker: FaceLandmarker? = null

    private lateinit var settings: MouthMidiSettings

    private lateinit var settingsRepository: SettingsRepository

    private lateinit var midiOutputManager: MidiOutputManager

    private lateinit var smoothingProcessor: SmoothingProcessor


    private var frameCount = 0
    private var mpSubmitted = 0
    private var mpErrors = 0
    private var lastMpError = ""

    private var lastJawOpen = 0f

    private var calibrationMode = false
    private var calibrationStep = 0
    private var calibrationMin = 1f
    private var calibrationMax = 0f
    private var calibrationStartTime = 0L
    private var lastCC = 0

    private var useFrontCamera = true
    private var cameraInstance: Camera? = null

    private var trackingEnabled = true


    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                faceStatus.text = "● Camera Denied"
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        settingsRepository = SettingsRepository(this)

        settings = settingsRepository.load()

        if (settings.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        previewView = findViewById(R.id.cameraPreview)
        faceStatus = findViewById(R.id.faceStatus)
        midiStatus = findViewById(R.id.midiStatus)
        outputStatus = findViewById(R.id.outputStatus)

        midiOutputManager = MidiOutputManager(this) { connected ->

            runOnUiThread {

                outputStatus.text =
                    if (connected)
                        "USB MIDI✅"
                    else
                        "No MIDI"

            }

        }
        midiOutputManager.connect()


        smoothingProcessor = SmoothingProcessor(
            settings.smoothing
        )



        midiStatus.text = "CC${settings.midiCC} CH${settings.midiChannel}"

        ccValue = findViewById(R.id.ccValue)
        ccMeter = findViewById(R.id.ccMeter)

        cameraButton = findViewById(R.id.cameraButton)
        startButton = findViewById(R.id.startButton)

        settingsButton = findViewById(R.id.settingsButton)

        startButton.text = "■"

        cameraButton.setOnClickListener {
            switchCamera()
        }

        startButton.setOnClickListener {
            trackingEnabled = !trackingEnabled
            startButton.text =
                if (trackingEnabled) "■" else "▶"
        }

        settingsButton.setOnClickListener {
            toggleSettings()
        }



        cameraExecutor = Executors.newSingleThreadExecutor()

        setupFaceLandmarker()


        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    private fun setupFaceLandmarker() {

        val baseOptions =
            BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build()


        val options =
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener(
                    OutputHandler.ResultListener { result, _ ->

                        processFace(result)

                    }
                )
                .build()


        faceLandmarker =
            FaceLandmarker.createFromOptions(
                this,
                options
            )
    }


    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)


        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()


            val preview =
                Preview.Builder()
                    .build()


            preview.setSurfaceProvider(
                previewView.surfaceProvider
            )


            val analyzer =
                ImageAnalysis.Builder()
                    .setOutputImageFormat(
                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                    )
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()


            analyzer.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                processFrame(imageProxy)

            }


            val cameraSelector =
                if (useFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA


            cameraProvider.unbindAll()


            cameraInstance =
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analyzer
                )

            cameraInstance?.cameraControl?.enableTorch(
                settings.flashlightEnabled
            )


        }, ContextCompat.getMainExecutor(this))
    }




    private fun switchCamera() {

        useFrontCamera = !useFrontCamera

        startCamera()
    }

    private fun processFrame(
        imageProxy: ImageProxy
    ) {

        frameCount++

        val mediaImage =
            imageProxy.image


        if (mediaImage != null) {

            try {

                val mpImage =
                    MediaImageBuilder(mediaImage)
                        .build()


                mpSubmitted++


                faceLandmarker?.detectAsync(
                    mpImage,
                    System.currentTimeMillis()
                )


            } catch (e: Exception) {

                mpErrors++

                lastMpError =
                    e.message ?: "unknown"

                Log.e(
                    "MouthMIDI",
                    "MediaPipe error: ${e.message}"
                )
            }
        }


        


        imageProxy.close()
    }



    private fun processFace(
        result: FaceLandmarkerResult
    ) {

        runOnUiThread {


            if (result.faceLandmarks().isEmpty()) {

                faceStatus.text =
                    "● No Face"

                if (settings.holdLastValue) {
                    updateCC(lastCC)
                } else {
                    updateCC(0)
                }

                return@runOnUiThread
            }


            faceStatus.text =
                "● Face:✅"


            val landmarks =
                result.faceLandmarks()[0]

            Log.d(
                "MouthMIDI",
                "LANDMARK_COUNT=" + landmarks.size

              )
            result.faceBlendshapes().ifPresent { faces ->
                if (faces.isNotEmpty()) {
                    val jaw = faces[0].firstOrNull {
                        it.categoryName() == "jawOpen"
                    }
                    lastJawOpen = jaw?.score() ?: 0f

                      JawState.currentJawOpen = lastJawOpen


                    if (calibrationMode) {

                        val now = System.currentTimeMillis()

                        when(calibrationStep) {

                            1 -> {

                                calibrationMin =
                                    minOf(calibrationMin, lastJawOpen)

                                runOnUiThread {

                                    val elapsed =
                                        (now - calibrationStartTime) / 1000f

                                    calibrationStatusText.text =
                                        String.format(
                                            "Recording closed mouth: %.1f / 3.0 sec",
                                            elapsed
                                        )
                                }


                                if (now - calibrationStartTime > 3000) {

                                    settings.jawClosedCalibration =
                                        calibrationMin

                                    calibrationStep = 2

                                    calibrationMax = 0f

                                    calibrationStartTime = now

                                    runOnUiThread {





        recalibrateButton.text =
                                            "Open mouth fully..."

                                        calibrationStatusText.text =
                                            "Now open mouth fully..."

                                    }
                                }

                            }


                            2 -> {

                                calibrationMax =
                                    maxOf(calibrationMax, lastJawOpen)

                                runOnUiThread {

                                    val elapsed =
                                        (now - calibrationStartTime) / 1000f

                                    calibrationStatusText.text =
                                        String.format(
                                            "Recording open mouth: %.1f / 3.0 sec",
                                            elapsed
                                        )
                                }


                                if (now - calibrationStartTime > 3000) {

                                    settings.jawOpenCalibration =
                                        calibrationMax

                                    calibrationMode = false
                                    calibrationStep = 0

                                    settingsRepository.save(settings)

                                    runOnUiThread {

                                        loadSettingsUI()





        recalibrateButton.text =
                                            "Recalibrate Mouth Range"

                                        calibrationStatusText.text =
                                              ""

                                    }

                                }

                            }

                        }
                    }
                }
            }


                var mouthOpen =
                    smoothingProcessor.process(
                        calibrateJaw(lastJawOpen)
                    )

                if (mouthOpen < settings.deadZone) {
                    mouthOpen = 0f
                }

                val processedValue =
                    if (settings.invert)
                        1f - mouthOpen
                    else
                        mouthOpen

                val cc =
                    (settings.minCC + processedValue * (settings.maxCC - settings.minCC))
                        .toInt()
                        .coerceIn(settings.minCC, settings.maxCC)


            if (trackingEnabled) {
                updateCC(cc)
            } else {

                if (settings.holdLastValue) {
                    updateCC(lastCC)
                } else {
                    updateCC(0)
                }
            }

        }
    }







    private fun calibrateJaw(value: Float): Float {

        val range = settings.jawOpenCalibration - settings.jawClosedCalibration

        if (range <= 0f) return 0f

        return ((value - settings.jawClosedCalibration) / range)
            .coerceIn(0f, 1f)
    }



    private fun updateCC(
        value:Int
    ){

        lastCC = value

        midiOutputManager.sendCC(
            settings.midiChannel,
            settings.midiCC,
            value
        )

        ccValue.text =
            value
                .toString()
                .padStart(3,'0')


        ccMeter.setValue(value)
    }



    override fun onBackPressed() {

        if (::settingsContainer.isInitialized &&
            settingsContainer.visibility == View.VISIBLE) {

            saveSettings()

            settingsContainer.visibility = View.GONE

        } else {

            super.onBackPressed()

        }
    }


    override fun onDestroy() {

        super.onDestroy()

        cameraExecutor.shutdown()


        midiOutputManager.cleanup()
        faceLandmarker?.close()
    }

    private fun toggleSettings() {

        if (!::settingsContainer.isInitialized) {
            settingsContainer = findViewById(R.id.settingsContainer)
        }

        if (settingsView == null) {

            settingsView =
                LayoutInflater.from(this)
                    .inflate(
                        R.layout.activity_settings,
                        settingsContainer,
                        false
                    )

            settingsView?.layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

            settingsContainer.addView(settingsView)

            setupSettingsControls()

            setupSettingsSwipe()
        }

        if (settingsContainer.visibility == View.VISIBLE) {

            saveSettings()
            settingsContainer.visibility = View.GONE

        } else {

            settingsContainer.visibility = View.VISIBLE
        }
    }


    private fun setupSettingsSwipe() {

        val view = settingsView ?: return

        settingsScrollView =
            view.findViewById(R.id.settingsScrollView)

        var downY = 0f

        view.setOnTouchListener { _, event ->

            when (event.action) {

                android.view.MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    true
                }

                android.view.MotionEvent.ACTION_UP -> {

                    val diff = event.rawY - downY

                    if (diff > 200 && settingsScrollView.scrollY == 0) {

                        saveSettings()

                        settingsContainer.visibility =
                            View.GONE

                        true

                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    
    private fun updateWifiSettingsVisibility() {

        if (wifiTransportRadio.isChecked) {
            wifiSettingsContainer.visibility = View.VISIBLE
        } else {
            wifiSettingsContainer.visibility = View.GONE
        }

    }

    private fun setupSettingsControls() {

        val view = settingsView ?: return

        flashlightSwitch = view.findViewById(R.id.flashlightSwitch)

        flashlightSwitch.setOnCheckedChangeListener { _, checked ->
            settings.flashlightEnabled = checked
            cameraInstance?.cameraControl?.enableTorch(checked)
        }

        keepScreenAwakeSwitch = view.findViewById(R.id.keepScreenAwakeSwitch)

        keepScreenAwakeSwitch.setOnCheckedChangeListener { _, checked ->
            Log.d("MouthMIDI", "KEEP_AWAKE_TOGGLED=" + checked)

            settings.keepScreenAwake = checked
            settingsRepository.save(settings)

            if (checked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        midiChannelInput = view.findViewById(R.id.midiChannelInput)
        midiCCInput = view.findViewById(R.id.midiCCInput)

        jawClosedCalibrationInput = view.findViewById(R.id.jawClosedCalibrationInput)
        jawOpenCalibrationInput = view.findViewById(R.id.jawOpenCalibrationInput)

        smoothingSeekBar = view.findViewById(R.id.smoothingSeekBar)
        minCCSeekBar = view.findViewById(R.id.minCCSeekBar)
        maxCCSeekBar = view.findViewById(R.id.maxCCSeekBar)

        deadZoneSeekBar = view.findViewById(R.id.deadZoneSeekBar)


        smoothingValueText = view.findViewById(R.id.smoothingValueText)
        minCCValueText = view.findViewById(R.id.minCCValueText)
        maxCCValueText = view.findViewById(R.id.maxCCValueText)

        deadZoneValueText = view.findViewById(R.id.deadZoneValueText)

        invertSwitch = view.findViewById(R.id.invertSwitch)
        holdLastValueSwitch = view.findViewById(R.id.holdLastValueSwitch)

        usbTransportRadio = view.findViewById(R.id.usbTransportRadio)
        wifiTransportRadio = view.findViewById(R.id.wifiTransportRadio)

          usbTransportRadio.setOnCheckedChangeListener { _, _ ->
              updateWifiSettingsVisibility()
          }

          wifiTransportRadio.setOnCheckedChangeListener { _, _ ->
              updateWifiSettingsVisibility()
          }

          wifiSettingsContainer =
              view.findViewById(R.id.wifiSettingsContainer)

          wifiHostInput =
              view.findViewById(R.id.wifiHostInput)

          wifiPortInput =
              view.findViewById(R.id.wifiPortInput)

          wifiSessionInput =
              view.findViewById(R.id.wifiSessionInput)

        recalibrateButton =
            view.findViewById(R.id.recalibrateButton)

        defaultCalibrationButton =
            view.findViewById(R.id.defaultCalibrationButton)

        calibrationStatusText =
            view.findViewById(R.id.calibrationStatusText)

        presetSpinner =
            view.findViewById(R.id.presetSpinner)

        
        presetSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {

                    val name =
                        presetSpinner.selectedItem.toString()

                    if (!updatingPresetSpinner) {

                        if (name == "Default") {
                            loadDefaultSettings()
                        } else {
                            applyPreset(name)
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        savePresetButton =
            view.findViewById(R.id.savePresetButton)

        loadPresetButton =
            view.findViewById(R.id.loadPresetButton)


        themeOrangeButton = view.findViewById(R.id.themeOrangeButton)
        themeYellowButton = view.findViewById(R.id.themeYellowButton)
        themeGreenButton = view.findViewById(R.id.themeGreenButton)
        themeBlueButton = view.findViewById(R.id.themeBlueButton)
        themePurpleButton = view.findViewById(R.id.themePurpleButton)

        themeOrangeButton.setOnClickListener {
            settings.themeColor = "orange"
            settingsRepository.save(settings)
        }

        themeYellowButton.setOnClickListener {
            settings.themeColor = "yellow"
            settingsRepository.save(settings)
        }

        themeGreenButton.setOnClickListener {
            settings.themeColor = "green"
            settingsRepository.save(settings)
        }

        themeBlueButton.setOnClickListener {
            settings.themeColor = "blue"
            settingsRepository.save(settings)
        }

        themePurpleButton.setOnClickListener {
            settings.themeColor = "purple"
            settingsRepository.save(settings)
        }

        val youtubeLinkText =
            view.findViewById<TextView>(R.id.youtubeLinkText)

        youtubeLinkText.paintFlags =
            youtubeLinkText.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        youtubeLinkText.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/@rizwanshakir")
                )
            )
        }



        defaultCalibrationButton.setOnClickListener {

            settings.jawClosedCalibration = 0.01f
            settings.jawOpenCalibration = 0.80f

            settingsRepository.save(settings)

            loadSettingsUI()
        }





        recalibrateButton.text =
            "Recalibrate Mouth Range"

        recalibrateButton.setOnClickListener {

            calibrationMode = true
            calibrationStep = 1

            calibrationMin = 1f
            calibrationMax = 0f
            calibrationStartTime = System.currentTimeMillis()





        recalibrateButton.text =
                "Close mouth..."

        }


        savePresetButton.setOnClickListener {

            val input = EditText(this)

            AlertDialog.Builder(this)
                .setTitle("Save Preset")
                .setMessage("Preset name")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->

                    val name =
                        input.text.toString().trim()

                    if (name.isNotEmpty()) {

                        saveSettings()

                        settingsRepository.savePreset(
                            name,
                            currentPresetSettings()
                        )

                        settingsRepository.saveLastPreset(name)

                          refreshPresetSpinner()

                        calibrationStatusText.text =
                              ""
                    }

                }
                .setNegativeButton("Cancel", null)
                .show()
        }


        loadPresetButton.setOnClickListener {

            val name =
                presetSpinner.selectedItem.toString()

            if (name == "Default") {
                return@setOnClickListener
            }

            settingsRepository.deletePreset(name)

            settingsRepository.saveLastPreset("Default")

            refreshPresetSpinner()

            presetSpinner.setSelection(0)

        }



        refreshPresetSpinner()

        setupSliderListeners()

        loadSettingsUI()
    }


      private fun setupSliderListeners() {

          smoothingSeekBar.setOnSeekBarChangeListener(
              object : SeekBar.OnSeekBarChangeListener {
                  override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                      smoothingValueText.text =
                          String.format("%.2f", progress / 100f)
                  }
                  override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                  override fun onStopTrackingTouch(seekBar: SeekBar?) {}
              }
          )

          minCCSeekBar.setOnSeekBarChangeListener(
              object : SeekBar.OnSeekBarChangeListener {
                  override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                      minCCValueText.text = progress.toString()
                  }
                  override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                  override fun onStopTrackingTouch(seekBar: SeekBar?) {}
              }
          )

            maxCCSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        maxCCValueText.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )

          deadZoneSeekBar.setOnSeekBarChangeListener(
              object : SeekBar.OnSeekBarChangeListener {
                  override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                      deadZoneValueText.text =
                          String.format("%.2f", progress / 100f)
                  }
                  override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                  override fun onStopTrackingTouch(seekBar: SeekBar?) {}
              }
          )

      }


    private fun refreshPresetSpinner() {

        val names =
            mutableListOf("Default")

        names.addAll(
            settingsRepository.getPresetNames()
        )

        val lastPreset =
            settingsRepository.getLastPreset()

        updatingPresetSpinner = true

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names
            )

        presetSpinner.adapter = adapter

        val index =
            names.indexOf(lastPreset)

        if (index >= 0) {
            presetSpinner.setSelection(index)
        } else {
            presetSpinner.setSelection(0)
        }

        updatingPresetSpinner = false
    }



    private fun currentPresetSettings(): PresetSettings {

        return PresetSettings(

            jawClosedCalibration =
                settings.jawClosedCalibration,

            jawOpenCalibration =
                settings.jawOpenCalibration,

            minCC =
                settings.minCC,

            maxCC =
                settings.maxCC,

            midiCC =
                settings.midiCC,

            midiChannel =
                settings.midiChannel,

            smoothing =
                settings.smoothing,

              deadZone =
                  settings.deadZone,


            invert =
                settings.invert,

            holdLastValue =
                settings.holdLastValue,

            themeColor =
                settings.themeColor
        )
    }

    

      private fun loadDefaultSettings() {

          settings.jawClosedCalibration = 0.01f
          settings.jawOpenCalibration = 0.80f

          settings.minCC = 0
          settings.maxCC = 127

          settings.midiCC = 1
          settings.midiChannel = 1

          settings.smoothing = 0f
          settings.deadZone = 0f

          settings.invert = false
          settings.holdLastValue = true

          settings.themeColor = "orange"

          settingsRepository.save(settings)

          loadSettingsUI()

          settingsRepository.saveLastPreset("Default")
      }


      private fun applyPreset(name: String) {

          val preset =
              settingsRepository.loadPreset(name)

          settings.jawClosedCalibration =
              preset.jawClosedCalibration

          settings.jawOpenCalibration =
              preset.jawOpenCalibration

          settings.minCC =
              preset.minCC

          settings.maxCC =
              preset.maxCC

          settings.midiCC =
              preset.midiCC

          settings.midiChannel =
              preset.midiChannel

          settings.smoothing =
              preset.smoothing

          settings.deadZone =
              preset.deadZone

          settings.invert =
              preset.invert

          settings.holdLastValue =
              preset.holdLastValue

          settings.themeColor =
              preset.themeColor

          settingsRepository.save(settings)

          settingsRepository.saveLastPreset(name)

          loadSettingsUI()
      }


private fun loadSettingsUI() {

          midiChannelInput.setText(settings.midiChannel.toString())
          midiCCInput.setText(settings.midiCC.toString())

          jawClosedCalibrationInput.setText(
              String.format("%.2f", settings.jawClosedCalibration)
          )

        jawOpenCalibrationInput.setText(
            String.format("%.2f", settings.jawOpenCalibration)
        )


        smoothingSeekBar.progress =
            (settings.smoothing * 100).toInt()

        smoothingValueText.text =
            String.format("%.2f", settings.smoothing)


        minCCSeekBar.progress =
            settings.minCC

        minCCValueText.text =
            settings.minCC.toString()


          maxCCSeekBar.progress =
              settings.maxCC

          maxCCValueText.text =
              settings.maxCC.toString()

          deadZoneSeekBar.progress =
              (settings.deadZone * 100).toInt()

          deadZoneValueText.text =
              String.format("%.2f", settings.deadZone)



        invertSwitch.isChecked = settings.invert
        holdLastValueSwitch.isChecked = settings.holdLastValue


        flashlightSwitch.isChecked = settings.flashlightEnabled
        keepScreenAwakeSwitch.isChecked = settings.keepScreenAwake

        if (settings.transport == "WIFI") {
              wifiTransportRadio.isChecked = true
          } else {
              usbTransportRadio.isChecked = true
          }

          wifiHostInput.setText(
              settings.wifiHost
          )

          wifiPortInput.setText(
              settings.wifiPort.toString()
          )

          wifiSessionInput.setText(
              settings.wifiSessionName
          )

          updateWifiSettingsVisibility()

    }

    private fun saveSettings() {


        settings.midiChannel =
            midiChannelInput.text.toString().toIntOrNull()
                ?.coerceIn(1,16) ?: 1

        settings.midiCC =
            midiCCInput.text.toString().toIntOrNull()
                ?.coerceIn(0,127) ?: 1

        settings.smoothing =
            smoothingSeekBar.progress / 100f

        settings.minCC =
            minCCSeekBar.progress.coerceIn(0,127)

        settings.maxCC =
            maxCCSeekBar.progress.coerceIn(0,127)

        settings.deadZone =
            deadZoneSeekBar.progress / 100f


        settings.invert =
            invertSwitch.isChecked

        settings.holdLastValue =
            holdLastValueSwitch.isChecked

        settings.keepScreenAwake =
            keepScreenAwakeSwitch.isChecked

        settings.flashlightEnabled = flashlightSwitch.isChecked

        settings.transport =
            if (wifiTransportRadio.isChecked) "WIFI" else "USB"

          settings.wifiHost =
              wifiHostInput.text.toString()

          settings.wifiPort =
              wifiPortInput.text.toString()
                  .toIntOrNull() ?: 5004

          settings.wifiSessionName =
              wifiSessionInput.text.toString()

        settingsRepository.save(settings)

        smoothingProcessor = SmoothingProcessor(settings.smoothing)

        midiStatus.text =
            "CC${settings.midiCC} CH${settings.midiChannel}"
    }

}

