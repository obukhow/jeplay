package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/**
 * Wrapper for MEDIA_SOURCE_INFO. Launcher / SVMediaManager call
 * {@code VDMediaItem.getValue} — a bare {@link VDMediaInfo} is rejected as null.
 */
public class VDMediaItem implements Parcelable {
    public static final Creator<VDMediaItem> CREATOR = new Creator<VDMediaItem>() {
        @Override
        public VDMediaItem createFromParcel(Parcel parcel) {
            return new VDMediaItem(parcel);
        }

        @Override
        public VDMediaItem[] newArray(int size) {
            return new VDMediaItem[size];
        }
    };

    private int mMediaType;
    private VDMediaInfo mMediaInfo;
    private Parcelable mFmAmInfo;
    private Parcelable mDABInfo;

    public VDMediaItem() {
    }

    public VDMediaItem(Parcel parcel) {
        mMediaType = parcel.readInt();
        mMediaInfo = parcel.readParcelable(VDMediaInfo.class.getClassLoader());
        mFmAmInfo = parcel.readParcelable(getClass().getClassLoader());
        mDABInfo = parcel.readParcelable(getClass().getClassLoader());
    }

    public static VDEvent createEvent(int eventId, VDMediaItem value) {
        return new VDEvent(eventId, createPayload(value));
    }

    public static Bundle createPayload(VDMediaItem value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaItem.class.getClassLoader());
        bundle.putParcelable(VDKey.INFO, value);
        return bundle;
    }

    public void putMediaType(int mediaType) {
        mMediaType = mediaType;
    }

    public void putMediaInfo(VDMediaInfo info) {
        mMediaInfo = info;
    }

    public int getMediaType() {
        return mMediaType;
    }

    public VDMediaInfo getMediaInfo() {
        return mMediaInfo;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mMediaType);
        parcel.writeParcelable(mMediaInfo, flags);
        parcel.writeParcelable(mFmAmInfo, flags);
        parcel.writeParcelable(mDABInfo, flags);
    }
}
