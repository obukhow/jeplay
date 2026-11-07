package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/** Play / pause status (VDEventMedia.MEDIA_SOURCE_PLAY_STATUS). Parcel layout must match OEM. */
public class VDMediaPlay implements Parcelable {
    public static final Creator<VDMediaPlay> CREATOR = new Creator<VDMediaPlay>() {
        @Override
        public VDMediaPlay createFromParcel(Parcel parcel) {
            return new VDMediaPlay(parcel);
        }

        @Override
        public VDMediaPlay[] newArray(int size) {
            return new VDMediaPlay[size];
        }
    };

    private int mediaType;
    private int playStatus;
    private int playAction;
    private int searchStatus;
    private VDMediaPlayActionInfo mediaPlayActionInfo;

    public VDMediaPlay() {
    }

    public VDMediaPlay(Parcel parcel) {
        mediaType = parcel.readInt();
        playStatus = parcel.readInt();
        playAction = parcel.readInt();
        searchStatus = parcel.readInt();
        mediaPlayActionInfo = parcel.readParcelable(VDMediaPlayActionInfo.class.getClassLoader());
    }

    public static VDEvent createEvent(int eventId, VDMediaPlay value) {
        return new VDEvent(eventId, createPayload(value));
    }

    public static Bundle createPayload(VDMediaPlay value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaPlay.class.getClassLoader());
        bundle.putParcelable(VDKey.STATUS, value);
        return bundle;
    }

    public void putMediaType(int value) {
        mediaType = value;
    }

    public void putPlayStatus(int value) {
        playStatus = value;
    }

    public void putPlayAction(int value) {
        playAction = value;
    }

    public int getPlayStatus() {
        return playStatus;
    }

    public int getMediaType() {
        return mediaType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mediaType);
        parcel.writeInt(playStatus);
        parcel.writeInt(playAction);
        parcel.writeInt(searchStatus);
        parcel.writeParcelable(mediaPlayActionInfo, flags);
    }
}
