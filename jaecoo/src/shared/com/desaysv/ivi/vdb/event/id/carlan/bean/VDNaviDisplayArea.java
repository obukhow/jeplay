package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/**
 * Cluster map slot / area ack (CarLan event 721702).
 * Area {@code 10} is full panel; {@code 9}/{@code 11} are the well.
 */
public class VDNaviDisplayArea implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721702;

    public static final Creator<VDNaviDisplayArea> CREATOR = new Creator<VDNaviDisplayArea>() {
        @Override
        public VDNaviDisplayArea createFromParcel(Parcel in) {
            return new VDNaviDisplayArea(in);
        }

        @Override
        public VDNaviDisplayArea[] newArray(int size) {
            return new VDNaviDisplayArea[size];
        }
    };

    private int NaviDisplayArea;
    private String NaviDisplayAreaResult;

    public VDNaviDisplayArea() {
    }

    public VDNaviDisplayArea(Parcel in) {
        NaviDisplayArea = in.readInt();
        NaviDisplayAreaResult = in.readString();
    }

    public static VDEvent createEvent(VDNaviDisplayArea info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDisplayArea.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setNaviDisplayArea(int value) {
        NaviDisplayArea = value;
    }

    public int getNaviDisplayArea() {
        return NaviDisplayArea;
    }

    public void setNaviDisplayAreaResult(String value) {
        NaviDisplayAreaResult = value;
    }

    public String getNaviDisplayAreaResult() {
        return NaviDisplayAreaResult;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(NaviDisplayArea);
        dest.writeString(NaviDisplayAreaResult);
    }
}
