package io.github.kennethchoinfosec.haven.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Size;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import io.github.kennethchoinfosec.haven.R;
import io.github.kennethchoinfosec.haven.HavenApplication;
import io.github.kennethchoinfosec.haven.util.CrossProfileDocumentsProvider;
import io.github.kennethchoinfosec.haven.util.Utility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A service to forward file information across the profile boundary
public class FileShuttleService extends Service {
    public static final long TIMEOUT = 10000;
    // Periodic task to stop the service when idle.
    // This service does not need to persist.
    private Runnable mSuicideTask = this::suicide;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private IFileShuttleService.Stub mStub = new IFileShuttleService.Stub() {
        @Override
        public void ping() {
            // Dummy method
            resetSuicideTask();
        }

        @Override
        public List<Map<String, Serializable>> loadFiles(String path) {
            resetSuicideTask();
            ArrayList<Map<String, Serializable>> ret = new ArrayList<>();
            File f = new File(resolvePath(path));
            if (f.listFiles() != null) {
                for (File child : f.listFiles()) {
                    ret.add(loadFileMeta(child.getPath()));
                }
            }
            return ret;
        }

        @Override
        public Map<String, Serializable> loadFileMeta(String path) {
            resetSuicideTask();
            File f = new File(resolvePath(path));
            HashMap<String, Serializable> map = new HashMap<>();
            map.put(DocumentsContract.Document.COLUMN_DOCUMENT_ID, f.getAbsolutePath());
            if (f.equals(getSharedStorageRoot())) {
                // Show "Haven" as the name of the root directory
                map.put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, getString(R.string.app_name));
            } else {
                map.put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, f.getName());
            }
            map.put(DocumentsContract.Document.COLUMN_SIZE, f.length());
            map.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified());

            if (f.isDirectory()) {
                map.put(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
                map.put(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE |
                                DocumentsContract.Document.FLAG_SUPPORTS_DELETE);
            } else {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        Utility.getFileExtension(f.getAbsolutePath()));
                int flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
                if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) {
                    flags |= DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL;
                }
                if (mime == null) {
                    mime = "application/unknown";
                }
                map.put(DocumentsContract.Document.COLUMN_MIME_TYPE, mime);
                map.put(DocumentsContract.Document.COLUMN_FLAGS, flags);
            }
            return map;
        }

        @Override
        public ParcelFileDescriptor openFile(String path, String mode) {
            resetSuicideTask();
            File f = new File(resolvePath(path));
            int numericMode = ParcelFileDescriptor.parseMode(mode);

            try {
                if ((numericMode & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0) {
                    // When the file is opened in writable mode, and the file is of a media
                    // type, we need to notify the media scanner of the update.
                    // Even though this is done as part of file creation as well, that scan
                    // might have failed because it happened before the writer was able to
                    // finish writing.
                    return ParcelFileDescriptor.open(f, numericMode, mHandler, (e) -> {
                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                Utility.getFileExtension(f.getAbsolutePath()));
                        notifyMediaScannerIfNecessary(f, mime);
                    });
                } else {
                    return ParcelFileDescriptor.open(f, numericMode);
                }
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public ParcelFileDescriptor openThumbnail(String path, Point sizeHint) {
            resetSuicideTask();
            String fullPath = resolvePath(path);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    Utility.getFileExtension(fullPath));
            if (mime == null) {
                return null;
            }
            if (mime.startsWith("image/")) {
                // Image thumbnail
                return loadImageThumbnail(fullPath, sizeHint);
            } else if (mime.startsWith("video/")) {
                // Video thumbnail
                return loadVideoThumbnail(fullPath, sizeHint);
            } else {
                return null;
            }
        }

        @Override
        public String createFile(String path, String mimeType, String displayName) {
            resetSuicideTask();
            File f;
            String fullPath = path + "/" + displayName;
            boolean isDirectory =
                    DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
            boolean shouldAppendExtension =
                    mimeType != null && !isDirectory && !mimeType.equals("application/octet-stream");

            // Append extension for files if a MIME type is specified
            if (shouldAppendExtension) {
                String extensionPart = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (!fullPath.endsWith(extensionPart)) {
                    fullPath += extensionPart;
                }
            }

            // Now we can create the file / directory
            f = new File(resolvePath(fullPath));
            try {
                if ((isDirectory && !f.mkdir()) || (!isDirectory && !f.createNewFile())) {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }

            notifyMediaScannerIfNecessary(f, mimeType);

            return f.getAbsolutePath();
        }

        @Override
        public String deleteFile(String path) {
            resetSuicideTask();
            File f = new File(resolvePath(path));
            f.delete();
            return f.getParentFile().getAbsolutePath();
        }

        @Override
        public boolean isChildOf(String parent, String child) {
            File parentFile = new File(resolvePath(parent));
            File childFile = new File(resolvePath(child));
            String parentPath = parentFile.getAbsolutePath();
            if (parentPath.charAt(parentPath.length() - 1) != '/') {
                parentPath += "/"; // Make sure it ends with '/'
            }
            return parentFile.exists() && parentFile.isDirectory()
                    && childFile.exists()
                    && childFile.getAbsolutePath().startsWith(parentPath);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        resetSuicideTask();
        return mStub;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("FileShuttleService", "being destroyed");
    }

    private String resolvePath(String path) {
        if (path.startsWith(CrossProfileDocumentsProvider.DUMMY_ROOT)) {
            return path.replaceFirst(CrossProfileDocumentsProvider.DUMMY_ROOT,
                    getSharedStorageRoot().getAbsolutePath());
        } else {
            return path;
        }
    }

    private void resetSuicideTask() {
        mHandler.removeCallbacks(mSuicideTask);
        mHandler.postDelayed(mSuicideTask, TIMEOUT);
    }

    private void suicide() {
        mHandler.removeCallbacks(mSuicideTask);
        ((HavenApplication) getApplication()).unbindFileShuttleService();
        stopSelf();
    }

    private ParcelFileDescriptor loadImageThumbnail(String fullPath, Point sizeHint) {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bmp = ThumbnailUtils.createImageThumbnail(new File(fullPath), thumbnailSize(sizeHint), null);
            } catch (IOException e) {
                bmp = Utility.decodeSampledBitmap(fullPath, sizeHint.x, sizeHint.y);
            }
        } else {
            bmp = Utility.decodeSampledBitmap(fullPath, sizeHint.x, sizeHint.y);
        }
        return bmp == null ? null : bitmapToFd(bmp);
    }

    private ParcelFileDescriptor loadVideoThumbnail(String fullPath, Point sizeHint) {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bmp = ThumbnailUtils.createVideoThumbnail(new File(fullPath), thumbnailSize(sizeHint), null);
            } catch (IOException e) {
                bmp = null;
            }
        } else {
            bmp = createLegacyVideoThumbnail(fullPath);
        }
        return bmp == null ? null : bitmapToFd(bmp);
    }

    @SuppressWarnings("deprecation")
    private Bitmap createLegacyVideoThumbnail(String fullPath) {
        // The String overload is the available platform API on Android 7 through 9.
        return ThumbnailUtils.createVideoThumbnail(fullPath, MediaStore.Video.Thumbnails.MINI_KIND);
    }

    private static Size thumbnailSize(Point sizeHint) {
        return new Size(Math.max(1, sizeHint.x), Math.max(1, sizeHint.y));
    }

    // Fallback method for thumbnail loading: just load from disk, but load a scaled down version
    private ParcelFileDescriptor loadBitmapThumbnail(String path, Point sizeHint) {
        Bitmap bmp = Utility.decodeSampledBitmap(path, sizeHint.x, sizeHint.y);

        if (bmp == null) {
            return null;
        }

        return bitmapToFd(bmp);
    }

    private ParcelFileDescriptor bitmapToFd(Bitmap bmp) {
        ParcelFileDescriptor[] pair;
        try {
            // Use a pipe as a virtual in-memory ParcelFileDescriptor
            pair = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            return null;
        }


        // Send the bitmap into the pipe in another thread, so that we can return the
        // reading fd to the Documents UI before we finish sending the Bitmap.
        new Thread(() -> {
            try (FileOutputStream os = new FileOutputStream(pair[1].getFileDescriptor())) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.flush();
            } catch (IOException e) {
                // ...
            }
            bmp.recycle();
        }).start();

        return pair[0];
    }

    private void notifyMediaScannerIfNecessary(File f, String mimeType) {
        // Scan after the file has been created or its output stream has closed.
        if (mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
            MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()},
                    new String[]{mimeType}, null);
        }
    }

    @SuppressWarnings("deprecation")
    private static File getSharedStorageRoot() {
        // Haven's DocumentsProvider exposes shared storage paths and requires MANAGE_EXTERNAL_STORAGE.
        return Environment.getExternalStorageDirectory();
    }
}
