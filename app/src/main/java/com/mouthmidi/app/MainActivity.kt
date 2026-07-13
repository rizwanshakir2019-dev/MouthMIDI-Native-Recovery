package com.mouthmidi.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.util.Log
import android.widget.TextView
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
    private var lastCC = 0

    private var useFrontCamera = true

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
            startActivity(Intent(this, SettingsActivity::class.java))
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


            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                analyzer
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

                updateCC(lastCC)

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
                }
            }


              val mouthOpen =
                smoothingProcessor.process(
                    calibrateJaw(lastJawOpen)
                )



              val cc =
                  (settings.minCC + mouthOpen * (settings.maxCC - settings.minCC))
                      .toInt()
                      .coerceIn(settings.minCC, settings.maxCC)


            if (trackingEnabled) {
                updateCC(cc)
            } else {
                updateCC(lastCC)
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



    override fun onDestroy() {

        super.onDestroy()

        cameraExecutor.shutdown()


        midiOutputManager.cleanup()
        faceLandmarker?.close()
    }
}
