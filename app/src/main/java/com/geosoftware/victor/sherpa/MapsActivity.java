package com.geosoftware.victor.sherpa;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements SensorEventListener{

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private ImageView imgButtonGps;
    private ImageView imgButtonPosition;
    private ImageView imgButtonSavedAddressList;
    private ImageView imgButtonSearchAddres;

    private FrameLayout frameLayoutMain;
    private LinearLayout linearLayoutSavedAddresses;
    private LinearLayout linearLayoutSearchAddress;
    private EditText edtSearchAddress;
    private ImageView imgButtonSearch;
    private LinearLayout linearLayoutButtonsMenu;

    private ListView lstSavedAddresses;
    private ListView lstFoundAddresses;



    Location localizacion;
    LocationManager locationManager;
    LocationListener locationListener;

    SensorManager sensorManager;
    Sensor accelerometer;
    Sensor magnetometer;


    boolean isGpsEnabled = false;
    boolean sherpaOn = false;
    Animation noGpsAnimation;
    double latitude = 0;
    double longitude = 0;
    LatLng latLngPosition = null;
    LatLng targetPosition = null;

    float azimuth = 0; //Acimut de la posición del teléfono respecto al norte
    float azimuthPositionPoint = 0; //Acimut entre la posicion actual y el coche, se sumará al acimut

    float[] matrizGravity;
    float[] matrizGeomagnetic;


    private Handler handlerAlignMap = new Handler();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        setUpMapIfNeeded();

        //----- ELEMENTS
        imgButtonGps = (ImageView) findViewById(R.id.imageView3);
        imgButtonPosition = (ImageView) findViewById(R.id.imageView2);
        imgButtonSavedAddressList = (ImageView) findViewById(R.id.imageView4);
        imgButtonSearchAddres = (ImageView) findViewById(R.id.imageView5);

        frameLayoutMain = (FrameLayout) findViewById(R.id.frame_layout_main);
        linearLayoutSearchAddress = (LinearLayout) findViewById(R.id.search_address_layout);
        edtSearchAddress = (EditText) findViewById(R.id.editText);
        imgButtonSearch = (ImageView) findViewById(R.id.imageView);
        linearLayoutSavedAddresses = (LinearLayout) findViewById(R.id.saved_address_layout);
        linearLayoutButtonsMenu = (LinearLayout) findViewById(R.id.linear_layout_buttons_menu);


        lstSavedAddresses = (ListView) findViewById(R.id.listView2);
        lstFoundAddresses = (ListView) findViewById(R.id.listView);


        //----- VARIABLES & CLASSES
        locationListener = new MyLocationListener();
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);


        noGpsAnimation = AnimationUtils.loadAnimation(this, R.anim.no_gps_animation);

        isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(isGpsEnabled){
            imgButtonGps.setImageResource(R.drawable.gps_receiving);
        }



        //----- ONCLICKLISTENERS EVENTS
        imgButtonGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isGpsEnabled){
                    Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(myIntent);
                }
            }
        });

        imgButtonPosition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                positioningMap(latitude, longitude);
            }
        });

        imgButtonSearchAddres.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(linearLayoutSearchAddress.getVisibility() == View.GONE){
                    linearLayoutSearchAddress.setVisibility(View.VISIBLE);
                }else{
                    linearLayoutSearchAddress.setVisibility(View.GONE);
                }
            }
        });

        imgButtonSavedAddressList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(lstSavedAddresses.getVisibility() == View.GONE){
                    lstSavedAddresses.setVisibility(View.VISIBLE);

                }else{
                    lstSavedAddresses.setVisibility(View.GONE);

                }
            }
        });

        imgButtonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String address = edtSearchAddress.getText().toString();

                if(!address.contentEquals("")){
                    searchAddress(address);
                }
            }
        });


    }

    @Override
    protected void onResume() {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);

        handlerAlignMap.postDelayed(runnableAlignMap, 2000);

        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    protected void onPause() {
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(this);

        handlerAlignMap.removeCallbacks(runnableAlignMap);

        super.onPause();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
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

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            matrizGravity = event.values;
        }

        if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            matrizGeomagnetic = event.values;
        }

        if(matrizGravity != null && matrizGeomagnetic != null){
            float[] R = new float[9];
            float[] I = new float[9];
            boolean recojeDatos = SensorManager.getRotationMatrix(R, I, matrizGravity, matrizGeomagnetic);

            if(recojeDatos){
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimuth = orientation[0]*180/3.14159f;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }



    //----- ADAPTERS
    private class AddressListAdapter extends ArrayAdapter<Address>{

        Activity context;
        List<Address> addressList;

        public AddressListAdapter(Activity context, List<Address> addressList) {
            super(context, R.layout.row_found_address, addressList);

            this.context = context;
            this.addressList = addressList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if(convertView == null){
                LayoutInflater inflater = context.getLayoutInflater();
                convertView = inflater.inflate(R.layout.row_found_address, null);

                holder = new ViewHolder();
                holder.txtAddress = (TextView) convertView.findViewById(R.id.textView);
                holder.txtDistance = (TextView) convertView.findViewById(R.id.textView2);
                holder.imgLocation = (ImageView) convertView.findViewById(R.id.imageView6);
                holder.imgWalk = (ImageView) convertView.findViewById(R.id.imageView7);

                convertView.setTag(holder);
            }else{
                holder = (ViewHolder) convertView.getTag();
            }

            final Address address = addressList.get(position);
            holder.txtAddress.setText(address.getAddressLine(0) + ", " + address.getAddressLine(1));

            LatLng latLngAddress = new LatLng(address.getLatitude(), address.getLongitude());
            holder.txtDistance.setText(getDistance(latLngPosition, latLngAddress) + " m");

            holder.imgLocation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    positioningMap(address.getLatitude(), address.getLongitude());
                }
            });

            holder.imgWalk.setOnClickListener(new View.OnClickListener() {
                  @Override
                  public void onClick(View v) {

                  }
              }
            );


            return convertView;
        }
    }

    public class ViewHolder{
        TextView txtAddress;
        TextView txtDistance;
        ImageView imgLocation;
        ImageView imgWalk;

    }



    public class MyLocationListener implements LocationListener{

        @Override
        public void onLocationChanged(Location location) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            latLngPosition = new LatLng(latitude, longitude);

            if(sherpaOn){
                if(targetPosition != null){
                    mMap.clear();
                    drawLine(latLngPosition, targetPosition);
                }
            }else{
                
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status){
                case LocationProvider.AVAILABLE:
                    Log.i("", "onStatusChanged Avaliable");
                    imgButtonGps.setImageResource(R.drawable.gps_receiving);
                    imgButtonGps.clearAnimation();

                    break;
                case LocationProvider.OUT_OF_SERVICE:
                    Log.i("", "onStatusChanged out of service");
                    imgButtonGps.setImageResource(R.drawable.gps);

                    break;
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    Log.i("", "onStatusChanged temporaly unavaliable");
                    imgButtonGps.setImageResource(R.drawable.gps_searching);
                    imgButtonGps.startAnimation(noGpsAnimation);

                    break;
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.i("", "onProviderEnabled");
            imgButtonGps.setImageResource(R.drawable.gps_receiving);
        }

        @Override
        public void onProviderDisabled(String provider) {
            imgButtonGps.setImageResource(R.drawable.gps_click);
            imgButtonGps.startAnimation(noGpsAnimation);
            isGpsEnabled = false;
        }
    }


    public void alignMap(float azimuth){
        CameraPosition planoSituacion = new CameraPosition.Builder(mMap.getCameraPosition()).bearing(azimuth).build();
        CameraUpdate planoSituacionActual = CameraUpdateFactory.newCameraPosition(planoSituacion);

        mMap.animateCamera(planoSituacionActual);
    }

    public void positioningMap(double latitude, double longitude){
        handlerAlignMap.removeCallbacks(runnableAlignMap);
        mMap.clear();
        LatLng position = new LatLng(latitude, longitude);
        CameraPosition planoSituacion = new CameraPosition.Builder().target(position).zoom(17).build();
        CameraUpdate planoSituacionActual = CameraUpdateFactory.newCameraPosition(planoSituacion);

        mMap.animateCamera(planoSituacionActual);
        mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("Marker")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        handlerAlignMap.postDelayed(runnableAlignMap, 2000);
    }

    public void searchAddress(String addressName){
        List<Address> foundAddreses= new ArrayList<>();
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try{
            foundAddreses = geocoder.getFromLocationName(addressName, 5);
        }catch (IOException e){
            Toast.makeText(getApplicationContext(), getString(R.string.adrress_not_found), Toast.LENGTH_SHORT).show();
        }

        Log.i("","direcciones encontradas: " + foundAddreses.size());
        AddressListAdapter adapter = new AddressListAdapter(this, foundAddreses);
        lstFoundAddresses.setAdapter(adapter);
    }

//    public List<Address> searchAddress(String addressName){
//        List<Address> foundAddreses= new ArrayList<>();
//        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
//
//        try{
//            foundAddreses = geocoder.getFromLocationName(addressName, 5);
//        }catch (IOException e){
//            Toast.makeText(getApplicationContext(), getString(R.string.adrress_not_found), Toast.LENGTH_SHORT).show();
//        }
//
//        return foundAddreses;
//    }

    public String getAddress(double latitud, double longitud){
        String direccion = "";

        try{
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(latitud, longitud, 5);

            if(!list.isEmpty()){
                Address address = list.get(0);
                direccion = address.getAddressLine(0) + ", " + address.getAddressLine(1) + ", "
                        + address.getAddressLine(2);
            }
        }catch(Exception e){
            Toast.makeText(getApplicationContext(), getString(R.string.adrress_not_found), Toast.LENGTH_SHORT).show();
        }

        return direccion;
    }

    public float getDistance(LatLng initialPosition, LatLng targetPosition)
    {
        float distance;

        Location pointA = new Location("A");
        pointA.setLatitude(initialPosition.latitude);
        pointA.setLongitude(initialPosition.longitude);

        Location pointB = new Location("B");
        pointB.setLatitude(targetPosition.latitude);
        pointB.setLongitude(targetPosition.longitude);

        distance = pointA.distanceTo(pointB);
        return distance;
    }

    public void drawLine(LatLng myPosition, LatLng targetPosition){
        PolylineOptions linea = new PolylineOptions().add(myPosition).add(targetPosition);
        linea.width(4);
        linea.color(Color.RED);

        mMap.addPolyline(linea);
    }



    Runnable runnableAlignMap = new Runnable() {
        @Override
        public void run() {
            alignMap(azimuth);
            handlerAlignMap.postDelayed(this, 100);
        }
    };
}
