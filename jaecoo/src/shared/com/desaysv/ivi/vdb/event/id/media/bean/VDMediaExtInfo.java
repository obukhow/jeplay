package com.desaysv.ivi.vdb.event.id.media.bean;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/** Connect / mode extras (VDEventMedia.MEDIA_SOURCE_EXT_INFO). */
public class VDMediaExtInfo implements Parcelable {
    public static final Creator<VDMediaExtInfo> CREATOR = new Creator<VDMediaExtInfo>() {
        @Override
        public VDMediaExtInfo createFromParcel(Parcel parcel) {
            return new VDMediaExtInfo(parcel);
        }

        @Override
        public VDMediaExtInfo[] newArray(int size) {
            return new VDMediaExtInfo[size];
        }
    };

    private int mediaType;
    private int cyclicMode;
    private int connectState;
    private Uri mediaList;

    public VDMediaExtInfo() {
    }

    public VDMediaExtInfo(Parcel parcel) {
        mediaType = parcel.readInt();
        cyclicMode = parcel.readInt();
        connectState = parcel.readInt();
        mediaList = parcel.readParcelable(Uri.class.getClassLoader());
    }

    public static VDEvent createEvent(int eventId, VDMediaExtInfo value) {
        return new VDEvent(eventId, createPayload(value));
    }

    public static Bundle createPayload(VDMediaExtInfo value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaExtInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.INFO, value);
        return bundle;
    }

    public void putMediaType(int value) {
        mediaType = value;
    }

    public void putCyclicMode(int value) {
        cyclicMode = value;
    }

    public void putConnectState(int value) {
        connectState = value;
    }

    public int getMediaType() {
        return mediaType;
    }

    public int getConnectState() {
        return connectState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mediaType);
        parcel.writeInt(cyclicMode);
        parcel.writeInt(connectState);
        parcel.writeParcelable(mediaList, flags);
    }
}
