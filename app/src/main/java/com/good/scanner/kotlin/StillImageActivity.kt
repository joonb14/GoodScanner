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
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Pair
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import com.good.scanner.BitmapUtils.getBitmapFromContentUri
import com.good.scanner.ImgProcUtils
import com.good.scanner.kotlin.textdetector.TextRecognitionProcessor
import com.good.scanner.preference.SettingsActivity.LaunchSource
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.vision.demo.R
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.IOException
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

/** Activity demonstrating different image detector features with a still image from camera.  */
@KeepName
class StillImageActivity : AppCompatActivity() {
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

    private fun startCameraIntentForResult() { // Clean up last time's image
        imageUri = null
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "New Picture")
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    private fun startChooseImageIntentForResult() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE)
    }

    override fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: Intent?
    ) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            // TODO: Intent를 이용하여 다른 Activity로 image uri 넘기기
            val intent = Intent(this, CropImageActivity::class.java).apply {
                putExtra(IMAGE_URI, imageUri)
            }
            startActivity(intent)
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == Activity.RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data!!.data
            // TODO: Intent를 이용하여 다른 Activity로 image uri 넘기기
             val intent = Intent(this, CropImageActivity::class.java).apply {
                 putExtra(IMAGE_URI, imageUri)
             }
             startActivity(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
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
        const val IMAGE_URI = "IMAGE_URI"

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV is not loaded!")
            } else {
                Log.d(TAG, "OpenCV is loaded successfully!")
            }
        }
    }
}
