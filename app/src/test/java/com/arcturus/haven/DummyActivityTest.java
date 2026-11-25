package com.arcturus.haven.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DummyActivityTest {
    @Test
    public void mapPackageInstallerStatusToResult_mapsSuccessToResultOk() {
        assertEquals(Activity.RESULT_OK,
                DummyActivity.mapPackageInstallerStatusToResult(PackageInstaller.STATUS_SUCCESS));
    }

    @Test
    public void mapPackageInstallerStatusToResult_mapsFailuresAndAbortsToCanceled() {
        assertEquals(Activity.RESULT_CANCELED,
                DummyActivity.mapPackageInstallerStatusToResult(PackageInstaller.STATUS_FAILURE));
        assertEquals(Activity.RESULT_CANCELED,
                DummyActivity.mapPackageInstallerStatusToResult(PackageInstaller.STATUS_FAILURE_ABORTED));
        assertEquals(Activity.RESULT_CANCELED,
                DummyActivity.mapPackageInstallerStatusToResult(PackageInstaller.STATUS_FAILURE_BLOCKED));
    }

    @Test
    public void buildPendingIntentFlags_addsMutableFlagOnAndroidSAndAbove() {
        int sFlags = DummyActivity.buildPendingIntentFlags(Build.VERSION_CODES.S);
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE, sFlags);

        int preSFlags = DummyActivity.buildPendingIntentFlags(Build.VERSION_CODES.R);
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, preSFlags);
        assertNotEquals(0, sFlags & PendingIntent.FLAG_MUTABLE);
    }
}
