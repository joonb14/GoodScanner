package com.good.scanner.kotlin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.google.mlkit.vision.demo.R;

import java.util.ArrayList;
import java.util.List;

/**
 * An image view subclass which allows for selection of a portion of the image using a
 * convex quadrilateral
 */
public class QuadrilateralSelectionImageView extends androidx.appcompat.widget.AppCompatImageView {

    private String TAG = "QuadrilateralSelectionImageView";
    private Paint mBackgroundPaint;
    private Paint mBorderPaint;
    private Paint mCirclePaint;
    private Path mSelectionPath;
    private Path mBackgroundPath;

    private PointF mUpperLeftPoint;
    private PointF mUpperRightPoint;
    private PointF mLowerLeftPoint;
    private PointF mLowerRightPoint;
    private PointF mLastTouchedPoint;

    public QuadrilateralSelectionImageView(Context context) {
        super(context);
        init(null, 0);
    }

    public QuadrilateralSelectionImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public QuadrilateralSelectionImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(0x00000000);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(getResources().getColor(R.color.light_green_700));
        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(8);

        mCirclePaint = new Paint();
        mCirclePaint.setColor(getResources().getColor(R.color.light_green_700));
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeWidth(8);

        mSelectionPath = new Path();
        mBackgroundPath = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mUpperLeftPoint == null || mUpperRightPoint == null || mLowerRightPoint == null || mLowerLeftPoint == null) {
            setDefaultSelection();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(mUpperLeftPoint == null) return;

        mSelectionPath.reset();
        mSelectionPath.setFillType(Path.FillType.EVEN_ODD);
        mSelectionPath.moveTo(mUpperLeftPoint.x, mUpperLeftPoint.y);
        mSelectionPath.lineTo(mUpperRightPoint.x, mUpperRightPoint.y);
        mSelectionPath.lineTo(mLowerRightPoint.x, mLowerRightPoint.y);
        mSelectionPath.lineTo(mLowerLeftPoint.x, mLowerLeftPoint.y);
        mSelectionPath.close();

        mBackgroundPath.reset();
        mBackgroundPath.setFillType(Path.FillType.EVEN_ODD);
        mBackgroundPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        mBackgroundPath.addPath(mSelectionPath);

        canvas.drawPath(mBackgroundPath, mBackgroundPaint);
        canvas.drawPath(mSelectionPath, mBorderPaint);

        canvas.drawCircle(mUpperLeftPoint.x, mUpperLeftPoint.y, 30, mCirclePaint);
        Log.d(TAG, "mUpperLeftPoint: "+mUpperLeftPoint.x+","+mUpperLeftPoint.y);
        canvas.drawCircle(mUpperRightPoint.x, mUpperRightPoint.y, 30, mCirclePaint);
        Log.d(TAG, "mUpperRightPoint: "+mUpperRightPoint.x+","+mUpperRightPoint.y);
        canvas.drawCircle(mLowerRightPoint.x, mLowerRightPoint.y, 30, mCirclePaint);
        Log.d(TAG, "mLowerRightPoint: "+mLowerRightPoint.x+","+mLowerRightPoint.y);
        canvas.drawCircle(mLowerLeftPoint.x, mLowerLeftPoint.y, 30, mCirclePaint);
        Log.d(TAG, "mLowerLeftPoint: "+mLowerLeftPoint.x+","+mLowerLeftPoint.y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                boolean isConvex = false;
                PointF eventPoint = new PointF(event.getX(), event.getY());

                // Determine if the shape will still be convex when we apply the users next drag
                if (mLastTouchedPoint == mUpperLeftPoint) {
                    isConvex = isConvexQuadrilateral(eventPoint, mUpperRightPoint, mLowerRightPoint, mLowerLeftPoint);
                } else if (mLastTouchedPoint == mUpperRightPoint) {
                    isConvex = isConvexQuadrilateral(mUpperLeftPoint, eventPoint, mLowerRightPoint, mLowerLeftPoint);
                } else if (mLastTouchedPoint == mLowerRightPoint) {
                    isConvex = isConvexQuadrilateral(mUpperLeftPoint, mUpperRightPoint, eventPoint, mLowerLeftPoint);
                } else if (mLastTouchedPoint == mLowerLeftPoint) {
                    isConvex = isConvexQuadrilateral(mUpperLeftPoint, mUpperRightPoint, mLowerRightPoint, eventPoint);
                }

                if (isConvex && mLastTouchedPoint != null) {
                    mLastTouchedPoint.set(event.getX(), event.getY());
                }

                break;
            }
            case MotionEvent.ACTION_DOWN: {
                int p = 100;
                if (event.getX() < mUpperLeftPoint.x + p && event.getX() > mUpperLeftPoint.x - p &&
                        event.getY() < mUpperLeftPoint.y + p && event.getY() > mUpperLeftPoint.y - p) {
                    mLastTouchedPoint = mUpperLeftPoint;
                } else if (event.getX() < mUpperRightPoint.x + p && event.getX() > mUpperRightPoint.x - p &&
                        event.getY() < mUpperRightPoint.y + p && event.getY() > mUpperRightPoint.y - p) {
                    mLastTouchedPoint = mUpperRightPoint;
                } else if (event.getX() < mLowerRightPoint.x + p && event.getX() > mLowerRightPoint.x - p &&
                        event.getY() < mLowerRightPoint.y + p && event.getY() > mLowerRightPoint.y - p) {
                    mLastTouchedPoint = mLowerRightPoint;
                } else if (event.getX() < mLowerLeftPoint.x + p && event.getX() > mLowerLeftPoint.x - p &&
                        event.getY() < mLowerLeftPoint.y + p && event.getY() > mLowerLeftPoint.y - p) {
                    mLastTouchedPoint = mLowerLeftPoint;
                } else {
                    mLastTouchedPoint = null;
                }
                break;
            }
        }
        invalidate();
        return true;
    }

    /**
     * Translate the given point from view coordinates to image coordinates
     *
     * @param point The point to translate
     * @return The translated point
     */
    private PointF viewPointToImagePoint(PointF point) {
        Matrix matrix = new Matrix();
        getImageMatrix().invert(matrix);
        return mapPointToMatrix(point, matrix);
    }

    /**
     * Helper to map a given PointF to a given Matrix
     * <p>
     * NOTE: http://stackoverflow.com/questions/19958256/custom-imageview-imagematrix-mappoints-and-invert-inaccurate
     *
     * @param point  The point to map
     * @param matrix The matrix
     * @return The mapped point
     */
    private PointF mapPointToMatrix(PointF point, Matrix matrix) {
        float[] points = new float[]{point.x, point.y};
        matrix.mapPoints(points);
        if (points.length > 1) {
            return new PointF(points[0], points[1]);
        } else {
            return null;
        }
    }

    /**
     * Returns a list of points representing the quadrilateral.  The points are converted to represent
     * the location on the image itself, not the view.
     *
     * @return A list of points translated to map to the image
     */
    public List<PointF> getPoints() {
        List<PointF> list = new ArrayList<>();
        list.add(viewPointToImagePoint(mUpperLeftPoint));
        list.add(viewPointToImagePoint(mUpperRightPoint));
        list.add(viewPointToImagePoint(mLowerRightPoint));
        list.add(viewPointToImagePoint(mLowerLeftPoint));
        return list;
    }

    /**
     * Deletes all the set points. Used by StillImageActivity's crop image button
     * Re-initiallizing the points to erase lines and circles by onDraw call
     *
     * @return
     */
    public void deletePoints() {
        mUpperLeftPoint = null;
        mUpperRightPoint = null;
        mLowerRightPoint = null;
        mLowerLeftPoint = null;
    }

    /**
     * Set the points in order to control where the selection will be drawn.  The points should
     * be represented in regards to the image, not the view.  This method will translate from image
     * coordinates to view coordinates.
     * <p>
     * NOTE: Calling this method will invalidate the view
     *
     * @param points A list of points. Passing null will set the selector to the default selection.
     */
    public void setPoints(List<PointF> points) {
        if (points != null) {
            mUpperLeftPoint = points.get(0);
            mUpperRightPoint = points.get(1);
            mLowerRightPoint = points.get(2);
            mLowerLeftPoint = points.get(3);
        } else {
            setDefaultSelection();
        }

        invalidate();
    }

    /**
     * Gets the coordinates representing a rectangles corners.
     * <p>
     * The order of the points is
     * 0------->1
     * ^        |
     * |        v
     * 3<-------2
     *
     * @param rect The rectangle
     * @return An array of 8 floats
     */
    private float[] getCornersFromRect(RectF rect) {
        return new float[]{
                rect.left, rect.top,
                rect.right, rect.top,
                rect.right, rect.bottom,
                rect.left, rect.bottom
        };
    }

    /**
     * Sets the points into a default state (A rectangle following the image view frame with
     * padding)
     */
    private void setDefaultSelection() {
        RectF rect = new RectF();

        float padding = 100;
        rect.right = getWidth() - padding;
        rect.bottom = getHeight() - padding;
        rect.top = padding;
        rect.left = padding;

        float pts[] = getCornersFromRect(rect);
        mUpperLeftPoint = new PointF(pts[0], pts[1]);
        mUpperRightPoint = new PointF(pts[2], pts[3]);
        mLowerRightPoint = new PointF(pts[4], pts[5]);
        mLowerLeftPoint = new PointF(pts[6], pts[7]);
    }

    /**
     * Determine if the given points are a convex quadrilateral.  This is used to prevent the
     * selection from being dragged into an invalid state.
     *
     * @param ul The upper left point
     * @param ur The upper right point
     * @param lr The lower right point
     * @param ll The lower left point
     * @return True is the quadrilateral is convex
     */
    private boolean isConvexQuadrilateral(PointF ul, PointF ur, PointF lr, PointF ll) {
        // http://stackoverflow.com/questions/9513107/find-if-4-points-form-a-quadrilateral

        PointF p = ll;
        PointF q = lr;
        PointF r = subtractPoints(ur, ll);
        PointF s = subtractPoints(ul, lr);

        double s_r_crossProduct = crossProduct(r, s);
        double t = crossProduct(subtractPoints(q, p), s) / s_r_crossProduct;
        double u = crossProduct(subtractPoints(q, p), r) / s_r_crossProduct;

        if (t < 0 || t > 1.0 || u < 0 || u > 1.0) {
            return false;
        } else {
            return true;
        }
    }

    private PointF subtractPoints(PointF p1, PointF p2) {
        return new PointF(p1.x - p2.x, p1.y - p2.y);
    }

    private float crossProduct(PointF v1, PointF v2) {
        return v1.x * v2.y - v1.y * v2.x;
    }
}


