package com.arcturus.haven.util;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.admin.PackagePolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.os.ext.SdkExtensions;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.arcturus.haven.R;
import com.arcturus.haven.receivers.HavenDeviceAdminReceiver;
import com.arcturus.haven.services.IHavenService;
import com.arcturus.haven.ui.DummyActivity;
import com.arcturus.haven.ui.MainActivity;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Utility {
    private static final ExecutorService SHORTCUT_EXECUTOR = Executors.newSingleThreadExecutor();
    // Determine if the current app is the owner of the current profile
    // TODO: Replace all occurrences of duplicated code to call this function instead
    public static boolean isProfileOwner(Context context) {
        return context.getSystemService(DevicePolicyManager.class)
                .isProfileOwnerApp(context.getPackageName());
    }

    public static String stringJoin(String delimiter, String[] list) {
        return String.join(delimiter, list);
    }

    // Affiliate an Intent to another profile (i.e. the Work profile that we manage)
    // This method cares nothing about if the other profile even exists.
    // When there is no other profile, this method would just simply throw
    // an IndexOutOfBoundException
    // which can be caught and resolved.
    public static void transferIntentToProfile(Context context, Intent intent) {
        transferIntentToProfileUnsigned(context, intent);
        // Add signature
        AuthenticationUtility.signIntent(intent);
    }

    public static void transferIntentToProfileUnsigned(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> info = pm.queryIntentActivities(intent, 0);
        Optional<ResolveInfo> i = info.stream()
                .filter((r) -> !r.activityInfo.packageName.equals(context.getPackageName()))
                .findFirst();
        if (i.isPresent()) {
            intent.setComponent(new ComponentName(i.get().activityInfo.packageName, i.get().activityInfo.name));
        } else {
            throw new IllegalStateException("Cannot find an intent in other profile");
        }
    }

    // Determine if the work profile is already available
    // If so, return true and set all the corresponding flags to true
    // This is for scenarios where the asynchronous part of the
    // setup process might be finished before the synchronous part
    public static boolean isWorkProfileAvailable(Context context) {
        LocalStorageManager storage = LocalStorageManager.getInstance();
        Intent intent = new Intent(DummyActivity.TRY_START_SERVICE);
        try {
            // DO NOT sign this request, because this won't be actually sent to work profile
            // If this is signed, and is the first request to be signed,
            // then the other side would never receive the auth_key
            Utility.transferIntentToProfileUnsigned(context, intent);
            storage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
            storage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);
            return true;
        } catch (IllegalStateException e) {
            // If any exception is thrown, this means that the profile is not available
            return false;
        }
    }

    // Enforce policies and configurations in the work profile
    public static void enforceWorkProfilePolicies(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        ComponentName adminComponent = new ComponentName(context.getApplicationContext(), HavenDeviceAdminReceiver.class);

        // Clear everything first to ensure our policies are set properly
        manager.clearCrossProfileIntentFilters(adminComponent);

        // Allow cross-profile intents for START_SERVICE
        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_SERVICE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.TRY_START_SERVICE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.UNFREEZE_AND_LAUNCH),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.FREEZE_ALL_IN_LIST),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.PUBLIC_FREEZE_ALL),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED); // Used by FreezeService in profile

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.FINALIZE_PROVISION),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_FILE_SHUTTLE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_FILE_SHUTTLE_2),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.SYNCHRONIZE_PREFERENCE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        // Needed by HavenService and has to be proxied by the MainActivity in main profile
        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.INSTALL_PACKAGE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.UNINSTALL_PACKAGE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        // Allow ACTION_SEND and ACTION_SEND_MULTIPLE to cross from managed to parent
        IntentFilter actionSendFilter = new IntentFilter();
        actionSendFilter.addAction(Intent.ACTION_SEND);
        actionSendFilter.addAction(Intent.ACTION_SEND_MULTIPLE);
        try {
            actionSendFilter.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException ignored) {
            // WTF?
        }
        actionSendFilter.addCategory(Intent.CATEGORY_DEFAULT);
        manager.addCrossProfileIntentFilter(
                adminComponent,
                actionSendFilter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        // Picker-based sharing is user mediated and independent from File Shuttle.
        if (SettingsManager.getInstance().getCrossProfilePickerEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)) {
                IntentFilter photoPickerFilter = new IntentFilter(MediaStore.ACTION_PICK_IMAGES);
                photoPickerFilter.addCategory(Intent.CATEGORY_DEFAULT);
                manager.addCrossProfileIntentFilter(
                        adminComponent,
                        photoPickerFilter,
                        DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
                                | DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);
            }

            IntentFilter documentPickerFilter = new IntentFilter();
            documentPickerFilter.addAction(Intent.ACTION_GET_CONTENT);
            documentPickerFilter.addAction(Intent.ACTION_OPEN_DOCUMENT);
            documentPickerFilter.addCategory(Intent.CATEGORY_DEFAULT);
            documentPickerFilter.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                documentPickerFilter.addDataType("*/*");
            } catch (IntentFilter.MalformedMimeTypeException ignored) {
                // Keep the picker filters if a vendor rejects this wildcard MIME type.
            }
            manager.addCrossProfileIntentFilter(
                    adminComponent,
                    documentPickerFilter,
                    DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
                            | DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);
        }
        
        // Browser intents are allowed from work profile to parent
        IntentFilter browsableIntentFilter = new IntentFilter(Intent.ACTION_VIEW);
        browsableIntentFilter.addCategory(Intent.CATEGORY_BROWSABLE);
        browsableIntentFilter.addDataScheme("http");
        browsableIntentFilter.addDataScheme("https");
        manager.addCrossProfileIntentFilter(
                adminComponent,
                browsableIntentFilter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);
        IntentFilter browsableDefaultIntentFilter = new IntentFilter(Intent.ACTION_VIEW);
        browsableDefaultIntentFilter.addCategory(Intent.CATEGORY_BROWSABLE);
        browsableDefaultIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        browsableDefaultIntentFilter.addDataScheme("http");
        browsableDefaultIntentFilter.addDataScheme("https");
        manager.addCrossProfileIntentFilter(
                adminComponent,
                browsableDefaultIntentFilter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        // Block contacts searching optionally
        boolean blockContacts = SettingsManager.getInstance().getBlockContactsSearchingEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.setManagedProfileContactsAccessPolicy(new PackagePolicy(
                    blockContacts
                            ? PackagePolicy.PACKAGE_POLICY_ALLOWLIST
                            : PackagePolicy.PACKAGE_POLICY_BLOCKLIST));
        } else {
            manager.setCrossProfileContactsSearchDisabled(adminComponent, blockContacts);
        }

        manager.setProfileEnabled(adminComponent);
    }

    /** Hide all launcher and main-profile UI components inside the managed work profile. */
    public static void hideMainActivityInManagedProfile(Context context) {
        PackageManager pm = context.getPackageManager();
        Context appContext = context.getApplicationContext();
        String[] componentsToDisable = new String[] {
                "com.arcturus.haven.ui.WelcomeModeActivity",
                "com.arcturus.haven.ui.MainActivity",
                "com.arcturus.haven.launcher.launcher.HomeActivity",
                "com.arcturus.haven.launcher.search.LauncherSearchActivity",
                "com.arcturus.haven.launcher.launcher.FeedActivity",
                "com.arcturus.haven.launcher.settings.LauncherSettingsActivity",
                "com.arcturus.haven.launcher.widgets.WidgetPickerActivity",
                "com.arcturus.haven.launcher.widgets.IconGroupWidgetConfigActivity",
                "com.arcturus.haven.launcher.widgets.IconGroupWidgetProvider",
                "com.arcturus.haven.launcher.widgets.BatteryWidgetProvider",
                "com.arcturus.haven.launcher.widgets.VaultControlWidgetProvider",
                "com.arcturus.haven.launcher.launcher.NotificationHighlightService",
                "com.arcturus.haven.launcher.core.permissions.LauncherActionAccessibilityService"
        };
        for (String cls : componentsToDisable) {
            try {
                pm.setComponentEnabledSetting(
                        new ComponentName(appContext, cls),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (Exception ignored) {
                // Keep disabling remaining components even if one is absent.
            }
        }
    }

    public static void enforceUserRestrictions(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        ComponentName adminComponent = new ComponentName(context.getApplicationContext(), HavenDeviceAdminReceiver.class);
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS);
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS);

        manager.addUserRestriction(adminComponent, UserManager.ALLOW_PARENT_PROFILE_APP_LINKING);
    }

    // Detect if the device is MIUI
    public static boolean isMIUI() {
        try {
            Process proc = Runtime.getRuntime().exec("getprop ro.miui.ui.version.name");
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = reader.readLine().trim();
            return !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // From <https://stackoverflow.com/questions/3035692/how-to-convert-a-drawable-to-a-bitmap>
    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static void killHavenServices(IHavenService serviceMain, IHavenService serviceWork) {
        // Ensure that all our other services are killed at this point
        try {
            serviceWork.stopHavenService(true);
        } catch (Exception e) {
            // We are stopping anyway
        }

        try {
            serviceMain.stopHavenService(false);
        } catch (Exception e) {
            // We are stopping anyway
        }
    }

    // Delete apps that no longer exist from the auto freeze list
    public static void deleteMissingApps(String pref, List<ApplicationInfoWrapper> apps) {
        List<String> list = new ArrayList<>(
                Arrays.asList(LocalStorageManager.getInstance().getStringList(pref)));
        list.removeIf((it) -> apps.stream().noneMatch((x) -> x.getPackageName().equals(it)));
        LocalStorageManager.getInstance().setStringList(pref, list.toArray(new String[]{}));
    }

    public static void createLauncherShortcut(Context context, Intent launchIntent, Icon icon, String id, String label) {
        Context appContext = context.getApplicationContext();
        ShortcutManager shortcutManager = appContext.getSystemService(ShortcutManager.class);

        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
            ShortcutInfo info = new ShortcutInfo.Builder(appContext, id)
                    .setIntent(launchIntent)
                    .setIcon(icon)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .build();
            SHORTCUT_EXECUTOR.execute(() -> {
                Intent addIntent = shortcutManager.createShortcutResultIntent(info);
                shortcutManager.requestPinShortcut(info,
                        PendingIntent.getBroadcast(appContext, 0, addIntent, PendingIntent.FLAG_IMMUTABLE).getIntentSender());
            });
        } else {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(appContext, appContext.getString(R.string.unsupported_launcher), Toast.LENGTH_LONG).show());
        }
    }

    // Functions to load scaled down version of Bitmap
    // from <https://developer.android.com/topic/performance/graphics/load-bitmap?hl=es#java>
    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmap(String filePath,
                                             int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    public static Bitmap decodeSampledBitmap(FileDescriptor fd,
                                             int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    // Get file's extension name
    public static String getFileExtension(String filePath) {
        int index = filePath.lastIndexOf(".");
        if (index > 0) {
            return filePath.substring(index + 1);
        } else {
            return null;
        }
    }

    // Check if USAGE_STATS is granted
    public static boolean checkUsageStatsPermission(Context context) {
        return checkSpecialAccessPermission(context, AppOpsManager.OPSTR_GET_USAGE_STATS);
    }

    // Check if SYSTEM_ALERT_WINDOW is granted
    public static boolean checkSystemAlertPermission(Context context) {
        return checkSpecialAccessPermission(context, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW);
    }

    // Check if all file access r/w is granted
    @RequiresApi(Build.VERSION_CODES.R)
    public static boolean checkAllFileAccessPermission() {
        return Environment.isExternalStorageManager();
    }

    // Check special access permission through AppOps
    public static boolean checkSpecialAccessPermission(Context context, String name) {
        AppOpsManager appops = context.getSystemService(AppOpsManager.class);
        int mode = appops.checkOpNoThrow(name, android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // Pipe an InputStream to OutputStream
    public static void pipe(InputStream is, OutputStream os) throws IOException {
        int n;
        byte[] buffer = new byte[65536];
        while ((n = is.read(buffer)) > -1) {
            os.write(buffer, 0, n);
        }
    }

    // Utilities to build notifications for cross-version compatibility
    private static final String NOTIFICATION_CHANNEL_ID = "HavenService";
    private static final String NOTIFICATION_CHANNEL_IMPORTANT = "HavenService-Important";
    public static Notification buildNotification(Context context, String ticker, String title, String desc, int icon) {
        return buildNotification(context, false, ticker, title, desc, icon);
    }

    public static Notification buildNotification(Context context, boolean important, String ticker, String title, String desc, int icon) {
        String id = important ? NOTIFICATION_CHANNEL_IMPORTANT : NOTIFICATION_CHANNEL_ID;
        // Android O and later: Notification Channel
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(id) == null) {
            NotificationChannel chan = new NotificationChannel(
                    id,
                    important ? context.getString(R.string.notifications_important)
                            : context.getString(R.string.app_name),
                    important ? NotificationManager.IMPORTANCE_HIGH
                            : NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(chan);
        }

        // Disable everything: do not disturb the user
        NotificationChannel chan = nm.getNotificationChannel(id);
        if (!important) {
            chan.enableVibration(false);
            chan.enableLights(false);
            chan.setImportance(NotificationManager.IMPORTANCE_MIN);
        } else {
            chan.enableVibration(true);
            chan.setImportance(NotificationManager.IMPORTANCE_HIGH);
        }
        nm.createNotificationChannel(chan);

        // Create foreground notification to keep the service alive
        return new Notification.Builder(context, id)
                .setTicker(ticker)
                .setContentTitle(title)
                .setContentText(desc)
                .setSmallIcon(icon)
                .build();
    }

    // A wrapper over arbitrary ActivityResultContract that provides
    // hardcoded input parameters and do not accept input with launch()
    public static class ActivityResultContractInputWrapper<I, O, T extends ActivityResultContract<I, O>>
            extends ActivityResultContract<Void, O> {
        private final T mInner;
        private final I mInput;

        public ActivityResultContractInputWrapper(T inner, I input) {
            mInner = inner;
            mInput = input;
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            return mInner.createIntent(context, mInput);
        }

        @Override
        public O parseResult(int resultCode, @Nullable Intent intent) {
            return mInner.parseResult(resultCode, intent);
        }
    }
}
