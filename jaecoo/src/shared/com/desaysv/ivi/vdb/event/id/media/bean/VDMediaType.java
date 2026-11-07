package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/** Current media source type (VDEventMedia.CURRENT_SOURCE_TYPE). Parcel layout must match OEM. */
public class VDMediaType implements Parcelable {
    public static final Creator<VDMediaType> CREATOR = new Creator<VDMediaType>() {
        @Override
        public VDMediaType createFromParcel(Parcel parcel) {
            return new VDMediaType(parcel);
        }

        @Override
        public VDMediaType[] newArray(int size) {
            return new VDMediaType[size];
        }
    };

    private int mMediaType;

    public VDMediaType() {
    }

    public VDMediaType(Parcel parcel) {
        mMediaType = parcel.readInt();
    }

    public static VDEvent createEvent(int eventId, VDMediaType value) {
        return new VDEvent(eventId, createPayload(value));
    }

    public static Bundle createPayload(VDMediaType value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaType.class.getClassLoader());
        bundle.putParcelable(VDKey.TYPE, value);
        return bundle;
    }

    public void putMediaType(int mediaType) {
        mMediaType = mediaType;
    }

    public int getMediaType() {
        return mMediaType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mMediaType);
    }
}
