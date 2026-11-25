package com.arcturus.haven;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import com.arcturus.haven.launcher.bridge.HavenProfileSession;
import com.arcturus.haven.services.FileShuttleService;
import com.arcturus.haven.services.HavenService;
import com.arcturus.haven.util.LocalStorageManager;
import com.arcturus.haven.util.SettingsManager;
import com.arcturus.haven.util.Utility;

public class HavenApplication extends Application {
    private ServiceConnection mHavenServiceConnection = null;
    private ServiceConnection mFileShuttleServiceConnection = null;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);
        SettingsManager.initialize(this);
        HavenProfileSession.initialize(this);
        if (Utility.isProfileOwner(this)) {
            Utility.hideMainActivityInManagedProfile(this);
        }
    }

    public void bindHavenService(ServiceConnection conn, boolean foreground) {
        unbindHavenService();
        Intent intent = new Intent(getApplicationContext(), HavenService.class);
        intent.putExtra("foreground", foreground);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mHavenServiceConnection = conn;
    }

    public void bindFileShuttleService(ServiceConnection conn) {
        unbindFileShuttleService();;
        Intent intent = new Intent(getApplicationContext(), FileShuttleService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mFileShuttleServiceConnection = conn;
    }

    public void unbindHavenService() {
        if (mHavenServiceConnection != null) {
            try {
                unbindService(mHavenServiceConnection);
            } catch (Exception e) {
                // This method call might fail if the service is already unbound
                // just ignore anything that might happen.
                // We will be stopping already if this would ever happen.
            }
        }

        mHavenServiceConnection = null;
    }

    public void unbindFileShuttleService() {
        if (mFileShuttleServiceConnection != null) {
            try {
                unbindService(mFileShuttleServiceConnection);
            } catch (Exception e) {
                // ...
            }
        }

        mFileShuttleServiceConnection = null;
    }
}
