package io.github.kennethchoinfosec.haven.migration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.util.List;

import io.github.kennethchoinfosec.haven.R;
import io.github.kennethchoinfosec.haven.receivers.HavenDeviceAdminReceiver;

/**
 * Moves an existing 0.1.x work profile to com.arcturus.haven.
 *
 * <p>Personal profile: checks that the new app is installed and opens this activity inside
 * the work profile. Work profile (where this package is the profile owner): installs the
 * new app into the profile if needed and calls
 * {@link DevicePolicyManager#transferOwnership}.</p>
 */
public class MigrationActivity extends Activity {
    private static final String TAG = "HavenMigration";

    static final String TARGET_PACKAGE = "com.arcturus.haven";
    static final String TARGET_ADMIN_RECEIVER = "com.arcturus.haven.receivers.HavenDeviceAdminReceiver";
    static final String EXTRA_MIGRATED_FROM = "com.arcturus.haven.extra.MIGRATED_FROM";
    private static final String RELEASES_URL =
            "https://github.com/Kenneth-Cho-InfoSec/Haven/releases/latest";

    private DevicePolicyManager mPolicyManager;
    private AlertDialog mDialog;
    // Set once the transfer has been attempted, so that onResume() does not replace the
    // result with a fresh evaluation (starting the new app pauses and resumes us).
    private boolean mShowingResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPolicyManager = getSystemService(DevicePolicyManager.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mShowingResult) {
            return;
        }
        // Re-evaluate every time, e.g. after the user installed the new app and came back.
        if (mPolicyManager.isProfileOwnerApp(getPackageName())) {
            showWorkProfileStep();
        } else if (isInManagedProfile()) {
            showMessage(getString(R.string.migration_already_done));
        } else {
            showPersonalProfileStep();
        }
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        super.onDestroy();
    }

    // ---- Personal profile ----

    private void showPersonalProfileStep() {
        if (!isTargetInstalled()) {
            showDialog(new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.migration_title)
                    .setMessage(R.string.migration_need_new_app)
                    .setPositiveButton(R.string.migration_download, (d, w) -> openReleasesPage())
                    .setNegativeButton(R.string.migration_close, (d, w) -> finish()));
            return;
        }

        UserHandle workProfile = findWorkProfile();
        if (workProfile == null) {
            showMessage(getString(R.string.migration_no_work_profile));
            return;
        }

        showDialog(new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.migration_title)
                .setMessage(R.string.migration_ready)
                .setPositiveButton(R.string.migration_continue, (d, w) -> openInWorkProfile(workProfile))
                .setNegativeButton(R.string.migration_close, (d, w) -> finish()));
    }

    private UserHandle findWorkProfile() {
        CrossProfileApps crossProfileApps = getSystemService(CrossProfileApps.class);
        List<UserHandle> profiles = crossProfileApps.getTargetUserProfiles();
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    private void openInWorkProfile(UserHandle workProfile) {
        UserManager userManager = getSystemService(UserManager.class);
        if (userManager.isQuietModeEnabled(workProfile)) {
            showMessage(getString(R.string.migration_work_paused));
            return;
        }
        try {
            getSystemService(CrossProfileApps.class).startMainActivity(
                    new ComponentName(this, MigrationActivity.class), workProfile);
            finish();
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "Cannot start migration inside the work profile", e);
            showMessage(getString(R.string.migration_cannot_open_work));
        }
    }

    private void openReleasesPage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No browser available", e);
        }
    }

    // ---- Work profile (we are the profile owner) ----

    private void showWorkProfileStep() {
        showDialog(new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.migration_title)
                .setMessage(R.string.migration_confirm)
                .setPositiveButton(R.string.migration_start, (d, w) -> migrate())
                .setNegativeButton(R.string.migration_close, (d, w) -> finish()));
    }

    private void migrate() {
        mShowingResult = true;
        ComponentName admin = new ComponentName(this, HavenDeviceAdminReceiver.class);
        ComponentName target = new ComponentName(TARGET_PACKAGE, TARGET_ADMIN_RECEIVER);

        if (!isTargetInstalled()) {
            boolean installed;
            try {
                // Makes the copy that is already installed in the personal profile
                // available inside this work profile.
                installed = mPolicyManager.installExistingPackage(admin, TARGET_PACKAGE);
            } catch (SecurityException e) {
                Log.w(TAG, "installExistingPackage failed", e);
                installed = false;
            }
            if (!installed || !isTargetInstalled()) {
                showMessage(getString(R.string.migration_install_failed));
                return;
            }
        }

        try {
            // The intent filters registered by 0.1.x point to actions that no longer exist
            // in this package. Remove them while we still own the profile; the new app
            // registers its own filters once it becomes the profile owner.
            mPolicyManager.clearCrossProfileIntentFilters(admin);

            PersistableBundle extras = new PersistableBundle();
            extras.putString(EXTRA_MIGRATED_FROM, getPackageName());
            mPolicyManager.transferOwnership(admin, target, extras);
        } catch (IllegalArgumentException e) {
            // Thrown when the target admin does not declare <support-transfer-ownership/>.
            Log.w(TAG, "Target does not accept ownership transfer", e);
            showMessage(getString(R.string.migration_not_supported, String.valueOf(e.getMessage())));
            return;
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "transferOwnership failed", e);
            showMessage(getString(R.string.migration_failed, String.valueOf(e.getMessage())));
            return;
        }

        // Start the new app once inside the profile so that it leaves the stopped state and
        // finishes its profile-owner setup even if the TRANSFER_OWNERSHIP_COMPLETE broadcast
        // was not delivered. Its launcher entry hides itself again right away.
        Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (launch != null) {
            try {
                startActivity(launch);
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "Could not start the new app inside the work profile", e);
            }
        }

        showMessage(getString(R.string.migration_done));
    }

    // ---- Helpers ----

    private boolean isTargetInstalled() {
        try {
            getPackageManager().getPackageInfo(TARGET_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isInManagedProfile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return getSystemService(UserManager.class).isManagedProfile();
        }
        return false;
    }

    private void showMessage(String message) {
        showDialog(new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.migration_title)
                .setMessage(message)
                .setPositiveButton(R.string.migration_close, (d, w) -> finish()));
    }

    private void showDialog(AlertDialog.Builder builder) {
        dismissDialog();
        mDialog = builder
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void dismissDialog() {
        if (mDialog != null) {
            mDialog.setOnCancelListener(null);
            mDialog.dismiss();
            mDialog = null;
        }
    }
}
