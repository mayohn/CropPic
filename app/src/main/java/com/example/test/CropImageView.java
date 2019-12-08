package com.example.test;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class CropImageView extends View implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener {
    private static final String TAG = "CropImageView";
    private Bitmap mBitmap;
    private static final int PADDING = 40;
    private static final int CORNER_LENGH = 40;
    private static final int RECT_STROKE = 3;
    private static final int CORNER_STROKE = 8;
    private static final String BLACK_COLOR = "#CC000000";
    private Paint mPaint;
    private Paint mRectanglePaint;
    private Paint mCornerPaint;
    private Paint mBlackPaint;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private float mOffsetX, mOffsetY;
    private RectF mDst;//最初的图片显示矩形
    private RectF mBitmapRect;//图片即时矩形
    private RectF mRect;////矩形的最初大小
    private RectF mRectangle;//矩形的即时大小
    private ArrayList<RectF> cornerList = new ArrayList<>();
    private int pointIndex = -1;
    private float mZoom = 1f;
    private ValueAnimator animationRestore;
    private ValueAnimator flingAnimation;
    private float mLeft, mTop;
    private Scroller scroller;


    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void initView() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setDither(true);//防抖

        mRectanglePaint = new Paint();
        mRectanglePaint.setAntiAlias(true);
        mRectanglePaint.setDither(true);
        mRectanglePaint.setStrokeWidth(RECT_STROKE);
        mRectanglePaint.setStyle(Paint.Style.STROKE);
        mRectanglePaint.setColor(Color.GREEN);

        mCornerPaint = new Paint();
        mCornerPaint.setAntiAlias(true);
        mCornerPaint.setDither(true);
        mCornerPaint.setStrokeWidth(CORNER_STROKE);
        mCornerPaint.setStyle(Paint.Style.STROKE);
        mCornerPaint.setColor(Color.GREEN);


        mBlackPaint = new Paint();
        mBlackPaint.setAntiAlias(true);
        mBlackPaint.setDither(true);
        mBlackPaint.setStyle(Paint.Style.FILL);
        mBlackPaint.setColor(Color.parseColor(BLACK_COLOR));
        gestureDetector = new GestureDetector(getContext(), this);
        scaleGestureDetector = new ScaleGestureDetector(getContext(), this);
        scroller = new Scroller(getContext(), null, true);
        mOffsetX = 0;
        mOffsetY = 0;

    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
//        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        calcuBitmap(w, h);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackgroud(canvas);
        canvas.translate(mOffsetX, mOffsetY);
        drawBitmap(canvas);
        canvas.translate(-mOffsetX, -mOffsetY);
        drawRectAngle(canvas);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = true;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (containPoint(event)) {

                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                MotionEvent eventUp = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), MotionEvent.ACTION_UP, event.getX(), event.getY(), event.getMetaState());
                rectEvent(eventUp);
                break;
        }

        if (pointIndex > -1) {
            rectEvent(event);
            return retVal;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            restoreAnimation();
        }
        retVal = scaleGestureDetector.onTouchEvent(event);
        retVal = gestureDetector.onTouchEvent(event) || retVal;
        return retVal;
    }

    private void drawBackgroud(Canvas canvas) {
        if (getBackground() == null) {
            mPaint.setColor(Color.BLACK);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
        }
    }

    private boolean containPoint(MotionEvent event) {
        pointIndex = -1;
        for (int i = 0; i < cornerList.size(); i++) {
            RectF rectF = cornerList.get(i);
            if (rectF.contains(event.getX(), event.getY())) {
                pointIndex = i + 1;
                return true;
            }
        }
        return false;
    }

    private float lastX, lastY;

    private void rectEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                getRectAngle(event);
                lastX = event.getX();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                pointIndex = -1;
                break;
        }
    }

    private void getRectAngle(MotionEvent event) {
        float dx = event.getX() - lastX;
        float dy = event.getY() - lastY;


        float rectLeft = 0;
        float rectTop = 0;
        float rectRight = 0;
        float rectBottom = 0;
        switch (pointIndex) {
            case 1://左上角
                if (mRectangle.left + dx < mRect.left) {
                    dx = mRect.left - mRectangle.left;
                }
                if (mRectangle.top + dy < mRect.top) {
                    dy = mRect.top - mRectangle.top;
                }
                rectLeft = mRectangle.left + dx;
                rectTop = mRectangle.top + dy;
                rectRight = mRectangle.right;
                rectBottom = mRectangle.bottom;
                if (rectRight - rectLeft < CORNER_LENGH * 3) {
                    rectLeft = rectRight - CORNER_LENGH * 3;
                }
                if (rectBottom - rectTop < CORNER_LENGH * 3) {
                    rectTop = rectBottom - CORNER_LENGH * 3;
                }
                break;
            case 2://上边
                if (mRectangle.top + dy < mRect.top) {
                    dy = mRect.top - mRectangle.top;
                }
                rectLeft = mRectangle.left;
                rectTop = mRectangle.top + dy;
                rectRight = mRectangle.right;
                rectBottom = mRectangle.bottom;
                if (rectBottom - rectTop < CORNER_LENGH * 3) {
                    rectTop = rectBottom - CORNER_LENGH * 3;
                }
                break;
            case 3://右上角
                if (mRectangle.right + dx > mRect.right) {
                    dx = mRect.right - mRectangle.right;
                }
                if (mRectangle.top + dy < mRect.top) {
                    dy = mRect.top - mRectangle.top;
                }
                rectLeft = mRectangle.left;
                rectTop = mRectangle.top + dy;
                rectRight = mRectangle.right + dx;
                rectBottom = mRectangle.bottom;
                if (rectRight - rectLeft < CORNER_LENGH * 3) {
                    rectRight = rectLeft + CORNER_LENGH * 3;
                }
                if (rectBottom - rectTop < CORNER_LENGH * 3) {
                    rectTop = rectBottom - CORNER_LENGH * 3;
                }
                break;
            case 4://左边
                if (mRectangle.left + dx < mRect.left) {
                    dx = mRect.left - mRectangle.left;
                }
                rectLeft = mRectangle.left + dx;
                rectTop = mRectangle.top;
                rectRight = mRectangle.right;
                rectBottom = mRectangle.bottom;
                if (rectRight - rectLeft < CORNER_LENGH * 3) {
                    rectLeft = rectRight - CORNER_LENGH * 3;
                }

                break;
            case 5://右边
                if (mRectangle.right + dx > mRect.right) {
                    dx = mRect.right - mRectangle.right;
                }
                rectLeft = mRectangle.left;
                rectTop = mRectangle.top;
                rectRight = mRectangle.right + dx;
                rectBottom = mRectangle.bottom;
                if (rectRight - rectLeft < CORNER_LENGH * 3) {
                    rectRight = rectLeft + CORNER_LENGH * 3;
                }
                break;
            case 6://左下角
                if (mRectangle.left + dx < mRect.left) {
                    dx = mRect.left - mRectangle.left;
                }
                if (mRectangle.bottom + dy > mRect.bottom) {
                    dy = mRect.bottom - mRectangle.bottom;
                }
                rectLeft = mRectangle.left + dx;
                rectTop = mRectangle.top;
                rectRight = mRectangle.right;
                rectBottom = mRectangle.bottom + dy;
                if (rectRight - rectLeft < CORNER_LENGH * 3) {
                    rectLeft = rectRight - CORNER_LENGH * 3;
                }
                if (rectBottom - rectTop < CORNER_LENGH * 3) {
                    rectBottom = rectTop + CORNER_LENGH * 3;
                }
                break;
            case 7://下边
                if (mRectangle.bottom + dy > mRect.bottom) {
                    dy = mRect.bottom - mRectangle.bottom;
                }
                rectLeft = mRectangle.left;
                rectTop = mRectangle.top;
                rectRight = mRectangle.right;
                rectBottom = mRectangle.bottom + dy;
                if (rectBottom - rectTop < CORNER_LENGH * 3) {
                    rectBottom = rectTop + CORNER_LENGH * 3;
                }
                break;
            case 8://右下角
                if (mRectangle.right + dx > mRect.right) {
                    dx = mRect.right - mRectangle.right;
                }
                if (mRectangle.bottom + dy > mRect.bottom) {
                    dy = mRect.bottom - mRectangle.bottom;
                }
                rectLeft = mRectangle.left;
                rectTop = mRectangle.top;
                rectRight = mRectangle.right + dx;
                rectBottom = mRectangle.bottom + dy;
                if (rectRight - rectLeft < CORNER_LENGH * 3) {
                    rectRight = rectLeft + CORNER_LENGH * 3;
                }
                if (rectBottom - rectTop < CORNER_LENGH * 3) {
                    rectBottom = rectTop + CORNER_LENGH * 3;
                }
                break;
        }
        mRectangle = new RectF(rectLeft, rectTop, rectRight, rectBottom);
        invalidate();
    }

    private void calcuBitmap(int w, int h) {
        if (mBitmap != null) {
            if (mDst == null) {
                float ratio = (float) (w - 2 * PADDING) / (float) (h - 2 * PADDING);
                float bitmapRatio = (float) mBitmap.getWidth() / (float) mBitmap.getHeight();
                float width = 0, height = 0;
                if (ratio > bitmapRatio) {//以高为限制
                    height = h - 2 * PADDING;
                    width = height * bitmapRatio;
                    mTop = PADDING;
                    mLeft = (w - width) / 2f;
                } else {
                    width = w - 2 * PADDING;
                    height = width / bitmapRatio;
                    mTop = (h - height) / 2f;
                    mLeft = PADDING;
                }
                mDst = new RectF(0, 0, width, height);
                mRectangle = new RectF(mLeft, mTop, mLeft + width, mTop + height);
                mRect = new RectF(mLeft, mTop, mLeft + width, mTop + height);
            } else {
                mLeft = mRectangle.left;
                mTop = mRectangle.top;
            }
            mBitmapRect = new RectF(mDst.left, mDst.top, mDst.right, mDst.bottom);
            Path path = new Path();
            path.addRect(mBitmapRect, Path.Direction.CCW);
            Matrix matrix = new Matrix();
            matrix.setScale(mZoom, mZoom);
            matrix.postTranslate(mLeft + mOffsetX, mTop + mOffsetY);
            path.transform(matrix);
            path.computeBounds(mBitmapRect, false);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        mOffsetX -= distanceX;
        mOffsetY -= distanceY;
        invalidate();
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float dr = detector.getScaleFactor();
        float realX = detector.getFocusX() - mOffsetX - mLeft;
        float realY = detector.getFocusY() - mOffsetY - mTop;


        mZoom = mZoom * dr;
        calcuBitmap(getWidth(), getHeight());
        mOffsetX = detector.getFocusX() - realX * dr - mLeft;
        mOffsetY = detector.getFocusY() - realY * dr - mTop;
        invalidate();
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    /**
     * 绘制图片
     *
     * @param canvas
     */
    private void drawBitmap(Canvas canvas) {
        if (mBitmap != null) {
            Rect src = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            Path path = new Path();
            path.addRect(mDst, Path.Direction.CCW);
            Matrix matrix = new Matrix();
            matrix.setScale(mZoom, mZoom);
            matrix.postTranslate(mLeft, mTop);
            path.transform(matrix);
            RectF matrixRectf = new RectF();
            path.computeBounds(matrixRectf, false);
            canvas.drawBitmap(mBitmap, src, matrixRectf, mPaint);
        }
    }

    /**
     * 绘制矩形框
     *
     * @param canvas
     */
    private void drawRectAngle(Canvas canvas) {
        if (mRectangle != null) {
            Path path = new Path();
            //左上角
            path.moveTo(mRectangle.left + CORNER_LENGH, mRectangle.top - RECT_STROKE / 2f);
            path.lineTo(mRectangle.left - RECT_STROKE / 2f, mRectangle.top - RECT_STROKE / 2f);
            path.lineTo(mRectangle.left - RECT_STROKE / 2f, mRectangle.top + CORNER_LENGH);
            //上边
            path.moveTo(mRectangle.centerX() - CORNER_LENGH / 2f, mRectangle.top - RECT_STROKE / 2f);
            path.lineTo(mRectangle.centerX() + CORNER_LENGH / 2f, mRectangle.top - RECT_STROKE / 2f);
            //右上角
            path.moveTo(mRectangle.right - CORNER_LENGH, mRectangle.top - RECT_STROKE / 2f);
            path.lineTo(mRectangle.right + RECT_STROKE / 2f, mRectangle.top - RECT_STROKE / 2f);
            path.lineTo(mRectangle.right + RECT_STROKE / 2f, mRectangle.top + CORNER_LENGH);
            //左边
            path.moveTo(mRectangle.left - RECT_STROKE / 2f, mRectangle.centerY() - CORNER_LENGH / 2f);
            path.lineTo(mRectangle.left - RECT_STROKE / 2f, mRectangle.centerY() + CORNER_LENGH / 2f);
            //右边
            path.moveTo(mRectangle.right + RECT_STROKE / 2f, mRectangle.centerY() - CORNER_LENGH / 2f);
            path.lineTo(mRectangle.right + RECT_STROKE / 2f, mRectangle.centerY() + CORNER_LENGH / 2f);
            //左下角
            path.moveTo(mRectangle.left - RECT_STROKE / 2f, mRectangle.bottom - CORNER_LENGH);
            path.lineTo(mRectangle.left - RECT_STROKE / 2f, mRectangle.bottom + RECT_STROKE / 2f);
            path.lineTo(mRectangle.left + CORNER_LENGH, mRectangle.bottom + RECT_STROKE / 2f);

            //下边
            path.moveTo(mRectangle.centerX() - CORNER_LENGH / 2f, mRectangle.bottom + RECT_STROKE / 2f);
            path.lineTo(mRectangle.centerX() + CORNER_LENGH / 2f, mRectangle.bottom + RECT_STROKE / 2f);
            //右下角
            path.moveTo(mRectangle.right - CORNER_LENGH, mRectangle.bottom + RECT_STROKE / 2f);
            path.lineTo(mRectangle.right + RECT_STROKE / 2f, mRectangle.bottom + RECT_STROKE / 2f);
            path.lineTo(mRectangle.right + RECT_STROKE / 2f, mRectangle.bottom - CORNER_LENGH);
            cornerList.clear();
            cornerList.add(new RectF(mRectangle.left - CORNER_LENGH, mRectangle.top - CORNER_LENGH, mRectangle.left + CORNER_LENGH, mRectangle.top + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.centerX() - CORNER_LENGH, mRectangle.top - CORNER_LENGH, mRectangle.centerX() + CORNER_LENGH, mRectangle.top + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.right - CORNER_LENGH, mRectangle.top - CORNER_LENGH, mRectangle.right + CORNER_LENGH, mRectangle.top + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.left - CORNER_LENGH, mRectangle.centerY() - CORNER_LENGH, mRectangle.left + CORNER_LENGH, mRectangle.centerY() + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.right - CORNER_LENGH, mRectangle.centerY() - CORNER_LENGH, mRectangle.right + CORNER_LENGH, mRectangle.centerY() + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.left - CORNER_LENGH, mRectangle.bottom - CORNER_LENGH, mRectangle.left + CORNER_LENGH, mRectangle.bottom + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.centerX() - CORNER_LENGH, mRectangle.bottom - CORNER_LENGH, mRectangle.centerX() + CORNER_LENGH, mRectangle.bottom + CORNER_LENGH));
            cornerList.add(new RectF(mRectangle.right - CORNER_LENGH, mRectangle.bottom - CORNER_LENGH, mRectangle.right + CORNER_LENGH, mRectangle.bottom + CORNER_LENGH));
            Path rectPath = new Path();
            rectPath.addRect(0, 0, getWidth(), mRectangle.top, Path.Direction.CCW);
            rectPath.addRect(0, mRectangle.top, mRectangle.left, mRectangle.bottom, Path.Direction.CCW);
            rectPath.addRect(0, mRectangle.bottom, getWidth(), getHeight(), Path.Direction.CCW);
            rectPath.addRect(mRectangle.right, mRectangle.top, getWidth(), mRectangle.bottom, Path.Direction.CCW);
            canvas.drawPath(rectPath, mBlackPaint);
            canvas.drawPath(path, mCornerPaint);
            canvas.drawRect(mRectangle, mRectanglePaint);
        }
    }

    public void restoreAnimation() {
        stopAll();
        animationRestore = ValueAnimator.ofFloat(0, 1);
        animationRestore.setInterpolator(new DecelerateInterpolator());
        ZoomAnimation zoomAnimation = new ZoomAnimation(mZoom, mOffsetX, mOffsetY, mLeft, mTop);
        animationRestore.addUpdateListener(zoomAnimation);
        animationRestore.addListener(zoomAnimation);
        animationRestore.setDuration(300);
        animationRestore.start();
    }

    class ZoomAnimation implements ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {
        float zoom, offsetX, offsetY, left, top;

        ZoomAnimation(float zoom, float offsetX, float offsetY, float left, float top) {
            this.zoom = zoom;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.left = left;
            this.top = top;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float offset = (Float) animation.getAnimatedValue();
            if (zoom <= 1) {
                mOffsetX = offsetX - offsetX * offset;
                mOffsetY = offsetY - offsetY * offset;
                mZoom = zoom + (1f - zoom) * offset;
            } else {
                if (mOffsetX > 0) {
                    mOffsetX = offsetX - offsetX * offset;
                }
                if (mOffsetY > 0) {
                    mOffsetY = offsetY - offsetY * offset;
                }
                if (mOffsetX < getWidth() - left * 2 - mDst.width() * zoom) {
                    mOffsetX = offsetX + (getWidth() - left * 2 - mDst.width() * zoom - offsetX) * offset;
                }
                if (mOffsetY < getHeight() - top * 2 - mDst.height() * zoom) {
                    mOffsetY = offsetY + (getHeight() - top * 2 - mDst.height() * zoom - offsetY) * offset;
                }
            }
            calcuBitmap(getWidth(), getHeight());
            invalidate();
        }

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {

        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    }

    public void stopAll() {
        stopFling();
    }

    public void stopFling() {
        if (flingAnimation != null) {
            scroller.forceFinished(true);
            flingAnimation.cancel();
            flingAnimation = null;
        }
    }
}
