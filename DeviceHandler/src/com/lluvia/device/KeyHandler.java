/*
 * Copyright (C) 2014 Slimroms
 * Copyright (C) 2018 The LLuvia Open Source Prpject
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
package com.lluvia.device;

import com.android.internal.app.Activity;
import com.android.internal.app.NotificationManager;
import com.android.internal.content.Context;
import com.android.internal.content.Intent;
import com.android.internal.content.pm.PackageManager.NameNotFoundException;
import com.android.internal.content.SharedPreferences;
import com.android.internal.hardware.Sensor;
import com.android.internal.hardware.SensorEvent;
import com.android.internal.hardware.SensorEventListener;
import com.android.internal.hardware.SensorManager;
import com.android.internal.media.AudioManager;
import com.android.internal.os.Handler;
import com.android.internal.os.Message;
import com.android.internal.os.PowerManager;
import com.android.internal.os.PowerManager.WakeLock;
import com.android.internal.os.SystemProperties;
import com.android.internal.os.Vibrator;
import com.android.internal.provider.Settings;
import com.android.internal.util.Log;
import com.android.internal.view.KeyEvent;

import com.android.internal.service.notification.ZenModeConfig;

import com.lluvia.device.settings.ScreenOffGesture;

import com.com.android.internal.internal.os.DeviceKeyHandler;
import com.com.android.internal.internal.util.ArrayUtils;
import com.com.android.internal.internal.util.lluvia.ActionConstants;
import com.com.android.internal.internal.util.lluvia.Action;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;

    // Supported scancodes
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int GESTURE_V_UP_SCANCODE = 255;
    // Slider
    private static final int MODE_TOTAL_SILENCE = 600;
    private static final int MODE_ALARMS_ONLY = 601;
    private static final int MODE_PRIORITY_ONLY = 602;
    private static final int MODE_NONE = 603;
    private static final int MODE_VIBRATE = 604;
    private static final int MODE_RING = 605;

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_SWIPE_DOWN_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_V_UP_SCANCODE,
        GESTURE_LTR_SCANCODE,
        GESTURE_GTR_SCANCODE,
        MODE_TOTAL_SILENCE,
        MODE_ALARMS_ONLY,
        MODE_PRIORITY_ONLY,
        MODE_NONE,
        MODE_VIBRATE,
        MODE_RING
    };

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final NotificationManager mNotificationManager;
    private Context mGestureContext = null;
    private EventHandler mEventHandler;
    private Handler mHandler;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;

    public KeyHandler(Context context) {
        mContext = context;
        mEventHandler = new EventHandler();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();
        mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ProximityWakeLock");

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        try {
            mGestureContext = mContext.createPackageContext(
                    "com.lluvia.device", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
        }
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            String action = null;
            switch(event.getScanCode()) {
            case GESTURE_CIRCLE_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_CIRCLE,
                        ActionConstants.ACTION_CAMERA);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOUBLE_SWIPE,
                        ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
                        doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_DOWN,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_V_UP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_UP,
                        ActionConstants.ACTION_TORCH);
                        doHapticFeedback();
                break;
            case GESTURE_LTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_LEFT,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_GTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_RIGHT,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
            case MODE_TOTAL_SILENCE:
            	Log.d(TAG, "MODE_TOTAL_SILENCE");
            	mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                     setZenMode(Settings.Global.ZEN_MODE_NO_INTERRUPTIONS); 
                     Log.d(TAG, "MODE_TOTAL_SILENCE handler executed");  
                    }
                });
                break;
            case MODE_ALARMS_ONLY:
				Log.d(TAG, "MODE_ALARMS_ONLY");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                     setZenMode(Settings.Global.ZEN_MODE_ALARMS); 
                     Log.d(TAG, "MODE_ALARMS_ONLY handler executed");  
                    }
                });
                break;
            case MODE_PRIORITY_ONLY:
            	Log.d(TAG, "MODE_PRIORITY_ONLY");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
						setZenMode(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
						setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                     Log.d(TAG, "MODE_PRIORITY_ONLY handler executed");  
                    }
                });
                break;
            case MODE_NONE:
				Log.d(TAG, "MODE_NONE");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
						setZenMode(Settings.Global.ZEN_MODE_OFF);
						setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                     Log.d(TAG, "MODE_NONE handler executed");  
                    }
                });
                break;
            case MODE_VIBRATE:
				Log.d(TAG, "MODE_VIBRATE");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
						setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                     Log.d(TAG, "MODE_VIBRATE handler executed");  
                    }
                });
                break;
            case MODE_RING:
				Log.d(TAG, "MODE_RING");
				mHandler.post(new Runnable() {
                    @Override
                    public void run() {
						setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                     Log.d(TAG, "MODE_RING handler executed");  
                    }
                });
                break;
            }

            if (action == null || action != null && action.equals(ActionConstants.ACTION_NULL)) {
                return;
            }
            if (action.equals(ActionConstants.ACTION_CAMERA)
                    || !action.startsWith("**")) {
                Action.processAction(mContext, ActionConstants.ACTION_WAKE_DEVICE, false);
            }
            Action.processAction(mContext, action, false);
        }
    }

    private void setZenMode(int mode) {
        mNotificationManager.setZenMode(mode, null, TAG);
        if (mVibrator != null) {
            mVibrator.vibrate(50);
        }
    }

    private void setRingerModeInternal(int mode) {
        mAudioManager.setRingerModeInternal(mode);
        if (mVibrator != null) {
            mVibrator.vibrate(50);
        }
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
            mVibrator.vibrate(50);
    }

    private SharedPreferences getGestureSharedPreferences() {
        return mGestureContext.getSharedPreferences(
                ScreenOffGesture.GESTURE_SETTINGS,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public boolean handleKeyEvent(KeyEvent event) {
		Log.d(TAG, "KeyEvent: " + event);
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        int scanCode = event.getScanCode();
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, scanCode);
        if (isKeySupported && !mEventHandler.hasMessages(GESTURE_REQUEST)) {
            Message msg = getMessageForKeyEvent(event);
            if (scanCode < MODE_TOTAL_SILENCE && mProximitySensor != null) {
				Log.d(TAG, "Handling Key Event: " + event + " with a delay");
                mEventHandler.sendMessageDelayed(msg, 200);
                processEvent(event);
            } else {
				Log.d(TAG, "Handling Key Event: " + event);
                mEventHandler.sendMessage(msg);
            }
        }
        return isKeySupported;
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }

    private void processEvent(final KeyEvent keyEvent) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(keyEvent);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public boolean canHandleKeyEvent(KeyEvent event) {
    	return false;
    }

    @Override
    public boolean isCameraLaunchEvent(KeyEvent event) {
    	return false;
    }

    @Override
    public boolean isWakeEvent(KeyEvent event) {
    	return false;
    }

    @Override
    public boolean isDisabledKeyEvent(KeyEvent event) {
    	return false;
    }

    @Override
    public Intent isActivityLaunchEvent(KeyEvent event) {
        return null;
    }
}