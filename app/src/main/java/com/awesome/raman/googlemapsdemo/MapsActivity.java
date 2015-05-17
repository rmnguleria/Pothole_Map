
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
import android.widget.TextView;
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
import java.util.Timer;
import java.util.TimerTask;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

public class MapsActivity extends FragmentActivity implements SensorEventListener,LocationListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locationManager;
    private ArrayList<LatLng> congestion;
    private ArrayList<LatLng> potholes;

    TextView t1;
    long timeStamp;
    int idd = 0;

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
    //float px_Acc,py_Acc,pz_Acc;
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
    SensorManager mSensorManager;

    // VARIABLES NEEDED FOR SENSOR DATA REORIENTATION...

    // angular speeds from gyro
    private float[] gyro = new float[3];

    // rotation matrix from gyro data
    private float[] gyroMatrix = new float[9];

    // orientation angles from gyro matrix
    private float[] gyroOrientation = new float[3];

    // magnetic field vector
    private float[] magnet = new float[3];

    // accelerometer vector
    private float[] accel = new float[3];

    // orientation angles from accel and magnet
    private float[] accMagOrientation = new float[3];

    // final orientation angles from sensor fusion
    private float[] fusedOrientation = new float[3];

    // accelerometer and magnetometer based rotation matrix
    private float[] rotationMatrix = new float[9];

    public static final float EPSILON = 0.000000001f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private float timestamp;
    private boolean initState = true;

    public static final int TIME_CONSTANT = 30;
    public static final float FILTER_COEFFICIENT = 0.98f;
    private Timer fuseTimer = new Timer();


    public void startDemo(View v){
        appData = new ArrayList<>();
        meanData = new ArrayList<>();
        id = 0;

        mSensorManager = (SensorManager)this.getSystemService(SENSOR_SERVICE);
        initListeners();

        // wait for one second until gyroscope and magnetometer/accelerometer
        // data is initialised then schedule the complementary filter task
        fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(),
                1000, TIME_CONSTANT);

        potholeModel = initializeModel(potholeObject);
        brakeModel = initializeModel(brakeObject);

        /*if(potholeModel == null || brakeModel == null){
            Toast.makeText(this,"Models cannot be intialized",Toast.LENGTH_LONG).show();
            System.exit(1);
        }*/
    }

    public void stopDemo(View v){
        // stopping Accelerometer and GPS
        mSensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);

        combineValues();

        int gpsIter = 0;
        for(MeanObject meanObject : meanData){

            // pothole Prediction .....
            //int potholePred = (int) evaluatePothole(meanObject);
            int potholePred = 0;
            Toast.makeText(getBaseContext(),meanObject.getX_std() + "," + meanObject.getY_std()+ "," + meanObject.getZ_std() + "\n",Toast.LENGTH_LONG).show();
            //Toast.makeText(getApplicationContext(),"x_std" "z_std "+meanObject.getZ_std() ,Toast.LENGTH_LONG).show();
            if(meanObject.getZ_std()>0.2){
                potholePred = 1;
            }
            if(potholePred == 1){
                double lat = 0,longi = 0;
                for(;gpsIter<gpsData.size();gpsIter++){
                    GPSData gpsD = gpsData.get(gpsIter);
                    if(gpsD.timeStamp > meanObject.getTimeStamp()){
                        Toast.makeText(getApplicationContext(),"check",Toast.LENGTH_LONG).show();
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
            //int brakePred = (int) evaluateBrake(meanObject);
            int brakePred = 0;
            Toast.makeText(getApplicationContext(),"Delta_y" + meanObject.getDelta_y(),Toast.LENGTH_LONG).show();
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
        int totalValues = appData.size();
        long duration = appData.get(totalValues-1).getTimeStamp() -appData.get(0).getTimeStamp();
        combine = (int)((totalValues*1000)/duration);
        Toast.makeText(getApplicationContext(),"freq:- " + combine,Toast.LENGTH_LONG).show();
        int sizeIter = appData.size() - appData.size()%combine;
        int totalSecs = sizeIter/combine;

        for(int i=0;i<totalSecs;i++){
            long timeStamp = 0;
            float x_mean = 0,y_mean = 0,z_mean = 0 ,x_std ,y_std ,z_std, x_var = 0,y_var =0,z_var =0;
            float delta_x ,delta_y ,delta_z , max_x=-100,min_x=100,max_y=-100,min_y=100,max_z=-100,min_z=100;
            int start = i*combine;
            int end = (i+1)*combine;
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
        t1=(TextView) findViewById(R.id.result);
        initializeGyroMatrix();
        fillCongestion();
        fillPothole();
        setGradient();
        setUpMapIfNeeded();
        gpsData = new ArrayList<>();
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2, 20, this);
    }

    // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
    public void initListeners(){
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    class calculateFusedOrientationTask extends TimerTask {
        public void run() {
            float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;

            /*
             * Fix for 179° <--> -179° transition problem:
             * Check whether one of the two orientation angles (gyro or accMag) is negative while the other one is positive.
             * If so, add 360° (2 * math.PI) to the negative value, perform the sensor fusion, and remove the 360° from the result
             * if it is greater than 180°. This stabilizes the output in positive-to-negative-transition cases.
             */

            // azimuth
            if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
                fusedOrientation[0] = (float) (FILTER_COEFFICIENT * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]);
                fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
                fusedOrientation[0] = (float) (FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI));
                fusedOrientation[0] -= (fusedOrientation[0] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
            }

            // pitch
            if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
                fusedOrientation[1] = (float) (FILTER_COEFFICIENT * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]);
                fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
                fusedOrientation[1] = (float) (FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI));
                fusedOrientation[1] -= (fusedOrientation[1] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
            }

            // roll
            if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
                fusedOrientation[2] = (float) (FILTER_COEFFICIENT * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]);
                fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
                fusedOrientation[2] = (float) (FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI));
                fusedOrientation[2] -= (fusedOrientation[2] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];
            }

            // overwrite gyro matrix and orientation with fused orientation
            // to compensate for gyro drift
            appData.add(new DataObject(idd++,timeStamp,fusedOrientation[1],fusedOrientation[2],fusedOrientation[0]));
            gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
            System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);

            // update sensor output in GUI
            //mHandler.post(updateOreintationDisplayTask);
        }
    }

    private float[] getRotationMatrixFromOrientation(float[] o) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float)Math.sin(o[1]);
        float cosX = (float)Math.cos(o[1]);
        float sinY = (float)Math.sin(o[2]);
        float cosY = (float)Math.cos(o[2]);
        float sinZ = (float)Math.sin(o[0]);
        float cosZ = (float)Math.cos(o[0]);

        // rotation about x-axis (pitch)
        xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
        xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
        xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
        yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
        yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
        zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
        zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }

    private float[] matrixMultiplication(float[] A, float[] B) {
        float[] result = new float[9];

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }

    public void initializeGyroMatrix(){
        gyroOrientation[0] = 0.0f;
        gyroOrientation[1] = 0.0f;
        gyroOrientation[2] = 0.0f;

        // initialise gyroMatrix with identity matrix
        gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f;
        gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f;
        gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f;
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

    public svm_model initializeModel(String fileName){
        ObjectInputStream objectInputStream = null;
        svm_model model = null;
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
        return model;
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
                //setUpMap();
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
        /* long currTime = System.nanoTime();

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
        } */
        switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                // copy new accelerometer data into accel array and calculate orientation
                System.arraycopy(sensorEvent.values, 0, accel, 0, 3);
                calculateAccMagOrientation();
                timeStamp = System.currentTimeMillis();
                break;

            case Sensor.TYPE_GYROSCOPE:
                // process gyro data
                gyroFunction(sensorEvent);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                // copy new magnetometer data into magnet array
                System.arraycopy(sensorEvent.values, 0, magnet, 0, 3);
                break;
        }

    }

    // calculates orientation angles from accelerometer and magnetometer output
    public void calculateAccMagOrientation() {
        if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation);
        }
    }

    // This function is borrowed from the Android reference
    // at http://developer.android.com/reference/android/hardware/SensorEvent.html#values
    // It calculates a rotation vector from the gyroscope angular speed values.
    private void getRotationVectorFromGyro(float[] gyroValues,
                                           float[] deltaRotationVector,
                                           float timeFactor)
    {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude =
                (float)Math.sqrt(gyroValues[0] * gyroValues[0] +
                        gyroValues[1] * gyroValues[1] +
                        gyroValues[2] * gyroValues[2]);

        // Normalize the rotation vector if it's big enough to get the axis
        if(omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    // This function performs the integration of the gyroscope data.
    // It writes the gyroscope based orientation into gyroOrientation.
    public void gyroFunction(SensorEvent event) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if (accMagOrientation == null)
            return;

        // initialisation of the gyroscope based rotation matrix
        if(initState) {
            float[] initMatrix ;
            initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            float[] test = new float[3];
            SensorManager.getOrientation(initMatrix, test);
            gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
            initState = false;
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        float[] deltaVector = new float[4];
        if(timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            System.arraycopy(event.values, 0, gyro, 0, 3);
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp;

        // convert rotation vector into rotation matrix
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation(gyroMatrix, gyroOrientation);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
