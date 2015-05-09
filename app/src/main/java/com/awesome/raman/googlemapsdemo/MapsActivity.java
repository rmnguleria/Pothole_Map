
package com.awesome.raman.googlemapsdemo;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

public class MapsActivity extends FragmentActivity implements SensorEventListener,LocationListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locationManager;
    private ArrayList<LatLng> congestion;
    private ArrayList<LatLng> potholes;

    private ArrayList<GPSData> potholesPred = new ArrayList<>();
    private ArrayList<GPSData> congestionPred = new ArrayList<>();
    private ArrayList<GPSData> gpsData;
    final double ns = 1000000000.0 / 50.0;
    long lastUpdate = System.nanoTime();
    ArrayList<DataObject> appData ;
    ArrayList<MeanObject> meanData;

    // combining 50 values , 1 sec data (freq = 50 Hz).
    int combine = 50;

    String potholeObject = "pothole.ser";
    String brakeObject = "brake.ser";
    float px_Acc,py_Acc,pz_Acc;
    int id = 0;
    private int[] colors = {
            Color.rgb(102, 225, 0), // green
            Color.rgb(0, 0, 255)
    };
    private svm_model potholeModel;
    private  svm_model brakeModel;
    float[] startPoints = {
            0.2f, 1f
    };
    SensorManager sensorManager;

    public void startDemo(View v){
        appData = new ArrayList<>();
        meanData = new ArrayList<>();
        id = 0;
        sensorManager = (SensorManager)this.getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        initializeModel(potholeModel,potholeObject);
        initializeModel(brakeModel,brakeObject);
    }

    public void stopDemo(View v){
        // stopping Accelerometer and GPS
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);

        combineValues();

        int gpsIter = 0;
        for(MeanObject meanObject : meanData){

            // pothole Prediction .....
            int potholePred = (int) evaluatePothole(meanObject);
            if(potholePred == 1){
                Log.d("Hello","Prediction Done");
                Toast.makeText(getApplicationContext(), "Prediction Done", Toast.LENGTH_LONG).show();
                double lat = 0,longi = 0;
                for(;gpsIter<gpsData.size();gpsIter++){
                    GPSData gpsD = gpsData.get(gpsIter);
                    if(gpsD.timeStamp > meanObject.getTimeStamp()){
                        lat = gpsD.lat;
                        longi = gpsD.longi;
                        break;
                    }
                }
                Toast.makeText(getApplicationContext(),"Pothole detected" + lat + " " + longi,Toast.LENGTH_LONG).show();
                potholesPred.add(new GPSData(meanObject.getTimeStamp(),lat,longi));
                mMap.addMarker(new MarkerOptions().position(new LatLng(lat, longi)));
            }

            // brake Prediction....
            int brakePred = (int) evaluateBrake(meanObject);
            if(brakePred == 1){
                double lat = 0,longi = 0;
                for(;gpsIter<gpsData.size();gpsIter++){
                    GPSData gpsD = gpsData.get(gpsIter);
                    if(gpsD.timeStamp > meanObject.getTimeStamp()){
                        lat = gpsD.lat;
                        longi = gpsD.longi;
                        break;
                    }
                }
                Toast.makeText(getApplicationContext(),"Brake detected" + lat + " " + longi,Toast.LENGTH_LONG).show();
                congestionPred.add(new GPSData(meanObject.getTimeStamp(), lat, longi));
                mMap.addMarker(new MarkerOptions().position(new LatLng(lat,longi)));
            }

        }
    }

    public void combineValues(){
        int sizeIter = appData.size() - appData.size()%combine;
        int totalSecs = sizeIter/combine;

        for(int i=0;i<totalSecs;i++){
            long timeStamp = 0;
            float x_mean = 0,y_mean = 0,z_mean = 0 ,x_std ,y_std ,z_std, x_var = 0,y_var =0,z_var =0;
            float delta_x ,delta_y ,delta_z , max_x=-100,min_x=100,max_y=-100,min_y=100,max_z=-100,min_z=100;
            int start = i*50;
            int end = (i+1)*50;
            for (int j=start;j<end;j++){
                // adding 50 values will divide later...
                x_mean += appData.get(j).getX_Acc();
                y_mean += appData.get(j).getY_Acc();
                z_mean += appData.get(j).getZ_Acc();

                // finding max and min for brake detection...
                max_x = Math.max(max_x, appData.get(j).getX_Acc());
                max_y = Math.max(max_y,appData.get(j).getY_Acc());
                max_z = Math.max(max_z,appData.get(j).getZ_Acc());

                min_x = Math.min(min_x, appData.get(j).getX_Acc());
                min_y = Math.min(min_y,appData.get(j).getY_Acc());
                min_z = Math.min(min_z,appData.get(j).getZ_Acc());
            }
            // calculating mean here ....
            x_mean /= combine;
            y_mean /= combine;
            z_mean /= combine;

            // variance calculation .. for calculating standard deviation..
            for (int j=start;j<end;j++){
                x_var += Math.pow(x_mean - appData.get(j).getX_Acc(),2);
                y_var += Math.pow(y_mean - appData.get(j).getY_Acc(),2);
                z_var += Math.pow(z_mean - appData.get(j).getZ_Acc(),2);
            }
            x_var /= combine;
            y_var /= combine;
            z_var /= combine;

            // standard deviation calculation...
            x_std = (float)Math.sqrt(x_var);
            y_std = (float)Math.sqrt(y_var);
            z_std = (float)Math.sqrt(z_var);

            // calculating deltas here .....
            delta_x = max_x - min_x;
            delta_y = max_y - min_y;
            delta_z = max_z - min_z;
            MeanObject meanObject = new MeanObject(i,timeStamp,x_std,y_std,z_std,x_mean,y_mean,z_mean,delta_x,delta_y,delta_z);
            meanData.add(meanObject);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        fillCongestion();
        fillPothole();
        setGradient();
        setUpMapIfNeeded();
        gpsData = new ArrayList<>();
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2, 20, this);
    }

    public double evaluatePothole(MeanObject meanObject) 	//my method to evaluatePothole
    {
        double[] features = new double[7];
        features[0] = 0;
        features[1] = meanObject.getX_mean();
        features[2] = meanObject.getY_mean();
        features[3] = meanObject.getZ_mean();
        features[4] = meanObject.getX_std();
        features[5] = meanObject.getY_std();
        features[6] = meanObject.getZ_std();
        svm_node[] nodes = new svm_node[features.length-1];	//separating label
        for (int i = 1; i < features.length; i++)
        {
            svm_node node = new svm_node();
            node.index = i;
            node.value = features[i];

            nodes[i-1] = node;
        }

        int totalClasses = 2;
        int[] labels = new int[totalClasses];
        svm.svm_get_labels(potholeModel, labels);

        double[] prob_estimates = new double[totalClasses];

        int a =  (int)svm.svm_predict_probability(potholeModel, nodes, prob_estimates);
        Toast.makeText(getApplicationContext(),"z_Std value" + meanObject.getZ_std(),Toast.LENGTH_LONG).show();
        return a;
    }

    public double evaluateBrake(MeanObject meanObject){
        double[] features = new double[10];
        features[0] = 0;
        features[1] = meanObject.getX_mean();
        features[2] = meanObject.getY_mean();
        features[3] = meanObject.getZ_mean();
        features[4] = meanObject.getX_std();
        features[5] = meanObject.getY_std();
        features[6] = meanObject.getZ_std();
        features[7] = meanObject.getDelta_x();
        features[8] = meanObject.getDelta_y();
        features[9] = meanObject.getDelta_z();
        svm_node[] nodes = new svm_node[features.length-1];	//separating label
        for (int i = 1; i < features.length; i++)
        {
            svm_node node = new svm_node();
            node.index = i;
            node.value = features[i];

            nodes[i-1] = node;
        }

        int totalClasses = 2;
        int[] labels = new int[totalClasses];
        svm.svm_get_labels(brakeModel, labels);

        double[] prob_estimates = new double[totalClasses];

        int a =  (int)svm.svm_predict_probability(brakeModel, nodes, prob_estimates);
        Toast.makeText(getApplicationContext(),"Delta_y value" + meanObject.getDelta_y(),Toast.LENGTH_LONG).show();
        return a;
    }

    public void initializeModel(svm_model model, String fileName){
        ObjectInputStream objectInputStream = null;
        try{
            InputStream file = new FileInputStream(fileName);
            InputStream buffer = new BufferedInputStream(file);
            objectInputStream = new ObjectInputStream(buffer);
            model = (svm_model)objectInputStream.readObject();
            Log.d("HELLO",model.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setGradient(){

    }

    private void fillPothole(){
        potholes = new ArrayList<>();

    }

    private void fillCongestion(){
        congestion = new ArrayList<>();

    }


    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        LatLng pec = new LatLng(30.740655, 76.760224);
        mMap.setMyLocationEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pec, 13));
        /* for(LatLng latLng:potholes){
            mMap.addMarker(new MarkerOptions().position(latLng));
        } */
        Gradient potholeGradient = new Gradient(colors,startPoints);
        TileProvider potholeProvider;
        potholeProvider = new HeatmapTileProvider.Builder().data(potholes).gradient(potholeGradient).build();
        TileOverlay potholeOverlay;
        potholeOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(potholeProvider));
        potholeOverlay.setVisible(true);

        TileProvider congestionProvider;
        congestionProvider = new HeatmapTileProvider.Builder().data(congestion).build();
        TileOverlay congestionOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(congestionProvider));
        congestionOverlay.setVisible(true);
    }

    @Override
    public void onLocationChanged(Location location) {
        gpsData.add(new GPSData(System.currentTimeMillis(),location.getLatitude(),location.getLongitude()));
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long currTime = System.nanoTime();

        Sensor sensor = sensorEvent.sensor;
        if(sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            if(currTime - lastUpdate >= ns) {
                px_Acc = sensorEvent.values[0];
                py_Acc = sensorEvent.values[1];
                pz_Acc = sensorEvent.values[2];
                DataObject data = new DataObject(id++, System.currentTimeMillis(), px_Acc, py_Acc, pz_Acc);
                lastUpdate = currTime;
                appData.add(data);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
