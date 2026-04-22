// IGetAppsCallback.aidl
package io.github.kennethchoinfosec.haven.services;

import io.github.kennethchoinfosec.haven.util.ApplicationInfoWrapper;

interface IGetAppsCallback {
    void callback(in List<ApplicationInfoWrapper> apps);
}
