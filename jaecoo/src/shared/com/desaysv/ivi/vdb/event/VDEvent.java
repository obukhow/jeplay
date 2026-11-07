package com.desaysv.ivi.vdb.event;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

/**
 * Desay VDB event envelope. Must match the head-unit SDK parcel layout so CarLan can unpack it.
 */
public class VDEvent implements Parcelable {
    public static final Creator<VDEvent> CREATOR = new Creator<VDEvent>() {
        @Override
        public VDEvent createFromParcel(Parcel in) {
            return new VDEvent(in);
        }

        @Override
        public VDEvent[] newArray(int size) {
            return new VDEvent[size];
        }
    };

    private int mId;
    private Bundle mPayload;
    private int mThreadType;
    private long mTimeMillis;

    public VDEvent(int id) {
        this.mThreadType = 0;
        this.mId = id;
    }

    public VDEvent(int id, Bundle payload) {
        this.mThreadType = 0;
        this.mId = id;
        this.mPayload = payload;
    }

    public VDEvent(Parcel parcel) {
        this.mId = parcel.readInt();
        this.mPayload = parcel.readBundle(VDEvent.class.getClassLoader());
        this.mThreadType = parcel.readInt();
        this.mTimeMillis = parcel.readLong();
    }

    public void createTimeMillis() {
        this.mTimeMillis = SystemClock.elapsedRealtime();
    }

    public int getId() {
        return mId;
    }

    public Bundle getPayload() {
        return mPayload;
    }

    public void setPayload(Bundle payload) {
        this.mPayload = payload;
    }

    public int getThreadType() {
        return mThreadType;
    }

    public void setThreadType(int threadType) {
        this.mThreadType = threadType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeBundle(mPayload);
        parcel.writeInt(mThreadType);
        parcel.writeLong(mTimeMillis);
    }
}
