package com.jackie.sample.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.widget.ImageView;

import java.lang.ref.SoftReference;
import java.util.ArrayList;

/**
 * Created by Jackie on 2017/6/22.
 * 逐帧动画工具，OOM
 */
public class FasterAnimationsContainer {
    private Context mContext;

    private class AnimationFrame {
        private int resourceId;
        private int duration;

        AnimationFrame(int resourceId, int duration){
            this.resourceId = resourceId;
            this.duration = duration;
        }

        public int getResourceId() {
            return resourceId;
        }
        public int getDuration() {
            return duration;
        }
    }

    private ArrayList<AnimationFrame> mAnimationFrames; // list for all frames of animation
    private int mIndex; // index of current frame

    private boolean mShouldRun; // true if the animation should continue running. Used to stop the animation
    private boolean mIsRunning; // true if the animation prevents starting the animation twice
    private SoftReference<ImageView> mSoftReferenceImageView; // Used to prevent holding ImageView when it should be dead.
    private Handler mHandler; // Handler to communication with UIThread

    private Bitmap mRecycleBitmap;  //Bitmap can recycle by inBitmap is SDK Version >=11

    // Listeners
    private OnAnimationStoppedListener mOnAnimationStoppedListener;
    private OnAnimationFrameChangedListener mOnAnimationFrameChangedListener;

    private FasterAnimationsContainer(Context context, ImageView imageView) {
        this.mContext = context;

        init(imageView);
    }

    // single instance procedures
    private static FasterAnimationsContainer sInstance;

    public static FasterAnimationsContainer getInstance(Context context, ImageView imageView) {
        if (sInstance == null) {
            synchronized (FasterAnimationsContainer.class) {
                if (sInstance == null) {
                    sInstance = new FasterAnimationsContainer(context, imageView);
                }
            }
        }

        sInstance.mRecycleBitmap = null;

        return sInstance;
    }

    /**
     * initialize image view and frames
     * @param imageView  imageView
     */
    public void init(ImageView imageView){
        mAnimationFrames = new ArrayList<>();
        mSoftReferenceImageView = new SoftReference<>(imageView);

        mHandler = new Handler();

        if(mIsRunning){
            stop();
        }

        mShouldRun = false;
        mIsRunning = false;

        mIndex = -1;
    }

    /**
     * add a frame of animation
     * @param index index of animation
     * @param resId resource id of drawable
     * @param interval milliseconds
     */
    public void addFrame(int index, int resId, int interval){
        mAnimationFrames.add(index, new AnimationFrame(resId, interval));
    }

    /**
     * add a frame of animation
     * @param resId resource id of drawable
     * @param interval milliseconds
     */
    public void addFrame(int resId, int interval){
        mAnimationFrames.add(new AnimationFrame(resId, interval));
    }

    /**
     * add all frames of animation
     * @param resIds resource id of drawable
     * @param interval milliseconds
     */
    public void addAllFrames(int[] resIds, int interval){
        for(int resId : resIds){
            mAnimationFrames.add(new AnimationFrame(resId, interval));
        }
    }

    /**
     * remove a frame with index
     * @param index index of animation
     */
    public void removeFrame(int index){
        mAnimationFrames.remove(index);
    }

    /**
     * clear all frames
     */
    public void removeAllFrames(){
        mAnimationFrames.clear();
    }

    /**
     * change a frame of animation
     * @param index index of animation
     * @param resId resource id of drawable
     * @param interval milliseconds
     */
    public void replaceFrame(int index, int resId, int interval){
        mAnimationFrames.set(index, new AnimationFrame(resId, interval));
    }

    private AnimationFrame getNext() {
        mIndex++;

        if (mIndex >= mAnimationFrames.size()) {
            mIndex = 0;
        }

        return mAnimationFrames.get(mIndex);
    }

    /**
     * Listener of animation to detect stopped
     */
    public interface OnAnimationStoppedListener{
        void onAnimationStopped();
    }

    /**
     * Listener of animation to get index
     */
    public interface OnAnimationFrameChangedListener{
        void onAnimationFrameChanged(int index);
    }


    /**
     * set a listener for OnAnimationStoppedListener
     * @param listener OnAnimationStoppedListener
     */
    public void setOnAnimationStoppedListener(OnAnimationStoppedListener listener){
        mOnAnimationStoppedListener = listener;
    }

    /**
     * set a listener for OnAnimationFrameChangedListener
     * @param listener OnAnimationFrameChangedListener
     */
    public void setOnAnimationFrameChangedListener(OnAnimationFrameChangedListener listener){
        mOnAnimationFrameChangedListener = listener;
    }

    /**
     * Starts the animation
     */
    public synchronized void start() {
        mShouldRun = true;

        if (mIsRunning) {
            return;
        }

        mHandler.post(new FramesSequenceAnimation());
    }

    /**
     * Stops the animation
     */
    public synchronized void stop() {
        mShouldRun = false;
    }

    private class FramesSequenceAnimation implements Runnable{

        @Override
        public void run() {
            ImageView imageView = mSoftReferenceImageView.get();

            if (!mShouldRun || imageView == null) {
                mIsRunning = false;

                if (mOnAnimationStoppedListener != null) {
                    mOnAnimationStoppedListener.onAnimationStopped();
                }

                return;
            }

            mIsRunning = true;

            if (imageView.isShown()) {
                AnimationFrame frame = getNext();
                GetImageDrawableTask task = new GetImageDrawableTask(imageView);
                task.execute(frame.getResourceId());
                // TODO postDelayed after onPostExecute
                mHandler.postDelayed(this, frame.getDuration());
            }
        }
    }

    private class GetImageDrawableTask extends AsyncTask<Integer, Void, Drawable>{

        private ImageView mImageView;

        public GetImageDrawableTask(ImageView imageView) {
            mImageView = imageView;
        }

        @SuppressLint("NewApi")
        @Override
        protected Drawable doInBackground(Integer... params) {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB){
                return mContext.getResources().getDrawable(params[0]);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;

            if (mRecycleBitmap != null) {
                options.inBitmap = mRecycleBitmap;
            }

            mRecycleBitmap = BitmapFactory.decodeResource(mContext.getResources(), params[0], options);
            BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(),mRecycleBitmap);

            return drawable;
        }

        @Override
        protected void onPostExecute(Drawable result) {
            super.onPostExecute(result);
            if(result!=null) mImageView.setImageDrawable(result);
            if (mOnAnimationFrameChangedListener != null) {
                mOnAnimationFrameChangedListener.onAnimationFrameChanged(mIndex);
            }
        }
    }
}