package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/**
 * Instrument → HU request for a navi area on the cluster (CarLan event 721705).
 * JePlay answers with [VDNaviDisplayCluster] + [VDNaviDisplayArea].
 */
public class VDNaviAreaDisplay implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721705;

    public static final Creator<VDNaviAreaDisplay> CREATOR = new Creator<VDNaviAreaDisplay>() {
        @Override
        public VDNaviAreaDisplay createFromParcel(Parcel in) {
            return new VDNaviAreaDisplay(in);
        }

        @Override
        public VDNaviAreaDisplay[] newArray(int size) {
            return new VDNaviAreaDisplay[size];
        }
    };

    private int RequestNaviAreaDisplay;

    public VDNaviAreaDisplay() {
        RequestNaviAreaDisplay = 0;
    }

    public VDNaviAreaDisplay(Parcel in) {
        RequestNaviAreaDisplay = in.readInt();
    }

    public static VDNaviAreaDisplay getValue(VDEvent event) {
        if (event == null || event.getPayload() == null) return null;
        event.getPayload().setClassLoader(VDNaviAreaDisplay.class.getClassLoader());
        return event.getPayload().getParcelable(PAYLOAD_KEY);
    }

    public static VDEvent createEvent(VDNaviAreaDisplay info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviAreaDisplay.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setRequestNaviAreaDisplay(int value) {
        RequestNaviAreaDisplay = value;
    }

    public int getRequestNaviAreaDisplay() {
        return RequestNaviAreaDisplay;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(RequestNaviAreaDisplay);
    }
}
