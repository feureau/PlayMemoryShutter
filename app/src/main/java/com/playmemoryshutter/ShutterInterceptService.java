package com.playmemoryshutter;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class ShutterInterceptService extends Service {
    private static final String TAG = "PlayMemoryShutter";

    private CameraController cameraController;
    private InputHandler inputHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "ShutterInterceptService starting...");

        try {
            cameraController = new CameraController();
            cameraController.open();

            inputHandler = new InputHandler(this, cameraController);
            inputHandler.startListening();

            Log.i(TAG, "Hardware successfully hooked.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize hardware hooks", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Keeps the service running in the background until explicitly stopped
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "ShutterInterceptService stopping...");

        if (inputHandler != null) {
            inputHandler.stopListening();
        }

        if (cameraController != null) {
            cameraController.release();
        }
        Log.i(TAG, "Hardware released safely.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not binding to UI
    }
}
