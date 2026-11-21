package io.github.kennethchoinfosec.haven.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.kennethchoinfosec.haven.services.HavenService;
import io.github.kennethchoinfosec.haven.services.IAppInstallCallback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class InstallerResultTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = InstrumentationRegistry.getTargetContext();

    @Test
    public void invalidApkReportsInstallerFailureAndClosesWindow() throws Exception {
        File apk = File.createTempFile("invalid", ".apk", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(apk)) {
                output.write(new byte[]{1, 2, 3});
            }
            assertOperation(new Intent(DummyActivity.INSTALL_PACKAGE).putExtra("apk", apk.getPath()),
                    HavenService.RESULT_PACKAGE_OPERATION_FAILED);
        } finally {
            apk.delete();
        }
    }

    @Test
    public void fixtureInstallOrUninstallReportsResultAndClosesWindow() throws Exception {
        // For the manual confirmation matrix, stage a fixture APK in the target
        // user's cache, or give its installed package name to test uninstallation.
        Bundle arguments = InstrumentationRegistry.getArguments();
        String operation = arguments.getString("installerOperation");
        assumeTrue(operation != null);
        boolean uninstall = "uninstall".equals(operation);
        String target = arguments.getString("installerTarget");
        assertTrue(target != null && !target.isEmpty());
        assertOperation(new Intent(uninstall ? DummyActivity.UNINSTALL_PACKAGE : DummyActivity.INSTALL_PACKAGE)
                        .putExtra(uninstall ? "package" : "apk", target),
                "cancel".equals(operation) ? Activity.RESULT_CANCELED : Activity.RESULT_OK);
    }

    private void assertOperation(Intent request, int expectedResult) throws Exception {
        ArrayBlockingQueue<Integer> results = new ArrayBlockingQueue<>(1);
        Bundle callback = new Bundle();
        callback.putBinder("callback", new IAppInstallCallback.Stub() {
            @Override
            public void callback(int result) {
                results.offer(result);
            }
        });
        request.setClass(context, DummyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("callback", callback);
        DummyActivity.registerSameProcessRequest(request);
        Activity window = instrumentation.startActivitySync(request);
        assertEquals(Integer.valueOf(expectedResult), results.poll(35, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();
        assertTrue("DummyActivity still owns a window", window.isFinishing() || window.isDestroyed());
    }
}
