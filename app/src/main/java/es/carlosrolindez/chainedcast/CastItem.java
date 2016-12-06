package es.carlosrolindez.chainedcast;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class CastItem implements Parcelable{
	
	private String description;
	private long durationMs;
	private String urlAddress;

	public CastItem(String desc, String url) {
		description = desc;
		durationMs = 0;
		urlAddress = url;
	}

	public CastItem(String url, long duration, String desc) {
		description = desc;
		durationMs = duration;
		urlAddress = url;
	}


	public void setDuration(long time) {
		durationMs = time;
	}
	
	public long getDuration() {
		return durationMs;
	}

	public void setUrl(String url) {
		urlAddress = url;
	}

	public String getUrl() {
		return urlAddress;
	}
	
	public void setDescription(String newDescription) {
		description = newDescription;
	}
	
	public String getDescription() {
		return description;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int flags) {

        parcel.writeString(description);
        parcel.writeLong(durationMs);
        parcel.writeString(urlAddress);
	}


    public static final Creator<CastItem> CREATOR = new Creator<CastItem>()
    {
        @Override
        public CastItem createFromParcel(Parcel parcel) {
            String description = parcel.readString();
            Long durationMs = parcel.readLong();
            String urlAddress = parcel.readString();

            return new CastItem(urlAddress,durationMs,description);
        }

        @Override
        public CastItem[] newArray(int size) {
            return new CastItem[size];
        }

    };

	public static boolean isIncluded(ArrayList<CastItem> list, String url) {
		if (list==null) return false;
		if (url== null) return true;
		for (CastItem audio:list) {
			if (url.equals(audio.getUrl())) return true;
		}
		return false;
	}

	public static String formatedTime(long milisec) {
		int sec = (int) milisec / 1000;
		int min = sec / 60;
		sec -= (min * 60);
		return ("" + min + ":" + sec);
	}

}
