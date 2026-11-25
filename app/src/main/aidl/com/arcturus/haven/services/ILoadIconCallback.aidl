// ILoadIconCallback.aidl
package com.arcturus.haven.services;

import android.graphics.Bitmap;

interface ILoadIconCallback {
    void callback(in Bitmap icon);
}
