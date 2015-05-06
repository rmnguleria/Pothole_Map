package com.awesome.raman.googlemapsdemo;

/**
 * Main Data . stored in csv format.
 */
public class DataObject {
    private int id;
    private long timeStamp;
    private float x_Acc;
    private float y_Acc;
    private float z_Acc;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public long getTimeStamp() {
        return timeStamp;
    }

    public float getX_Acc() {
        return x_Acc;
    }

    public float getZ_Acc() {
        return z_Acc;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void setX_Acc(float x_Acc) {
        this.x_Acc = x_Acc;
    }

    public void setY_Acc(float y_Acc) {
        this.y_Acc = y_Acc;
    }

    public void setZ_Acc(float z_Acc) {
        this.z_Acc = z_Acc;
    }

    public float getY_Acc() {
        return y_Acc;
    }

    public DataObject(){

    }

    public DataObject(int id, long timeStamp, float x_Acc, float y_Acc, float z_Acc){
        this.id = id;
        this.timeStamp = timeStamp;
        this.x_Acc = x_Acc;
        this.y_Acc = y_Acc;
        this.z_Acc = z_Acc;
    }

    public String toString(){
        return getId() + "," + getTimeStamp() + "," + getX_Acc() + "," + getY_Acc() + "," + getZ_Acc() ;
    }
}
