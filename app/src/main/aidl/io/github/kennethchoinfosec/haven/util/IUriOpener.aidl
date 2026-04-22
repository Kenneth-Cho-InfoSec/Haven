// IUriOpener.aidl
package io.github.kennethchoinfosec.haven.util;

import android.os.ParcelFileDescriptor;

interface IUriOpener {
    ParcelFileDescriptor openFile(in String mode);
}
