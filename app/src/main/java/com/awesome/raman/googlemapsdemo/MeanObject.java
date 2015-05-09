package com.awesome.raman.googlemapsdemo;

/**
 * Created by raman on 5/4/15.
 */
public class MeanObject {
    private int id;
    private long timeStamp;
    private float x_std;
    private float y_std;
    private float z_std;
    private float x_mean;
    private float y_mean;
    private float z_mean;
    private float delta_x;
    private float delta_y;
    private float delta_z;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public float getX_std() {
        return x_std;
    }

    public void setX_std(float x_std) {
        this.x_std = x_std;
    }

    public float getY_std() {
        return y_std;
    }

    public void setY_std(float y_std) {
        this.y_std = y_std;
    }

    public float getZ_std() {
        return z_std;
    }

    public void setZ_std(float z_std) {
        this.z_std = z_std;
    }

    public float getX_mean() {
        return x_mean;
    }

    public void setX_mean(float x_mean) {
        this.x_mean = x_mean;
    }

    public float getY_mean() {
        return y_mean;
    }

    public void setY_mean(float y_mean) {
        this.y_mean = y_mean;
    }

    public float getZ_mean() {
        return z_mean;
    }

    public void setZ_mean(float z_mean) {
        this.z_mean = z_mean;
    }

    public float getDelta_x() {
        return delta_x;
    }

    public void setDelta_x(float delta_x) {
        this.delta_x = delta_x;
    }

    public float getDelta_y() {
        return delta_y;
    }

    public void setDelta_y(float delta_y) {
        this.delta_y = delta_y;
    }

    public float getDelta_z() {
        return delta_z;
    }

    public void setDelta_z(float delta_z) {
        this.delta_z = delta_z;
    }

    public MeanObject(){

    }

    public MeanObject(int id,long timeStamp,float x_std,float y_std,float z_std,float x_mean,float y_mean,float z_mean,float delta_x,float delta_y,float delta_z){
        this.id = id;
        this.timeStamp = timeStamp;
        this.x_std = x_std;
        this.y_std = y_std;
        this.z_std = z_std;
        this.x_mean = x_mean;
        this.y_mean = y_mean;
        this.z_mean = z_mean;
        this.delta_x = delta_x;
        this.delta_y = delta_y;
        this.delta_z = delta_z;
    }
}
