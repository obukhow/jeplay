package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Parcel;
import android.os.Parcelable;

/** Nested action info on VDMediaPlay. Parcel layout must match OEM. */
public class VDMediaPlayActionInfo implements Parcelable {
    public static final Creator<VDMediaPlayActionInfo> CREATOR = new Creator<VDMediaPlayActionInfo>() {
        @Override
        public VDMediaPlayActionInfo createFromParcel(Parcel parcel) {
            return new VDMediaPlayActionInfo(parcel);
        }

        @Override
        public VDMediaPlayActionInfo[] newArray(int size) {
            return new VDMediaPlayActionInfo[size];
        }
    };

    private int mSeekPosition;
    private int mSeekStatus;
    private int mDisPlayView;
    private int mReason;
    private int mIntent;
    private int cyclicMode;
    private int collectStatus;

    public VDMediaPlayActionInfo() {
    }

    public VDMediaPlayActionInfo(Parcel parcel) {
        mSeekPosition = parcel.readInt();
        mSeekStatus = parcel.readInt();
        mDisPlayView = parcel.readInt();
        mReason = parcel.readInt();
        mIntent = parcel.readInt();
        cyclicMode = parcel.readInt();
        collectStatus = parcel.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mSeekPosition);
        parcel.writeInt(mSeekStatus);
        parcel.writeInt(mDisPlayView);
        parcel.writeInt(mReason);
        parcel.writeInt(mIntent);
        parcel.writeInt(cyclicMode);
        parcel.writeInt(collectStatus);
    }
}
