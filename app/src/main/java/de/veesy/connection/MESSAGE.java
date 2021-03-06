package de.veesy.connection;

/**
 * Created by Martin on 08.11.2017.
 * veesy.de
 * hs-augsburg
 */

public final class MESSAGE {

    public static final int DEVICE_FOUND = 1000;

    public static final int DISCOVERABILITY_ON = 2000;
    public static final int DISCOVERABILITY_OFF = 2001;
    public static final int ALREADY_DISCOVERABLE = 2003;

    public static final int PAIRING = 3000;
    public static final int PAIRED = 3001;
    public static final int NOT_PAIRED = 3002;
    public static final int ALREADY_PAIRED = 3003;

    public static final int START_DISCOVERING = 4000;
    public static final int STOP_DISCOVERING = 4001;

    public static final int RENAMED_DEVICE = 5000;
    public static final int ALREADY_NAMED_CORRECTLY = 5001;
    public static final int READY_TO_SHUTDOWN = 5002;

    public static final int CONNECTING = 6000;
    public static final int CONNECTED = 6001;
    public static final int DISCONNECTING = 6002;
    public static final int DISCONNECTED = 6003;
    public static final int RESPOND_AS_CLIENT = 6004;
    public static final int CONNECTION_ERROR = 6005;
    //public static final int NOT_ABLE_TO_CONNECT = 6006;

    public static final int DATA_TRANSMISSION_SUCCESSFUL = 7000;
    public static final int DATA_TRANSMISSION_FAILED = 7001;



}


