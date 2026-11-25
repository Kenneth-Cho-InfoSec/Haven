package com.arcturus.haven.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class HavenDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        // After Oreo, FinalizeActivity handles ACTION_PROVISIONING_SUCCESSFUL directly.
    }
}
