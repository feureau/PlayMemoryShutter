package com.playmemoryshutter;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper class around camera controls.
 * Uses Android Instrumentation to dispatch hardware keys securely.
 */
public class CameraController {
    private static final String TAG = "PlayMemoryShutter";

    // Flag to prevent infinite loops when we inject keys that our own InputHandler might catch
    public static final AtomicBoolean isInjecting = new AtomicBoolean(false);

    private final ExecutorService executorService;
    private InputHandler inputHandler;

    public CameraController() {
        executorService = Executors.newSingleThreadExecutor();
    }

    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    public void open() {
        Log.i(TAG, "CameraController: open() - using global hardware injection");
    }

    public void release() {
        Log.i(TAG, "CameraController: release()");
        if (executorService != null) {
            executorService.shutdownNow();
        }
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
            Log.i(TAG, "Could not read exact scene mode dial via ScalarProperties. Assuming Not Movie Mode.");
        }
        return false;
    }

    public void startVideo() {
        Log.i(TAG, "CameraController: Executing startVideo()");
        injectSyntheticTap(InputHandler.ISV_KEY_STASTOP); // 515
    }

    public void takePicture() {
        Log.i(TAG, "CameraController: Executing takePicture()");
        injectSyntheticTap(InputHandler.ISV_KEY_S2); // 518
    }

    /**
     * Re-injects a key event into the system so the native camera app can process it.
     * Used for keys we intercept but do not want to override (e.g. Menu, Dials, Half-press).
     */
    public void passThroughKeyEvent(final int action, final int keyCode) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    isInjecting.set(true);

                    if (action == KeyEvent.ACTION_DOWN) {
                        if (inputHandler != null) {
                            inputHandler.setOverlayFocusable(false);
                        }
                    }

                    Instrumentation inst = new Instrumentation();
                    long time = SystemClock.uptimeMillis();
                    KeyEvent event = new KeyEvent(time, time, action, keyCode, 0);
                    inst.sendKeySync(event);

                    if (action == KeyEvent.ACTION_UP) {
                        if (inputHandler != null) {
                            inputHandler.setOverlayFocusable(true);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error passing through key", e);
                } finally {
                    // Slight delay to ensure dispatch before unblocking
                    try { Thread.sleep(10); } catch (Exception ignored) {}
                    isInjecting.set(false);
                }
            }
        });
    }

    private void injectSyntheticTap(final int keyCode) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    isInjecting.set(true);

                    if (inputHandler != null) {
                        inputHandler.setOverlayFocusable(false);
                    }

                    Instrumentation inst = new Instrumentation();
                    long downTime = SystemClock.uptimeMillis();
                    long eventTime = SystemClock.uptimeMillis();

                    KeyEvent downEvent = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
                    inst.sendKeySync(downEvent);

                    Thread.sleep(20);

                    KeyEvent upEvent = new KeyEvent(downTime, eventTime + 20, KeyEvent.ACTION_UP, keyCode, 0);
                    inst.sendKeySync(upEvent);

                } catch (Exception e) {
                    Log.e(TAG, "Error injecting key tap", e);
                } finally {
                    if (inputHandler != null) {
                        inputHandler.setOverlayFocusable(true);
                    }
                    try { Thread.sleep(30); } catch (Exception ignored) {}
                    isInjecting.set(false);
                }
            }
        });
    }
}