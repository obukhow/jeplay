package com.desaysv.ivi.vdb.event.id.media;

/** Desay media VDB event ids (com.desaysv.ivi.vds.media). */
public final class VDEventMedia {
    /** Service range min — also the inject envelope id for client → bus publish. */
    public static final int MIN = 393216;
    public static final int CURRENT_SOURCE_TYPE = 393217;
    public static final int MEDIA_SOURCE_INFO = 393218;
    public static final int MEDIA_SOURCE_PLAY_LIST = 393219;
    public static final int MEDIA_SOURCE_PLAY_STATUS = 393220;
    public static final int MEDIA_SOURCE_PLAY_TIME = 393221;
    public static final int MEDIA_SOURCE_EXT_INFO = 393222;
    public static final int MEDIA_SOURCE_EXT_DATA = 393223;

    private VDEventMedia() {
    }
}
