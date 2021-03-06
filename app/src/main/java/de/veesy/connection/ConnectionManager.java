package de.veesy.connection;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Set;
import java.util.UUID;

import de.veesy.contacts.Contact;
import de.veesy.util.Constants;


/**
 * Created by Martin on 25.10.2017.
 * veesy.de
 * hs-augsburg
 */
public class ConnectionManager extends Observable {

    //region Class members

    private static final String TAG = "ConnectionManager";
    private static final UUID VEESY_UUID = UUID.fromString("54630abc-3b78-4cf2-9f0d-0b844924cf36");

    private static ConnectionManager unique = null;
    private static BluetoothAdapter btAdapter = null;

    private static boolean btClientMode_flag = false;

    private static String btName_device;
    private static String btName_prefix = "[veesy]";
    private static String btName_splitter = "-";
    private static boolean btNameCorrect_flag = false;

    private BluetoothAcceptorThread btAcceptorThread;

    private BluetoothConnectorThread btConnectorThread;
    private BluetoothDevice btConnectedDevice;

    private BluetoothConnectedThread btConnectedThread;

    private CountDownTimer btDiscover_countDownTimer;
    private CountDownTimer btConnection_timeOutHandler;
    private CountDownTimer btDataTransmission_errorHandler;

    private static ArrayList<BluetoothDevice> availableVeesyBTDevices;
    private static ArrayList<BluetoothDevice> originallyBondedBTDevices;

    private IntentFilter actionFoundFilter, actionBondStateFilter, actionScanModeChangedFilter, actionDiscoveryFinishedFilter, actionDiscoveryStartedFilter, actionPairingRequestFilter;

    private boolean btConnectorThread_runningFlag = false;


    private static String originalDeviceName = "Huawei Watch 2";

    private Contact receivedContact = null;
    private Contact sendContact = null;


    //endregion

    //region Initializing

    // Singleton Pattern to ensure only one instance of ConnectionManager is used
    private ConnectionManager() {
        Log.d(TAG, "Initializing ConnectionManager");
        if (!initBluetooth()) {
            Log.e(TAG, "bluetooth not supported");
            return;
        }
        originalDeviceName = device_getOriginalDeviceName();
        btName_device = originalDeviceName;
        availableVeesyBTDevices = new ArrayList<>();
        originallyBondedBTDevices = new ArrayList<>();
        originallyBondedBTDevices.addAll(btAdapter.getBondedDevices());
        initIntents();
    }

    public static ConnectionManager instance() {
        if (unique != null) Log.d(TAG, "ConnectionManager is already initialized");
        if (unique == null) {
            unique = new ConnectionManager();
        }
        return unique;
    }

    public void finish() {
        unique = null;
        Log.d(TAG, "Destroying ConnectionManager and executing shutdown");
    }

    //endregion

    //region Bluetooth - Initializing

    // initialize BluetoothAdapter
    private boolean initBluetooth() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            // Bluetooth is not supported
            Log.e(TAG, "Bluetooth is not supported on this device");
            return false;
        }
        Log.d(TAG, "Bluetooth initialized");
        return true;
    }

    //endregion

    // region Bluetooth - Renaming & Veesy environment

    /**
     * This method is renaming the bluetooth device
     * <p>
     * Therefore, Bluetooth has to be enabled (this needs some time)
     * First, we check, if the name is already correct in terms of a predefined style
     * if the name has the prefix [veesy] its correct
     * if not, we start a timeHandler which is delayed with 1s/  500ms
     * this handler waits for bluetooth to activate, and for the new name
     * to sink in (this also takes some time)
     * <p>
     * if something goes wrong, this method determines after 10s
     */
    private void device_renameTo(String name, boolean setBackOriginalName, boolean changeName) {
        if (btAdapter != null) {
            if (!isVeesyDevice(btAdapter.getName()) || setBackOriginalName || changeName) {

                Log.d(TAG, "Current device name is:      " + btAdapter.getName());
                Log.d(TAG, "Setting back orignal name:   " + setBackOriginalName);
                Log.d(TAG, "Trying to rename device to:  " + name);
                enableBluetooth();
                final long timeOutMillis = System.currentTimeMillis() + 10000;
                final Handler timeHandler = new Handler();
                final String newName = name;
                final long delayMillis = 500;
                btNameCorrect_flag = false;
                final boolean setBackNameAndShutdown = setBackOriginalName;
                timeHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!btNameCorrect_flag) {
                            btAdapter.setName(newName);
                            if (btAdapter.getName().equals(newName)) {
                                Log.d(TAG, "Renaming bluetooth device . . . done: " + btAdapter.getName());
                                btNameCorrect_flag = true;
                                if (!setBackNameAndShutdown) {
                                    setChanged();
                                    notifyObservers(MESSAGE.RENAMED_DEVICE);
                                } else {
                                    Log.d(TAG, "Renamed device and ready to shutdown");
                                    setChanged();
                                    notifyObservers(MESSAGE.READY_TO_SHUTDOWN);
                                }
                            }
                            if (!(btAdapter.getName().equals(newName)) && System.currentTimeMillis() < timeOutMillis) {
                                timeHandler.postDelayed(this, delayMillis);
                                if (!btAdapter.isEnabled())
                                    Log.d(TAG, "Renaming bluetooth device . . . waiting for Bluetooth to enable");
                                else
                                    Log.d(TAG, "Renaming bluetooth device . . . waiting for name to sink in");
                            }
                        }
                    }
                }, delayMillis);
            }
        } else {
            btNameCorrect_flag = true;
            Log.d(TAG, "Device is already named correctly");
            setChanged();
            notifyObservers(MESSAGE.ALREADY_NAMED_CORRECTLY);
        }
    }

    public boolean checkName() {
        String name = btAdapter.getName();
        if (isVeesyDevice(name)) return true;
        else device_renameTo(btName_prefix + btName_splitter + btName_device, false, false);
        return false;
    }

    /**
     * TODO deviceName is sometimes null :/
     * <p>
     * this method provides a possibility to track if a discovered BT device is part of the veesy
     * environment this method determines, whether the name of a Device could be split by
     * btName_splitter, and if, is the first part btName_prefix?
     */
    private static boolean isVeesyDevice(String deviceName) {
        boolean namedCorrectly = false;
        try {
            return deviceName.split(btName_splitter)[0].equals(btName_prefix);
        } catch (Exception e) {
            Log.d(TAG, "Trying to split name failed: " + deviceName);
            e.printStackTrace();
        }
        return namedCorrectly;
    }

    /*private static boolean isVeesyDevice(BluetoothDevice device) {
        return device != null && isVeesyDevice(device.getName());
    }*/


    //endregion

    //region Bluetooth - Enable & Discover

    /**
     * this method enables the bluetooth and sets it visible for some time
     * <p>
     * in order to start the User input dialog, we need to pass Context
     */
    public boolean startBluetoothIntent(Activity activity, int visibility_time) {
        // uncomment, commented to debug
        if (btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Log.d(TAG, "Already discoverable...");

            setChanged();
            notifyObservers(MESSAGE.ALREADY_DISCOVERABLE);

            return true;
        }
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, visibility_time);
        activity.startActivity(discoverableIntent);
        return false;
    }

    private static void enableBluetooth() {
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
        }
    }

    /**
     * this method searches for other Bluetooth devices
     */
    public void discoverBluetoothDevices() {
        cancelDiscovery();
        availableVeesyBTDevices.clear();
        if (btAdapter.startDiscovery()) {
            startDisocveryEndingThread();
            Log.d(TAG, " . . . . starting discovery");
        }
    }

    private void cancelDiscovery() {
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
            Log.d(TAG, " . . . . stopping discovery");
        }
    }

    private void startDisocveryEndingThread() {
        if (btDiscover_countDownTimer != null) btDiscover_countDownTimer.cancel();
        btDiscover_countDownTimer = new CountDownTimer(20000, 1000) {
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                cancelDiscovery();
            }
        }.start();
    }

    //endregion

    //region Bluetooth - Pairing Devices & Starting Connection

    /**
     * btPairWithDevice is called and tries to pair with the device
     * if the devices can be paired, the BroadcastReceiver will receive it
     */
    private void btPairWithDevice() {
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        Log.d(TAG, "Trying to create bond with " + btConnectedDevice.getName());
        btConnectedDevice.createBond();
    }


    public void btConnectToDevice(String deviceName) {
        //Achtung hier muss man den [veesy] zusatz wieder dazu tun
        if (!isVeesyDevice(deviceName)) deviceName = btName_prefix + btName_splitter + deviceName;
        for (BluetoothDevice d : availableVeesyBTDevices) {
            if (d.getName().equals(deviceName)) {
                btConnectedDevice = d;
                btEstablishConnection();
                break;
            }
        }
    }

    private void btEstablishConnection() {
        Log.d(TAG, "Starting connection attempt to: " + btConnectedDevice.getName() + " from: " + btAdapter.getName());
        if (btConnectedDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            btPairWithDevice();
        } else {
            Log.d(TAG, "Devices are already paired");
            setChanged();
            notifyObservers(MESSAGE.ALREADY_PAIRED);
            btStartConnection();
        }
    }

    public void retryPairing() {
        btConnectToDevice(btConnectedDevice.getName());
    }

    //ToDo - macht iwie dasselbe
    public void btReConnect() {
        if (btConnectedDevice != null) {
            btEstablishConnection();
        }
    }

    //endregion

    //region Bluetooth - Connection

    private void btStartConnection() {
        /*
         * wird tatsächlich 2 mal aufgerufen, da BOND_BONDED vom broadcast Receiver 2x empfangen wird
         */
        if (btConnectedDevice != null && !btConnectorThread_runningFlag) {
            Log.d(TAG, "Starting Connection Attempt with: " + btConnectedDevice.getName());
            btConnectorThread = new BluetoothConnectorThread(btConnectedDevice);
            btConnectorThread.start();
        }
    }


    /**
     * This thread listens for incoming connections and runs until a connection
     * is accepted or cancelled
     */

    private class BluetoothAcceptorThread extends Thread {
        private final BluetoothServerSocket btServerSocket;

        BluetoothAcceptorThread() {
            Log.d(TAG, "BluetoothAcceptorThread started");
            // Use a temporary object that is later assigned to btServerSocket
            // because btServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // VEESY_UUID is the apps UUID string, also used by the client code.
                tmp = btAdapter.listenUsingRfcommWithServiceRecord(Constants.appName, VEESY_UUID);
                Log.d(TAG, "BluetoothAcceptorThread: ServerSocket runing with UUID: " + VEESY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "BluetoothAcceptorThread: ServerSocket's listen() method failed", e);
            }
            btServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket btSocket;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    Log.d(TAG, "BluetoothAcceptorThread: ServerSocket started.....");
                    btSocket = btServerSocket.accept();
                    Log.d(TAG, "BluetoothAcceptorThread: ServerSocket accepted connection");
                    btClientMode_flag = true;
                    Log.d(TAG, "Device is acting as client" + btClientMode_flag);
                } catch (IOException e) {
                    Log.e(TAG, "BluetoothAcceptorThread: ServerSocket's accept() method failed", e);
                    break;
                }

                if (btSocket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    btManageConnection(btSocket);
                    closeServerSocket();
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        void closeServerSocket() {
            Log.d(TAG, "BluetoothAcceptorThread: ServerSocket shut down....");
            try {
                btServerSocket.close();
                Log.d(TAG, "BluetoothAcceptorThread: ServerSocket closed");
            } catch (IOException e) {
                Log.e(TAG, "BluetoothAcceptorThread: ServerSocket could not be closed", e);
            }
        }
    }

    /**
     * This thread tries to establish an outgoing connection
     * either it fails, or it succeeds
     */

    private class BluetoothConnectorThread extends Thread {
        private BluetoothSocket btSocket;


        BluetoothConnectorThread(BluetoothDevice device) {
            Log.d(TAG, "BluetoothConnectorThread started");
            btConnectedDevice = device;
            //btConnectedDeviceUUID = uuid;
            btConnectorThread_runningFlag = true;
        }

        public void run() {
            BluetoothSocket tmp = null;
            Log.i(TAG, " BluetoothConnectorThread running......");
            try {
                Log.d(TAG, "BluetoothConnectorThread: Trying to create RfcommSocket using: " + VEESY_UUID);
                tmp = btConnectedDevice.createRfcommSocketToServiceRecord(VEESY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "BluetoothConnectorThread: Could not create RfcommSocket", e);
            }
            btSocket = tmp;

            // We need to cancel discovery due to slowing down the connection
            if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();

            try {
                setChanged();
                notifyObservers(MESSAGE.CONNECTING);
                btSocket.connect();
                Log.d(TAG, "BluetoothConnectorThread: Connection established !!!");
                btClientMode_flag = false;
                Log.d(TAG, "Device is acting as Server");
                btManageConnection(btSocket);
            } catch (IOException e) {
                closeBluetoothSocket();
                Log.d(TAG, "BluetoothConnectorThread: Could not connect to UUID: " + VEESY_UUID);
                if (!btIsInClientMode()) {
                    setChanged();
                    notifyObservers(MESSAGE.CONNECTION_ERROR);
                }
            }
        }

        void closeBluetoothSocket() {
            Log.d(TAG, "BluetoothConnectorThread: Socket shut down....");
            try {
                btSocket.close();
                Log.d(TAG, "BluetoothConnectorThread: Socket closed");
            } catch (IOException e) {
                Log.e(TAG, "BluetoothConnectorThread: Socket could not be closed", e);
            }
        }
    }

    /**
     * Start to listen to Connection Attempts via Bluetooth
     */
    public synchronized void btStartListeningForConnectionAttempts() {

        // if any thread is trying to establish a connection, kill it
        if (btConnectorThread != null) {
            btConnectorThread.closeBluetoothSocket();
            btConnectorThread_runningFlag = false;
            btConnectorThread = null;
        }
        if (btAcceptorThread == null) {
            btAcceptorThread = new BluetoothAcceptorThread();
            btAcceptorThread.start();
        }
        Log.d(TAG, "Listening for bluetooth connection attempts . . . . . ");
    }


    private void btManageConnection(BluetoothSocket btSocket) {
        Log.d(TAG, "Starting connection . . . ");
        btConnectedThread = new BluetoothConnectedThread(btSocket);
        btConnectedThread.start();
    }

    //endregion

    //region Bluetooth - Handling connected Devices & Datatransmission

    private class BluetoothConnectedThread extends Thread {
        private final BluetoothSocket btSocket;
        private final ObjectInputStream btObjectStream_in;
        private final ObjectOutputStream btObjectStream_out;

        //initializing input and output stream
        BluetoothConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread started");
            btSocket = socket;
            ObjectInputStream tmp_in = null;
            ObjectOutputStream tmp_out = null;
            try {
                tmp_out = new ObjectOutputStream(btSocket.getOutputStream());
                tmp_out.flush();
                tmp_in = new ObjectInputStream(btSocket.getInputStream());
            } catch (IOException e) {
                Log.e(TAG, "Object stream init error");
                e.printStackTrace();
            }
            btObjectStream_in = tmp_in;
            btObjectStream_out = tmp_out;
        }

        public void run() {
            setChanged();
            notifyObservers(MESSAGE.CONNECTED);
            Log.d(TAG, "ConnectedThread: waiting for InputStream: ");
            while (true) {
                try {
                    receivedContact = (Contact) btObjectStream_in.readObject();
                    if (btClientMode_flag) {
                        setChanged();
                        notifyObservers(MESSAGE.RESPOND_AS_CLIENT);
                        stopConnectionTimeOutHandler();
                    } else {
                        stopDataTransmissionHandler();
                        setChanged();
                        notifyObservers(MESSAGE.DATA_TRANSMISSION_SUCCESSFUL);
                        stopConnectionTimeOutHandler();
                    }
                    Log.d(TAG, "ConnectedThread: InputStream: " + receivedContact.toString());
                } catch (IOException e) {
                    Log.e(TAG, "ConnectedThread: IO Error while reading InputStream", e);
                    break;
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "ConnectedThread: Some Error while reading InputStream", e);
                    e.printStackTrace();
                    break;
                }
            }
        }

        void write(Contact contact) {
            Log.d(TAG, "ConnectedThread: write-method called");
            try {
                Log.d(TAG, "ConnectedThread: Writing to outputStream . . . ");
                btObjectStream_out.writeObject(contact);
                Log.d(TAG, "ConnectedThread: Writing to outputStream . . . done");
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: Error while writing to OutputStream", e);
            }
        }

        void closeConnection() {
            if (btSocket == null) return;
            try {
                btSocket.close();
                Log.d(TAG, "ConnectedThread: Socket closed");
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: Socket could not be closed", e);
            }
        }
    }

    public void btCloseConnection() {
        if (btConnectedThread != null) btConnectedThread.closeConnection();
    }

    public void btSendData() {
        Log.d(TAG, "Trying to send own VK");
        if (btConnectedThread != null) btConnectedThread.write(sendContact);
    }


    private void stopDataTransmissionHandler() {
        if (btDataTransmission_errorHandler != null) {
            btDataTransmission_errorHandler.cancel();
        }
        btDataTransmission_errorHandler = null;
    }


    public void startConnectionTimeOutHandler() {
        btConnection_timeOutHandler = new CountDownTimer(12000, 1000) {
            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                Log.d(TAG, "Connection Time Out");
                setChanged();
                notifyObservers(MESSAGE.DATA_TRANSMISSION_FAILED);
                stopDataTransmissionHandler();
            }
        }.start();
    }

    private void stopConnectionTimeOutHandler() {
        if (btConnection_timeOutHandler != null) {
            Log.d(TAG, "Stopping Connection time out handler");
            btConnection_timeOutHandler.cancel();
        }
        btConnection_timeOutHandler = null;
    }

    //endregion

    //region Getter & Settter


    public List<String> btGetAvailableDeviceNames() {
        List<String> list = new ArrayList<>();
        for (BluetoothDevice d : availableVeesyBTDevices) {
            list.add(getVeesyDeviceName(d.getName()));
        }
        return list;
    }

    public boolean btIsInClientMode() {
        return btClientMode_flag;
    }

    public Contact getReceivedContact() {
        return receivedContact;
    }

    public void setSendContact(Contact contact) {
        this.sendContact = contact;
    }

    public void device_setVeesyName() {
        Log.d(TAG, "device_setVeesyName . . . .");
        String name = originalDeviceName;
        if (sendContact != null && !sendContact.isEmpty()) name = sendContact.getFullName();
        device_renameTo(btName_prefix + btName_splitter + name, false, false);
    }

    public void device_changeName() {
        Log.d(TAG, "Changing name . . . .");
        device_renameTo(btName_prefix + btName_splitter + sendContact.getFullName(), false, true);
    }

    private String device_getOriginalDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    /**
     * This method is required for all devices running API23+ Android must programmatically check
     * the permissions for bluetooth. Putting the proper permissions in the manifest is not enough.
     */
    public void btCheckPermissions(Activity activity) {
        int permissionCheck = activity.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
        if (permissionCheck != 0) {
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
        }
    }


    /**
     * This method should be accessible via the Settings menu
     * because if an user does not want his device to be named
     * "[veesy]- ..", he has the ability to set back the name     *
     */
    public void setBackOriginalDeviceName() {
        device_renameTo(originalDeviceName, true, false);
    }


    public boolean isAlreadyPaired() {
        return btConnectedDevice != null
                && btConnectedDevice.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    /**
     * This method tries to return the "real" device name
     * [veesy]-NAME --> NAME
     * <p>
     * if something goes wrong, it will return parameter originalDeviceName
     */
    private String getVeesyDeviceName(String deviceName) {
        try {
            String s[] = deviceName.split(btName_splitter);
            deviceName = s[1];
        } catch (Exception e) {
            Log.d(TAG, "getVeesyDeviceName failed");
        }
        return deviceName;
    }

    public void unpairAllDevices(boolean deleteAll) {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (deleteAll) {
                    removeBondFromDevice(device);
                    continue;
                }

                if (!originallyBondedBTDevices.contains(device)) {
                    //if (!device.getName().equals("z3")) {
                    removeBondFromDevice(device);
                    break;
                }
            }
        }
    }

    private void removeBondFromDevice(BluetoothDevice device) {
        try {
            Method m = device.getClass()
                    .getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            Log.d(TAG, "Removed device: " + device.getName());
        } catch (Exception e) {
            Log.e(TAG, "Removing has been failed. . . . " + e.getMessage());
        }
    }

    // endregion

    //region Bluetooth - BroadcastReceiver


    private void initIntents() {
        actionFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        actionBondStateFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        actionScanModeChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        actionDiscoveryFinishedFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        actionDiscoveryStartedFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        actionPairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
    }


    public void registerReceiver(Activity activity) {
        activity.registerReceiver(btReceiver_ACTION_FOUND, actionFoundFilter);
        activity.registerReceiver(btReceiver_BOND_STATE, actionBondStateFilter);
        activity.registerReceiver(btReceiver_SCAN_MODE, actionScanModeChangedFilter);
        activity.registerReceiver(btReceiver_ACTION_DISCOVERY_FINISHED, actionDiscoveryFinishedFilter);
        activity.registerReceiver(btReceiver_ACTION_DISCOVERY_STARTED, actionDiscoveryStartedFilter);
        activity.registerReceiver(btReceiver_ACTION_PAIRING_REQUEST, actionPairingRequestFilter);
    }

    public void unregisterReceiver(Activity activity) {
        if (btAdapter != null) {
            btAdapter.cancelDiscovery();
        }
        // Unregister broadcast listeners
        activity.unregisterReceiver(btReceiver_ACTION_FOUND);
        activity.unregisterReceiver(btReceiver_BOND_STATE);
        activity.unregisterReceiver(btReceiver_SCAN_MODE);
        activity.unregisterReceiver(btReceiver_ACTION_DISCOVERY_FINISHED);
        activity.unregisterReceiver(btReceiver_ACTION_DISCOVERY_STARTED);
        activity.unregisterReceiver(btReceiver_ACTION_PAIRING_REQUEST);
    }

    private final BroadcastReceiver btReceiver_ACTION_PAIRING_REQUEST = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action != null) {
                switch (action) {
                    // a new, unpaired device was found
                    case BluetoothDevice.ACTION_PAIRING_REQUEST:
                        String deviceName = device.getName();
                        Log.d(TAG, "Pairing Request Received from: " + deviceName);
                }
            }
        }
    };

    private final BroadcastReceiver btReceiver_ACTION_DISCOVERY_STARTED = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        Log.d(TAG, " . . . . discovery started");
                        setChanged();
                        notifyObservers(MESSAGE.START_DISCOVERING);
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver btReceiver_ACTION_DISCOVERY_FINISHED = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        Log.d(TAG, " . . . . discovery stopped");
                        if (btDiscover_countDownTimer != null) btDiscover_countDownTimer.cancel();
                        setChanged();
                        notifyObservers(MESSAGE.STOP_DISCOVERING);
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver btReceiver_ACTION_FOUND = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action != null) {
                switch (action) {
                    // a new, unpaired device was found
                    case BluetoothDevice.ACTION_FOUND:
                        String deviceName = device.getName();
                        String deviceHardwareAddress = device.getAddress();
                        if (isVeesyDevice(deviceName)) {
                            Log.d(TAG, "BroadcastReceiver: Veesy-Device found: " + deviceName + "; MAC " + deviceHardwareAddress);
                            if (!availableVeesyBTDevices.contains(device)) {
                                availableVeesyBTDevices.add(device);
                                setChanged();
                                notifyObservers(MESSAGE.DEVICE_FOUND);
                            }
                        }
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver btReceiver_BOND_STATE = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "btReceiver_BOND_STATE onReceive: " + action);
            if (action != null) {
                if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    //3 cases:
                    //case1: bonded already
                    switch (device.getBondState()) {
                        // already bonded
                        case BluetoothDevice.BOND_BONDED:
                            Log.d(TAG, "BroadcastReceiver: btConnectedDevice BOND_BONDED");
                            setChanged();
                            notifyObservers(MESSAGE.PAIRED);
                            //if (device.equals(btConnectedDevice))
                            btStartConnection();
                            break;
                        // breaking the bond
                        case BluetoothDevice.BOND_NONE:
                            Log.d(TAG, "BroadcastReceiver: btConnectedDevice BOND_NONE");
                            setChanged();
                            notifyObservers(MESSAGE.NOT_PAIRED);
                            break;
                        // creating a bond
                        case BluetoothDevice.BOND_BONDING:
                            setChanged();
                            notifyObservers(MESSAGE.PAIRING);
                            Log.d(TAG, "BroadcastReceiver: btConnectedDevice BOND_BONDING");
                            break;
                    }
                }
            }
        }
    };

    private final BroadcastReceiver btReceiver_SCAN_MODE = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                int msg = -1;
                switch (mode) {
                    //Device is in Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "BroadcastReceiver: Discoverability Enabled.");
                        msg = MESSAGE.DISCOVERABILITY_ON;
                        break;
                    //Device is NOT in discoverable mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "BroadcastReceiver: Discoverability Enabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "BroadcastReceiver: Discoverability Disabled. Not able to receive connections.");
                        msg = MESSAGE.DISCOVERABILITY_OFF;
                        break;
                }
                setChanged();
                notifyObservers(msg);
            }
        }
    };
    // endregion
}
