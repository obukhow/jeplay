package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;

/** Now-playing ID3 / cover (VDEventMedia.MEDIA_SOURCE_INFO). Parcel layout must match OEM. */
public class VDMediaInfo implements Parcelable {
    public static final Creator<VDMediaInfo> CREATOR = new Creator<VDMediaInfo>() {
        @Override
        public VDMediaInfo createFromParcel(Parcel parcel) {
            return new VDMediaInfo(parcel);
        }

        @Override
        public VDMediaInfo[] newArray(int size) {
            return new VDMediaInfo[size];
        }
    };

    private int id;
    private int mediaType;
    private int collectStatus;
    private int infoType;
    private String title;
    private String artist;
    private String album;
    private String composer;
    private String genre;
    private String albumImgUrl;
    private String fileName;
    private String fileUrl;
    private byte[] albumBuffer;
    private String lyrics;

    public VDMediaInfo() {
    }

    public VDMediaInfo(Parcel parcel) {
        id = parcel.readInt();
        mediaType = parcel.readInt();
        collectStatus = parcel.readInt();
        infoType = parcel.readInt();
        title = parcel.readString();
        artist = parcel.readString();
        album = parcel.readString();
        composer = parcel.readString();
        genre = parcel.readString();
        albumImgUrl = parcel.readString();
        fileName = parcel.readString();
        fileUrl = parcel.readString();
        albumBuffer = parcel.createByteArray();
        lyrics = parcel.readString();
    }

    public static VDEvent createEvent(int eventId, VDMediaInfo value) {
        return new VDEvent(eventId, createPayload(value));
    }

    public static Bundle createPayload(VDMediaInfo value) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaInfo.class.getClassLoader());
        bundle.putParcelable(VDKey.INFO, value);
        return bundle;
    }

    public void putId(int value) {
        id = value;
    }

    public void putMediaType(int value) {
        mediaType = value;
    }

    public void putCollectStatus(int value) {
        collectStatus = value;
    }

    public void putInfoType(int value) {
        infoType = value;
    }

    public void putTitle(String value) {
        title = value;
    }

    public void putArtist(String value) {
        artist = value;
    }

    public void putAlbum(String value) {
        album = value;
    }

    public void setAlbumBuffer(byte[] value) {
        albumBuffer = value;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public byte[] getAlbumBuffer() {
        return albumBuffer;
    }

    public int getMediaType() {
        return mediaType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(id);
        parcel.writeInt(mediaType);
        parcel.writeInt(collectStatus);
        parcel.writeInt(infoType);
        parcel.writeString(title);
        parcel.writeString(artist);
        parcel.writeString(album);
        parcel.writeString(composer);
        parcel.writeString(genre);
        parcel.writeString(albumImgUrl);
        parcel.writeString(fileName);
        parcel.writeString(fileUrl);
        parcel.writeByteArray(albumBuffer);
        parcel.writeString(lyrics);
    }
}
