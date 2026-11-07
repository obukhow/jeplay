package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/** Distance-to-turn content (event 721708). */
public class VDDistNaviContent implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721708;

    public static final Creator<VDDistNaviContent> CREATOR = new Creator<VDDistNaviContent>() {
        @Override
        public VDDistNaviContent createFromParcel(Parcel in) {
            return new VDDistNaviContent(in);
        }

        @Override
        public VDDistNaviContent[] newArray(int size) {
            return new VDDistNaviContent[size];
        }
    };

    private int DistNaviContent;

    public VDDistNaviContent() {
    }

    public VDDistNaviContent(Parcel parcel) {
        DistNaviContent = parcel.readInt();
    }

    public static VDEvent createEvent(VDDistNaviContent info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDDistNaviContent.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setDistNaviContent(int meters) {
        DistNaviContent = meters;
    }

    public int getDistNaviContent() {
        return DistNaviContent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(DistNaviContent);
    }
}
