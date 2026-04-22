package io.github.kennethchoinfosec.haven.receivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import io.github.kennethchoinfosec.haven.R;
import io.github.kennethchoinfosec.haven.ui.DummyActivity;
import io.github.kennethchoinfosec.haven.util.Utility;

public class HavenDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final int NOTIFICATION_ID = 114514;

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        // After Oreo, we use the activity intent ACTION_PROVISIONING_SUCCESSFUL for finalization
        // As it is an activity intent, it is way more reliable (and less hacky) than doing
        // it in a BroadcastReceiver
        // This is handled by FinalizeActivity, and thus we should ignore the event here
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return;
        // Complex logic in a BroadcastReceiver is not reliable
        // Delegate finalization to the DummyActivity
        Intent i = new Intent(context.getApplicationContext(), DummyActivity.class);
        i.setAction(DummyActivity.FINALIZE_PROVISION);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Delegate starting activity to notification to work around background limitations
        // And also maybe this will fix bugs on stupid custom OSes like MIUI / EMUI
        Notification notification = Utility.buildNotification(context, true,
                "Haven-finish-provision",
                context.getString(R.string.finish_provision_title),
                context.getString(R.string.finish_provision_desc),
                R.drawable.ic_notification_white_24dp);
        notification.contentIntent = PendingIntent.getActivity(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        context.getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification);
    }
}
