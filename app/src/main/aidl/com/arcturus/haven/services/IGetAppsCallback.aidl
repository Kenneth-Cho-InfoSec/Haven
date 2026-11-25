// IGetAppsCallback.aidl
package com.arcturus.haven.services;

import com.arcturus.haven.util.ApplicationInfoWrapper;

interface IGetAppsCallback {
    void callback(in List<ApplicationInfoWrapper> apps);
}
