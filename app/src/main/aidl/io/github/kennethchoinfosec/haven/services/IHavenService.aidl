// IHavenService.aidl
package io.github.kennethchoinfosec.haven.services;

import android.content.pm.ApplicationInfo;

import io.github.kennethchoinfosec.haven.services.IAppInstallCallback;
import io.github.kennethchoinfosec.haven.services.IGetAppsCallback;
import io.github.kennethchoinfosec.haven.services.ILoadIconCallback;
import io.github.kennethchoinfosec.haven.services.IStartActivityProxy;
import io.github.kennethchoinfosec.haven.util.ApplicationInfoWrapper;
import io.github.kennethchoinfosec.haven.util.UriForwardProxy;

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
}
