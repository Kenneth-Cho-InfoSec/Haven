package io.github.kennethchoinfosec.haven.services;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import io.github.kennethchoinfosec.haven.util.Utility;

// KillerService is a dirty fix to the fact that
// Activities cannot receive any event about their
// removal from recent tasks.
// Since we need to kill the process inside work profile
// when the app is closed by any means, we have to ensure
// its being killed in every possible circumstance.
public class KillerService extends Service {
    private IHavenService mServiceMain = null;
    private IHavenService mServiceWork = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getBundleExtra("extra");
        mServiceMain = IHavenService.Stub.asInterface(extras.getBinder("main"));
        mServiceWork = IHavenService.Stub.asInterface(extras.getBinder("work"));
        return START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            killEverything();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        killEverything();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        killEverything();
    }

    private void killEverything() {
        Utility.killHavenServices(mServiceMain, mServiceWork);
        // Kill this service itself
        stopSelf();
    }
}
