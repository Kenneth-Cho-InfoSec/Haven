// ILoadIconCallback.aidl
package io.github.kennethchoinfosec.haven.services;

import android.graphics.Bitmap;

interface ILoadIconCallback {
    void callback(in Bitmap icon);
}
