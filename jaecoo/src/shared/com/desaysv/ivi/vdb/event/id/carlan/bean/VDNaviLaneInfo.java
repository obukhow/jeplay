package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import java.util.ArrayList;

/** CarLan lane-guidance strip (event 721697). */
public class VDNaviLaneInfo implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721697;

    public static final Creator<VDNaviLaneInfo> CREATOR = new Creator<VDNaviLaneInfo>() {
        @Override
        public VDNaviLaneInfo createFromParcel(Parcel in) {
            return new VDNaviLaneInfo(in);
        }

        @Override
        public VDNaviLaneInfo[] newArray(int size) {
            return new VDNaviLaneInfo[size];
        }
    };

    private ArrayList<LaneInfo> RoadInfo;

    public static class LaneInfo implements Parcelable {
        public static final Creator<LaneInfo> CREATOR = new Creator<LaneInfo>() {
            @Override
            public LaneInfo createFromParcel(Parcel in) {
                return new LaneInfo(in);
            }

            @Override
            public LaneInfo[] newArray(int size) {
                return new LaneInfo[size];
            }
        };

        private int LaneIconId;
        private int LaneId;
        /**
         * YaSwitcher: 0 = clear/hide strip slot, 1 = show. White vs gray is in LaneIconId.
         */
        private int LaneInfo;

        public LaneInfo() {
        }

        public LaneInfo(Parcel parcel) {
            LaneInfo = parcel.readInt();
            LaneId = parcel.readInt();
            LaneIconId = parcel.readInt();
        }

        public void setLaneIconId(int iconId) {
            LaneIconId = iconId;
        }

        public void setLaneId(int id) {
            LaneId = id;
        }

        public void setLaneInfo(int info) {
            LaneInfo = info;
        }

        public int getLaneIconId() {
            return LaneIconId;
        }

        public int getLaneId() {
            return LaneId;
        }

        public int getLaneInfo() {
            return LaneInfo;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(LaneInfo);
            parcel.writeInt(LaneId);
            parcel.writeInt(LaneIconId);
        }
    }

    public VDNaviLaneInfo() {
    }

    public VDNaviLaneInfo(Parcel parcel) {
        RoadInfo = parcel.createTypedArrayList(LaneInfo.CREATOR);
    }

    public static VDEvent createEvent(VDNaviLaneInfo info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviLaneInfo.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setRoadInfo(ArrayList<LaneInfo> lanes) {
        RoadInfo = lanes;
    }

    public ArrayList<LaneInfo> getRoadInfo() {
        return RoadInfo;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedList(RoadInfo);
    }
}
