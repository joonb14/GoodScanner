/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.good.scanner.kotlin

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.provider.MediaStore
import android.util.Log
import android.util.Pair
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import com.good.scanner.kotlin.textdetector.TextRecognitionProcessor
import com.good.scanner.preference.SettingsActivity.LaunchSource
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.vision.demo.R
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Core.compare
import org.opencv.imgproc.Imgproc
import java.io.IOException
import java.util.*

/** Activity demonstrating different image detector features with a still image from camera.  */
@KeepName
class StillImageActivity : AppCompatActivity() {
    private var preview: ImageView? = null
    private var graphicOverlay: com.good.scanner.GraphicOverlay? = null
    private var selectedMode = TEXT_RECOGNITION_KOREAN
    private var selectedSize: String? =
            com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_SCREEN
    private var isLandScape = false
    private var imageUri: Uri? = null

    // Max width (portrait mode)
    private var imageMaxWidth = 0

    // Max height (portrait mode)
    private var imageMaxHeight = 0
    private var imageProcessor: com.good.scanner.VisionImageProcessor? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_still_image)
        findViewById<View>(R.id.select_image_button)
                .setOnClickListener { view: View ->
                    // Menu for selecting either: a) take new photo b) select from existing
                    val popup =
                            PopupMenu(this@StillImageActivity, view)
                    popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                        val itemId =
                                menuItem.itemId
                        if (itemId == R.id.select_images_from_local) {
                            startChooseImageIntentForResult()
                            return@setOnMenuItemClickListener true
                        } else if (itemId == R.id.take_photo_using_camera) {
                            startCameraIntentForResult()
                            return@setOnMenuItemClickListener true
                        }
                        false
                    }
                    val inflater = popup.menuInflater
                    inflater.inflate(R.menu.camera_button_menu, popup.menu)
                    popup.show()
                }
        preview = findViewById(R.id.preview)
        graphicOverlay = findViewById(R.id.graphic_overlay)

        populateFeatureSelector()
        populateSizeSelector()
        isLandScape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (savedInstanceState != null) {
            imageUri =
                    savedInstanceState.getParcelable(com.good.scanner.kotlin.StillImageActivity.Companion.KEY_IMAGE_URI)
            imageMaxWidth =
                    savedInstanceState.getInt(com.good.scanner.kotlin.StillImageActivity.Companion.KEY_IMAGE_MAX_WIDTH)
            imageMaxHeight =
                    savedInstanceState.getInt(com.good.scanner.kotlin.StillImageActivity.Companion.KEY_IMAGE_MAX_HEIGHT)
            selectedSize =
                    savedInstanceState.getString(com.good.scanner.kotlin.StillImageActivity.Companion.KEY_SELECTED_SIZE)
        }

        val rootView = findViewById<View>(R.id.root)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        imageMaxWidth = rootView.width
                        imageMaxHeight =
                                rootView.height - findViewById<View>(R.id.control).height
                        if (com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_SCREEN == selectedSize) {
                            tryReloadAndDetectInImage()
                        }
                    }
                })

        val settingsButton = findViewById<ImageView>(R.id.settings_button)
        settingsButton.setOnClickListener {
            val intent =
                    Intent(
                            applicationContext,
                            com.good.scanner.preference.SettingsActivity::class.java
                    )
            intent.putExtra(
                    com.good.scanner.preference.SettingsActivity.EXTRA_LAUNCH_SOURCE,
                    LaunchSource.STILL_IMAGE
            )
            startActivity(intent)
        }
    }

    public override fun onResume() {
        super.onResume()
        Log.d(com.good.scanner.kotlin.StillImageActivity.Companion.TAG, "onResume")
        createImageProcessor()
        tryReloadAndDetectInImage()
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

    private fun populateFeatureSelector() {
        val featureSpinner = findViewById<Spinner>(R.id.feature_selector)
        val options: MutableList<String> = ArrayList()
        options.add(com.good.scanner.kotlin.StillImageActivity.Companion.TEXT_RECOGNITION_KOREAN)

        // Creating adapter for featureSpinner
        val dataAdapter =
                ArrayAdapter(this, R.layout.spinner_style, options)
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        featureSpinner.adapter = dataAdapter
        featureSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                    parentView: AdapterView<*>,
                    selectedItemView: View?,
                    pos: Int,
                    id: Long
            ) {
                if (pos >= 0) {
                    selectedMode = parentView.getItemAtPosition(pos).toString()
                    createImageProcessor()
                    tryReloadAndDetectInImage()
                }
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }
    }

    private fun populateSizeSelector() {
        val sizeSpinner = findViewById<Spinner>(R.id.size_selector)
        val options: MutableList<String> = ArrayList()
        options.add(com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_SCREEN)
        options.add(com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_1024_768)
        options.add(com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_640_480)
        options.add(com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_ORIGINAL)
        // Creating adapter for featureSpinner
        val dataAdapter =
                ArrayAdapter(this, R.layout.spinner_style, options)
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        sizeSpinner.adapter = dataAdapter
        sizeSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                    parentView: AdapterView<*>,
                    selectedItemView: View?,
                    pos: Int,
                    id: Long
            ) {
                if (pos >= 0) {
                    selectedSize = parentView.getItemAtPosition(pos).toString()
                    tryReloadAndDetectInImage()
                }
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(
                com.good.scanner.kotlin.StillImageActivity.Companion.KEY_IMAGE_URI,
                imageUri
        )
        outState.putInt(
                com.good.scanner.kotlin.StillImageActivity.Companion.KEY_IMAGE_MAX_WIDTH,
                imageMaxWidth
        )
        outState.putInt(
                com.good.scanner.kotlin.StillImageActivity.Companion.KEY_IMAGE_MAX_HEIGHT,
                imageMaxHeight
        )
        outState.putString(
                com.good.scanner.kotlin.StillImageActivity.Companion.KEY_SELECTED_SIZE,
                selectedSize
        )
    }

    private fun startCameraIntentForResult() { // Clean up last time's image
        imageUri = null
        preview!!.setImageBitmap(null)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "New Picture")
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(
                    takePictureIntent,
                    com.good.scanner.kotlin.StillImageActivity.Companion.REQUEST_IMAGE_CAPTURE
            )
        }
    }

    private fun startChooseImageIntentForResult() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
                Intent.createChooser(intent, "Select Picture"),
                com.good.scanner.kotlin.StillImageActivity.Companion.REQUEST_CHOOSE_IMAGE
        )
    }

    override fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: Intent?
    ) {
        if (requestCode == com.good.scanner.kotlin.StillImageActivity.Companion.REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            tryReloadAndDetectInImage()
        } else if (requestCode == com.good.scanner.kotlin.StillImageActivity.Companion.REQUEST_CHOOSE_IMAGE && resultCode == Activity.RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data!!.data
            tryReloadAndDetectInImage()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun tryReloadAndDetectInImage() {
        Log.d(
                com.good.scanner.kotlin.StillImageActivity.Companion.TAG,
                "Try reload and detect image"
        )
        try {
            if (imageUri == null) {
                return
            }

            if (com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_SCREEN == selectedSize && imageMaxWidth == 0) {
                // UI layout has not finished yet, will reload once it's ready.
                return
            }

            val imageBitmap = com.good.scanner.BitmapUtils.getBitmapFromContentUri(contentResolver, imageUri)
                    ?: return
            // Clear the overlay first
            graphicOverlay!!.clear()

            val resizedBitmap: Bitmap
            resizedBitmap = if (selectedSize == com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_ORIGINAL) {
                imageBitmap
            } else {
                // Get the dimensions of the image view
                val targetedSize: Pair<Int, Int> = targetedWidthHeight

                // Determine how much to scale down the image
                val scaleFactor = Math.max(
                        imageBitmap.width.toFloat() / targetedSize.first.toFloat(),
                        imageBitmap.height.toFloat() / targetedSize.second.toFloat()
                )
                Bitmap.createScaledBitmap(
                        imageBitmap,
                        (imageBitmap.width / scaleFactor).toInt(),
                        (imageBitmap.height / scaleFactor).toInt(),
                        true
                )
            }

            val imageMat = Mat()
            Utils.bitmapToMat(resizedBitmap, imageMat)
            // TODO : Image preprocessing - Input Image to Greyscale Image
            // image 를 B&W image 로 변환한 결과를 greyMat 에 저장
            val greyMat = Mat(imageMat.size(), CvType.CV_8UC1) // for GreyScale Bitmap (CV_8UC1)
            Imgproc.cvtColor(imageMat, greyMat, Imgproc.COLOR_RGB2GRAY, 4)

            // TODO : Image preprocessing - Gaussian Blur
            // Gaussian Blur 한 결과를 blurredMat 에 저장
            val blurredMat = Mat(imageMat.size(), CvType.CV_8UC1)
            val ksize = Size(5.0, 5.0)
            Imgproc.GaussianBlur(greyMat, blurredMat, ksize, 0.0)

            // TODO : Image preprocessing - Edge Detection
            // Canny 를 이용하여 Edge Detection 한 결과를 edgesMat 에 저장
            val edgesMat = Mat(imageMat.size(), CvType.CV_8UC1)
            Imgproc.Canny(blurredMat, edgesMat, 80.0, 100.0)
            // edges Mat 를 이용하여 Edge Detection 한 결과를 edgeDetectedBitmap 에 저장
            val edgeDetectedBitmap = Bitmap.createBitmap(edgesMat.cols(), edgesMat.rows(), Bitmap.Config.ARGB_8888) // for RGBA Bitmap (ARGB_8888)
            Utils.matToBitmap(edgesMat, edgeDetectedBitmap)

            // TODO : Image preprocessing - Find Contour
            val contours = findContours(edgesMat)
            if (contours != null) {
                for (contour in contours){
                    Log.d(TAG, "Contour: "+contour.toString())
                }
            }

            // TODO : Image preprocessing - Perspective Transform

            // TODO : Image preprocessing - Thresholding (Result image should be black & white)

            // for debugging (release 땐 아래의 for release 로!)
            preview!!.setImageBitmap(edgeDetectedBitmap)
            // for release
            // preview!!.setImageBitmap(resizedBitmap)
            if (imageProcessor != null) {
                graphicOverlay!!.setImageSourceInfo(
                        resizedBitmap.width, resizedBitmap.height, /* isFlipped= */false
                )
                imageProcessor!!.processBitmap(resizedBitmap, graphicOverlay)
            } else {
                Log.e(
                        com.good.scanner.kotlin.StillImageActivity.Companion.TAG,
                        "Null imageProcessor, please check adb logs for imageProcessor creation error"
                )
            }
        } catch (e: IOException) {
            Log.e(
                    com.good.scanner.kotlin.StillImageActivity.Companion.TAG,
                    "Error retrieving saved image"
            )
            imageUri = null
        }
    }

    private val targetedWidthHeight: Pair<Int, Int>
        get() {
            val targetWidth: Int
            val targetHeight: Int
            when (selectedSize) {
                com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_SCREEN -> {
                    targetWidth = imageMaxWidth
                    targetHeight = imageMaxHeight
                }
                com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_640_480 -> {
                    targetWidth = if (isLandScape) 640 else 480
                    targetHeight = if (isLandScape) 480 else 640
                }
                com.good.scanner.kotlin.StillImageActivity.Companion.SIZE_1024_768 -> {
                    targetWidth = if (isLandScape) 1024 else 768
                    targetHeight = if (isLandScape) 768 else 1024
                }
                else -> throw IllegalStateException("Unknown size")
            }
            return Pair(targetWidth, targetHeight)
        }

    private fun createImageProcessor() {
        try {
            when (selectedMode) {
                com.good.scanner.kotlin.StillImageActivity.Companion.TEXT_RECOGNITION_KOREAN ->
                    imageProcessor =
                            TextRecognitionProcessor(this, KoreanTextRecognizerOptions.Builder().build())
                else -> Log.e(
                        com.good.scanner.kotlin.StillImageActivity.Companion.TAG,
                        "Unknown selectedMode: $selectedMode"
                )
            }
        } catch (e: Exception) {
            Log.e(
                    com.good.scanner.kotlin.StillImageActivity.Companion.TAG,
                    "Can not create image processor: $selectedMode",
                    e
            )
            Toast.makeText(
                    applicationContext,
                    "Can not create image processor: " + e.message,
                    Toast.LENGTH_LONG
            )
                    .show()
        }
    }

    // TODO : Image preprocessing - Find Contour
    fun findContours(outmat: Mat): ArrayList<MatOfPoint?>? {
        val hierarchy = Mat()
        val contours: List<MatOfPoint> = ArrayList<MatOfPoint>()
        Imgproc.findContours(outmat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val maxArea = 100.0
        var maxAreaIdx = -1
        val contourArray: ArrayList<MatOfPoint?> = ArrayList<MatOfPoint?>()
        for (idx in contours.indices) {
            val contour: MatOfPoint = contours[idx]
            val contourArea = Imgproc.contourArea(contour)
            if (contourArea > maxArea) {
                // maxArea = contourArea;
                maxAreaIdx = idx
                contourArray.add(contour)
                //  Imgproc.drawContours(image, contours, maxAreaIdx, new Scalar(0,0, 255));
            }
        }
        return contourArray
    }

    companion object {
        private const val TAG = "StillImageActivity"
        private const val TEXT_RECOGNITION_KOREAN = "Text Recognition Korean"

        private const val SIZE_SCREEN = "w:screen" // Match screen width
        private const val SIZE_1024_768 = "w:1024" // ~1024*768 in a normal ratio
        private const val SIZE_640_480 = "w:640" // ~640*480 in a normal ratio
        private const val SIZE_ORIGINAL = "w:original" // Original image size
        private const val KEY_IMAGE_URI = "com.google.mlkit.vision.demo.KEY_IMAGE_URI"
        private const val KEY_IMAGE_MAX_WIDTH = "com.google.mlkit.vision.demo.KEY_IMAGE_MAX_WIDTH"
        private const val KEY_IMAGE_MAX_HEIGHT = "com.google.mlkit.vision.demo.KEY_IMAGE_MAX_HEIGHT"
        private const val KEY_SELECTED_SIZE = "com.google.mlkit.vision.demo.KEY_SELECTED_SIZE"
        private const val REQUEST_IMAGE_CAPTURE = 1001
        private const val REQUEST_CHOOSE_IMAGE = 1002

        init {
            if(!OpenCVLoader.initDebug()){
                Log.d(TAG, "OpenCV is not loaded!")
            } else {
                Log.d(TAG, "OpenCV is loaded successfully!")
            }
        }
    }
}
