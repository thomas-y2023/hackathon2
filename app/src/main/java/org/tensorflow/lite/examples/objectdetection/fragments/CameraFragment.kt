/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import org.tensorflow.lite.examples.objectdetection.AdRecord
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import org.tensorflow.lite.examples.objectdetection.MyCameraFilter
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private var player: SimpleExoPlayer? = null

    private var resultList = LinkedList<String>()
    private val sampleSize = 10
    private val validFactor = 0.5

    private var playerIsPlaying: Boolean = false
    private var playerUrlSet: Boolean = false

    private var adsData = ArrayList<AdRecord>()
    private var tagWithAdsUrlMap = HashMap<String, MutableList<String>>()
    private var random = Random();
    private var nextAdKeywords = "";

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

        player?.release()
    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        player = SimpleExoPlayer.Builder(requireActivity()).build();
        player?.setVideoSurfaceView(fragmentCameraBinding.surfaceView)
        player?.playWhenReady = true

        player?.addListener(object: Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                super.onPlayerStateChanged(playWhenReady, playbackState)
                if (playWhenReady && playbackState == Player.STATE_READY) {
                    Log.i("test---", "start play")
                    playerIsPlaying = true;
                } else if (playbackState == Player.STATE_ENDED) {
                    Log.i("test---", "play end")
                    playerIsPlaying = false;
                    playerUrlSet = false;
                    fragmentCameraBinding.curAdsKeywords.text = ""
                } else {
                    // buffering
                    Log.i("test---", "buffering")
                }
            }
        })

        // init ads data
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/airplane_backpack_handbag.ts", listOf("airplane", "backpack", "handbag")))
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/book.mp4", listOf("book")))
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/bottle_cup.mp4", listOf("bottle", "cup")))
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/hairdryer.ts", listOf("hairdryer")))
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/hairdryer_toothbrush.ts", listOf("hairdryer", "toothbrush")))
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/laptop_keyboard_mouse.ts", listOf("laptop", "keyboard", "mouse")))
        adsData.add(AdRecord("http://10.0.34.14/erica/hackathon/teddybear.ts", listOf("teddybear")))
        adsData.add(AdRecord("http://10.0.6.245/main/videos/logitech_mx_master_3s.mp4", listOf("mouse")))
        adsData.add(AdRecord("http://10.0.6.245/main/videos/samsung_s23.mp4", listOf("cell phone")))

//        adsData.add(AdRecord("asset:///airplane_backpack_handbag.ts", listOf("airplane", "backpack", "handbag")))
//        adsData.add(AdRecord("asset://book.mp4", listOf("book")))
//        adsData.add(AdRecord("asset://bottle_cup.mp4", listOf("bottle", "cup")))
//        adsData.add(AdRecord("asset://hairdryer.ts", listOf("hairdryer")))
//        adsData.add(AdRecord("asset://hairdryer_toothbrush.ts", listOf("hairdryer", "toothbrush")))
//        adsData.add(AdRecord("asset://laptop_keyboard_mouse.ts", listOf("laptop", "keyboard", "mouse")))
//        adsData.add(AdRecord("asset://teddybear.ts", listOf("teddybear")))
//        adsData.add(AdRecord("asset://logitech_mx_master_3s.mp4", listOf("mouse")))
//        adsData.add(AdRecord("asset://samsung_s23.mp4", listOf("cell phone")))

        for (ad in adsData) {
            for (tag in ad.tags) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tagWithAdsUrlMap.putIfAbsent(tag, ArrayList<String>())
                    tagWithAdsUrlMap[tag]!!.add(ad.url)
                }
            }
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector = CameraSelector.Builder().addCameraFilter(MyCameraFilter("0")).build();

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                              image.width,
                              image.height,
                              Bitmap.Config.ARGB_8888
                            )
                        }

                        detectObjects(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
      results: MutableList<Detection>?,
      inferenceTime: Long,
      imageHeight: Int,
      imageWidth: Int
    ) {
        activity?.runOnUiThread {
            Log.d(TAG, "onResults: inferenceTime: $inferenceTime")

            // Pass necessary information to OverlayView for drawing on the canvas
            // fragmentCameraBinding.overlay.setResults(
            //     results ?: LinkedList<Detection>(),
            //     imageHeight,
            //     imageWidth
            // )

            if (!resultList.isEmpty() && resultList.size >= sampleSize) {
                resultList.removeFirst()
            }

            if (results != null && results.size > 0) {
                resultList.add(results[0].categories[0].label)
            }

            Log.i("test---", resultList.size.toString())
            if (resultList.size == sampleSize) {
                val mostFrequentCategory = findMode(resultList)[0]
                val url = findSuitableVideoUrl(mostFrequentCategory)
                val item = MediaItem.fromUri(url)

                if (mostFrequentCategory != "person") {
                    fragmentCameraBinding.nextAdsKeywords.text = mostFrequentCategory
                    nextAdKeywords = mostFrequentCategory

                    if (!playerIsPlaying && !playerUrlSet) {
                        fragmentCameraBinding.curAdsKeywords.text = nextAdKeywords

                        player?.setMediaItem(item)
                        player?.prepare()
                        player?.play()
                        playerUrlSet = true;
                        Log.i("test---", "set video link")
                    }
                }
            }
        }
    }

    fun findSuitableVideoUrl(category: String): String {
//        return "https://videocdn.bodybuilding.com/video/mp4/62000/62792m.mp4"
        val adsList = tagWithAdsUrlMap[category]
            ?: return "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_1MB.mp4"
        val idx = random.nextInt(adsList.size)
        return adsList[idx]
    }

    fun findMode(list: List<String>): List<String> {
        val frequencyMap = list.groupingBy { it }.eachCount()
        val maxFrequency = frequencyMap.values.maxOrNull()
        return frequencyMap.filterValues { it == maxFrequency }.keys.toList()
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
