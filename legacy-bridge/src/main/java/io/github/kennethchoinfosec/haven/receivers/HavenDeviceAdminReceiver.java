package io.github.kennethchoinfosec.haven.receivers;

import android.app.admin.DeviceAdminReceiver;

/**
 * Same component name as the 0.1.x profile owner. Keeping it is what lets this bridge
 * remain the profile owner after the in-place update, so it can call transferOwnership().
 */
public class HavenDeviceAdminReceiver extends DeviceAdminReceiver {
}
