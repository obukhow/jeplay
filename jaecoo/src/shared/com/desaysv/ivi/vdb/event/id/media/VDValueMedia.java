package com.desaysv.ivi.vdb.event.id.media;

/** Desay media enums used by VDB now-playing payloads. */
public final class VDValueMedia {

    public static final class InfoType {
        public static final int ALL = 0;
        public static final int ID3 = 1;
    }

    public static final class MediaCollectStatus {
        public static final int WITHOUT_COLLECT = 1;
    }

    public static final class MediaConnectState {
        public static final int DISCONNECTED = 1;
        public static final int CONNECTED_SEARCHING = 2;
        public static final int CONNECTED_SEARCHED = 3;
    }

    public static final class MediaPlayStatus {
        public static final int PLAYING = 1;
        public static final int PAUSED = 2;
        public static final int STOPPED = 3;
    }

    public static final class MediaType {
        public static final int UNKNOWN = 1;
        public static final int BT_MUSIC = 13;
        public static final int CARPLAY = 18;
    }

    private VDValueMedia() {
    }
}
