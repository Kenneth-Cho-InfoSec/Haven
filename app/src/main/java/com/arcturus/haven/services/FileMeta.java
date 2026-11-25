package com.arcturus.haven.services;

import android.os.Parcel;
import android.os.Parcelable;

public class FileMeta implements Parcelable {
    public static final Creator<FileMeta> CREATOR = new Creator<FileMeta>() {
        @Override
        public FileMeta createFromParcel(Parcel source) {
            return new FileMeta(source);
        }

        @Override
        public FileMeta[] newArray(int size) {
            return new FileMeta[size];
        }
    };

    public final String documentId;
    public final String displayName;
    public final long size;
    public final long lastModified;
    public final String mimeType;
    public final int flags;

    public FileMeta(
            String documentId,
            String displayName,
            long size,
            long lastModified,
            String mimeType,
            int flags) {
        this.documentId = documentId;
        this.displayName = displayName;
        this.size = size;
        this.lastModified = lastModified;
        this.mimeType = mimeType;
        this.flags = flags;
    }

    protected FileMeta(Parcel source) {
        this.documentId = source.readString();
        this.displayName = source.readString();
        this.size = source.readLong();
        this.lastModified = source.readLong();
        this.mimeType = source.readString();
        this.flags = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(documentId);
        dest.writeString(displayName);
        dest.writeLong(size);
        dest.writeLong(lastModified);
        dest.writeString(mimeType);
        dest.writeInt(this.flags);
    }
}
