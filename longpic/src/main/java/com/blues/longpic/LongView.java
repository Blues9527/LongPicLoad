package com.blues.longpic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Scroller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import androidx.annotation.Nullable;

/**
 * 大图加载
 */
public class LongView extends View implements GestureDetector.OnGestureListener,
        View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {
    private final String TAG = this.getClass().getSimpleName();
    private final Rect mRect;
    private final BitmapFactory.Options mOptions;
    private final GestureDetector mGestureDetector;
    private final Scroller mScroller;
    private final ScaleGestureDetector mScaleDetector;
    private int mImageWidth;
    private int mImageHeight;
    private BitmapRegionDecoder mDecoder;
    private int mViewHeight;
    private int mViewWidth;
    private float mScale;
    private Bitmap mBitmap;
    private Matrix matrix = new Matrix();
    private Matrix scaleMatrix = new Matrix();
    private float mCurrentScale;
    private float mTempScale = 1.0f;
    private boolean mScaling;
    private static final int SIXTY_FPS_INTERVAL = 1000 / 60;

    public LongView(Context context) {
        this(context, null);
    }

    public LongView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LongView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //step1：设置成员变量
        mRect = new Rect();
        //内存复用 bitmapFactory.options
        mOptions = new BitmapFactory.Options();
        //手势识别
        mGestureDetector = new GestureDetector(context, this);
        //滚动类
        mScroller = new Scroller(context);

        //缩放
        mScaleDetector = new ScaleGestureDetector(context, this);

        //设置触摸事件监听
        setOnTouchListener(this);
    }

    //step2:设置图片，得到图片信息
    public void setImage(InputStream is) {
        //获取图片的宽高（没有将整个图片加载进内存）
        mOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, mOptions);
        mImageWidth = mOptions.outWidth;
        mImageHeight = mOptions.outHeight;

        //开启复用
        mOptions.inMutable = true;
        //设置图片编码格式 ARGB分别表示A:透明度 R：red G：green B：blue
        //ARGB_8888 表示A R G B 各占8位，共32位，转换成字节就是 4个字节（8位=1字节 / 8bit = 1byte）
        mOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        mOptions.inJustDecodeBounds = false;

        try {
            //区域解码器
            mDecoder = BitmapRegionDecoder.newInstance(is, false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        requestLayout();
    }

    //方法重载，设置资源id
    public void setImage(int resId) {
        setImage(getResources().openRawResource(resId));
    }

    //方法重载，设置网络路径，需要手动下载
    public void setImage(String path) {
        InputStream is = null;
        try {
            URL url = new URL(path);
            URLConnection connection = url.openConnection();
            connection.setReadTimeout(15_000);
            connection.setConnectTimeout(15_000);
            connection.connect();

            is = connection.getInputStream();
            setImage(is);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //step3:测量图片
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mViewWidth = getMeasuredWidth();
        mViewHeight = getMeasuredHeight();
        //确定图片加载区域
        mRect.left = 0;
        mRect.top = 0;
        mRect.right = mImageWidth;
        //得到图片具体加载的高度，根据view的宽度和图片宽度得出缩放因子
        mScale = mViewWidth / (float) mImageWidth;
        mRect.bottom = (int) (mViewHeight / mScale);
    }

    //step4:画出具体内容
    @Override
    protected void onDraw(Canvas canvas) {
        Log.i(TAG, "onDraw");
        super.onDraw(canvas);
        if (mDecoder == null) {
            return;
        }
        //内存复用(复用的bitmap的尺寸必须要跟即将解码的bitmap尺寸一致)
        mOptions.inBitmap = mBitmap;

        //指定解码区域
        mBitmap = mDecoder.decodeRegion(mRect, mOptions);
        //得到矩阵缩放，得到view大小

        //TODO 优化，因为此处首次进入的时候想要缩放，会闪一下
        if (mScaling) {
            canvas.drawBitmap(mBitmap, scaleMatrix, null);
        } else {
            matrix.setScale(mScale, mScale);
            canvas.drawBitmap(mBitmap, matrix, null);
        }

    }

    //step5:处理点击事件
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        //直接将事件交给手势事件
        mGestureDetector.onTouchEvent(motionEvent);
        return mScaleDetector.onTouchEvent(motionEvent);
    }


    //step6:处理手势
    @Override
    public boolean onDown(MotionEvent motionEvent) {
        //如果移动没有停止，强制停止
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        return true;
    }

    //step7:处理滑动事件

    /**
     * @param motionEvent  开始事件，手指按下去获取坐标
     * @param motionEvent1 获取当前事件的坐标
     * @param v            x轴移动距离
     * @param v1           y轴移动距离
     * @return
     */
    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        //上下移动时mRect需要改变显示区域
        mRect.offset(0, (int) v1);
        //移动的时候处理顶部和底部的情况
        if (mRect.bottom > mImageHeight) {
            mRect.bottom = mImageHeight;
            mRect.top = mImageHeight - (int) (mViewHeight / mScale);

//            Log.i(TAG, mImageHeight + " " + mViewHeight / mScale);
        }

        //因为是对图片宽度进行适应屏幕宽度的，所以图片宽度等于屏幕宽度，无须左右移动
//        if (mRect.right > mImageWidth) {
//            mRect.right = mImageWidth;
//            mRect.left = mImageWidth - (int) (mImageWidth * mScale);
//        }
//        if (mRect.left < 0) {
//            mRect.left = 0;
//            mRect.right = (int) (mViewWidth / mScale);
//        }

        if (mRect.top < 0) {
            mRect.top = 0;
            mRect.bottom = (int) (mViewHeight / mScale);
        }

        invalidate();
        return false;
    }

    //step8:处理惯性问题

    /**
     * @param motionEvent  开始事件，手指按下去获取坐标
     * @param motionEvent1 获取当前事件的坐标
     * @param v            x轴移动距离
     * @param v1           y轴移动距离
     * @return
     */
    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        mScroller.fling(0, mRect.top,
                0, (int) -v1,
                0, 0,
                0, mImageHeight - (int) (mViewHeight / mScale));
        return false;
    }

    //step9:处理计算结果
    @Override
    public void computeScroll() {
        if (mScroller.isFinished()) {
            return;
        }
        //如果滑动没有结果
        if (mScroller.computeScrollOffset()) {
            mRect.top = mScroller.getCurrY();
            mRect.bottom = mRect.top + (int) (mViewHeight / mScale);
            invalidate();
        }
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
    }


    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    //----------ScaleGestureDetector-----------
    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        mCurrentScale = scaleGestureDetector.getScaleFactor();
        mCurrentScale = mTempScale * mCurrentScale;
        mCurrentScale = Math.max(0.5f, Math.min(mCurrentScale, 2.5f));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    //处理缩放逻辑
                    scaleMatrix.setScale(mCurrentScale, mCurrentScale);
                }
            });
        } else {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    //处理缩放逻辑
                    scaleMatrix.setScale(mCurrentScale, mCurrentScale);
                }
            }, SIXTY_FPS_INTERVAL);
        }
        invalidate();
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        mScaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
        mTempScale = mCurrentScale;
    }
}
