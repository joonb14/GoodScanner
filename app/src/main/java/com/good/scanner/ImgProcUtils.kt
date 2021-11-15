package com.good.scanner

import android.graphics.Bitmap
import android.util.Log
import com.good.scanner.kotlin.StillImageActivity
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.ArrayList

class ImgProcUtils {
    private fun convertMatOfPointToMatOfPoint2f(matOfPoint: MatOfPoint): MatOfPoint2f {
        return MatOfPoint2f().also {
            matOfPoint.convertTo(it, CvType.CV_32F)
        }
    }

    private fun convertMatOfPoint2fToMatOfPoint(matOfPoint2f: MatOfPoint2f): MatOfPoint {
        return MatOfPoint().also {
            matOfPoint2f.convertTo(it, CvType.CV_32S)
        }
    }

    fun convertBitmapToMat(bitmap: Bitmap): Mat {
        return Mat().also {
            Utils.bitmapToMat(bitmap, it)
        }
    }

    fun convertMatToBitmap(mat: Mat): Bitmap {
        return Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888).also {
            Utils.matToBitmap(mat, it)
        }
    }

    fun convertToGreyScale(beforeMat: Mat): Mat {
        // TODO: Image preprocessing - Input Image to Greyscale Image
        // image 를 B&W image 로 변환한 결과를 greyMat 에 저장
        return Mat(beforeMat.size(), CvType.CV_8UC1).also {
            Imgproc.cvtColor(beforeMat, it, Imgproc.COLOR_RGB2GRAY, 4)
        }
    }

    fun convertToBlurred(beforeMat: Mat): Mat {
        // TODO: Image preprocessing - Gaussian Blur
        // Gaussian Blur 한 결과를 blurredMat 에 저장
        return Mat(beforeMat.size(), CvType.CV_8UC1).also {
            Imgproc.GaussianBlur(beforeMat, it, Size(5.0, 5.0), 0.0)
        }
    }

    fun convertToEdgeDetected(beforeMat: Mat): Mat {
        // TODO: Image preprocessing - Edge Detection
        // Canny 를 이용하여 Edge Detection 한 결과를 edgesMat 에 저장
        return Mat(beforeMat.size(), CvType.CV_8UC1).also {
            Imgproc.Canny(beforeMat, it, 75.0, 200.0)
        }
    }

    fun findContours(mat: Mat): List<MatOfPoint2f> {
        // TODO: Image preprocessing - Find Contour
        return ArrayList<MatOfPoint>().also {
            Imgproc.findContours(mat, it, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        }.sortedWith(
                compareBy { -Imgproc.contourArea(it) } // descending sort
        ).subList(0, CONTOUR_NUM).map {
            convertMatOfPointToMatOfPoint2f(it)
        }
    }

    fun approximatePolygonal(contour: MatOfPoint2f): MatOfPoint2f {
        return MatOfPoint2f().also {
            Imgproc.approxPolyDP(contour, it, 0.02 * Imgproc.arcLength(contour, true), true)
        }
    }

    fun scaleContour(mat: Mat, contour: MatOfPoint2f, scaleX : Float, scaleY : Float): MatOfPoint2f {
        // TODO: Image preprocessing - Find Contour And Sort
        // Contour 자체가 선이 이어져 닫힌 도형을 의미하다보니 이미지에 4개의 모서리가 완전히 들어와있지않으면 제대로 인식이 안댐... 특히나 바코드 같은거 때문에 더....
        // contourArea 계산할때 사이즈가 많이 크면 인식을 못하는건가 싶기도하고....
        val contourList = contour.toList()
        for (i in contourList.indices) { // 작게 rescale한 image를 이용하여 contour를 찾았기 때문에 contour size를 원본이미지에 맞게 rescaling한다
            contourList[i].x = contourList[i].x * scaleX
            contourList[i].y = contourList[i].y * scaleY
        }
        val scaledContour = MatOfPoint2f()
        scaledContour.fromList(contourList)
        return scaledContour
    }

    companion object {
        private const val CONTOUR_NUM = 5
        private const val TAG = "ImgProcUtils"
    }
}