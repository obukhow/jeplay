package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/** CarLan road / next-turn payload (event 721698). Parcel field order matches Desay SDK. */
public class VDNaviRoadInfo implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721698;

    public static final Creator<VDNaviRoadInfo> CREATOR = new Creator<VDNaviRoadInfo>() {
        @Override
        public VDNaviRoadInfo createFromParcel(Parcel in) {
            return new VDNaviRoadInfo(in);
        }

        @Override
        public VDNaviRoadInfo[] newArray(int size) {
            return new VDNaviRoadInfo[size];
        }
    };

    private int IntersectionZoomStatus;
    private int NextNaviActiion;
    private int NextNaviActionProgbar;
    private String NextRoadName;
    private int RoadIcon;
    private String RoadName;
    private int RoadType;
    private int SegRemainDis;

    public VDNaviRoadInfo() {
    }

    public VDNaviRoadInfo(Parcel parcel) {
        SegRemainDis = parcel.readInt();
        RoadType = parcel.readInt();
        RoadIcon = parcel.readInt();
        RoadName = parcel.readString();
        NextRoadName = parcel.readString();
        NextNaviActionProgbar = parcel.readInt();
        NextNaviActiion = parcel.readInt();
        IntersectionZoomStatus = parcel.readInt();
    }

    public static VDEvent createEvent(VDNaviRoadInfo info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviRoadInfo.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setSegRemainDis(int meters) {
        SegRemainDis = meters;
    }

    public void setRoadIcon(int icon) {
        RoadIcon = icon;
    }

    public void setRoadType(int type) {
        RoadType = type;
    }

    public void setRoadName(String name) {
        RoadName = name;
    }

    public void setNextRoadName(String name) {
        NextRoadName = name;
    }

    public int getSegRemainDis() {
        return SegRemainDis;
    }

    public int getRoadIcon() {
        return RoadIcon;
    }

    public String getRoadName() {
        return RoadName;
    }

    public String getNextRoadName() {
        return NextRoadName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(SegRemainDis);
        parcel.writeInt(RoadType);
        parcel.writeInt(RoadIcon);
        parcel.writeString(RoadName);
        parcel.writeString(NextRoadName);
        parcel.writeInt(NextNaviActionProgbar);
        parcel.writeInt(NextNaviActiion);
        parcel.writeInt(IntersectionZoomStatus);
    }
}
