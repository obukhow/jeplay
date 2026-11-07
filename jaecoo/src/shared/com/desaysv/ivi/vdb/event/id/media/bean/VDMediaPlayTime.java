package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/** Elapsed / duration (VDEventMedia.MEDIA_SOURCE_PLAY_TIME). Parcel layout must match OEM. */
public class VDMediaPlayTime implements Parcelable {
    public static final Creator<VDMediaPlayTime> CREATOR = new Creator<VDMediaPlayTime>() {
        @Override
        public VDMediaPlayTime createFromParcel(Parcel parcel) {
            return new VDMediaPlayTime(parcel);
        }

        @Override
        public VDMediaPlayTime[] newArray(int size) {
            return new VDMediaPlayTime[size];
        }
    };

    private int position;
    private int duration;
    private int mediaType;

    public VDMediaPlayTime() {
    }

    public VDMediaPlayTime(Parcel parcel) {
        position = parcel.readInt();
        duration = parcel.readInt();
        mediaType = parcel.readInt();
    }

    public static VDEvent createEvent(int eventId, VDMediaPlayTime value) {
        return new VDEvent(eventId, createPayload(value));
    }

    public static Bundle createPayload(VDMediaPlayTime value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaPlayTime.class.getClassLoader());
        // OEM uses the literal "value" key (VDKey.VALUE).
        bundle.putParcelable(VDKey.VALUE, value);
        return bundle;
    }

    public void putPosition(int value) {
        position = value;
    }

    public void putDuration(int value) {
        duration = value;
    }

    public void putMediaType(int value) {
        mediaType = value;
    }

    public int getPosition() {
        return position;
    }

    public int getDuration() {
        return duration;
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
        parcel.writeInt(position);
        parcel.writeInt(duration);
        parcel.writeInt(mediaType);
    }
}
