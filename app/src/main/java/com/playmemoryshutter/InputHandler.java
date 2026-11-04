package com.playmemoryshutter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Listens to physical button presses globally via a transparent System Alert overlay.
 */
public class InputHandler {
    private static final String TAG = "PlayMemoryShutter";

    public static final int ISV_KEY_S2 = 518; // Shutter full-press
    public static final int ISV_KEY_STASTOP = 515; // Video record button

    private final Context context;
    private final CameraController cameraController;
    private WindowManager windowManager;
    private View interceptView;
    private WindowManager.LayoutParams layoutParams;
    private boolean isListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public InputHandler(Context context, CameraController cameraController) {
        this.context = context;
        this.cameraController = cameraController;
    }

    public void startListening() {
        if (isListening) return;

        Log.i(TAG, "InputHandler: startListening()");

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        interceptView = new View(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                // Ignore events we are injecting
                if (CameraController.isInjecting.get()) {
                    return true;
                }

                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    boolean handledByUs = handleKeyDown(event.getKeyCode());
                    if (handledByUs) {
                        return true;
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (isRemappedKey(event.getKeyCode())) {
                        return true; // Consume UP for mapped keys
                    }
                }

                if (!isRemappedKey(event.getKeyCode())) {
                    cameraController.passThroughKeyEvent(event.getAction(), event.getKeyCode());
                }

                return true;
            }
        };

        interceptView.setFocusableInTouchMode(true);

        layoutParams = new WindowManager.LayoutParams(
                0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        try {
            windowManager.addView(interceptView, layoutParams);
            interceptView.requestFocus();
            isListening = true;
            Log.i(TAG, "InputHandler: Overlay view added and focused.");
        } catch (Exception e) {
            Log.e(TAG, "InputHandler: Failed to add overlay.", e);
        }
    }

    public void stopListening() {
        Log.i(TAG, "InputHandler: stopListening()");
        if (isListening && windowManager != null && interceptView != null) {
            try {
                windowManager.removeView(interceptView);
                isListening = false;
            } catch (Exception e) {
                Log.e(TAG, "InputHandler: Error removing overlay", e);
            }
        }
    }

    public void setOverlayFocusable(final boolean focusable) {
        if (!isListening || windowManager == null || interceptView == null) {
            return;
        }
        final Runnable updateTask = new Runnable() {
            @Override
            public void run() {
                if (!isListening || windowManager == null || interceptView == null) return;
                try {
                    if (focusable) {
                        layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    } else {
                        layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    }
                    windowManager.updateViewLayout(interceptView, layoutParams);
                    if (focusable) {
                        interceptView.requestFocus();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update overlay focus state", e);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateTask.run();
        } else {
            final CountDownLatch latch = new CountDownLatch(1);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        updateTask.run();
                    } finally {
                        latch.countDown();
                    }
                }
            });
            try {
                latch.await(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isRemappedKey(int keyCode) {
        if (keyCode != ISV_KEY_S2 && keyCode != ISV_KEY_STASTOP) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        int shutterMode = prefs.getInt(MainActivity.KEY_SHUTTER_MODE, 0);
        int videoBtnMode = prefs.getInt(MainActivity.KEY_VIDEO_BTN_MODE, 0);

        if (keyCode == ISV_KEY_S2) {
            if (shutterMode == 2) return true;
            if (shutterMode == 1 && cameraController.isMovieModeDial()) return true;
        }
        if (keyCode == ISV_KEY_STASTOP) {
            if (videoBtnMode == 1) return true;
        }
        return false;
    }

    private boolean handleKeyDown(int keyCode) {
        if (!isRemappedKey(keyCode)) return false;

        if (keyCode == ISV_KEY_S2) {
            Log.i(TAG, "Shutter intercepted -> Video");
            cameraController.startVideo();
            return true;
        }
        if (keyCode == ISV_KEY_STASTOP) {
            Log.i(TAG, "Video button intercepted -> Take Photo");
            cameraController.takePicture();
            return true;
        }
        return false;
    }
}