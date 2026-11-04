package com.playmemoryshutter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

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
    private boolean isListening = false;

    public InputHandler(Context context, CameraController cameraController) {
        this.context = context;
        this.cameraController = cameraController;
    }

    public void startListening() {
        Log.i(TAG, "InputHandler: startListening()");

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        interceptView = new View(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                // If we are actively injecting a key programmatically, ignore all events to prevent infinite loops
                if (CameraController.isInjecting) {
                    return true;
                }

                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    boolean handledByUs = handleKeyDown(event.getKeyCode());

                    if (!handledByUs) {
                        // FOCUS STEALING FIX:
                        // Because our overlay holds focus, returning false drops the key entirely.
                        // To allow normal camera operation (dials, menu, half-press), we MUST re-inject it.
                        cameraController.passThroughKey(event.getKeyCode());
                    }
                }

                // We ALWAYS return true here because we are manually routing the keys.
                // If we handled it, we drop it. If we didn't, we've already re-injected it above.
                return true;
            }
        };

        interceptView.setFocusableInTouchMode(true);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        try {
            windowManager.addView(interceptView, params);
            interceptView.requestFocus();
            isListening = true;
            Log.i(TAG, "InputHandler: Overlay view added and focused.");
        } catch (Exception e) {
            Log.e(TAG, "InputHandler: Failed to add overlay. SYSTEM_ALERT_WINDOW permission missing?", e);
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

    /**
     * Checks settings and decides whether to block and override a key press.
     * @return true if intercepted and overridden, false if the key should be passed through normally.
     */
    private boolean handleKeyDown(int keyCode) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);

        int shutterMode = prefs.getInt(MainActivity.KEY_SHUTTER_MODE, 0);
        int videoBtnMode = prefs.getInt(MainActivity.KEY_VIDEO_BTN_MODE, 0);

        if (keyCode == ISV_KEY_S2) {
            if (shutterMode == 2) {
                Log.i(TAG, "Shutter intercepted -> Video (All Modes)");
                cameraController.startVideo();
                return true;
            } else if (shutterMode == 1) {
                if (cameraController.isMovieModeDial()) {
                    Log.i(TAG, "Shutter intercepted -> Video (Movie Mode matches)");
                    cameraController.startVideo();
                    return true;
                }
            }
            return false;
        }

        if (keyCode == ISV_KEY_STASTOP) {
            if (videoBtnMode == 1) {
                Log.i(TAG, "Video button intercepted -> Take Photo");
                cameraController.takePicture();
                return true;
            }
            return false;
        }

        return false;
    }
}