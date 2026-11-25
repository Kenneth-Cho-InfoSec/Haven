package com.arcturus.haven.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

import com.arcturus.haven.util.Utility;

public class HavenDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        // After Oreo, FinalizeActivity handles ACTION_PROVISIONING_SUCCESSFUL directly.
    }

    @Override
    public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
        super.onTransferOwnershipComplete(context, bundle);
        // An existing work profile was handed over to us, e.g. by the Haven 0.1.6 bridge
        // (io.github.kennethchoinfosec.haven). Apply the same policies a freshly
        // provisioned profile gets, so the personal-profile app can find us.
        Utility.adoptTransferredWorkProfile(context);
    }
}
