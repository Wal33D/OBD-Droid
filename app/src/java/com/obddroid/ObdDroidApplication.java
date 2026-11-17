package com.obddroid;

import android.app.Application;

import com.obddroid.utils.EdgeToEdgeInsets;

/**
 * Application entry point. Holds process-wide setup that every activity needs.
 */
public class ObdDroidApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // targetSdk 35+ draws every activity edge to edge on Android 15+; keep content clear of the bars.
        EdgeToEdgeInsets.install(this);
    }
}
