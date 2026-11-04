package com.playmemoryshutter;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Wrapper class around camera controls.
 * Uses Android Instrumentation to dispatch hardware keys securely.
 */
public class CameraController {
    private static final String TAG = "PlayMemoryShutter";

    // Flag to prevent infinite loops when we inject keys that our own InputHandler might catch
    public static volatile boolean isInjecting = false;

    public void open() {
        Log.i(TAG, "CameraController: open() - using global hardware injection");
    }

    public void release() {
        Log.i(TAG, "CameraController: release()");
    }

    public boolean isMovieModeDial() {
        try {
            Class<?> scalarProps = Class.forName("com.sony.scalar.sysutil.ScalarProperties");
            java.lang.reflect.Method getInt = scalarProps.getMethod("getInt", String.class);

            // INTVAL_UI_SCENE_MODE_MOVIE = 2085
            int currentMode = (Integer) getInt.invoke(null, "ui.scene.mode");
            Log.i(TAG, "Current Scene Mode: " + currentMode);
            return currentMode == 2085;
        } catch (Exception e) {
            Log.i(TAG, "Could not read exact scene mode dial via ScalarProperties. Assuming Movie Mode.");
        }
        return true;
    }

    public void startVideo() {
        Log.i(TAG, "CameraController: Executing startVideo()");
        injectKey(InputHandler.ISV_KEY_STASTOP); // 515
    }

    public void takePicture() {
        Log.i(TAG, "CameraController: Executing takePicture()");
        injectKey(InputHandler.ISV_KEY_S2); // 518
    }

    /**
     * Re-injects a key into the system so the native camera app can process it.
     * Used for keys we intercept but do not want to override (e.g. Menu, Dials, Half-press).
     */
    public void passThroughKey(int keyCode) {
        injectKey(keyCode);
    }

    private void injectKey(final int keyCode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    isInjecting = true; // Block our InputHandler from catching this

                    Instrumentation inst = new Instrumentation();
                    long downTime = SystemClock.uptimeMillis();
                    long eventTime = SystemClock.uptimeMillis();

                    KeyEvent downEvent = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
                    inst.sendKeySync(downEvent);

                    Thread.sleep(20); // Short delay to simulate real button press

                    KeyEvent upEvent = new KeyEvent(downTime, eventTime + 20, KeyEvent.ACTION_UP, keyCode, 0);
                    inst.sendKeySync(upEvent);

                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: Need INJECT_EVENTS permission or system privileges", e);
                } catch (Exception e) {
                    Log.e(TAG, "Error injecting key", e);
                } finally {
                    // Small delay before unblocking to ensure the system processes the UP event
                    try { Thread.sleep(30); } catch (Exception ignored) {}
                    isInjecting = false;
                }
            }
        }).start();
    }
}