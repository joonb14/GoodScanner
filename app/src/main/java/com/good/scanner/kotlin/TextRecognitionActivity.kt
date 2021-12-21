package com.good.scanner.kotlin


import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.good.scanner.GraphicOverlay
import com.good.scanner.VisionImageProcessor
import com.good.scanner.kotlin.textdetector.TextRecognitionProcessor
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.vision.demo.R
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.opencv.android.OpenCVLoader

/** Activity demonstrating different image detector features with a still image from camera.  */
@KeepName
class TextRecognitionActivity : AppCompatActivity() {
    private var preview: ImageView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var selectedMode = TEXT_RECOGNITION_KOREAN
    private var selectedSize: String? =
            TextRecognitionActivity.SIZE_SCREEN
    private var isLandScape = false
    private var imageUri: Uri? = null

    // Max width (portrait mode)
    private var imageMaxWidth = 0

    // Max height (portrait mode)
    private var imageMaxHeight = 0
    private var imageProcessor: TextRecognitionProcessor? = null
    private var resizedResultBitmap: Bitmap ?= null
    var textView:TextView ?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_text_recognition)

        if(intent.hasExtra(CropImageActivity.PROCESSED_BITMAP)) {
            val byteArray: ByteArray? = intent.getByteArrayExtra(CropImageActivity.PROCESSED_BITMAP)
            Log.d(TextRecognitionActivity.TAG,"byteArray: {$byteArray}")
            resizedResultBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
            Log.d(TextRecognitionActivity.TAG,"resizedResultBitmap: {$resizedResultBitmap}")
        }
        else {
            Log.d(TextRecognitionActivity.TAG,"Error in sending resizedResultBitmap")
        }

        preview = findViewById<ImageView>(R.id.preview)
        preview!!.setImageBitmap(resizedResultBitmap)
        graphicOverlay = findViewById(R.id.graphic_overlay)

        isLandScape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (savedInstanceState != null) {
            imageMaxWidth =
                    savedInstanceState.getInt(TextRecognitionActivity.KEY_IMAGE_MAX_WIDTH)
            imageMaxHeight =
                    savedInstanceState.getInt(TextRecognitionActivity.KEY_IMAGE_MAX_HEIGHT)
            selectedSize =
                    savedInstanceState.getString(TextRecognitionActivity.KEY_SELECTED_SIZE)
        }

        textView = findViewById<View>(R.id.textView) as TextView
        val inferenceButton = findViewById<View>(R.id.model_inference_button)
        inferenceButton.setOnClickListener{ view: android.view.View? ->
            createImageProcessor()
            // TODO: Run AI Model - Text Recognition
            if (imageProcessor != null) {
                Log.d(TAG, "imageProcessor creation success")
                graphicOverlay!!.clear()
                graphicOverlay!!.setImageSourceInfo(resizedResultBitmap!!.width, resizedResultBitmap!!.height, false)
                imageProcessor
                imageProcessor!!.processBitmap(resizedResultBitmap, graphicOverlay!!)
                // TODO : imageProcessor가 찾은 Text 받아서 textView에 출력하기
                inferenceButton.visibility = View.GONE
                val scrollView = findViewById<View>(R.id.scrollview)
                scrollView.layoutParams.height *= 5 // resize scrollview

            } else {
                Log.e(TAG, "Null imageProcessor, please check adb logs for imageProcessor creation error")
            }
        }

        val rootView = findViewById<View>(R.id.root)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        imageMaxWidth = rootView.width
                        imageMaxHeight =
                                rootView.height - findViewById<View>(R.id.control).height
                    }
                })

    }

    public override fun onResume() {
        super.onResume()
        Log.d(TextRecognitionActivity.TAG, "onResume")
        createImageProcessor()
        // TODO : show bitmap as image
    }

    public override fun onPause() {
        super.onPause()
        imageProcessor?.run {
            this.stop()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        imageProcessor?.run {
            this.stop()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_IMAGE_URI, imageUri)
        outState.putInt(KEY_IMAGE_MAX_WIDTH, imageMaxWidth)
        outState.putInt(KEY_IMAGE_MAX_HEIGHT, imageMaxHeight)
        outState.putString(KEY_SELECTED_SIZE, selectedSize)
    }

    private fun createImageProcessor() {
        try {
            when (selectedMode) {
                TEXT_RECOGNITION_KOREAN ->
                    imageProcessor =
                            TextRecognitionProcessor(this, KoreanTextRecognizerOptions.Builder().build(), textView!!)
                else -> Log.e(TAG, "Unknown selectedMode: $selectedMode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can not create image processor: $selectedMode", e)
            Toast.makeText(
                    applicationContext,
                    "Can not create image processor: " + e.message,
                    Toast.LENGTH_LONG
            )
                    .show()
        }
    }

    companion object {
        var text: Text? = null;
        private const val TAG = "TextRecognitionActivity"
        private const val TEXT_RECOGNITION_KOREAN = "Text Recognition Korean"

        private const val SIZE_SCREEN = "w:screen" // Match screen width
        private const val SIZE_1024_768 = "w:1024" // ~1024*768 in a normal ratio
        private const val SIZE_640_480 = "w:640" // ~640*480 in a normal ratio
        private const val SIZE_ORIGINAL = "w:original" // Original image size
        private const val KEY_IMAGE_URI = "com.google.mlkit.vision.demo.KEY_IMAGE_URI"
        private const val KEY_IMAGE_MAX_WIDTH = "com.google.mlkit.vision.demo.KEY_IMAGE_MAX_WIDTH"
        private const val KEY_IMAGE_MAX_HEIGHT = "com.google.mlkit.vision.demo.KEY_IMAGE_MAX_HEIGHT"
        private const val KEY_SELECTED_SIZE = "com.google.mlkit.vision.demo.KEY_SELECTED_SIZE"

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV is not loaded!")
            } else {
                Log.d(TAG, "OpenCV is loaded successfully!")
            }
        }
    }
}
