package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import org.tensorflow.lite.examples.objectdetection.databinding.ActivityMainBinding
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import android.graphics.Matrix
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class AgeGenderDetectionHelper(
    val context: Context,
    val ageGenderDetectionListener: AgeGenderDetectionListener?
    ){
    private val coroutineScope = CoroutineScope( Dispatchers.Main )
    private val realTimeOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    private val firebaseFaceDetector = FaceDetection.getClient(realTimeOpts)
    lateinit var ageModelInterpreter: Interpreter
    lateinit var genderModelInterpreter: Interpreter
    private lateinit var ageEstimationModel: AgeEstimationModel
    private lateinit var genderClassificationModel: GenderClassificationModel
    private val compatList = CompatibilityList()
    private var modelFilename = arrayOf( "model_age_nonq.tflite", "model_gender_nonq.tflite" )
    private val shift = 5
    private var predictedAge = 0
    private var predictedGender = ""
    private var subjectImage: Bitmap? = null
    private var ageInferenceTime = 0L
    private var genderInferenceTime = 0L
    private var initialized = false
    private var predicting = false

    init {
        val options = Interpreter.Options().apply {
            // addDelegate(GpuDelegate( compatList.bestOptionsForThisDevice ) )
            // addDelegate(NnApiDelegate())
        }
        coroutineScope.launch {
            initModels(options)
            initialized = true
        }
    }

        // Suspending function to initialize the TFLite interpreters.
    public suspend fun initModels(options: Interpreter.Options) = withContext( Dispatchers.Default ) {
        ageModelInterpreter = Interpreter(FileUtil.loadMappedFile( context , modelFilename[0]), options )
        genderModelInterpreter = Interpreter(FileUtil.loadMappedFile( context , modelFilename[1]), options )

        withContext( Dispatchers.Main ){
            ageEstimationModel = AgeEstimationModel().apply {
                interpreter = ageModelInterpreter
            }
            genderClassificationModel = GenderClassificationModel().apply {
                interpreter = genderModelInterpreter
            }
        }
    }

    // public fun destroy() {
    //     ageModelInterpreter.close()
    //     genderModelInterpreter.close()
    // }

    public fun detectFaces(image: Bitmap) {
        if (!initialized) {
            return
        }
        if (predicting) {
            return
        }
        val inputImage = InputImage.fromBitmap(image, 0)
        // Pass the clicked picture to MLKit's FaceDetector.
        firebaseFaceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if ( faces.size != 0 ) {
                        // Set the cropped Bitmap
                        //subjectImage = cropToBBox(image, faces[0].boundingBox)
                        // Launch a coroutine
                        coroutineScope.launch {
                            predicting = true
                            val res = mutableListOf<Person>()
                            for (i in faces.indices) {
                            try {
                            // Predict the age and the gender.
                            val bitmap = cropToBBox(image, faces[i].boundingBox)
                            val age = ageEstimationModel.predictAge(bitmap)
                            val gender = genderClassificationModel.predictGender(bitmap)

                            ageInferenceTime = ageEstimationModel.inferenceTime
                            genderInferenceTime = genderClassificationModel.inferenceTime


                            // Show the final output to the user.
                            predictedAge = floor( age.toDouble() ).toInt()
                            predictedGender = if ( gender[ 0 ] > gender[ 1 ] ) { "Male" } else { "Female" }
                            res.add(Person(predictedAge, predictedGender, max(ageInferenceTime, genderInferenceTime)))
                            } catch (e:IllegalArgumentException) {

                            }
                            }
                            ageGenderDetectionListener?.agd_onResults(res)
                            predicting = false
                        }
                    }
                    else {
                        // Show a dialog to the user when no faces were detected.
                    }


                }
    }

    private fun cropToBBox(image: Bitmap, bbox: Rect) : Bitmap {
        return Bitmap.createBitmap(
            image,
            bbox.left - 0 * shift,
            bbox.top + shift,
            bbox.width() + 0 * shift,
            bbox.height() + 0 * shift
        )
    }

    private fun rotateBitmap(original: Bitmap, degrees: Float): Bitmap? {
        val matrix = Matrix()
        matrix.preRotate(degrees)
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    interface AgeGenderDetectionListener{
        fun agd_onError(error: String)
        fun agd_onResults(
            results: List<Person>
        )
    }

    public class Person(val age: Int, val gender: String, val inferenceTime: Long){

    }


}