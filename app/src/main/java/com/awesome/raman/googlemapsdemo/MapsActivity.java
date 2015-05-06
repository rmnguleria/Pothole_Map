
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

import java.util.ArrayList;
import java.util.Random;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class MapsActivity extends FragmentActivity implements SensorEventListener,LocationListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locationManager;
    private ArrayList<LatLng> congestion;
    private ArrayList<LatLng> potholes;
    private ArrayList<GPSData> gpsData;
    final double ns = 1000000000.0 / 50.0;
    long lastUpdate = System.nanoTime();
    ArrayList<DataObject> appData ;
    ArrayList<MeanObject> meanData;
    float px_Acc,py_Acc,pz_Acc;
    int id = 0;
    private int[] colors = {
            Color.rgb(102, 225, 0), // green
            Color.rgb(0, 0, 255)
    };
    private svm_model model;
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
    }

    public void stopDemo(View v){
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        // calculate the global mean of all six values
        //globalMean = calculateGlobalMean(appData);
        int combine = 50;
        // calculate mean & standard deviation
        int sizeIter = appData.size() - appData.size()%combine;
        for(int i=0;i<sizeIter;i+=combine){
            long timeStamp = 0;
            float x_mean = 0,y_mean = 0,z_mean = 0 ,x_std ,y_std ,z_std, x_var = 0,y_var =0,z_var =0;
            for (int j=i;j<i+combine;j++){
                x_mean += appData.get(j).getX_Acc();
                y_mean += appData.get(j).getY_Acc();
                z_mean += appData.get(j).getZ_Acc();
            }
            x_mean /= combine;
            y_mean /= combine;
            z_mean /= combine;
            for (int j=i;j<i+combine;j++){
                x_var += Math.pow(x_mean - appData.get(j).getX_Acc(),2);
                y_var += Math.pow(y_mean - appData.get(j).getY_Acc(),2);
                z_var += Math.pow(z_mean - appData.get(j).getZ_Acc(),2);
            }
            x_var /= combine;
            y_var /= combine;
            z_var /= combine;

            x_std = (float)Math.sqrt(x_var);
            y_std = (float)Math.sqrt(y_var);
            z_std = (float)Math.sqrt(z_var);
            MeanObject meanObject = new MeanObject(i/combine,timeStamp,x_std,y_std,z_std,x_mean,y_mean,z_mean);
            meanData.add(meanObject);
        }

        //Log.d("HELLO", model.toString());

        Toast.makeText(getApplicationContext(), "Prediction Done", Toast.LENGTH_LONG).show();

        for(MeanObject meanObject : meanData){
            int pred = (int)evaluate(meanObject);
            if(pred == 1){
                Log.d("Hello","Prediction Done");
                Toast.makeText(getApplicationContext(), "Prediction Done", Toast.LENGTH_LONG).show();
                double lat = 0,longi = 0;
                for(GPSData gpsD:gpsData){
                    if(gpsD.timeStamp > meanObject.getTimeStamp()){
                        lat = gpsD.lat;
                        longi = gpsD.longi;
                        break;
                    }
                }
                Toast.makeText(getApplicationContext(),"Pothole detected" + lat + " " + longi,Toast.LENGTH_LONG).show();
                mMap.addMarker(new MarkerOptions().position(new LatLng(lat,longi)));
            }

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
        //trainData();
        //Log.d("HELLO", model.toString());
        gpsData = new ArrayList<>();
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 2, this);
    }

    public double evaluate(MeanObject meanObject) 	//my method to evaluate
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
        svm.svm_get_labels(model, labels);

        double[] prob_estimates = new double[totalClasses];


        for (int i = 0; i < totalClasses; i++){
            System.out.print("(" + labels[i] + ":" + prob_estimates[i] + ")");
        }
        System.out.println("(Actual:" + features[0] + " Prediction:" );

        int a =  (int)svm.svm_predict_probability(model, nodes, prob_estimates);
        Toast.makeText(getApplicationContext(),"z_Std value" + meanObject.getZ_std(),Toast.LENGTH_LONG).show();
        if(meanObject.getZ_std() > 3){
            return 1;
        }
        return 0;
    }

    public void trainData(){
        int ncols = 7;
        int nrows = 10000;
        double[][] train = new double[nrows][ncols];

        //methods
        //training data generation
        for(int i = 0 ;i<nrows;i++) {
            boolean val = new Random().nextInt(12) == 0;
            train[i][0] = 0;
            train[i][1] = new Random().nextDouble();
            train[i][2] = new Random().nextDouble();
            train[i][3] = new Random().nextDouble();
            train[i][4] = new Random().nextDouble();
            train[i][5] = new Random().nextDouble();
            train[i][6] = new Random().nextDouble()/2;
            if(val) {
                train[i][0] = 1;
                train[i][6] += 0.5;
            }
        }

        svm_problem prob = new svm_problem();
        int dataCount = train.length;	//data count is m
        prob.y = new double[dataCount];	//array of labels
        prob.l = dataCount;	//m
        prob.x = new svm_node[dataCount][];    	//X of examples containing features of each row

        for (int i = 0; i < dataCount; i++){
            double[] features = train[i];	//putting the ith row inside features for each row
            prob.x[i] = new svm_node[features.length-1];	//taking out the label
            for (int j = 1; j < features.length; j++){
                svm_node node = new svm_node();
                node.index = j;
                node.value = features[j];
                prob.x[i][j-1] = node;
            }
            prob.y[i] = features[0];
        }

        svm_parameter param = new svm_parameter();
        param.probability = 1;
        param.gamma = 0.5;
        param.nu = 0.5;
        param.C = 1;
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.RBF;
        param.cache_size = 2000;
        param.eps = 0.001;

        model = svm.svm_train(prob, param);

        Toast.makeText(getApplicationContext(),"Training Done",Toast.LENGTH_LONG).show();


    /*    ObjectInputStream objectInputStream = null;
        try{
            InputStream file = new FileInputStream("./model.ser");
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
        } */

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
        double[] lat = {30.765079, 30.711101, 30.711221, 30.711996, 30.712070, 30.713060, 30.714600, 30.715071, 30.715338
                , 30.715799, 30.716168, 30.716509, 30.717875};
        double[] longi = {76.787442, 76.713722, 76.713893, 76.715031, 76.715213, 76.716799, 76.719170, 76.719953, 76.720329
                , 76.721187, 76.721713, 76.722260, 76.724309};
        double[] ncLat = {30.765010000000004,30.765850000000004,30.76592,30.765750000000004,30.764840000000003,30.764750000000003,30.761860000000002,30.760990000000003,30.759210000000003,30.759030000000003,30.758850000000002,30.758480000000002,30.758160000000004,30.755650000000003,30.753560000000004,30.753210000000003,30.75316,30.75315,30.753110000000003,30.75307,30.753030000000003,30.752930000000003,30.752820000000003,30.752730000000003,30.75269,30.752650000000003,30.752640000000003,30.752660000000002,30.752660000000002,30.75259,30.752160000000003,30.7511,30.749920000000003,30.7496,30.749160000000003,30.748500000000003,30.746010000000002,30.745910000000002,30.745890000000003,30.74584,30.745760000000004,30.74566,30.745600000000003,30.745590000000004,30.745630000000002,30.74564,30.745610000000003,30.74545,30.745330000000003,30.745260000000002,30.745210000000004,30.7451,30.744860000000003,30.744660000000003,30.744370000000004,30.744080000000004,30.74377,30.743570000000002,30.743190000000002,30.742890000000003,30.742150000000002,30.73945,30.73937,30.73927,30.739120000000003,30.739060000000002,30.739,30.73889,30.7388,30.738760000000003,30.73871,30.738690000000002,30.738720000000004,30.738740000000004,30.738720000000004,30.738690000000002,30.738640000000004,30.736700000000003,30.735730000000004,30.735060000000004,30.73222,30.73204,30.731980000000004,30.73186,30.731740000000002,30.73167,30.73166,30.731700000000004,30.731740000000002,30.731340000000003,30.730940000000004,30.730680000000003,30.730590000000003,30.730510000000002,30.73036,30.730090000000004,30.730000000000004,30.729960000000002,30.72989,30.729860000000002,30.729860000000002,30.729910000000004,30.729920000000003,30.72979,30.729660000000003,30.729460000000003,30.729190000000003,30.72839,30.727960000000003,30.72755,30.72736,30.727310000000003,30.727210000000003,30.727130000000002,30.72707,30.72706,30.72707,30.72708,30.726930000000003,30.726570000000002,30.726340000000004,30.725990000000003,30.72567,30.725350000000002,30.72483,30.72321,30.72295,30.722710000000003,30.722490000000004,30.722260000000002,30.721930000000004,30.721640000000004,30.720570000000002,30.718660000000003,30.718080000000004,30.71787,30.717720000000003,30.717540000000003,30.7175,30.717460000000003,30.71721,30.71704,30.716130000000003,30.714720000000003,30.71421,30.713300000000004,30.712120000000002,30.711820000000003,30.711700000000004,30.710330000000003,30.708720000000003,30.70829,30.708380000000002,30.708450000000003,30.7085};

        double[] ncLong = {76.78438000000001,76.78595,76.78608000000001,76.78620000000001,76.78688000000001,76.78694,76.78151000000001,76.77988,76.77649000000001,76.77639,76.77633,76.77624,76.77649000000001,76.77846000000001,76.78011000000001,76.78042,76.78053000000001,76.78059,76.78070000000001,76.78076,76.7808,76.78084000000001,76.78083000000001,76.78078000000001,76.78072,76.78062000000001,76.78049,76.78042,76.78026000000001,76.78009,76.77943,76.77776,76.77583000000001,76.77537000000001,76.77465000000001,76.77359000000001,76.76966,76.76958,76.76958,76.76958,76.76956000000001,76.76949,76.76937000000001,76.76924000000001,76.76911000000001,76.76901000000001,76.76891,76.76854,76.7681,76.76752,76.76681,76.76612,76.76519,76.76471000000001,76.76407,76.76355000000001,76.76305,76.76279000000001,76.76239000000001,76.76208000000001,76.76140000000001,76.75880000000001,76.75871000000001,76.75862000000001,76.75856,76.75852,76.75854000000001,76.75854000000001,76.75850000000001,76.75845000000001,76.75835000000001,76.75822000000001,76.75809000000001,76.75806,76.75802,76.75797,76.75781,76.75471,76.75314,76.75210000000001,76.74753000000001,76.74731000000001,76.74733,76.74733,76.74725000000001,76.74712000000001,76.74696,76.74684,76.74680000000001,76.74614000000001,76.74544,76.74480000000001,76.74441,76.74389000000001,76.7425,76.73996000000001,76.73934000000001,76.73933000000001,76.73928000000001,76.73921,76.73913,76.73905,76.73904,76.7385,76.73802,76.73745000000001,76.73683000000001,76.73551,76.73485000000001,76.73427000000001,76.73411,76.73411,76.73408,76.73401000000001,76.73391000000001,76.7338,76.73373000000001,76.73369000000001,76.73339,76.73296,76.73273,76.73246,76.73230000000001,76.73219,76.73208000000001,76.73188,76.73182000000001,76.73171,76.73159000000001,76.73142,76.73108,76.73074000000001,76.72899000000001,76.72599000000001,76.72505000000001,76.72476,76.72457,76.72447000000001,76.72447000000001,76.72446000000001,76.7245,76.72456000000001,76.72535,76.72656,76.72698000000001,76.72771,76.72872000000001,76.72897,76.72912000000001,76.72698000000001,76.72431,76.72364,76.72357000000001,76.72369,76.72377};

        double[] mtLat = {30.705530000000003,30.705160000000003,30.704600000000003,30.704420000000002,30.704220000000003,30.70398,30.70285,30.702040000000004,30.702030000000004,30.702080000000002,30.702050000000003,30.700850000000003,30.700190000000003,30.69882,30.698130000000003,30.697710000000004,30.697480000000002,30.697210000000002,30.696120000000004,30.69517,30.694910000000004};

        double[] mtLong = {76.70871000000001,76.70906000000001,76.71002,76.71025,76.71039,76.71062,76.7116,76.71223,76.7137,76.71437,76.71447,76.71446,76.71448000000001,76.7145,76.71449000000001,76.71469,76.71479000000001,76.71491,76.71586,76.71664000000001,76.71685000000001};

        double[] pecLat = {30.766102,30.765135,30.765075,30.764335,30.765145,30.765503,30.765510,30.765370,30.765420,30.765835,30.765994,30.765644,30.765145,30.769115,30.767380,30.767136,30.768991,30.768772,30.768901,30.769095,30.769005,30.769288,30.769261,30.769180};

        double[] pecLong = {76.785964,76.784626,76.784508,76.783153,76.784631,76.781627,76.781461,76.781069,76.781321,76.783032,76.783032,76.782697,76.784631,76.788209,76.788724,76.788507,76.787837,76.788129,76.788408, 76.788440,76.788714,76.788743,76.789135,76.789009};

        double[] chLat = {30.70857,30.710380000000004,30.7115,30.711710000000004,30.711750000000002,30.711700000000004,30.711370000000002,30.71073,30.708620000000003,30.706540000000004,30.706090000000003,30.705800000000004,30.705750000000002,30.705640000000002,30.70559,30.70558,30.705560000000002,30.70548,30.705350000000003,30.705240000000003,30.705150000000003,30.705050000000004,30.704990000000002,30.704980000000003,30.705000000000002,30.70502,30.704940000000004,30.704760000000004,30.704530000000002,30.70172,30.70145,30.699250000000003,30.698320000000002,30.697950000000002,30.697630000000004,30.69712,30.696210000000004,30.694360000000003,30.69319,30.692670000000003,30.69225,30.6901,30.689650000000004,30.689040000000002,30.68736,30.687230000000003,30.68633,30.68588,30.683480000000003,30.681070000000002,30.67986,30.678160000000002,30.677450000000004,30.677590000000002};

        double[] chLong = {76.72388000000001,76.72686,76.72866,76.729,76.72907000000001,76.72912000000001,76.72939000000001,76.72994000000001,76.7317,76.73345,76.73392000000001,76.73416,76.73422000000001,76.73440000000001,76.73454000000001,76.73459000000001,76.73468000000001,76.73479,76.73486000000001,76.73487,76.73485000000001,76.73477000000001,76.73464000000001,76.73452,76.73440000000001,76.73438,76.73421,76.73391000000001,76.73358,76.7291,76.72866,76.72515,76.72369,76.72371000000001,76.72372,76.72414,76.7249,76.72644000000001,76.72744,76.72794,76.72823000000001,76.73,76.73037000000001,76.7309,76.73229,76.73240000000001,76.73317,76.73355000000001,76.73562000000001,76.73759000000001,76.73859,76.73998,76.74058000000001,76.74081000000001};

        for(int i=0;i<lat.length;i++){
            potholes.add(new LatLng(lat[i], longi[i]));
        }

        for(int i=0;i<pecLat.length;i++){
            potholes.add(new LatLng(pecLat[i],pecLong[i]));
        }

        for(int i=0;i<ncLat.length;i++){
            Random random = new Random();
            int index = random.nextInt(ncLat.length);
            potholes.add(new LatLng(ncLat[i],ncLong[i]));
        }
        for(int i=0;i<mtLat.length;i++){
            Random random = new Random();
            int index = random.nextInt(mtLat.length);
            potholes.add(new LatLng(mtLat[i],mtLong[i]));
        }

        for(int i=0;i<chLat.length;i++){
            potholes.add(new LatLng(chLat[i],chLong[i]));
        }
    }

    private void fillCongestion(){
        congestion = new ArrayList<>();
        double[] lat = {30.765010000000004,30.765850000000004,30.76592,30.765750000000004,30.764840000000003,30.764750000000003,30.761860000000002,30.760990000000003,30.759210000000003,30.759030000000003,30.758850000000002,30.758480000000002,30.758160000000004,30.755650000000003,30.753560000000004,30.753210000000003,30.75316,30.75315,30.753110000000003,30.75307,30.753030000000003,30.752930000000003,30.752820000000003,30.752730000000003,30.75269,30.752650000000003,30.752640000000003,30.752660000000002,30.752660000000002,30.75259,30.752160000000003,30.7511,30.749920000000003,30.7496,30.749160000000003,30.748500000000003,30.746010000000002,30.745910000000002,30.745890000000003,30.74584,30.745760000000004,30.74566,30.745600000000003,30.745590000000004,30.745630000000002,30.74564,30.745610000000003,30.74545,30.745330000000003,30.745260000000002,30.745210000000004,30.7451,30.744860000000003,30.744660000000003,30.744370000000004,30.744080000000004,30.74377,30.743570000000002,30.743190000000002,30.742890000000003,30.742150000000002,30.73945,30.73937,30.73927,30.739120000000003,30.739060000000002,30.739,30.73889,30.7388,30.738760000000003,30.73871,30.738690000000002,30.738720000000004,30.738740000000004,30.738720000000004,30.738690000000002,30.738640000000004,30.736700000000003,30.735730000000004,30.735060000000004,30.73222,30.73204,30.731980000000004,30.73186,30.731740000000002,30.73167,30.73166,30.731700000000004,30.731740000000002,30.731340000000003,30.730940000000004,30.730680000000003,30.730590000000003,30.730510000000002,30.73036,30.730090000000004,30.730000000000004,30.729960000000002,30.72989,30.729860000000002,30.729860000000002,30.729910000000004,30.729920000000003,30.72979,30.729660000000003,30.729460000000003,30.729190000000003,30.72839,30.727960000000003,30.72755,30.72736,30.727310000000003,30.727210000000003,30.727130000000002,30.72707,30.72706,30.72707,30.72708,30.726930000000003,30.726570000000002,30.726340000000004,30.725990000000003,30.72567,30.725350000000002,30.72483,30.72321,30.72295,30.722710000000003,30.722490000000004,30.722260000000002,30.721930000000004,30.721640000000004,30.720570000000002,30.718660000000003,30.718080000000004,30.71787,30.717720000000003,30.717540000000003,30.7175,30.717460000000003,30.71721,30.71704,30.716130000000003,30.714720000000003,30.71421,30.713300000000004,30.712120000000002,30.711820000000003,30.711700000000004,30.710330000000003,30.708720000000003,30.70829,30.708380000000002,30.708450000000003,30.7085};

        double[] longi = {76.78438000000001,76.78595,76.78608000000001,76.78620000000001,76.78688000000001,76.78694,76.78151000000001,76.77988,76.77649000000001,76.77639,76.77633,76.77624,76.77649000000001,76.77846000000001,76.78011000000001,76.78042,76.78053000000001,76.78059,76.78070000000001,76.78076,76.7808,76.78084000000001,76.78083000000001,76.78078000000001,76.78072,76.78062000000001,76.78049,76.78042,76.78026000000001,76.78009,76.77943,76.77776,76.77583000000001,76.77537000000001,76.77465000000001,76.77359000000001,76.76966,76.76958,76.76958,76.76958,76.76956000000001,76.76949,76.76937000000001,76.76924000000001,76.76911000000001,76.76901000000001,76.76891,76.76854,76.7681,76.76752,76.76681,76.76612,76.76519,76.76471000000001,76.76407,76.76355000000001,76.76305,76.76279000000001,76.76239000000001,76.76208000000001,76.76140000000001,76.75880000000001,76.75871000000001,76.75862000000001,76.75856,76.75852,76.75854000000001,76.75854000000001,76.75850000000001,76.75845000000001,76.75835000000001,76.75822000000001,76.75809000000001,76.75806,76.75802,76.75797,76.75781,76.75471,76.75314,76.75210000000001,76.74753000000001,76.74731000000001,76.74733,76.74733,76.74725000000001,76.74712000000001,76.74696,76.74684,76.74680000000001,76.74614000000001,76.74544,76.74480000000001,76.74441,76.74389000000001,76.7425,76.73996000000001,76.73934000000001,76.73933000000001,76.73928000000001,76.73921,76.73913,76.73905,76.73904,76.7385,76.73802,76.73745000000001,76.73683000000001,76.73551,76.73485000000001,76.73427000000001,76.73411,76.73411,76.73408,76.73401000000001,76.73391000000001,76.7338,76.73373000000001,76.73369000000001,76.73339,76.73296,76.73273,76.73246,76.73230000000001,76.73219,76.73208000000001,76.73188,76.73182000000001,76.73171,76.73159000000001,76.73142,76.73108,76.73074000000001,76.72899000000001,76.72599000000001,76.72505000000001,76.72476,76.72457,76.72447000000001,76.72447000000001,76.72446000000001,76.7245,76.72456000000001,76.72535,76.72656,76.72698000000001,76.72771,76.72872000000001,76.72897,76.72912000000001,76.72698000000001,76.72431,76.72364,76.72357000000001,76.72369,76.72377};

        for(int i=0;i<lat.length;i++){
            congestion.add(new LatLng(lat[i], longi[i]));
        }
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
