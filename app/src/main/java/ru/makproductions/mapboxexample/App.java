package ru.makproductions.mapboxexample;

import android.app.Application;

import com.mapbox.mapboxsdk.Mapbox;

import timber.log.Timber;

public class App extends Application {
    private static App instance;

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        Timber.plant(new Timber.DebugTree());
    }
}
