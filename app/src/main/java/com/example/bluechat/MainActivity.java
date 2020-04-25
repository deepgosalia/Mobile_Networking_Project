package com.example.bluechat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ResultReceiver;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener, OnMapReadyCallback {


    Button button, discover, listen, send, sendAlert;
    ListView listView;
    TextView carName, status, carDesc, carColor, carNo, distance, location, carReason;
    EditText writeMsg;

    SendReceive sendReceive;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] bluetoothDevices;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    Intent bluetoothIntent;

    int requestCodeEnable;
    private static final String APP_NAME = "Chat";
    private static final UUID MY_UUID = UUID.fromString("9fdc0cf4-7c47-11ea-bc55-0242ac130003");


    //For msg
    TextView xView, yView, zView;
    SensorManager sensorManager;
    Sensor accSensor;
    float currentX, currentY, currentZ;
    float lastX, lastY, lastZ;
    float xDiff, yDiff, zDiff;
    private boolean isAccAvailable, isNotFirst = false;
    private float shakeThreshold = 5f;
    Vibrator vibrator;
    /////////
    Button getLocation;
    int REQUEST_CODE_LOCATION = 1;
    TextView lat, lon, address;
    ResultReceiver resultReceiver;
    double finalLatitude = 0.0, finalLongitude = 0.0;
    double driverLatitude = 0.0, driverLongitude = 0.0;

    //////
    GoogleMap googleMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sendAlert = findViewById(R.id.sendAlert);
//        discover = findViewById(R.id.discover);
//        listView = findViewById(R.id.peerListView);
        listen = findViewById(R.id.listen);
        //status = findViewById(R.id.connectionStatus);
        carName = findViewById(R.id.carName);
        carDesc = findViewById(R.id.carDesc);
        carColor = findViewById(R.id.carColor);
        carNo = findViewById(R.id.carNo);
        distance = findViewById(R.id.distance);
        send = findViewById(R.id.sendButton);
        location = findViewById(R.id.location);
        carReason = findViewById(R.id.carReason);
        // writeMsg = findViewById(R.id.writeMsg);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(bluetoothIntent, requestCodeEnable);

            } else {
                bluetoothAdapter.disable();
            }
        }
        requestCodeEnable = 1;

        sendInit();
        //bluetoothMethod();
        listenDevices();
        listDevices();
        connectDevice();
//        initializeLocation();
        parseMessage("Toyota&&004-abcd&&white&&gatorlogo&&29.648643&&-82.3504657&&speedster");
        // initializeSensors();

    }

    private void sendInit() {
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string = "Toyota&004-abcd&&white&&gatorlogo&&29.648643&&-82.3504657&&speedster";
                //String string = String.valueOf(writeMsg.getText());
                sendReceive.write(string.getBytes());
            }
        });
    }

    private void connectDevice() {

        sendAlert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClientClass clientClass = new ClientClass(bluetoothDevices[0]);
                clientClass.start();
            }
        });
//        ClientClass clientClass = new ClientClass(bluetoothDevices[0]);
//        clientClass.start();
//        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                ClientClass clientClass = new ClientClass(bluetoothDevices[position]);
//                clientClass.start();
//
//                Toast.makeText(MainActivity.this, "Connecting", Toast.LENGTH_SHORT).show();
//            }
//
//        });
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_LISTENING:
                    //status.setText("Listening");
                    break;
                case STATE_CONNECTED:
                    //status.setText("Connected");
                    break;

                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    parseMessage(tempMsg);

                    carName.setText(tempMsg);
                    break;

            }
            return true;
        }
    });

    private void parseMessage(String msg) {
        // Split the string
        //  String string = "Toyota&&004-abcd&&white&&gatorlogo&&29.648643&&-82.3504657&&speedster";
        String[] split = msg.split("&&");
        // Set the textView
        carName.setText("car name: "+split[0]);
        carNo.setText("car no: "+split[1]);
        carColor.setText("car color: "+split[2]);
        carDesc.setText("car desc: "+split[3]);
        carReason.setText("car reason: "+split[6]);

        driverLatitude = Double.parseDouble(split[4]);
        System.out.println(driverLatitude);
        driverLongitude =Double.parseDouble(split[5]);
        initializeLocation();




    }

    public double CalculationByDistance(LatLng StartP, LatLng EndP) {
        int Radius = 6371;// radius of earth in Km
        double lat1 = StartP.latitude;
        double lat2 = EndP.latitude;
        double lon1 = StartP.longitude;
        double lon2 = EndP.longitude;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double valueResult = Radius * c;
        double km = valueResult / 1;
        DecimalFormat newFormat = new DecimalFormat("####");
        int kmInDec = Integer.valueOf(newFormat.format(km));
        double meter = valueResult % 1000;
        int meterInDec = Integer.valueOf(newFormat.format(meter));
        Log.i("Radius Value", "" + valueResult + "   KM  " + kmInDec
                + " Meter   " + meterInDec);

        return Radius * c;
    }

    private void listenDevices() {
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });
    }

    private void bluetoothMethod() {

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothAdapter == null) {
                    Toast.makeText(MainActivity.this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
                } else {
                    if (!bluetoothAdapter.isEnabled()) {
                        bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(bluetoothIntent, requestCodeEnable);

                    } else {
                        bluetoothAdapter.disable();
                    }
                }
            }
        });
    }

    private void listDevices() {

        Set<BluetoothDevice> bluetoothDeviceSet = bluetoothAdapter.getBondedDevices();
        String[] strings = new String[bluetoothDeviceSet.size()];
        bluetoothDevices = new BluetoothDevice[bluetoothDeviceSet.size()];
        int index = 0;
        if (bluetoothDeviceSet.size() > 0) {
            for (BluetoothDevice bluetoothDevice : bluetoothDeviceSet) {
                bluetoothDevices[index] = bluetoothDevice;
                strings[index] = bluetoothDevice.getName();
                index++;
            }
            //ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
            //listView.setAdapter(arrayAdapter);
        }
//        discover.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
//            }
//        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == requestCodeEnable) {
            if (requestCode == RESULT_OK) {
                Toast.makeText(this, "Enabled", Toast.LENGTH_SHORT).show();
            } else if (requestCode == RESULT_CANCELED) {
                Toast.makeText(this, "Enabling Cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;

        ServerClass() {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            while (socket == null) {
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                    e.printStackTrace();
                }
                if (socket != null) {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceive = new SendReceive(socket);
                    sendReceive.start();

                    break;
                }
            }

        }
    }

    private class ClientClass extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        ClientClass(BluetoothDevice device1) {
            device = device1;
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive = new SendReceive(socket);
            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    private class SendReceive extends Thread {
        BluetoothSocket bluetoothSocket;
        InputStream inputStream;
        OutputStream outputStream;

        public SendReceive(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream = tempIn;
            outputStream = tempOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /////////////
    public void initializeMap() {
        SupportMapFragment supportMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.maps);
        supportMapFragment.getMapAsync(this);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (finalLatitude != 0.0) {
            MarkerOptions markerOptions = new MarkerOptions();
            LatLng latLng = new LatLng(finalLatitude, finalLongitude);
            markerOptions.position(latLng);
            markerOptions.position(latLng);

            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
            googleMap.addMarker(markerOptions);

            LatLng latLng1 = new LatLng(driverLatitude, driverLongitude);

            MarkerOptions markerOptions1 = new MarkerOptions();
            markerOptions1.position(latLng1);
            markerOptions1.icon(BitmapDescriptorFactory.fromResource(R.drawable.baseline_warning_black_18dp));
            googleMap.addMarker(markerOptions1);


            double dist =  CalculationByDistance(latLng, latLng1);
            distance.setText(Double.toString(dist) + " km away");
        }

    }


    public void initializeLocation() {
//        getLocation = findViewById(R.id.getLocation);
//        lat = findViewById(R.id.lat);
//        lon = findViewById(R.id.lon);
//        address = findViewById(R.id.address);
        resultReceiver = new AddressResultReceiver(new Handler());
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_LOCATION);
        } else {
            getCurrentLocation();
        }
//        getLocation.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
//            }
//        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_LOCATION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getCurrentLocation() {
        final LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationServices.getFusedLocationProviderClient(MainActivity.this).requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                LocationServices.getFusedLocationProviderClient(MainActivity.this).removeLocationUpdates(this);
                if (locationResult != null && locationResult.getLocations().size() > 0) {
                    int latestLocationIndex = locationResult.getLocations().size() - 1;
                    finalLatitude = locationResult.getLocations().get(latestLocationIndex).getLatitude();
                    finalLongitude = locationResult.getLocations().get(latestLocationIndex).getLongitude();
                    ///set text
//                    lat.setText(String.format("Latitude: %s", finalLatitude));
//                    lon.setText(String.format("Latitude: %s", finalLongitude));

                    Location location = new Location("providerNA");
                    location.setLatitude(finalLatitude);
                    location.setLongitude(finalLongitude);
                    fetchAddress(location);

                    initializeMap();
                }

            }
        }, Looper.getMainLooper());
    }

    public void initializeSensors() {
//        xView = findViewById(R.id.xvalue);
//        yView = findViewById(R.id.yvalue);
//        zView = findViewById(R.id.zvalue);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            isAccAvailable = true;
        } else {
            xView.setText("Accelerometer not available");
            isAccAvailable = false;

        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        xView.setText(event.values[0] + "m/s2");
        yView.setText(event.values[1] + "m/s2");
        zView.setText(event.values[2] + "m/s2");

        currentX = event.values[0];
        currentY = event.values[1];
        currentZ = event.values[2];

        if (isNotFirst) {
            xDiff = Math.abs(lastX - currentX);
            yDiff = Math.abs(lastY - currentY);
            zDiff = Math.abs(lastZ - currentZ);

            if ((xDiff > shakeThreshold && yDiff > shakeThreshold) || (yDiff > shakeThreshold && zDiff > shakeThreshold) || (zDiff > shakeThreshold && xDiff > shakeThreshold)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(500);
                }
            }


        }
        lastX = currentX;
        lastY = currentY;
        lastZ = currentZ;
        isNotFirst = true;

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isAccAvailable) {
            sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isAccAvailable) {
            sensorManager.unregisterListener(this);
        }
    }

    private void fetchAddress(Location location) {
        Intent intent = new Intent(this, GeoAddress.class);
        intent.putExtra(Constants.RECEIVER, resultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);
        startService(intent);
    }


    private class AddressResultReceiver extends ResultReceiver {

        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            if (resultCode == Constants.SUCCESS_RESULT) {
                location.setText(resultData.getString(Constants.RESULT_DATA_KEY));

            } else {
                Toast.makeText(MainActivity.this, resultData.getString(Constants.RESULT_DATA_KEY), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
