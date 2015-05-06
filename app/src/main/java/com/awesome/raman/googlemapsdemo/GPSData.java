package com.awesome.raman.googlemapsdemo;

/**
 * Created by raman on 16/4/15.
 */
public class GPSData {
    long timeStamp;
    double lat;
    double longi;

    public GPSData(long timeStamp, double lat, double longi){
        this.timeStamp = timeStamp;
        this.lat = lat;
        this.longi = longi;
    }
}
