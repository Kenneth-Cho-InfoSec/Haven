// IFileShuttleService.aidl
package com.arcturus.haven.services;

import android.graphics.Point;
import android.os.ParcelFileDescriptor;
import com.arcturus.haven.services.FileMeta;

interface IFileShuttleService {
    void ping();
    List<FileMeta> loadFiles(String path);
    FileMeta loadFileMeta(String path);
    ParcelFileDescriptor openFile(String path, String mode);
    ParcelFileDescriptor openThumbnail(String path, in Point sizeHint);
    String createFile(String path, String mimeType, String displayName);
    String deleteFile(String path);
    boolean isChildOf(String parentPath, String childPath);
}
