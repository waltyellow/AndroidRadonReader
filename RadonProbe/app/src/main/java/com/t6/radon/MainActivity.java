package com.t6.radon;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{


    java.util.UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    BluetoothAdapter bluetoothAdapter;
    Button sendCommand;
    Button scanVisible;
    Button getPaired;
    Switch bluetoothSwitch;
    EditText commandLine;
    TextView statusBox;
    TextView locationFound;
    TextView statusColor;
    ConnectThread mConnectThread;
    ConnectedThread mConnectedThread;
    TextView locationView;
    ListView pairedListView;
    ArrayAdapter<String> mArrayAdapter;
    ListView listView;
    protected String root;
    private File file;
    private String connectTarget;
    public static String[] mLine;

    String filePath;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;
    private String mLastUpdateTime;
    private FileReader mFileReader;
    private List<String[]> entries;
    private long lastReadDate = 0;
    String deviceName;

    BufferedWriter bufferedFileWriter;
    ArrayAdapter<String> pairedDevicesArrayAdapter;
    public final int  REQUEST_ENABLE_BT = 1;

    private com.google.android.gms.location.LocationListener mLocationListener = new com.google.android.gms.location.LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            mCurrentLocation = location;
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            locationView.setText("Long:"+mCurrentLocation.getLongitude()+ ",Lat: " +mCurrentLocation.getLatitude());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // create file for which to save motion data


        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        sendCommand = (Button) findViewById(R.id.sendCommand);
        scanVisible = (Button) findViewById(R.id.scanner);
        getPaired = (Button) findViewById(R.id.pairedButton);
        locationView = (TextView) findViewById(R.id.locationView);
        locationFound = (TextView) findViewById(R.id.locationFound);
        statusColor = (TextView) findViewById(R.id.statusColor);
        pairedListView = (ListView) findViewById(R.id.listView);
        commandLine = (EditText) findViewById(R.id.editText);
        //listView = (ListView) findViewById(R.id.listView);
        sendCommand.setVisibility(View.INVISIBLE);
        commandLine.setVisibility(View.INVISIBLE);
        statusBox = (TextView) findViewById(R.id.statusBox);
        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // migrated
        buildGoogleApiClient();
        mGoogleApiClient.connect();
        root = Environment.getExternalStorageDirectory().toString();
        filePath = root + "/DCIM/records.csv";

        statusColor.setBackgroundColor(Color.GRAY);
        //read the file to initialize
        try {
            mFileReader = new FileReader(filePath);
        } catch (java.io.FileNotFoundException e) {
            try {
                String[] firstRow = {"TimeStamp" ,"Sound_sensor", "Moisture", "Light", "UV", "Radon", "HomeLocation"};
                FileWriter mFileWriter = new FileWriter(filePath);
                CSVWriter mCSVWriter = new CSVWriter(mFileWriter);
                mCSVWriter.writeNext(firstRow);
                mCSVWriter.close();
                mFileWriter.close();
            } catch (Exception e2) {
                e.printStackTrace();
            }
        }
        //now let's read again to make sure that we got the file read
        try {
            mFileReader = new FileReader(filePath);
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
        }
        CSVReader mCSVReader = new CSVReader(mFileReader);

        //read CSV into entries list
        try {
            entries = mCSVReader.readAll();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        if (entries.size() <= 1){
            lastReadDate = -1;
        }else{
            lastReadDate = Long.parseLong((entries.get(entries.size() - 1))[0]);

        }

        //back to BT
        initializePairedDeviceList();
        setScanVisibleAction();
        setGetPairedAction();
    }

    private void initializePairedDeviceList(){
        // Initialize array adapters. for already paired device
        pairedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);

        //sets what to do with each device added to the list
        pairedListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
                // Cancel discovery because it's costly and we're about to connect
                bluetoothAdapter.cancelDiscovery();
                // Get the device MAC address, which is the last 17 chars in the View
                String deviceInfo = ((TextView) v).getText().toString();
                connectTarget = deviceInfo.substring(deviceInfo.length() - 17);
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(connectTarget);
                System.out.println(device.getAddress());
                BluetoothSocket socket = null;

                new ConnectThread(device).start();
                //                mConnectedThread.write("STRAT");
                //  System.out.println("Directly written");

            }
        });
    }

    //update the list of paired devices
    private void updatePairedDeviceList(){
        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        pairedDevicesArrayAdapter.clear();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = "No device";
            pairedDevicesArrayAdapter.add(noDevices);
        }
    }

    public void setScanVisibleAction(){
        scanVisible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothAdapter.startDiscovery();
                System.out.println("startD");
                System.out.println(mCurrentLocation.getLongitude() + "," + mCurrentLocation.getLatitude());
            }
        });
    }

    public void setGetPairedAction(){
        getPaired.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pairedListView.setVisibility(View.VISIBLE);
                System.out.println("startP");
                System.out.println(mCurrentLocation.getLongitude() + "," + mCurrentLocation.getLatitude());
                updatePairedDeviceList();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            statusBox.setText("Status: Connecting to " + device.getName());
            statusColor.setBackgroundColor(Color.YELLOW);
            deviceName = device.getName();
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            //manageConnectedSocket(mmSocket);
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /**
     * Beginning of the borrowed content
     *
     */

    // Creates LocationRequest object per the Android developer instructions
    // TODO: Potentially change parameters
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(3000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    // Starts location updates (based on method from Android dev page)
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, mLocationListener);
    }

    // Stops location updates (based on method from Android dev page
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mLocationListener);
    }

    // Next three methods needed to build Google API Client
    @Override
    public void onConnected(Bundle connectionHint) {
        System.out.println("CONNECTION SUCCEEDED");
        locationFound.setBackgroundColor(Color.GREEN);
        createLocationRequest();
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        locationFound.setBackgroundColor(Color.YELLOW);
        stopLocationUpdates();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        locationFound.setBackgroundColor(Color.LTGRAY);
        System.out.println("CONNECTION FAILED");
    }

    // Method to setup GoogleApiClient given from the Android Developer page
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void writeIncomingData(String newData) throws java.io.FileNotFoundException, java.io.IOException {
        //to be optimized, everything is taken out here from the old file, modified and rewritten. It is only efficient for small amounts of rows here
        //If we have time we could optimize more here, but for our application it's enough
        newData = newData + ", HL";
        String[] newRow = newData.split("\\,");
        newRow[newRow.length - 1] = "("+ mCurrentLocation.getLongitude() +"," + mCurrentLocation.getLatitude() + ")";
        Long newTimeStamp = Long.parseLong(newRow[0]);
        statusBox.setText("Status: Received(t="+newTimeStamp+")");
        if (newTimeStamp > lastReadDate){
            FileWriter mFileWriter = new FileWriter(filePath);
            CSVWriter mCSVWriter = new CSVWriter(mFileWriter);
            entries.add(newRow);
            mCSVWriter.writeAll(entries);
            mCSVWriter.close();
            lastReadDate = newTimeStamp;
        }
        //discard out-of-order data, or old data
    }

    /**
     * End of the borrowed content
     *
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        BufferedReader bufferedReader;
        BufferedWriter bufferedWriter;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            bufferedReader = new BufferedReader(new InputStreamReader(mmInStream));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(mmOutStream));
            statusBox.setText("Status: Connected to device" + deviceName);
            statusColor.setBackgroundColor(Color.BLUE);
            //change how the activity looks
            pairedListView.setVisibility(View.INVISIBLE);
            sendCommand.setVisibility(View.VISIBLE);
            commandLine.setVisibility(View.VISIBLE);

            sendCommand.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    statusBox.setText("Status: Command sent("+commandLine.getText().toString()+")");
                    System.out.println(commandLine.getText().toString());
                    System.out.println("clicked");
                    System.out.println(mCurrentLocation.getLongitude() + "," + mCurrentLocation.getLatitude());
                    write(commandLine.getText().toString());
                }
            });
        }

        public void run() {
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Send the obtained bytes to the UI activity
                    String line = bufferedReader.readLine();
                    //System.out.println(line);
                    writeIncomingData(line);
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String s) {
            try {
                bufferedWriter.write(s);
                bufferedWriter.flush();
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }



}
