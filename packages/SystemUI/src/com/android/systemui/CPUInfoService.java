/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.systemui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class CPUInfoService extends Service {
    private View mView;
    private Thread mCurCPUThread;
    
    private class CPUView extends View {        
        private Paint mOnlinePaint;
        private Paint mOfflinePaint;
        private Paint mLpPaint;
        private float mAscent;
        private int mFH;

        private int mNeededWidth;
        private int mNeededHeight;
        
        private String[] mCurrFreq={"0", "0", "0", "0"};
        private String[] mCurrGov={"", "", "", ""};
        private boolean mLpMode;
        private String mCPUTemp;

    	private Handler mCurCPUHandler = new Handler() {
    	    public void handleMessage(Message msg) {
    	    	if(msg.obj==null){
    	    		return;
    	    	}
    	    	if(msg.what>=0 && msg.what<=3){
    	    		String[] parts=((String) msg.obj).split(":");
    	    		if(parts.length==2){
            			mCurrFreq[msg.what]=parts[0];
            			mCurrGov[msg.what]=parts[1];  
            		} else {
            			mCurrFreq[msg.what]="0";
            			mCurrGov[msg.what]="";  
            		}	
    	    	} else if(msg.what==4){
    	    		mLpMode = ((String) msg.obj).equals("1");
    	    	} else if(msg.what==5){
    	    		mCPUTemp = (String) msg.obj;
    	    		updateDisplay();
    	    	}
    	    }
    	};

        CPUView(Context c) {
            super(c);

            setPadding(4, 4, 4, 4);
            //setBackgroundResource(com.android.internal.R.drawable.load_average_background);

            // Need to scale text size by density...  but we won't do it
            // linearly, because with higher dps it is nice to squeeze the
            // text a bit to fit more of it.  And with lower dps, trying to
            // go much smaller will result in unreadable text.
            int textSize = 10;
            float density = c.getResources().getDisplayMetrics().density;
            if (density < 1) {
                textSize = 9;
            } else {
                textSize = (int)(10*density);
                if (textSize < 10) {
                    textSize = 10;
                }
            }
            mOnlinePaint = new Paint();
            mOnlinePaint.setAntiAlias(true);
            mOnlinePaint.setTextSize(textSize);
            mOnlinePaint.setARGB(255, 255, 255, 255);

            mOfflinePaint = new Paint();
            mOfflinePaint.setAntiAlias(true);
            mOfflinePaint.setTextSize(textSize);
            mOfflinePaint.setARGB(255, 255, 0, 0);

            mLpPaint = new Paint();
            mLpPaint.setAntiAlias(true);
            mLpPaint.setTextSize(textSize);
            mLpPaint.setARGB(255, 0, 255, 0);

            mAscent = mOnlinePaint.ascent();
            float descent = mOnlinePaint.descent();
            mFH = (int)(descent - mAscent + .5f);

            updateDisplay();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mCurCPUHandler.removeMessages(1);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(mNeededWidth, widthMeasureSpec),
                    resolveSize(mNeededHeight, heightMeasureSpec));
        }

		private String getCPUInfoString(int i) {
            String freq=mCurrFreq[i];
            String gov=mCurrGov[i];
            
            return "cpu:"+i+" "+gov+":"+freq;
		}
		
        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int W = mNeededWidth;
            final int RIGHT = getWidth()-1;
 
            int x = RIGHT - mPaddingRight;
            int top = mPaddingTop + 2;
            int bottom = mPaddingTop + mFH - 2;

            int y = mPaddingTop - (int)mAscent;
            
            canvas.drawText("Temp:"+mCPUTemp, RIGHT-mPaddingRight-250,
            	y-1, mOnlinePaint);
            y += mFH;
            
            for(int i=0; i<mCurrFreq.length; i++){
            	String s=getCPUInfoString(i);
				String freq=mCurrFreq[i];
               	if(!freq.equals("0")){
            	    if(i==0 && mLpMode){
            			canvas.drawText(s, RIGHT-mPaddingRight-250,
                    		y-1, mLpPaint);
            	    } else {
            			canvas.drawText(s, RIGHT-mPaddingRight-250,
                    		y-1, mOnlinePaint);
                    }
                } else {
            		canvas.drawText(s, RIGHT-mPaddingRight-250,
                    	y-1, mOfflinePaint);
                }
                y += mFH;
            }
        }

        void updateDisplay() {
            final int NW = 4;
            int maxWidth = 100;
            int neededWidth = mPaddingLeft + mPaddingRight + maxWidth;
            int neededHeight = mPaddingTop + mPaddingBottom + (mFH*(1+NW));
            if (neededWidth != mNeededWidth || neededHeight != mNeededHeight) {
                mNeededWidth = neededWidth;
                mNeededHeight = neededHeight;
                requestLayout();
            } else {
                invalidate();
            }
        }
            
    	private String toMHz(String mhzString) {
        	return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" MHz").toString();
    	}
    	
    	public Handler getHandler(){
    		return mCurCPUHandler;
    	}
    }

    protected class CurCPUThread extends Thread {
		private final String TAG = "CurCPUThread";
      	private boolean mInterrupt = false;
		private Handler mHandler;
		
		private static final String CURRENT_CPU = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
		private static final String CPU_ROOT = "/sys/devices/system/cpu/cpu";
		private static final String CPU_CUR_TAIL = "/cpufreq/scaling_cur_freq";
		private static final String CPU_LP_MODE = "/sys/kernel/debug/clock/cpu_lp/state";
	    private static final String CPU_GOV_TAIL = "/cpufreq/scaling_governor";
	    private static final String CPU_TEMP = "/sys/htc/cpu_temp";
	    		
		public CurCPUThread(Handler handler){
			mHandler=handler;
		}
			
       	public void interrupt() {
       	    mInterrupt = true;
       	}

        private String readOneLine(String fname) {
        	BufferedReader br;
        	String line = null;
        	try {
        	    br = new BufferedReader(new FileReader(fname), 512);
        	    try {
        	        line = br.readLine();
        	    } finally {
        	        br.close();
        	    }
        	} catch (Exception e) {
        	    return null;
        	}
        	return line;
    	}    

       	@Override
       	public void run() {
       	    try {
       	        while (!mInterrupt) {
       	            sleep(250);
       	            for(int i=0; i<4; i++){
       	            	final String freqFile=CPU_ROOT+i+CPU_CUR_TAIL;
       	            	String currFreq = readOneLine(freqFile);
       	            	final String govFile=CPU_ROOT+i+CPU_GOV_TAIL;
       	            	String currGov = readOneLine(govFile);
       	            	if(currFreq==null){
       	            		currFreq="0";
       	            		currGov="";
       	            	}

       	            	mHandler.sendMessage(mHandler.obtainMessage(i, currFreq+":"+currGov));
       	            }
       	            final String lpMode=readOneLine(CPU_LP_MODE);
       	            mHandler.sendMessage(mHandler.obtainMessage(4, lpMode));
       	            final String cpuTemp=readOneLine(CPU_TEMP);
       	            mHandler.sendMessage(mHandler.obtainMessage(5, cpuTemp));
       	        }
       	    } catch (InterruptedException e) {
       	        return;
       	    }
       	}
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mView = new CPUView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        params.setTitle("CPU Info");
        
        mCurCPUThread = new CurCPUThread(mView.getHandler());
        mCurCPUThread.start();

        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        wm.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCurCPUThread.isAlive()) {
            mCurCPUThread.interrupt();
            try {
                mCurCPUThread.join();
            } catch (InterruptedException e) {
            }
        }
        ((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(mView);
        mView = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
