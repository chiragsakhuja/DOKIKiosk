package com.mobilecomputing.doki.dokikiosk;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public class Home extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private TextView bluetoothStatus;
    private TextView mainMessage;
    private boolean serverRunning = false;
    private AcceptThread serverSocketThread;
    private Handler bluetoothHandler, mainHandler;
    private ServerChannel serverUpdate;

    private final static int MESSAGE_RECEIVED = 1;
    private final static int UPDATE_BLUETOOTH_STATUS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set up handlers for display elements
        bluetoothStatus = (TextView) findViewById(R.id.bluetooth_status);
        mainMessage = (TextView) findViewById(R.id.main_message);

        mainHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_RECEIVED) {
                    mainMessage.setText(new String((byte[]) msg.obj));
                }
            }
        };

        bluetoothHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == UPDATE_BLUETOOTH_STATUS) {
                    bluetoothStatus.setText((String) msg.obj);
                }
            }
        };

        // Set up bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            bluetoothStatus.setText("Sorry, this device does not support Bluetooth");
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }
        }

        serverSocketThread = new AcceptThread(bluetoothAdapter);
        serverSocketThread.start();
        serverRunning = true;

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (serverRunning == false) {
                    serverSocketThread = new AcceptThread(bluetoothAdapter);
                    serverSocketThread.start();
                } else {
                    serverSocketThread.cancel();
                }

                serverRunning = !serverRunning;
            }
        });

        serverUpdate = new ServerChannel(getResources().getString(R.string.server_url) + "/update");
        Button register_button = (Button) findViewById(R.id.register_button);
        register_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final JSONObject kioskInfo = new JSONObject();
                try {
                    String macAddress = android.provider.Settings.Secure.getString(getApplicationContext().getContentResolver(), "bluetooth_address");
                    kioskInfo.put("name", R.string.kiosk_name);
                    kioskInfo.put("mac", macAddress);
                    serverUpdate.postData(kioskInfo);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
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

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private final String NAME = "DOKI Kiosk";
        private final UUID MY_UUID = new UUID(0x0000000000000000L, 0xdeadbeef0badcafeL);

        public AcceptThread(BluetoothAdapter mBluetoothAdapter) {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;

            bluetoothHandler.obtainMessage(UPDATE_BLUETOOTH_STATUS, -1, -1, "Waiting for connection").sendToTarget();
        }

        public void run() {
            BluetoothSocket socket;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        break;
                    }
                    break;
                }
            }
        }

        public void manageConnectedSocket(BluetoothSocket socket) {
            bluetoothHandler.obtainMessage(UPDATE_BLUETOOTH_STATUS, -1, -1, "Waiting for message").sendToTarget();
            ConnectedThread thread = new ConnectedThread(socket);
            thread.start();
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() {
            try {
                bluetoothHandler.obtainMessage(UPDATE_BLUETOOTH_STATUS, -1, -1, "Server not running").sendToTarget();
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }

        private class ConnectedThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final InputStream mmInStream;
            private final OutputStream mmOutStream;

            public ConnectedThread(BluetoothSocket socket) {
                mmSocket = socket;
                InputStream tmpIn = null;
                OutputStream tmpOut = null;

                // Get the input and output streams, using temp objects because
                // member streams are final
                try {
                    tmpIn = socket.getInputStream();
                    tmpOut = socket.getOutputStream();
                } catch (IOException e) {
                }

                mmInStream = tmpIn;
                mmOutStream = tmpOut;
            }

            public void run() {
                byte[] buffer = new byte[1024];  // buffer store for the stream
                int bytes; // bytes returned from read()

                // Keep listening to the InputStream until an exception occurs
                while (true) {
                    try {
                        // Read from the InputStream
                        bytes = mmInStream.read(buffer);
                        // Send the obtained bytes to the UI activity
                        mainHandler.obtainMessage(MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                        bluetoothHandler.obtainMessage(UPDATE_BLUETOOTH_STATUS, -1, -1, "Message received").sendToTarget();
                    } catch (IOException e) {
                        break;
                    }
                }
            }

            /* Call this from the main activity to shutdown the connection */
            public void cancel() {
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}