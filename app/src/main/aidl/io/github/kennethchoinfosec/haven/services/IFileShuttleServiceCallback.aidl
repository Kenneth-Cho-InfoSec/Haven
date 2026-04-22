// IFileShuttleServiceCallback.aidl
package io.github.kennethchoinfosec.haven.services;

import io.github.kennethchoinfosec.haven.services.IFileShuttleService;

interface IFileShuttleServiceCallback {
    void callback(in IFileShuttleService service);
}
