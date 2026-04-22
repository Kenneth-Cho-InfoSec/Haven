package io.github.kennethchoinfosec.haven.util;

import android.app.Activity;
import android.content.pm.PackageInstaller;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.kennethchoinfosec.haven.R;

public class InstallationProgressListener extends PackageInstaller.SessionCallback {
    private final Activity mActivity;
    private final AlertDialog mDialog;
    private final LinearProgressIndicator mProgress;
    private final int mSessionId;
    private final PackageInstaller mPi;

    // Create a listener from an activity, and show a progress dialog for the sessionId
    // Only cares about the one sessionId provided here.
    // The caller is responsible for registering the callback;
    // however, this class will remove itself once the session has been finished.
    public InstallationProgressListener(Activity activity, PackageInstaller pi, int sessionId) {
        mActivity = activity;
        mPi = pi;
        mSessionId = sessionId;

        ViewGroup layout = (ViewGroup) LayoutInflater.from(activity)
                .inflate(R.layout.progress_dialog, (ViewGroup) activity.getWindow().getDecorView(), false);
        mProgress = layout.findViewById(R.id.progress);

        mDialog = new MaterialAlertDialogBuilder(activity)
                .setCancelable(false)
                .setTitle(R.string.app_installing)
                .setView(layout)
                .create();
        mDialog.show();
    }

    @Override
    public void onCreated(int sessionId) {

    }

    @Override
    public void onBadgingChanged(int sessionId) {

    }

    @Override
    public void onActiveChanged(int sessionId, boolean active) {

    }

    @Override
    public void onProgressChanged(int sessionId, float progress) {
        if (sessionId != mSessionId) {
            return;
        }

        int progressPercent = Math.max(0, Math.min(100, (int) (progress * 100)));
        mActivity.runOnUiThread(() -> mProgress.setProgressCompat(progressPercent, true));
    }

    @Override
    public void onFinished(int sessionId, boolean success) {
        if (sessionId != mSessionId) {
            return;
        }

        mActivity.runOnUiThread(() -> {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
            }
        });
        mPi.unregisterSessionCallback(this);
    }
}
