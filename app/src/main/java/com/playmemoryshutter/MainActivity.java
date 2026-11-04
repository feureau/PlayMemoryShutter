package com.playmemoryshutter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String PREF_NAME = "PlayMemoryShutterPrefs";
    public static final String KEY_AUTO_START = "auto_start";
    public static final String KEY_DELAY_SECONDS = "delay_seconds";
    public static final String KEY_SHUTTER_MODE = "shutter_mode";
    public static final String KEY_VIDEO_BTN_MODE = "video_btn_mode";

    private CheckBox chkAutoStart;
    private Spinner spinDelay;
    private Spinner spinShutter;
    private Spinner spinVideoBtn;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        chkAutoStart = findViewById(R.id.chkAutoStart);
        spinDelay = findViewById(R.id.spinDelay);
        spinShutter = findViewById(R.id.spinShutter);
        spinVideoBtn = findViewById(R.id.spinVideoBtn);

        setupSpinners();
        loadSettings();

        findViewById(R.id.btnSaveAndStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                startService(new Intent(MainActivity.this, ShutterInterceptService.class));
                Toast.makeText(MainActivity.this, "Settings Saved & Service Started", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, ShutterInterceptService.class));
                Toast.makeText(MainActivity.this, "Service Stopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSpinners() {
        ArrayAdapter<Integer> delayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new Integer[]{5, 10, 15});
        delayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinDelay.setAdapter(delayAdapter);

        ArrayAdapter<String> shutterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Normal (Default)", "Video (Movie Mode Only)", "Video (All Modes)"});
        shutterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinShutter.setAdapter(shutterAdapter);

        ArrayAdapter<String> videoBtnAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Normal (Default)", "Take Photo"});
        videoBtnAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinVideoBtn.setAdapter(videoBtnAdapter);
    }

    private void loadSettings() {
        chkAutoStart.setChecked(prefs.getBoolean(KEY_AUTO_START, false));

        int delay = prefs.getInt(KEY_DELAY_SECONDS, 10);
        if (delay == 5) spinDelay.setSelection(0);
        else if (delay == 15) spinDelay.setSelection(2);
        else spinDelay.setSelection(1); // default 10

        spinShutter.setSelection(prefs.getInt(KEY_SHUTTER_MODE, 0));
        spinVideoBtn.setSelection(prefs.getInt(KEY_VIDEO_BTN_MODE, 0));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_AUTO_START, chkAutoStart.isChecked());
        editor.putInt(KEY_DELAY_SECONDS, (Integer) spinDelay.getSelectedItem());
        editor.putInt(KEY_SHUTTER_MODE, spinShutter.getSelectedItemPosition());
        editor.putInt(KEY_VIDEO_BTN_MODE, spinVideoBtn.getSelectedItemPosition());
        editor.apply();
    }
}
