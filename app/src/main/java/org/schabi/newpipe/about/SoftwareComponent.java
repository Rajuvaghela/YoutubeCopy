package org.schabi.newpipe.about;

import android.os.Parcel;
import android.os.Parcelable;

public class SoftwareComponent implements Parcelable {

    public static final Creator<SoftwareComponent> CREATOR = new Creator<SoftwareComponent>() {
        @Override
        public SoftwareComponent createFromParcel(Parcel source) {
            return new SoftwareComponent(source);
        }

        @Override
        public SoftwareComponent[] newArray(int size) {
            return new SoftwareComponent[size];
        }
    };

    public String getName() {
        return name;
    }

    public String getYears() {
        return years;
    }

    public String getCopyrightOwner() {
        return copyrightOwner;
    }

    public String getLink() {
        return link;
    }

    public String getVersion() {
        return version;
    }

    private final License license;
    private final String name;
    private final String years;
    private final String copyrightOwner;
    private final String link;
    private final String version;

    public SoftwareComponent(String name, String years, String copyrightOwner, String link, License license) {
        this.name = name;
        this.years = years;
        this.copyrightOwner = copyrightOwner;
        this.link = link;
        this.license = license;
        this.version = null;
    }

    protected SoftwareComponent(Parcel in) {
        this.name = in.readString();
        this.license = in.readParcelable(License.class.getClassLoader());
        this.copyrightOwner = in.readString();
        this.link = in.readString();
        this.years = in.readString();
        this.version = in.readString();
    }

    public License getLicense() {
        return license;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeParcelable(license, flags);
        dest.writeString(copyrightOwner);
        dest.writeString(link);
        dest.writeString(years);
        dest.writeString(version);
    }
}
