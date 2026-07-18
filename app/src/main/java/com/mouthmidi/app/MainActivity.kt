package com.mouthmidi.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.util.Size
import android.widget.AdapterView
import android.widget.Switch
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.util.Log
import android.widget.TextView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.app.AlertDialog
import android.widget.Button
import android.widget.LinearLayout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
    private lateinit var settingsRootLayout: LinearLayout

    private lateinit var presetCard: View
    private lateinit var midiCard: View
    private lateinit var calibrationCard: View
    private lateinit var trackingCard: View
    private lateinit var deviceCard: View
    private lateinit var themeCard: View
    private lateinit var transportCard: View
    private lateinit var mainOverlay: View
    private lateinit var statusBar: View

    private lateinit var presetSpinner: Spinner
    private var updatingPresetSpinner = false
    private lateinit var savePresetButton: Button
    private lateinit var loadPresetButton: Button
    private lateinit var wifiConnectButton: Button

    private lateinit var themeOrangeButton: View
    private lateinit var themeYellowButton: View
    private lateinit var themeGreenButton: View
    private lateinit var themeBlueButton: View
    private lateinit var themePurpleButton: View

    private lateinit var settingsContainer: FrameLayout
    private var settingsView: View? = null

    private lateinit var flashlightSwitch: Switch
    private lateinit var keepScreenAwakeSwitch: Switch

    private lateinit var cameraResolutionLowRadio: RadioButton
    private lateinit var cameraResolutionBalancedRadio: RadioButton
    private lateinit var cameraResolutionHighRadio: RadioButton
    private lateinit var midiChannelInput: EditText
    private lateinit var midiCCInput: EditText
    private lateinit var jawClosedCalibrationInput: TextView
    private lateinit var jawOpenCalibrationInput: TextView
    private lateinit var attackSpeedSeekBar: SeekBar
    private lateinit var releaseSpeedSeekBar: SeekBar
    private lateinit var smoothingSeekBar: SeekBar
    private lateinit var minCCSeekBar: SeekBar
    private lateinit var maxCCSeekBar: SeekBar
    private lateinit var deadZoneSeekBar: SeekBar

    private lateinit var attackSpeedValueText: TextView
    private lateinit var releaseSpeedValueText: TextView
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

    private lateinit var attackReleaseProcessor: AttackReleaseProcessor

    private lateinit var smoothingProcessor: SmoothingProcessor

    private lateinit var settingsRepository: SettingsRepository

    private lateinit var midiTransport: MidiTransport



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
    private var lastSentCC = -1
    private val ccChangeThreshold = 2

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
                faceStatus.text = statusText(Color.GRAY, "Camera Denied")
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        settingsRepository = SettingsRepository(this)

        settings = settingsRepository.load()

        attackReleaseProcessor = AttackReleaseProcessor()

        smoothingProcessor = SmoothingProcessor()
        smoothingProcessor.setAmount(settings.smoothing)

        if (settings.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        previewView = findViewById(R.id.cameraPreview)
        faceStatus = findViewById(R.id.faceStatus)
        midiStatus = findViewById(R.id.midiStatus)
        midiStatus.text = String.format("CH%02d CC%02d", settings.midiChannel, settings.midiCC)
        outputStatus = findViewById(R.id.outputStatus)

        midiTransport =
            if (settings.transport == "WIFI") {

                RtpMidiTransport(
                    settings.wifiHost,
                    settings.wifiPort,
                    settings.wifiSessionName
                ) { connected ->

                    runOnUiThread {

                        outputStatus.text =
                            if (connected)
                                statusText(Color.GREEN, "WiFi")
                            else
                                statusText(Color.GRAY, "No MIDI")
                    }
                }

            } else {

                UsbMidiTransport(this) { connected ->

                    runOnUiThread {

                        outputStatus.text =
                            if (connected)
                                statusText(Color.GREEN, "USB")
                            else
                                statusText(Color.GRAY, "No MIDI")
                    }
                }
            }

        midiTransport.connect()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if (settings.transport == "WIFI" && midiTransport is RtpMidiTransport) {
                    val rtp = midiTransport as RtpMidiTransport

                    outputStatus.text =
                        "C=" + rtp.isActuallyConnected() +
                        " S=" + rtp.getSendCallCount() +
                        " P=" + rtp.getPacketCount() +
                        " Q=" + rtp.getQueueSize() +
                        " E=" + rtp.getLastError()
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1000)
            }
        }, 1000)







        ccValue = findViewById(R.id.ccValue)
        ccMeter = findViewById(R.id.ccMeter)

        mainOverlay = findViewById(R.id.mainOverlay)
        statusBar = findViewById(R.id.statusBar)

            mainOverlay.post {

                val screenHeight =
                    resources.displayMetrics.heightPixels

                val topHeight =
                    statusBar.height

                val bottomHeight =
                    mainOverlay.height

                val availableHeight =
                    screenHeight -
                    topHeight -
                    bottomHeight -
                    (24 * resources.displayMetrics.density).toInt()

                val isLandscape =
                    resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE

                val topOffset =
                    if (isLandscape) {
                        (availableHeight * 0.3186f).toInt()
                    } else {
                        (availableHeight * 0.1028f).toInt()
                    }

                ccMeter.translationY = -topOffset.toFloat()

                ccMeter.layoutParams =
                    ccMeter.layoutParams.apply {
                        width = (26 * resources.displayMetrics.density).toInt()
                        height = availableHeight
                    }

                ccMeter.requestLayout()


            }

        cameraButton = findViewById(R.id.cameraButton)
        startButton = findViewById(R.id.startButton)

        settingsButton = findViewById(R.id.settingsButton)

        addButtonPressAnimation(cameraButton)
        addButtonPressAnimation(startButton)
        addButtonPressAnimation(settingsButton)

        applyTheme()


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


    private fun statusText(
        dotColor: Int,
        text: String
    ): SpannableString {

        val value = "●$text"
        val spannable = SpannableString(value)

        spannable.setSpan(
            ForegroundColorSpan(dotColor),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
            RelativeSizeSpan(0.65f),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannable
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
                    .setTargetResolution(
                        when (settings.cameraResolution) {
                            "LOW" ->
                                Size(320, 240)

                            "HIGH" ->
                                Size(1280, 720)

                            else ->
                                Size(640, 480)
                        }
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
                    statusText(Color.GRAY, "No Face")

                if (settings.holdLastValue) {
                    updateCC(lastCC)
                } else {
                    updateCC(0)
                }

                return@runOnUiThread
            }


            faceStatus.text =
                statusText(Color.GREEN, "Face")


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

            applyTheme()





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


                val smoothedJaw =
                      smoothingProcessor.process(
                          calibrateJaw(lastJawOpen)
                      )

                var mouthOpen =
                      attackReleaseProcessor.process(
                          smoothedJaw,
                          settings.attackSpeed,
                          settings.releaseSpeed
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

        if (lastSentCC >= 0 && kotlin.math.abs(value - lastSentCC) < ccChangeThreshold) {
            lastCC = value
            return
        }
        lastSentCC = value
        lastCC = value

        midiTransport.sendCC(
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


        midiTransport.disconnect()
        faceLandmarker?.close()
    }

    private fun toggleSettings() {

        if (!::settingsContainer.isInitialized) {
            settingsContainer = findViewById(R.id.settingsContainer)
            applyTheme()
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

            settingsView?.post {
                                applyTheme()
            }


            setupSettingsSwipe()
        }

        if (settingsContainer.visibility == View.VISIBLE) {

            saveSettings()
            settingsContainer.visibility = View.GONE

        } else {

            settingsContainer.alpha = 1f
            settingsContainer.visibility = View.VISIBLE
        }
    }


    private fun setupSettingsSwipe() {

        val view = settingsView ?: return

        settingsRootLayout = view.findViewById(R.id.settingsRootLayout)

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

        settingsRootLayout = view.findViewById(R.id.settingsRootLayout)

        presetCard = view.findViewById(R.id.presetCard)
        midiCard = view.findViewById(R.id.midiCard)
        calibrationCard = view.findViewById(R.id.calibrationCard)
        trackingCard = view.findViewById(R.id.trackingCard)
        deviceCard = view.findViewById(R.id.deviceCard)
        themeCard = view.findViewById(R.id.themeCard)
        transportCard = view.findViewById(R.id.transportCard)

        settingsRootLayout.removeView(themeCard)

        val deviceIndex =
            settingsRootLayout.indexOfChild(deviceCard)

        settingsRootLayout.addView(
            themeCard,
            deviceIndex
        )



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

        cameraResolutionLowRadio =
            view.findViewById(R.id.cameraResolutionLowRadio)

        cameraResolutionBalancedRadio =
            view.findViewById(R.id.cameraResolutionBalancedRadio)

        cameraResolutionHighRadio =
            view.findViewById(R.id.cameraResolutionHighRadio)

        cameraResolutionLowRadio.setOnClickListener {
            settings.cameraResolution = "LOW"
            settingsRepository.save(settings)
        }

        cameraResolutionBalancedRadio.setOnClickListener {
            settings.cameraResolution = "BALANCED"
            settingsRepository.save(settings)
        }

        cameraResolutionHighRadio.setOnClickListener {
            settings.cameraResolution = "HIGH"
            settingsRepository.save(settings)
        }

        midiChannelInput = view.findViewById(R.id.midiChannelInput)
        midiCCInput = view.findViewById(R.id.midiCCInput)

        jawClosedCalibrationInput = view.findViewById(R.id.jawClosedCalibrationInput)
        jawOpenCalibrationInput = view.findViewById(R.id.jawOpenCalibrationInput)

          smoothingSeekBar =
              view.findViewById(R.id.smoothingSeekBar)

          smoothingValueText =
              view.findViewById(R.id.smoothingValueText)

          attackSpeedSeekBar =
              view.findViewById(R.id.attackSeekBar)

          releaseSpeedSeekBar =
              view.findViewById(R.id.releaseSeekBar)

          attackSpeedValueText =
              view.findViewById(R.id.attackValueText)

          releaseSpeedValueText =
              view.findViewById(R.id.releaseValueText)


        minCCSeekBar = view.findViewById(R.id.minCCSeekBar)
        maxCCSeekBar = view.findViewById(R.id.maxCCSeekBar)

        deadZoneSeekBar = view.findViewById(R.id.deadZoneSeekBar)


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

        wifiConnectButton =
            view.findViewById(R.id.wifiConnectButton)


        

        themeOrangeButton = view.findViewById(R.id.themeOrangeButton)
        themeYellowButton = view.findViewById(R.id.themeYellowButton)
        themeGreenButton = view.findViewById(R.id.themeGreenButton)
        themeBlueButton = view.findViewById(R.id.themeBlueButton)
        themePurpleButton = view.findViewById(R.id.themePurpleButton)

        themeOrangeButton.setOnClickListener {
            settings.themeColor = "pink"
            settingsRepository.save(settings)
            applyTheme()
        }

        themeYellowButton.setOnClickListener {
            settings.themeColor = "purple"
            settingsRepository.save(settings)
            applyTheme()
        }

        themeGreenButton.setOnClickListener {
            settings.themeColor = "cyan"
            settingsRepository.save(settings)
            applyTheme()
        }

        themeBlueButton.setOnClickListener {
            settings.themeColor = "green"
            settingsRepository.save(settings)
            applyTheme()
        }

        themePurpleButton.setOnClickListener {
            settings.themeColor = "gold"
            settingsRepository.save(settings)
            applyTheme()
        }
        val rsLogo =
            view.findViewById<ImageView>(R.id.rsLogo)

        val facebookLinkIcon =
            view.findViewById<ImageView>(R.id.facebookLinkIcon)

        val youtubeLinkIcon =
            view.findViewById<ImageView>(R.id.youtubeLinkIcon)

        val spotifyLinkIcon =
            view.findViewById<ImageView>(R.id.spotifyLinkIcon)

        rsLogo.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://lnkfi.re/riz1shakir")
                )
            )
        }

        facebookLinkIcon.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.facebook.com/Riz1Shakir")
            )
            intent.setPackage("com.android.chrome")
            startActivity(intent)
        }

        youtubeLinkIcon.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/@rizwanshakir")
                )
            )
        }

        spotifyLinkIcon.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/artist/2NI1LUvwQOLJ7JtlWPBETo?si=8a1fb1a76f7a430c")
                )
            )
        }



        defaultCalibrationButton.setOnClickListener {

            settings.jawClosedCalibration = 0.01f
            settings.jawOpenCalibration = 0.80f

            settingsRepository.save(settings)

            loadSettingsUI()

            applyTheme()
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

                        refreshPresetSpinner()

                        val adapter = presetSpinner.adapter
                        for (i in 0 until adapter.count) {
                            if (adapter.getItem(i).toString() == name) {
                                presetSpinner.setSelection(i)
                                break
                            }
                        }

          settingsRepository.saveLastPreset(name)

          loadSettingsUI()

            applyTheme()


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

        val lastPreset =
            settingsRepository.getLastPreset()

        if (lastPreset == "Default") {
            loadDefaultSettings()
        } else {
            applyPreset(lastPreset)

            applyTheme()

        }

    }


      private fun setupSliderListeners() {
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
            smoothingSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        smoothingValueText.text =
                            String.format("%.2f", progress / 100f)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )

            attackSpeedSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        attackSpeedValueText.text =
                            String.format("%.2f", (progress - 50) / 33.333f)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )


            releaseSpeedSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        releaseSpeedValueText.text =
                            String.format("%.2f", (progress - 50) / 33.333f)
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
              attackSpeed =
                  settings.attackSpeed,

              releaseSpeed =
                  settings.releaseSpeed,

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
          settings.attackSpeed = 0f
            settings.releaseSpeed = 0f
          settings.deadZone = 0f

          settings.invert = false
          settings.holdLastValue = true

          settings.themeColor = "pink"

          settingsRepository.save(settings)

          loadSettingsUI()

            applyTheme()

          settingsRepository.saveLastPreset("Default")

      }


      private fun applyPreset(name: String) {

          val preset =
              settingsRepository.loadPreset(name)

          println("DEBUG APPLY PRESET: $name theme=${preset.themeColor}")


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

        settings.attackSpeed =
                preset.attackSpeed

            settings.releaseSpeed =
                preset.releaseSpeed

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

            applyTheme()

      }



    private fun getThemeColor(): Int {
        return when (settings.themeColor.lowercase()) {
            "pink" -> Color.parseColor("#9A324F")
            "purple" -> Color.parseColor("#5C3FA2")
            "cyan" -> Color.parseColor("#32867E")
            "green" -> Color.parseColor("#599834")
            "gold" -> Color.parseColor("#B56C2C")
            else -> Color.parseColor("#9A324F")
        }
    }


    private fun applyCardTheme(view: View, color: Int) {

        if (view.background is GradientDrawable) {

            val drawable =
                view.background as GradientDrawable

            drawable.setColor(color)
        }

        if (view is ViewGroup) {

            for (i in 0 until view.childCount) {

                applyCardTheme(
                    view.getChildAt(i),
                    color
                )
            }
        }
    }



    private fun setCardBackground(
        view: View,
        color: Int
    ) {

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(color)
            setStroke(
                1,
                Color.parseColor("#444444")
            )
        }

        view.background = drawable
    }


    private fun getOverlayColor(cardColor: Int): Int {

        val r = (Color.red(cardColor) * 0.55).toInt()
        val g = (Color.green(cardColor) * 0.55).toInt()
        val b = (Color.blue(cardColor) * 0.55).toInt()

        return Color.argb(
            220,
            r,
            g,
            b
        )
    }


    private fun updateThemeSelection() {

        val buttons = listOf(
            themeOrangeButton,
            themeYellowButton,
            themeGreenButton,
            themeBlueButton,
            themePurpleButton
        )


        val selected =
            when (settings.themeColor.lowercase()) {

                "pink" -> themeOrangeButton
                "purple" -> themeYellowButton
                "cyan" -> themeGreenButton
                "green" -> themeBlueButton
                "gold" -> themePurpleButton

                else -> themeOrangeButton
            }


        buttons.forEach { button ->

            if (button == selected) {
                button.scaleX = 1.0f
                button.scaleY = 1.0f
            } else {
                button.scaleX = 0.91f
                button.scaleY = 0.91f
            }
        }
    }


    private fun getButtonFillColor(): Int {

        return when (settings.themeColor.lowercase()) {

            "pink" ->
                Color.parseColor("#24131D")

            "purple" ->
                Color.parseColor("#171022")

            "cyan" ->
                Color.parseColor("#102126")

            "green" ->
                Color.parseColor("#102117")

            "gold" ->
                Color.parseColor("#241D10")

            else ->
                Color.parseColor("#161620")
        }
    }



    private fun addButtonPressAnimation(button: Button) {

        button.setOnTouchListener { v, event ->

            when (event.action) {

                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(60)
                        .start()
                }

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                }
            }

            false
        }
    }


    private fun applyTheme() {

          println("DEBUG APPLY THEME: ${settings.themeColor} start=${::startButton.isInitialized} camera=${::cameraButton.isInitialized} settings=${::settingsButton.isInitialized}")

        val color = getThemeColor()

        println(
            "CARD DEBUG preset=" +
            ::presetCard.isInitialized +
            " midi=" +
            ::midiCard.isInitialized
        )

        val cardColor =
            when (settings.themeColor.lowercase()) {
                "pink" ->
                    Color.parseColor("#662B1825")

                "purple" ->
                    Color.parseColor("#66302055")

                "cyan" ->
                    Color.parseColor("#66204444")

                "green" ->
                    Color.parseColor("#66274420")

                "gold" ->
                    Color.parseColor("#66452D18")

                else ->
                    Color.parseColor("#66202030")
            }

        if (::presetCard.isInitialized) {

            println("THEME APPLYING CARD COLORS = $cardColor")

            println(
                "CARD SIZE preset=" +
                presetCard.width +
                " height=" +
                presetCard.height
            )


            println("THEME APPLYING CARD COLORS = $cardColor")

            println(
                "CARD SIZE preset=" +
                presetCard.width +
                " height=" +
                presetCard.height
            )

            setCardBackground(presetCard, cardColor)
            setCardBackground(midiCard, cardColor)
            setCardBackground(calibrationCard, cardColor)
            setCardBackground(trackingCard, cardColor)
            setCardBackground(deviceCard, cardColor)
            setCardBackground(themeCard, cardColor)
            setCardBackground(transportCard, cardColor)

        } else {

            println("CARD VIEWS NOT INITIALIZED")

        }

        val overlayColor = getOverlayColor(cardColor)

        if (::mainOverlay.isInitialized) {
            mainOverlay.setBackgroundColor(overlayColor)
        }

        if (::statusBar.isInitialized) {
            statusBar.setBackgroundColor(overlayColor)
        }

        if (::settingsContainer.isInitialized) {
            settingsContainer.setBackgroundColor(overlayColor)
        }

        if (::settingsScrollView.isInitialized) {
            settingsScrollView.setBackgroundColor(Color.TRANSPARENT)
        }

        if (::settingsRootLayout.isInitialized) {
            settingsRootLayout.setBackgroundColor(Color.TRANSPARENT)
        }

        ccValue.setTextColor(Color.WHITE)
        midiStatus.setTextColor(Color.WHITE)

        val startButtonDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            cornerRadius = 38f
            setColor(color)
            setStroke(1, Color.WHITE)
        }

        startButton.backgroundTintList = null

          startButton.background =
            startButtonDrawable

        cameraButton.setTextColor(color)
        settingsButton.setTextColor(color)

        val buttonDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            cornerRadius = 30f
            setColor(getButtonFillColor())
            setStroke(1, Color.WHITE)
        }

        cameraButton.backgroundTintList = null
        settingsButton.backgroundTintList = null

        cameraButton.background =
            buttonDrawable.constantState?.newDrawable()

        settingsButton.background =
            buttonDrawable.constantState?.newDrawable()

        val settingsButtonDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(color)
            setStroke(1, Color.WHITE)
        }


        if (::savePresetButton.isInitialized) {
            savePresetButton.background =
                settingsButtonDrawable.constantState?.newDrawable()

            loadPresetButton.background =
                settingsButtonDrawable.constantState?.newDrawable()

            wifiConnectButton.background =
                settingsButtonDrawable.constantState?.newDrawable()

            recalibrateButton.background =
                settingsButtonDrawable.constantState?.newDrawable()

            defaultCalibrationButton.background =
                settingsButtonDrawable.constantState?.newDrawable()
        }


    
          if (
              ::themeOrangeButton.isInitialized &&
              ::themeYellowButton.isInitialized &&
              ::themeGreenButton.isInitialized &&
              ::themeBlueButton.isInitialized &&
              ::themePurpleButton.isInitialized
          ) {
              updateThemeSelection()
          }


}
private fun loadSettingsUI() {

            applyTheme()
            updateThemeSelection()

          midiChannelInput.setText(settings.midiChannel.toString())
          midiCCInput.setText(settings.midiCC.toString())

          jawClosedCalibrationInput.setText(
              String.format("%.2f", settings.jawClosedCalibration)
          )

        jawOpenCalibrationInput.setText(
            String.format("%.2f", settings.jawOpenCalibration)
        )


        smoothingSeekBar.progress =
            (settings.smoothing * 100f).toInt()

        smoothingValueText.text =
            String.format("%.2f", settings.smoothing)

        attackSpeedSeekBar.progress =
            ((settings.attackSpeed * 33.333f) + 50).toInt()

        releaseSpeedSeekBar.progress =
            ((settings.releaseSpeed * 33.333f) + 50).toInt()

        attackSpeedValueText.text =
            String.format("%.2f", settings.attackSpeed)

        releaseSpeedValueText.text =
            String.format("%.2f", settings.releaseSpeed)


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

        cameraResolutionLowRadio.isChecked =
            settings.cameraResolution == "LOW"

        cameraResolutionBalancedRadio.isChecked =
            settings.cameraResolution == "BALANCED"

        cameraResolutionHighRadio.isChecked =
            settings.cameraResolution == "HIGH"

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

        smoothingProcessor.setAmount(
            settings.smoothing
        )

        settings.attackSpeed =
              (attackSpeedSeekBar.progress - 50) / 33.333f

          settings.releaseSpeed =
              (releaseSpeedSeekBar.progress - 50) / 33.333f

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


        midiStatus.text = String.format("CH%02d CC%02d", settings.midiChannel, settings.midiCC)
    }

}

