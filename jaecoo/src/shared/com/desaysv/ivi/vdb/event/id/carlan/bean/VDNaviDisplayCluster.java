package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/**
 * Tell the instrument that a navi map is (or is not) available on the cluster
 * (CarLan event 721699). Set NaviFrontDeskStatus/DisplayCluster to
 * {@code "true"} while the map is up.
 */
public class VDNaviDisplayCluster implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721699;

    public static final Creator<VDNaviDisplayCluster> CREATOR = new Creator<VDNaviDisplayCluster>() {
        @Override
        public VDNaviDisplayCluster createFromParcel(Parcel in) {
            return new VDNaviDisplayCluster(in);
        }

        @Override
        public VDNaviDisplayCluster[] newArray(int size) {
            return new VDNaviDisplayCluster[size];
        }
    };

    private String NaviFrontDeskStatus;
    private String DisplayCluster;
    private int Perspective;
    private String PerspectiveResult;
    private String RequestDisplayNaviArea;

    public VDNaviDisplayCluster() {
        Perspective = 0;
    }

    public VDNaviDisplayCluster(Parcel in) {
        NaviFrontDeskStatus = in.readString();
        DisplayCluster = in.readString();
        Perspective = in.readInt();
        PerspectiveResult = in.readString();
        RequestDisplayNaviArea = in.readString();
    }

    public static VDEvent createEvent(VDNaviDisplayCluster info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDisplayCluster.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setNaviFrontDeskStatus(String value) {
        NaviFrontDeskStatus = value;
    }

    public String getNaviFrontDeskStatus() {
        return NaviFrontDeskStatus;
    }

    public void setDisplayCluster(String value) {
        DisplayCluster = value;
    }

    public String getDisplayCluster() {
        return DisplayCluster;
    }

    public void setPerspective(int value) {
        Perspective = value;
    }

    public int getPerspective() {
        return Perspective;
    }

    public void setPerspectiveResult(String value) {
        PerspectiveResult = value;
    }

    public String getPerspectiveResult() {
        return PerspectiveResult;
    }

    public void setRequestDisplayNaviArea(String value) {
        RequestDisplayNaviArea = value;
    }

    public String getRequestDisplayNaviArea() {
        return RequestDisplayNaviArea;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(NaviFrontDeskStatus);
        dest.writeString(DisplayCluster);
        dest.writeInt(Perspective);
        dest.writeString(PerspectiveResult);
        dest.writeString(RequestDisplayNaviArea);
    }
}
