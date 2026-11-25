// IHavenService.aidl
package com.arcturus.haven.services;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import com.arcturus.haven.services.IAppInstallCallback;
import com.arcturus.haven.services.IGetAppsCallback;
import com.arcturus.haven.services.ILoadIconCallback;
import com.arcturus.haven.services.IStartActivityProxy;
import com.arcturus.haven.util.ApplicationInfoWrapper;
import com.arcturus.haven.util.UriForwardProxy;

interface IHavenService {
    void ping();
    void stopHavenService(boolean kill);
    void getApps(IGetAppsCallback callback, boolean showAll);
    void loadIcon(in ApplicationInfoWrapper info, ILoadIconCallback callback);
    void installApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void installApk(in UriForwardProxy uri, IAppInstallCallback callback);
    void uninstallApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void freezeApp(in ApplicationInfoWrapper app);
    void unfreezeApp(in ApplicationInfoWrapper app);
    boolean hasUsageStatsPermission();
    boolean hasSystemAlertPermission();
    boolean hasAllFileAccessPermission();
    List<String> getCrossProfileWidgetProviders();
    boolean setCrossProfileWidgetProviderEnabled(String pkgName, boolean enabled);
    void setStartActivityProxy(in IStartActivityProxy proxy);
    List<String> getCrossProfilePackages();
    void setCrossProfilePackages(in List<String> packages);
    Bundle getManagedApplicationRestrictions(String packageName);
    void setManagedApplicationRestrictions(String packageName, in Bundle restrictions);
}
