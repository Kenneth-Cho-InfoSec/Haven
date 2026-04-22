package io.github.kennethchoinfosec.haven;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import io.github.kennethchoinfosec.haven.services.FileShuttleService;
import io.github.kennethchoinfosec.haven.services.HavenService;
import io.github.kennethchoinfosec.haven.util.LocalStorageManager;
import io.github.kennethchoinfosec.haven.util.SettingsManager;

public class HavenApplication extends Application {
    private ServiceConnection mHavenServiceConnection = null;
    private ServiceConnection mFileShuttleServiceConnection = null;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);
        SettingsManager.initialize(this);
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
