// IUriOpener.aidl
package com.arcturus.haven.util;

import android.os.ParcelFileDescriptor;

interface IUriOpener {
    ParcelFileDescriptor openFile(in String mode);
}
