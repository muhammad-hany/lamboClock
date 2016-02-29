/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.muhammadworkstation.lamboclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.EmbossMaskFilter;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = MyWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            lampoOnDraw(canvas,bounds);
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);


        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }




        private float  circleRatio ;
        private float progressAnimationDegree=0;

        private Path segmentDashPath;
        private Path inbetweenArcPath;
        private Path smallDashesArcPath;
        private Path indicatorRingPath;
        private Path innerBluePath1; //the outer one
        private Path innerBluePath2; //the inner one
        private Path innerYellowPath;
        private Path standAloneNumberPath;
        private Path progressAnimationPath;
        private Paint segmentPaint;
        private Paint firstSegmentPaint;
        private Paint inbetweenArcPaint;
        private Paint smallDashPaint;
        private Paint outerRingPaint;
        private Paint smallDashesPathPaint;
        private Paint textPaint;
        private Paint indicatorInnerBallPaint;
        private Paint bitmabPaint;
        private Paint indicatorRingPaint;
        private Paint indicatorRingBlurPaint;
        private Paint innerBluePaint1;
        private Paint innerBluePaint2;
        private Paint innerYellowPaint;
        private Paint standAloneNumberPaint;
        private Paint silverPaint;
        private Paint silverBlurPaint;
        private Paint progressAnimationPaint;
        private Paint progressAnimationBlurPaint;
        private Paint statusMessagePaint;
        private Paint antiFiberPaint ;

        private MaskFilter filter;
        private MaskFilter silverFilter;

        private Typeface typeface;

        private Matrix rotationMatrix;
        private Matrix progressAnimationMatrix;

        private RectF segemntDashArcRect;
        private RectF outerRingRect;
        private RectF smallDashesArcRect;
        private RectF indicatorCircleRec;
        private RectF upperArcRect;
        private RectF innerBlueRect1;
        private RectF innerBlueRect2_1;
        private RectF innerBlueRect2_2;
        private RectF innerYellowRect;

         Bitmap cashedBitmap;
         Bitmap smartBitmap;



        private Shader smartThirdCircleShader;
        private Shader smartFourthCircleShader;


        private double newSpeed;
        private double oldSpeed=0;


        private double newAcc;
        private double oldAcc=0;

        private boolean isItFirst=true;
        private boolean surfaceCreated=false;
        private boolean smartFirst=true;

        float SEGEMNT_HIEGHT =1.05f;
        private Paint txtPaint;

        private boolean isRunning=false;
        protected boolean progressAnimationState=true;
        protected int  status;
        String messgae;
        public static final int CONNECTED=0;
        public static final int CONNECTING=1;
        public static final int CHECK_LOCATION=2;

        public static final int SMART_THEME=0;
        public static final int FURIOUS_THEME=1;


        private String unitPerHour;
        private String unit;
        private String smallUnit;

        private int avgZeroCount=0;
        private int avg=0;

        private Typeface aSans;
        private String ValueTxt;



        ArrayList<Float> avgSpeeds;




        private Shader outerShader;
        private Shader innerShader;

        private static  final int W=0;
        private static  final int H=1;



        private void lampoOnDraw(Canvas canvas ,Rect bounds) {
            if (isItFirst){

                /************************************************************************
                 *************VIEW KEY***************************************************/
                circleRatio= (float) (bounds.width() / 2.6);
                /************************************************************************
                 *************VIEW KEY***************************************************/

                cashedBitmap= Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);

                newSpeed=/*Math.round(MainActivity.getSpeed())*/0;

                Canvas cashedcancvas=new Canvas(cashedBitmap);


                cashedcancvas.drawColor(Color.rgb(6, 11, 30));
                cashedcancvas.translate(bounds.width() / 2, bounds.height() / 2);



                prepareLampoPaint();
                initVariables();
                cashedcancvas.drawCircle(0, 0, circleRatio + 20, antiFiberPaint);

                drawInnerTextures(cashedcancvas, bounds);
                drawSegmentDash(cashedcancvas, bounds);
                drawNumbers(cashedcancvas, bounds);

                drawWhiteBorder(cashedcancvas, bounds);
                drawSmallDashes(cashedcancvas, bounds);


                drawSliverOuterCircle(cashedcancvas, bounds);
              //  drawAccGauge(cashedcancvas, bounds);
                canvas.drawBitmap(cashedBitmap, 0, 0, null);





               /* newSpeed=*//*Math.round(MainActivity.getSpeed());*//*0;*/
                isItFirst=false;


            }


            canvas.save();
            canvas.drawBitmap(cashedBitmap, 0, 0, null);


            canvas.translate(bounds.width() / 2, bounds.height());



            /*drawStandAloneNumber(canvas,bounds);*/



           // drawIndicator(canvas, bounds);
            /*drawComDistance(canvas,bounds);*/
            canvas.restore();



        }

        private void initVariables() {

            avgSpeeds=new ArrayList<>();


            segmentDashPath=new Path();









            segemntDashArcRect=new RectF();
            outerRingRect=new RectF();
            smallDashesArcRect=new RectF();


            rotationMatrix=new Matrix();


            inbetweenArcPath=new Path();






            smallDashesArcPath=new Path();



            indicatorRingPath=new Path();




            indicatorCircleRec=new RectF();
            upperArcRect=new RectF();
            innerBlueRect1=new RectF();

            innerBluePath1=new Path();



            innerBlueRect2_1=new RectF();
            innerBlueRect2_2=new RectF();



            innerBluePath2=new Path();



            innerYellowPath=new Path();
            innerYellowRect=new RectF();




            standAloneNumberPath=new Path();






            progressAnimationPath=new Path();
            progressAnimationMatrix=new Matrix();

            progressAnimationState=true;








            newSpeed=0;
        }

        private void drawAccGauge(Canvas canvas, Rect bounds) {


       /* segemntDashArcRect.set(-circleRatio, -circleRatio, circleRatio, circleRatio);
        segmentDashPath.moveTo(getX(65, circleRatio, 0), getY(65, circleRatio, 0));
        segmentDashPath.arcTo(segemntDashArcRect, 65, 10);
        segmentDashPath.lineTo(getX(73, circleRatio / SEGEMNT_HIEGHT, 0), getY(73, circleRatio / SEGEMNT_HIEGHT, 0));
        segmentDashPath.lineTo(getX(67, circleRatio / SEGEMNT_HIEGHT, 0), getY(67, circleRatio / SEGEMNT_HIEGHT, 0));
        segmentDashPath.close();

        segmentPaint.setColor(Color.YELLOW);
        *//*canvas.drawPath(segmentDashPath, firstSegmentPaint);*//*
        canvas.drawPath(segmentDashPath, segmentPaint);

        rotationMatrix.setRotate(-20);


        for (int i=0;i<1;i++){
            segmentDashPath.transform(rotationMatrix);
            canvas.drawPath(segmentDashPath, segmentPaint);


        }


        segmentDashPath.rewind();





        inbetweenArcPath.rewind();
        inbetweenArcPath.moveTo(getX(70,circleRatio-27.7,0),getY(70,circleRatio-27.7,0));
        inbetweenArcPath.lineTo(getX(90-23.6,circleRatio-26.8,0),getY(90-23.6,circleRatio-26.8,0));
        inbetweenArcPath.lineTo(getX(64,circleRatio,0),getY(64,circleRatio,0));
        inbetweenArcPath.arcTo(segemntDashArcRect, 64, -7.5f);
        inbetweenArcPath.lineTo(getX(53.5,circleRatio-26.8,0),getY(53.5,circleRatio-26.8,0));
        inbetweenArcPath.lineTo(getX(50,circleRatio-27.7,0),getY(50,circleRatio-27.7,0));
        canvas.drawPath(inbetweenArcPath, inbetweenArcPaint);


        // draw blue segemnt
        inbetweenArcPath.rewind();
        smallDashesArcRect.set(-circleRatio / 1.0225f, -circleRatio / 1.0225f, circleRatio / 1.0225f, circleRatio / 1.0225f);
        inbetweenArcPath.moveTo(getX(70-4.6,circleRatio / 1.0747,0),getY(70-4.6,circleRatio / 1.0747,0));
        inbetweenArcPath.lineTo(getX(70-6.6,circleRatio / 1.0225,0),getY(70-6.6,circleRatio / 1.0225,0));
        inbetweenArcPath.arcTo(smallDashesArcRect,70-6.6f,-6.5f);
        inbetweenArcPath.lineTo(getX(54.7,circleRatio / 1.0747,0),getY(54.7,circleRatio / 1.0747,0));
        smallDashesArcRect.set(-circleRatio / 1.0747f, -circleRatio / 1.0747f, circleRatio / 1.0747f, circleRatio / 1.0747f);
        inbetweenArcPath.arcTo(smallDashesArcRect,54.7f,10.5f);

        canvas.drawPath(inbetweenArcPath,smallDashesPathPaint);*/



            //my new fliped segement


            inbetweenArcPath.rewind();
            inbetweenArcPath.moveTo(getX(90 - 5, circleRatio - 19, 0), getY(90 - 5, circleRatio-19,0));
            inbetweenArcPath.lineTo(getX(90 - 7, circleRatio, 0), getY(90 - 7, circleRatio,0));
            segemntDashArcRect.set(-circleRatio, -circleRatio, circleRatio, circleRatio);
            inbetweenArcPath.arcTo(segemntDashArcRect,90-7,-13+7);
            inbetweenArcPath.lineTo(getX(90 - 15, circleRatio-19, 0), getY(90-15,circleRatio-19,0));
        /*inbetweenArcPath.close();*/

            canvas.drawPath(inbetweenArcPath, firstSegmentPaint);
            rotationMatrix.setRotate(-35);
            inbetweenArcPath.transform(rotationMatrix);
            canvas.drawPath(inbetweenArcPath, firstSegmentPaint);


            //drawing white line
            inbetweenArcPath.rewind();
            inbetweenArcPath.moveTo(getX(-10, circleRatio - 27.5, 90), getY(-10, circleRatio - 27.5, 90));
            inbetweenArcPath.lineTo(getX(-16.97, circleRatio - 24.56f, 90), getY(-16.97, circleRatio - 24.56f, 90));
            inbetweenArcPath.lineTo(getX(-14, circleRatio - inbetweenArcPaint.getStrokeWidth(), 90), getY(-14, circleRatio - inbetweenArcPaint.getStrokeWidth(), 90));
            segemntDashArcRect.set(-circleRatio + (inbetweenArcPaint.getStrokeWidth()/2), -circleRatio +(inbetweenArcPaint.getStrokeWidth()/2), circleRatio  -(inbetweenArcPaint.getStrokeWidth()/2), circleRatio  -(inbetweenArcPaint.getStrokeWidth()/2));
            inbetweenArcPath.arcTo(segemntDashArcRect, 90 - 14, -32.6f);
            inbetweenArcPath.lineTo(getX(-38, circleRatio - 24.56f, 90), getY(-38, circleRatio - 24.56f, 90));
            inbetweenArcPath.lineTo(getX(-45,circleRatio - 27.5,90),getY(-45,circleRatio - 27.5,90));
            canvas.drawPath(inbetweenArcPath,inbetweenArcPaint);




        }




       /* private void drawComDistance(Canvas canvas, Rect bounds) {
            if (MainActivity.mainUnit==MainActivity.METRIC_UNIT) {
                ValueTxt = String.valueOf((double) Math.round(MainActivity.getCumulativeDistance() * 10) / 10);
            }else {
                ValueTxt = String.valueOf((double) Math.round(MainActivity.getCumulativeDistance()*0.621371 * 10) / 10);
            }

            canvas.drawText(ValueTxt+" Km",circleRatio/1.73f,circleRatio/1.82186f,innerYellowPaint);
        }*/

        private void prepareLampoPaint(){
            segmentPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            segmentPaint.setColor(Color.rgb(60, 117, 140));
            segmentPaint.setStyle(Paint.Style.FILL);
            segmentPaint.setStrokeWidth(/*3f*/circleRatio / 92.3f);

            inbetweenArcPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            inbetweenArcPaint.setColor(Color.WHITE);
            inbetweenArcPaint.setStyle(Paint.Style.STROKE);

            smallDashPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            smallDashPaint.setColor(Color.WHITE);

            outerRingPaint =new Paint(Paint.ANTI_ALIAS_FLAG);
            outerRingPaint.setStyle(Paint.Style.STROKE);
            outerRingPaint.setColor(Color.rgb(40, 117, 140));

            firstSegmentPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            firstSegmentPaint.setColor(Color.WHITE);

            textPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
           // typeface= Typeface.createFromAsset(getContext().getAssets(), "fonts/recharge bd.ttf");
            textPaint.setTypeface(typeface);
            textPaint.setColor(Color.WHITE);

            filter=new EmbossMaskFilter(new float[]{10f,20,50},0f,50,7);

            indicatorInnerBallPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            indicatorInnerBallPaint.setColor(Color.rgb(34, 17, 71));
            indicatorInnerBallPaint.setMaskFilter(filter);

            smallDashesPathPaint =new Paint(Paint.ANTI_ALIAS_FLAG);
            smallDashesPathPaint.setStyle(Paint.Style.FILL);
            smallDashesPathPaint.setStrokeWidth(/*3*/circleRatio/92.3f);
            smallDashesPathPaint.setColor(Color.rgb(60, 117, 140));


            bitmabPaint=new Paint(Paint.FILTER_BITMAP_FLAG);


            indicatorRingPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            indicatorRingPaint.setColor(Color.rgb(254, 254, 255)/*rgb(17, 168, 171)*/);
            indicatorRingPaint.setStyle(Paint.Style.FILL);
            indicatorRingPaint.setStrokeWidth(/*3f*/circleRatio/92.3f);
            indicatorRingPaint.setStrokeJoin(Paint.Join.ROUND);
            indicatorRingPaint.setStrokeCap(Paint.Cap.ROUND);

            indicatorRingBlurPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            indicatorRingBlurPaint.setColor(Color.rgb(254, 254, 255)/*rgb(17, 168, 171)*/);
            indicatorRingBlurPaint.setStrokeWidth(/*10*/circleRatio/27.692f);
            indicatorRingBlurPaint.setMaskFilter(new BlurMaskFilter(/*10*/circleRatio/27.692f, BlurMaskFilter.Blur.SOLID));


            silverFilter =new EmbossMaskFilter(new float[]{0,-100,70},0.8f,20,5);

            outerShader =new SweepGradient(0,0,new int[]{Color.rgb(23,51,71),Color.rgb(127,197,228),Color.rgb(73,139,175)},new float[] {0.2f,0.4f,0.6f});

            innerBluePaint1=new Paint(Paint.ANTI_ALIAS_FLAG);

            innerBluePaint1.setStyle(Paint.Style.STROKE);
            innerBluePaint1.setStrokeWidth(/*6f*/circleRatio/46.1538f);
            innerBluePaint1.setShader(outerShader);


            innerShader=new SweepGradient(0,0,Color.rgb(73,139,175)/*Color.rgb(127,197,228)*/,Color.rgb(23,51,71)/*new float[] {0.2f,0.4f,0.2f}*/);


            innerBluePaint2=new Paint(Paint.ANTI_ALIAS_FLAG);
            innerBluePaint2.setShader(innerShader);

            innerYellowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            innerYellowPaint.setColor(Color.rgb(252, 220, 112));
            innerYellowPaint.setStyle(Paint.Style.STROKE);
            innerYellowPaint.setStrokeWidth(/*3f*/circleRatio/92.3f);
            innerYellowPaint.setTextSize(/*20f*/circleRatio/13.846f);

            standAloneNumberPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            standAloneNumberPaint.setTypeface(typeface);

            silverPaint =new Paint(Paint.ANTI_ALIAS_FLAG);
            silverPaint.setStyle(Paint.Style.STROKE);
            silverPaint.setColor(Color.WHITE);
            silverPaint.setStrokeWidth(/*10f*/circleRatio/27.692f);


        /*silverPaint=getDefaultInnerRimPaint();*/

            silverBlurPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            silverBlurPaint.setColor(Color.WHITE);
            silverBlurPaint.setStyle(Paint.Style.STROKE);
            silverBlurPaint.setStrokeWidth(/*15f*/circleRatio / 18.4615f);
            silverBlurPaint.setMaskFilter(new BlurMaskFilter(/*15*/circleRatio / 18.4615f, BlurMaskFilter.Blur.SOLID));

            txtPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            txtPaint.setTextSize(/*20*/circleRatio/13.846f);
            txtPaint.setColor(Color.RED);

            progressAnimationPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            progressAnimationPaint.setStyle(Paint.Style.STROKE);
            progressAnimationPaint.setStrokeWidth(/*4f*/circleRatio / 69.2307f);
            progressAnimationPaint.setColor(Color.RED);

            progressAnimationBlurPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            progressAnimationBlurPaint.setStyle(Paint.Style.STROKE);
            progressAnimationBlurPaint.setColor(Color.RED);
            progressAnimationBlurPaint.setStrokeWidth(/*40f*/circleRatio / 6.923076f);
            progressAnimationBlurPaint.setMaskFilter(new BlurMaskFilter(/*40f*/circleRatio / 6.923076f, BlurMaskFilter.Blur.NORMAL));



            statusMessagePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            statusMessagePaint.setColor(Color.YELLOW);
            statusMessagePaint.setTextSize(/*30*/circleRatio / 9.23f);
            statusMessagePaint.setTypeface(typeface);

            antiFiberPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            antiFiberPaint.setColor(Color.rgb(6, 11, 30));

            segemntDashArcRect=new RectF();
        }





        private void drawSliverOuterCircle(Canvas canvas, Rect bounds) {


            canvas.drawCircle(0, 0, /*circleRatio + 20*/circleRatio/0.93264f, silverBlurPaint);
            canvas.drawCircle(0, 0, /*circleRatio + 20*/circleRatio/0.93264f, silverPaint);

        }

        /*private void drawStandAloneNumber(Canvas canvas, Rect bounds) {

            //drawing the page stand alone path

            standAloneNumberPaint.setColor(Color.rgb(254, 254, 255));
            standAloneNumberPath.moveTo(getX(7.98, circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), 0), getY(7.98, circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), 0));
            standAloneNumberPath.lineTo(circleRatio / 2.1433f, circleRatio / 7.124f);
            standAloneNumberPath.arcTo(innerYellowRect, 16.49f, 23.42f);
            standAloneNumberPath.lineTo(getX(18.29, circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), 0), getY(18.29, circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), 0));

            standAloneNumberPath.arcTo(segemntDashArcRect, 7.98f, 10.31f);

            canvas.drawPath(standAloneNumberPath, standAloneNumberPaint);
            standAloneNumberPaint.setTextSize(circleRatio / 18.415f);

            if (MainActivity.mainUnit==MainActivity.IMPERIAL_UNIT) {
                unitPerHour ="MPH";
            }else {
                unitPerHour ="Km/h";
            }
            canvas.drawText(unitPerHour, circleRatio / 1.384f, circleRatio / 2.517f, standAloneNumberPaint);


            standAloneNumberPaint.setColor(Color.rgb(6, 11, 30));
            standAloneNumberPaint.setTextSize(circleRatio / 6.44f);
            canvas.drawText(String.valueOf((int) oldSpeed), -correct(String.valueOf((int) oldSpeed), W, standAloneNumberPaint) + *//*300*//*circleRatio / 1.38f, circleRatio / 3.461f, standAloneNumberPaint);

        *//*canvas.drawLine(300,-200,300,200,segmentPaint);*//*


        }*/



        //***************************************************************

        private void drawInnerTextures(Canvas canvas, Rect bounds) {
            //making the thin blue arc
            innerBlueRect1.set(-circleRatio / 1.6615f, -circleRatio / 1.6615f, circleRatio / 1.6615f, circleRatio / 1.6615f);
            innerBluePath1.rewind();
            innerBluePath1.moveTo(circleRatio / 1.6615f, 0);
            innerBluePath1.arcTo(innerBlueRect1, 0, -270);
            innerBluePath1.lineTo(circleRatio / 1.294f, circleRatio / 1.6615f);

            canvas.drawPath(innerBluePath1, innerBluePaint1);

            //making the wide inner texture
            innerBlueRect2_1.set(-circleRatio / 1.694f, -circleRatio / 1.694f, circleRatio / 1.694f, circleRatio / 1.694f);
            innerBlueRect2_2.set(-circleRatio / 1.976f, -circleRatio / 1.976f, circleRatio / 1.976f, circleRatio / 1.976f);
            segemntDashArcRect.set(-circleRatio / 1.0225f, -circleRatio / 1.0225f, circleRatio / 1.0225f, circleRatio / 1.0225f);
            innerBluePath2.moveTo(circleRatio / 1.694f, 0);
            innerBluePath2.arcTo(innerBlueRect2_1, 0, -270);
            innerBluePath2.lineTo(circleRatio / 1.294f, circleRatio / 1.694f);
            innerBluePath2.arcTo(segemntDashArcRect, 36.5f, -7.93f);
            innerBluePath2.lineTo(circleRatio / 16.424f, circleRatio / 2.0915f);
            innerBluePath2.lineTo(0, circleRatio / 1.976f);
            innerBluePath2.arcTo(innerBlueRect2_2, 90, 270);
            innerBluePath2.lineTo( circleRatio/1.694f, 0);


            canvas.drawPath(innerBluePath2, innerBluePaint2);

            //drawing the yellow texture

            innerYellowRect.set(-circleRatio/2.0524f, -circleRatio/2.0524f, circleRatio/2.0524f, circleRatio/2.0524f);

            innerYellowPath.moveTo(circleRatio/7.287f, 0);
            innerYellowPath.lineTo(circleRatio/2.0524f, 0);
            innerYellowPath.arcTo(innerYellowRect, 0, -270);
            innerYellowPath.lineTo(circleRatio/16.424f, circleRatio/2.1908f);
            innerYellowPath.lineTo(circleRatio/1.1519f, circleRatio/2.1908f);

            canvas.drawPath(innerYellowPath,innerYellowPaint);

            //making the yellow text of comulative distance










        }

//todo make altidude meter



        //*************************************************************************************************

        private void drawIndicator(Canvas canvas, Rect bounds) {

            if (oldSpeed<newSpeed){
                oldSpeed+=0.5;
            }else if (oldSpeed>newSpeed){
                oldSpeed -= 0.5;
            }

            rotationMatrix.setRotate((float) getDgree(oldSpeed));
            indicatorCircleRec.set(-circleRatio / 8.86f, -circleRatio / 8.86f, circleRatio / 8.86f, circleRatio / 8.86f);
            upperArcRect.set(-circleRatio / 33.6f, -circleRatio / 5.2522f, circleRatio / 33.6f, -circleRatio / 7.64f);


            canvas.drawCircle(0, 0, circleRatio / 13.845f, indicatorInnerBallPaint);
            indicatorRingPath.rewind();

            indicatorRingPath.moveTo(-circleRatio / 22.8276f, -circleRatio / 9.6145f);
            indicatorRingPath.lineTo(-circleRatio / 34.526f, -circleRatio / 5.9728f);
            indicatorRingPath.addArc(upperArcRect, 193.16f, 153.68f);
            indicatorRingPath.lineTo(circleRatio / 34.526f, -circleRatio / 5.9728f);
            indicatorRingPath.lineTo(circleRatio / 22.8276f, -circleRatio / 9.6145f);
            indicatorRingPath.arcTo(indicatorCircleRec, -67.31f, 128.57f);
            indicatorRingPath.lineTo(circleRatio / 18.4231f, circleRatio / 10.1058f);
            indicatorRingPath.lineTo(circleRatio / 52.344f, circleRatio / 1.5475f);
            indicatorRingPath.lineTo(-circleRatio / 52.344f, circleRatio / 1.5475f);
            indicatorRingPath.arcTo(indicatorCircleRec, 118.18f, 128.57f);
            indicatorRingPath.addCircle(0, 0, circleRatio / 17.306f, Path.Direction.CCW);
            indicatorRingPath.transform(rotationMatrix);
            canvas.drawPath(indicatorRingPath, indicatorRingPaint);
            canvas.drawPath(indicatorRingPath, indicatorRingBlurPaint);


            //making the red line

            indicatorRingPath.rewind();
            indicatorRingPaint.setColor(Color.RED);
            indicatorRingPaint.setMaskFilter(null);
            indicatorRingPath.moveTo(-circleRatio / 138.45f, -circleRatio / 4.49221f);
            indicatorRingPath.lineTo(circleRatio/138.45f, -circleRatio/4.4944f);
            indicatorRingPath.lineTo(circleRatio/69.225f,-circleRatio/5.2078f);
            indicatorRingPath.lineTo(circleRatio/184.6f,circleRatio/1.0365f);
            indicatorRingPath.lineTo(-circleRatio/184.6f,circleRatio/1.0365f);
            indicatorRingPath.lineTo(-circleRatio/69.225f,-circleRatio / 5.2078f);
            indicatorRingPath.lineTo(-circleRatio / 138.45f, -circleRatio / 4.49221f);
            indicatorRingPath.transform(rotationMatrix);
            canvas.drawPath(indicatorRingPath, indicatorRingPaint);










        }

        private void drawNumbers(Canvas canvas, Rect bounds) {
            textPaint.setTextSize(circleRatio / 8);
            int numbers=1;

            for (int i=0;i<360;i+=30){

                canvas.drawText(String.valueOf(numbers), getX(i, circleRatio / 1.3f, -60) - correct(String.valueOf(numbers), W, textPaint), getY(i, circleRatio / 1.3, -60) + correct(String.valueOf(numbers), H, textPaint), textPaint);


                numbers+=1;
            }
        }

        private void drawSegmentDash(Canvas canvas, Rect bounds) {


            segemntDashArcRect.set(-circleRatio, -circleRatio, circleRatio, circleRatio);

            canvas.drawArc(outerRingRect, 90, 270, false, outerRingPaint);
            segmentDashPath.moveTo(getX(85, circleRatio, 0), getY(85, circleRatio, 0));
            segmentDashPath.arcTo(segemntDashArcRect, 85, 10);
            segmentDashPath.lineTo(getX(93, circleRatio / SEGEMNT_HIEGHT, 0), getY(93, circleRatio / SEGEMNT_HIEGHT, 0));
            segmentDashPath.lineTo(getX(87, circleRatio / SEGEMNT_HIEGHT, 0), getY(87, circleRatio / SEGEMNT_HIEGHT, 0));
            segmentDashPath.close();


            firstSegmentPaint.setColor(Color.WHITE);
            canvas.drawPath(segmentDashPath, firstSegmentPaint);

            rotationMatrix.setRotate(30);


            for (int i=0;i<11;i++){
                segmentDashPath.transform(rotationMatrix);
                canvas.drawPath(segmentDashPath, segmentPaint);


            }


            segmentDashPath.rewind();












        }

        private void drawWhiteBorder(Canvas canvas, Rect bounds) {
            inbetweenArcPaint.setStrokeWidth(circleRatio / 80);
            segemntDashArcRect.set(-circleRatio + (inbetweenArcPaint.getStrokeWidth() / 2), -circleRatio + (inbetweenArcPaint.getStrokeWidth() / 2), circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2));

            //the thic white border
            inbetweenArcPath.moveTo(0, (circleRatio / SEGEMNT_HIEGHT) / 1.06f);
            inbetweenArcPath.lineTo(0, (circleRatio / SEGEMNT_HIEGHT) / 1.019f);
            inbetweenArcPath.lineTo((getX(93.5, circleRatio / 1.01, 0)), (circleRatio / SEGEMNT_HIEGHT) / 1.019f);
            inbetweenArcPath.lineTo(getX(96, circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), 0), getY(96, circleRatio - (inbetweenArcPaint.getStrokeWidth() / 2), 0));
            inbetweenArcPath.arcTo(segemntDashArcRect, 97, 17);
            inbetweenArcPath.lineTo(getX(26.4, (circleRatio / SEGEMNT_HIEGHT) / 1.019, 90), getY(26.4, (circleRatio / SEGEMNT_HIEGHT) / 1.019, 90));
            inbetweenArcPath.lineTo(getX(30, (circleRatio / SEGEMNT_HIEGHT) / 1.019, 90), getY(30, circleRatio / SEGEMNT_HIEGHT / 1.019, 90));
            drawBlueOuterCircle(canvas);

            smallDashPaint.setStrokeWidth(circleRatio / 180);


            for (int i=0;i<12;i++){
                canvas.drawPath(inbetweenArcPath, inbetweenArcPaint);
                inbetweenArcPath.transform(rotationMatrix);
            }
        }

        private void drawSmallDashes(Canvas canvas, Rect bounds) {
            canvas.save();

            for (int i=0;i<12;i++){
                canvas.save();
                for (int j=0;j<4;j++){
                    canvas.drawLine(getX(96.6, circleRatio/1.0225, 0), getY(96.6, circleRatio/1.0225 , 0),getX(96.6, circleRatio /1.0747f, 0), getY(96.6, circleRatio/1.0747f,0),smallDashPaint);
                    canvas.rotate(5.6f);
                }
                canvas.restore();
                canvas.rotate(30);
            }
            canvas.restore();
        }

        private void drawBlueOuterCircle(Canvas canvas) {
            smallDashesArcRect.set(-circleRatio / 1.0225f, -circleRatio / 1.0225f, circleRatio / 1.0225f, circleRatio / 1.0225f);

            smallDashesArcPath.moveTo(getX(94.6, circleRatio / 1.0747, 0), getY(94.6, circleRatio / 1.0747, 0));

            smallDashesArcPath.lineTo(getX(96.6, circleRatio / 1.0225, 0), getY(96.6, circleRatio / 1.025, 0));
            smallDashesArcPath.arcTo(smallDashesArcRect, 96.6f, 17);
            smallDashesArcPath.lineTo((getX(115.4, circleRatio / 1.0747, 0)), getY(115.4, circleRatio / 1.0747, 0));
            smallDashesArcRect.set(-circleRatio / 1.0747f, -circleRatio / 1.0747f, circleRatio / 1.0747f, circleRatio / 1.0747f);
            smallDashesArcPath.arcTo(smallDashesArcRect, 115.4f, -21.65f);

            for (int i=0;i<12;i++){
                canvas.drawPath(smallDashesArcPath,smallDashesPathPaint);
                smallDashesArcPath.transform(rotationMatrix);
            }


        }

        private float correct (String number,int type,Paint paint){
            if (type== W){
                return  (paint.measureText(number))/2;
            }else {
                return (Math.abs(paint.ascent())+Math.abs(paint.descent()))/4;
            }
        }





        protected double getDgree(double speed){
            return (speed*270/180);
        }




        protected float getX(double dgre, double radius, int indexAngle){
            return (float) (radius*(Math.cos(Math.toRadians(dgre + indexAngle))));
        }

        protected float getY(double dgre, double radius, int indexAngle) {
            return  (float) (radius * (Math.sin(Math.toRadians(dgre + indexAngle))));
        }
    }











    }


