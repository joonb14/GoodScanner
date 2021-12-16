package com.good.scanner.kotlin


import android.app.Activity
import android.content.ContentValues
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
import com.good.scanner.GraphicOverlay
import com.good.scanner.ImgProcUtils
import com.good.scanner.VisionImageProcessor
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
class CropImageActivity : AppCompatActivity() {
    private var preview: QuadrilateralSelectionImageView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var selectedMode = TEXT_RECOGNITION_KOREAN
    private var selectedSize: String? =
            CropImageActivity.SIZE_SCREEN
    private var isLandScape = false
    private var imageUri: Uri? = null

    // Max width (portrait mode)
    private var imageMaxWidth = 0

    // Max height (portrait mode)
    private var imageMaxHeight = 0
    private var imageProcessor: VisionImageProcessor? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_crop_image)

        if(intent.hasExtra(StillImageActivity.IMAGE_URI)) {
            imageUri = intent.getParcelableExtra(StillImageActivity.IMAGE_URI)
            Log.d(TAG,"imageUri: {$imageUri}")
        }
        else {
            Log.d(TAG,"Error in sending imageUri")
        }

        findViewById<View>(R.id.crop_image_button)
                .setOnClickListener{ view: View? ->
                    if (imageUri == null) return@setOnClickListener
                    if (SIZE_SCREEN == selectedSize && imageMaxWidth == 0) return@setOnClickListener // UI layout has not finished yet, will reload once it's ready.
                    val imageBitmap = getBitmapFromContentUri(contentResolver, imageUri) ?: return@setOnClickListener

                    val points: List<PointF> = preview!!.getPoints()
                    graphicOverlay!!.clear() // Clear the overlay first
                    val resizedBitmap = getResizedBitmap(imageBitmap)
                    val utils = ImgProcUtils()
                    val orig = utils.convertBitmapToMat(resizedBitmap)
                    // TODO: Image preprocessing - Perspective Transform
                    val transformed = perspectiveTransform(orig, points)
                    // TODO: Image preprocessing - Thresholding (Result image should be black & white)
                    val mResult = applyThreshold(transformed!!)
                    val resizedResultBitmap = getResizedBitmap(mResult!!)
                    preview!!.deletePoints()
                    preview!!.setImageBitmap(resizedResultBitmap)
                    orig.release()
                    transformed.release()

                    // TODO: Run AI Model - Text Recognition
                    if (imageProcessor != null) {
                        graphicOverlay!!.setImageSourceInfo(resizedResultBitmap.width, resizedBitmap.height, false)
                        imageProcessor!!.processBitmap(resizedResultBitmap, graphicOverlay)
                    } else {
                        Log.e(TAG, "Null imageProcessor, please check adb logs for imageProcessor creation error")
                    }
                }
        preview = findViewById<QuadrilateralSelectionImageView>(R.id.preview)
        graphicOverlay = findViewById(R.id.graphic_overlay)

        isLandScape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (savedInstanceState != null) {
            imageUri =
                    savedInstanceState.getParcelable(CropImageActivity.KEY_IMAGE_URI)
            imageMaxWidth =
                    savedInstanceState.getInt(CropImageActivity.KEY_IMAGE_MAX_WIDTH)
            imageMaxHeight =
                    savedInstanceState.getInt(CropImageActivity.KEY_IMAGE_MAX_HEIGHT)
            selectedSize =
                    savedInstanceState.getString(CropImageActivity.KEY_SELECTED_SIZE)
        }

        val rootView = findViewById<View>(R.id.root)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        imageMaxWidth = rootView.width
                        imageMaxHeight =
                                rootView.height - findViewById<View>(R.id.control).height
                        if (CropImageActivity.SIZE_SCREEN == selectedSize) {
                            tryReloadAndDetectInImage()
                        }
                    }
                })
    }

    public override fun onResume() {
        super.onResume()
        Log.d(CropImageActivity.TAG, "onResume")
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

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_IMAGE_URI, imageUri)
        outState.putInt(KEY_IMAGE_MAX_WIDTH, imageMaxWidth)
        outState.putInt(KEY_IMAGE_MAX_HEIGHT, imageMaxHeight)
        outState.putString(KEY_SELECTED_SIZE, selectedSize)
    }

    private fun getResizedBitmap(bitmap: Bitmap): Bitmap {
        val scaleFactor = (bitmap.width.toFloat() / targetedWidthHeight.first.toFloat())
                .coerceAtLeast(bitmap.height.toFloat() / targetedWidthHeight.second.toFloat())
        return when(selectedSize == SIZE_ORIGINAL) {
            true -> bitmap
            false -> Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width / scaleFactor).toInt(),
                    (bitmap.height / scaleFactor).toInt(),
                    true
            )
        }
    }

    private fun tryReloadAndDetectInImage() {
        Log.d(TAG, "Try reload and detect image")
        try {
            if (imageUri == null) return
            if (SIZE_SCREEN == selectedSize && imageMaxWidth == 0) return // UI layout has not finished yet, will reload once it's ready.

            val imageBitmap = getBitmapFromContentUri(contentResolver, imageUri) ?: return
            graphicOverlay!!.clear() // Clear the overlay first
            val resizedBitmap = getResizedBitmap(imageBitmap)

            // TODO:
            //  image = imutils.resize(image, height = 500)
            //  coef_y = orig.shape[0] / image.shape[0]
            //  coef_x = orig.shape[1] / image.shape[1]
            //  screenCnt[:, :, 0] = screenCnt[:, :, 0] * coef_x
            //  screenCnt[:, :, 1] = screenCnt[:, :,  1] * coef_y

            val scaleFactor = (resizedBitmap.height.toFloat() / 500.0) // Height가 500이 되도록 image를 rescaling. edge detection에는 낮은 해상도의 이미지가 더 제격이다
            val scaledBitmap = Bitmap.createScaledBitmap(
                    resizedBitmap,
                    (resizedBitmap.width / scaleFactor).toInt(),
                    (resizedBitmap.height / scaleFactor).toInt(),
                    true
            )
            preview!!.setImageBitmap(resizedBitmap)

            val scaleY = resizedBitmap.height.toFloat() / scaledBitmap.height.toFloat()
            val scaleX = resizedBitmap.width.toFloat() / scaledBitmap.width.toFloat()

            val utils = ImgProcUtils()
            val imageMat = utils.convertBitmapToMat(resizedBitmap)
            val scaledImageMat = utils.convertBitmapToMat(scaledBitmap)
            val greyMat = utils.convertToGreyScale(scaledImageMat)
            val blurredMat = utils.convertToBlurred(greyMat)
            val edgesMat = utils.convertToEdgeDetected(blurredMat)
            val contours = utils.findContours(edgesMat)

            contours.firstOrNull {
                utils.approximatePolygonal(it).toArray().size == 4
            }?.let {
                Log.d(TAG,"Contour Found!")
                val scaledContour = utils.scaleContour(imageMat, it, scaleX, scaleY)
                val result = ArrayList<PointF>()
                val points = sortPoints(scaledContour.toArray())
                result.add(PointF(java.lang.Double.valueOf(points[0].x).toFloat(), java.lang.Double.valueOf(points[0].y).toFloat()))
                result.add(PointF(java.lang.Double.valueOf(points[1].x).toFloat(), java.lang.Double.valueOf(points[1].y).toFloat()))
                result.add(PointF(java.lang.Double.valueOf(points[2].x).toFloat(), java.lang.Double.valueOf(points[2].y).toFloat()))
                result.add(PointF(java.lang.Double.valueOf(points[3].x).toFloat(), java.lang.Double.valueOf(points[3].y).toFloat()))
                preview?.setPoints(result)
            } ?: {
                preview?.setPoints(null)
            }
            edgesMat.release()
            blurredMat.release()
            greyMat.release()
            scaledImageMat.release()
            imageMat.release()

        } catch (e: IOException) {
            Log.e(TAG, "Error retrieving saved image")
            imageUri = null
        }
    }

    private val targetedWidthHeight: Pair<Int, Int>
        get() {
            val targetWidth: Int
            val targetHeight: Int
            when (selectedSize) {
                SIZE_SCREEN -> {
                    targetWidth = imageMaxWidth
                    targetHeight = imageMaxHeight
                }
                SIZE_640_480 -> {
                    targetWidth = if (isLandScape) 640 else 480
                    targetHeight = if (isLandScape) 480 else 640
                }
                SIZE_1024_768 -> {
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
                TEXT_RECOGNITION_KOREAN ->
                    imageProcessor =
                            TextRecognitionProcessor(this, KoreanTextRecognizerOptions.Builder().build())
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

    /**
     * Transform the coordinates on the given Mat to correct the perspective.
     *
     * @param src A valid Mat
     * @param points A list of coordinates from the given Mat to adjust the perspective
     * @return A perspective transformed Mat
     */
    private fun perspectiveTransform(src: Mat, points: List<PointF>): Mat? {
        val point1 = Point(points[0].x.toDouble(), points[0].y.toDouble())
        val point2 = Point(points[1].x.toDouble(), points[1].y.toDouble())
        val point3 = Point(points[2].x.toDouble(), points[2].y.toDouble())
        val point4 = Point(points[3].x.toDouble(), points[3].y.toDouble())
        val pts = arrayOf(point1, point2, point3, point4)
        return fourPointTransform(src, sortPoints(pts))
    }

    /**
     * Apply a threshold to give the "scanned" look
     *
     * NOTE:
     * See the following link for more info http://docs.opencv.org/3.1.0/d7/d4d/tutorial_py_thresholding.html#gsc.tab=0
     * @param src A valid Mat
     * @return The processed Bitmap
     */
    private fun applyThreshold(src: Mat): Bitmap? {
        Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2GRAY)

        // Some other approaches
//        Imgproc.adaptiveThreshold(src, src, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 15);
//        Imgproc.threshold(src, src, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
        Imgproc.GaussianBlur(src, src, Size(5.0, 5.0), 0.0)
        Imgproc.adaptiveThreshold(src, src, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)
        val bm = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(src, bm)
        return bm
    }

    /**
     * Sort the points
     *
     * The order of the points after sorting:
     * 0------->1
     * ^        |
     * |        v
     * 3<-------2
     *
     * NOTE:
     * Based off of http://www.pyimagesearch.com/2014/08/25/4-point-opencv-getperspective-transform-example/
     *
     * @param src The points to sort
     * @return An array of sorted points
     */
    private fun sortPoints(src: Array<Point>): Array<Point> {
        val srcPoints = ArrayList(listOf(*src))
        val result = arrayOf<Point>(Point(0.0,0.0), Point(0.0,0.0), Point(0.0,0.0), Point(0.0,0.0))

        val sumComparator  = compareBy<Point> {it.y + it.x}
        val differenceComparator =compareBy<Point> {it.y - it.x}
        result[0] = Collections.min(srcPoints, sumComparator) // Upper left has the minimal sum
        result[2] = Collections.max(srcPoints, sumComparator) // Lower right has the maximal sum
        result[1] = Collections.min(srcPoints, differenceComparator) // Upper right has the minimal difference
        result[3] = Collections.max(srcPoints, differenceComparator) // Lower left has the maximal difference
        return result
    }

    /**
     * NOTE:
     * Based off of http://www.pyimagesearch.com/2014/08/25/4-point-opencv-getperspective-transform-example/
     *
     * @param src
     * @param pts
     * @return
     */
    private fun fourPointTransform(src: Mat, pts: Array<Point>): Mat {
        val ratio = 1.0
        val ul = pts[0]
        val ur = pts[1]
        val lr = pts[2]
        val ll = pts[3]
        val widthA = sqrt((lr.x - ll.x).pow(2.0) + (lr.y - ll.y).pow(2.0))
        val widthB = sqrt((ur.x - ul.x).pow(2.0) + (ur.y - ul.y).pow(2.0))
        val maxWidth = Math.max(widthA, widthB) * ratio
        val heightA = sqrt((ur.x - lr.x).pow(2.0) + (ur.y - lr.y).pow(2.0))
        val heightB = sqrt((ul.x - ll.x).pow(2.0) + (ul.y - ll.y).pow(2.0))
        val maxHeight = Math.max(heightA, heightB) * ratio
        val resultMat = Mat(java.lang.Double.valueOf(maxHeight).toInt(), java.lang.Double.valueOf(maxWidth).toInt(), CvType.CV_8UC4)
        val srcMat = Mat(4, 1, CvType.CV_32FC2)
        val dstMat = Mat(4, 1, CvType.CV_32FC2)
        srcMat.put(0, 0, ul.x * ratio, ul.y * ratio, ur.x * ratio, ur.y * ratio, lr.x * ratio, lr.y * ratio, ll.x * ratio, ll.y * ratio)
        dstMat.put(0, 0, 0.0, 0.0, maxWidth, 0.0, maxWidth, maxHeight, 0.0, maxHeight)
        val M = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        Imgproc.warpPerspective(src, resultMat, M, resultMat.size())
        srcMat.release()
        dstMat.release()
        M.release()
        return resultMat
    }

    companion object {
        private const val TAG = "CropImageActivity"
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
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV is not loaded!")
            } else {
                Log.d(TAG, "OpenCV is loaded successfully!")
            }
        }
    }
}
