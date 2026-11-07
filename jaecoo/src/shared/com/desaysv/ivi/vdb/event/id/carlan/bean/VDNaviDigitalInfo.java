package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/**
 * Navigation status for cluster/HUD (event 721701).
 * YaSwitcher uses 3 while guiding and 0/4 when idle.
 */
public class VDNaviDigitalInfo implements Parcelable {
    public static final String PAYLOAD_KEY = "data";
    public static final int EVENT_ID = 721701;
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_GUIDING = 3;

    public static final Creator<VDNaviDigitalInfo> CREATOR = new Creator<VDNaviDigitalInfo>() {
        @Override
        public VDNaviDigitalInfo createFromParcel(Parcel in) {
            return new VDNaviDigitalInfo(in);
        }

        @Override
        public VDNaviDigitalInfo[] newArray(int size) {
            return new VDNaviDigitalInfo[size];
        }
    };

    private int CameraType;
    private int NaviStatus;
    private int SpeedingInfo;
    private int WarningMessage;

    public VDNaviDigitalInfo() {
    }

    public VDNaviDigitalInfo(Parcel parcel) {
        NaviStatus = parcel.readInt();
        SpeedingInfo = parcel.readInt();
        CameraType = parcel.readInt();
        WarningMessage = parcel.readInt();
    }

    public static VDEvent createEvent(VDNaviDigitalInfo info) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDigitalInfo.class.getClassLoader());
        bundle.putParcelable(PAYLOAD_KEY, info);
        return new VDEvent(EVENT_ID, bundle);
    }

    public void setNaviStatus(int status) {
        NaviStatus = status;
    }

    public int getNaviStatus() {
        return NaviStatus;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(NaviStatus);
        parcel.writeInt(SpeedingInfo);
        parcel.writeInt(CameraType);
        parcel.writeInt(WarningMessage);
    }
}
