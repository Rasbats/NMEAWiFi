package mike.rossiter.vf.wifi;

import android.app.Activity;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.view.View;
import android.view.MenuInflater;
import android.preference.PreferenceActivity;
import android.content.Intent;

import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Simple UI demonstrating how to open a serial communication link to a
 * remote host over WiFi, send and receive messages, and update the display.
 * <p>
 * Author: Hayk Martirosyan
 */
public class WiFiActivity extends Activity implements AsyncResponse {
    // Tag for logging
    private final String TAG = getClass().getSimpleName();
    private static final int BUFFER = 2048;
    public File zipFile;
    SharedPreferences preferences;
    SharedPreferences myprefs;
    boolean isVFConnected;
    boolean isNMEAConnected;
    boolean isDisconnected;
    public static String ip_host;
    public static String ip_port;
    public static String api_key;

    // AsyncTask object that manages the connection in a separate thread
    WiFiSocketTask wifiTask = null;
    // AsyncTask object that manages the upload in a separate thread
    UploadTask myUpload = null;
    // AsyncTask object that manages the upload in a separate thread
    TestInternetTask myInternetTest = null;
    // AsyncTask object that tests for NMEA connection.
    TestNMEAConnectionTask myNMEATest = null;

    // Alert Dialog Manager
    ShowAlert alert = new ShowAlert();

    // UI elements
    TextView textStatus, textRX, textTX, testText;
    EditText editTextAddress, editTextPort, editSend;
    Button buttonConnect, buttonDisconnect, buttonSendFile;
    //Button buttonWrite, buttonRead;
    Button buttonWiFi;

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void processFinish(File zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences: {
                Intent intent = new Intent();
                intent.setClassName(this, "mike.rossiter.vf.wifi.WiFiActivity$MyPreferencesActivity");
                startActivity(intent);

                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wi_fi);

        // Save references to UI elements
        textStatus = (TextView) findViewById(R.id.textStatus);
        textRX = (TextView) findViewById(R.id.textRX);
        textTX = (TextView) findViewById(R.id.textTX);
        //testText = (TextView) findViewById(R.id.testText);
        editTextAddress = (EditText) findViewById(R.id.address);
        editTextPort = (EditText) findViewById(R.id.port);
        //editSend = (EditText) findViewById(R.id.editSend);
        buttonConnect = (Button) findViewById(R.id.connectButton);
        buttonDisconnect = (Button) findViewById(R.id.disconnectButton);
        //buttonSend = (Button) findViewById(R.id.buttonSend);
        buttonSendFile = (Button) findViewById(R.id.buttonSendFile);
        // Disable send button until a connection is made
        //buttonSend.setEnabled(false);
        //buttonWrite = (Button) findViewById(R.id.buttonWrite);
        //buttonRead = (Button) findViewById(R.id.buttonRead);
        buttonWiFi = (Button) findViewById(R.id.buttonWiFi);
        buttonWiFi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWifiSettings();
            }
        });

        isVFConnected = false;
        isNMEAConnected = false;
        isDisconnected = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        ReadPreferences();
    }

    public void ReadPreferences() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        ip_port = preferences.getString("port", "");
        ip_host = preferences.getString("host", "");
        api_key = preferences.getString("key", "");

        if (ip_port.equals("")) {
            alert.showAlertDialog(this,
                    "Missing NMEA Connection Data",
                    "Please enter the NMEA Device Port using Preferences", false);

        }

        if (ip_host.equals("")) {
            alert.showAlertDialog(this,
                    "Missing NMEA Connection Data",
                    "Please enter the NMEA Device Host using Preferences", false);
        }

        editTextAddress.setText(ip_host);
        editTextPort.setText((ip_port));
    }

    public void openWifiSettings() {
        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.wifi.WifiSettings");
        intent.setComponent(cn);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Helper function, print a status to both the UI and program log.
     */
    void setStatus(String s) {
        Log.v(TAG, s);
        textStatus.setText(s);
    }

    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * Try to start a connection with the specified remote host.
     */
    public void connectButtonPressed(View v) {
        isDisconnected = false;

        String ip_port = preferences.getString("port", "");
        final String host = preferences.getString("host", "");

        if (ip_port.equals("") || host.equals("")) {
            alert.showAlertDialog(this,
                    "Missing NMEA Connection Data",
                    "Please enter the NMEA Device Address/Port using Preferences", false);
            return;
        }

        final int port = Integer.parseInt(ip_port);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                myNMEATest = new TestNMEAConnectionTask(host, port);
                myNMEATest.execute();
            }
        });

        myNMEATest = null;

        if (!isNMEAConnected) {
            return;
        } else {
            //Set the status to connected
            connected();

            wifiTask = new WiFiSocketTask(host, port, this);

            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    if (isNMEAConnected) {
                        wifiTask.execute();
                    }
                }
            });
        }

        executorService.shutdown();
    }

    /**
     * Disconnect from the connection.
     */
    public void disconnectButtonPressed(View v) {
        if (wifiTask == null) {
            setStatus("Already disconnected!");
            return;
        }
        isDisconnected = true;
        wifiTask.disconnect();
        wifiTask = null;
        setStatus("Disconnecting...");
    }

    /**
     * Invoked by the WiFiSocketTask AsyncTask when the connection is successfully established.
     */
    private void connected() {
        setStatus("Connected.");
        //buttonSend.setEnabled(true);
    }

    /**
     * Invoked by the WiFiSocketTask AsyncTask when the connection ends..
     */
    private void disconnected() {
        setStatus("Disconnected.");
        //buttonSend.setEnabled(false);
        textRX.setText("");
        textTX.setText("");
        wifiTask = null;
    }

    private File zip(File tempfile, String zipFileName) throws IOException {
        File zipFile = null;

        try {
            BufferedInputStream origin = null;

            //Default to writing to SD card if possible
            if(isExternalStorageWritable())
                zipFile = new File(getExternalFilesDir(null),zipFileName);
            else
                zipFile = new File(getFilesDir(),zipFileName);

            FileOutputStream dest = new FileOutputStream(zipFile);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            byte data[] = new byte[BUFFER];

            Log.v("Compress", "Adding: " + tempfile.getAbsolutePath());
            FileInputStream fi = new FileInputStream(tempfile);
            origin = new BufferedInputStream(fi, BUFFER);

            ZipEntry entry = new ZipEntry(tempfile.getName());
            out.putNextEntry(entry);
            int count;

            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                out.write(data, 0, count);
            }
            origin.close();

            out.close();

            Log.i(TAG,"Zip Location:" + zipFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("VFRecorder", "exception", e);
            throw e;
        }

        return zipFile;
    }

    /**
     * The method attached to the test internet method
     */
    public void onTestNMEA(View v) {
        // Location of the NMEA host
        String address = preferences.getString("host", "");
        String port = preferences.getString("port", "");

        try {
            if (port.equals("") || address.equals("")) {
                alert.showAlertDialog(this,
                        "Missing NMEA Connection Data",
                        "Please enter the NMEA Device Address/Port using Preferences", false);
            } else {
                int i_port = Integer.parseInt(port);
                myNMEATest = new TestNMEAConnectionTask(address, i_port);
                myNMEATest.execute();
            }
        } catch (Exception e) {
            Log.e("VFRecorder", "exception", e);
        }
    }

    public void onTestInternet(View v) {
        // Location of the remote host
        try {
            myInternetTest = new TestInternetTask();
            myInternetTest.execute();

        } catch (Exception e) {
            Log.e("VFRecorder", "exception", e);
        }
    }

    /**
     * The method attached to 'buttonSendFile'.
     */
    public void onUploadFile(View v) {
        myInternetTest = new TestInternetTask();
        myUpload = new UploadTask(this.zipFile);

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                myInternetTest.execute();
            }
        });

        if (isVFConnected) {
            connected();
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (isVFConnected) {
                    myUpload.execute();
                }
            }
        });

        executorService.shutdown();
    }

    /**
     * ==================================================================================
     * Invoked by the TestNMEAConnection Task AsyncTask when the test is made.
     * This is in the main UI thread.
     */
    private void TestNMEAConnectionMessage(String msg) {
        if (msg.equals("false")) {
            // Internet Connection is not present
            alert.showAlertDialog(this,
                    "Connection Failed",
                    "NMEA Connection is NOT Available", false);
            //testText.setText(msg);
            isNMEAConnected = false;
            // stop executing code by return
        } else if (msg.equals("true")) {
            setStatus("NMEA Connection");
            // Internet Connection is not present
            // alert.showAlertDialog(this,
            //"NMEA Connection",
            //"NMEA Connection is Available", true);

            //testText.setText(msg);
            isNMEAConnected = true;
            // stop executing code by return
        }
    }

    /**
     * AsyncTask that connects to NMEA.
     * We test that a connection can be made.
     * The result of the test is sent back to the main ui thread.
     */
    private class TestNMEAConnectionTask extends AsyncTask<Void, String, Void> {
        // Location of the remote host
        String address;
        int port;

        // Special messages denoting connection status
        private static final String PING_MSG = "SOCKET_PING";
        private static final String CONNECTED_MSG = "SOCKET_CONNECTED";
        private static final String DISCONNECTED_MSG = "SOCKET_DISCONNECTED";

        Socket socket = null;
        BufferedReader inStream = null;
        OutputStream outStream = null;

        // Signal to disconnect from the socket
        private boolean disconnectSignal = false;

        // Socket timeout - close if no messages received (ms)
        private int timeout = 500;

        // Constructor
        TestNMEAConnectionTask(String address, int port) {
            this.address = address;
            this.port = port;
        }

        @Override
        protected Void doInBackground(Void... arg) {
            String msg = "false";

            {
                try {

                    // Open the socket and connect to it
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(address, port), timeout);

                    // Get the input and output streams
                    inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    outStream = socket.getOutputStream();

                    // Confirm that the socket opened
                    if (socket.isConnected()) {
                        // Make sure the input stream becomes ready, or timeout
                        long start = System.currentTimeMillis();
                        while (!inStream.ready()) {
                            long now = System.currentTimeMillis();
                            if (now - start > timeout) {
                                Log.e(TAG, "Input stream timeout, disconnecting!");
                                disconnectSignal = true;
                                break;
                            }
                        }
                    } else {
                        Log.e(TAG, "Socket did not connect!");
                        msg = "false";
                        publishProgress(msg);
                        disconnectSignal = true;
                    }

                    // Read messages in a loop until disconnected
                    while (!disconnectSignal) {
                        // Send it to the UI thread
                        msg = "true";
                        publishProgress("true");
                        disconnectSignal = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in socket thread!", e);
                }

                // Send a disconnect message
                //msg = "false";
                publishProgress(msg);

                // Once disconnected, try to close the streams
                try {
                    if (socket != null) socket.close();
                    if (inStream != null) inStream.close();
                    if (outStream != null) outStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String msg = values[0];
            if (msg == null) return;
                // Invoke the UploadMessage callback for all other messages
            else
                TestNMEAConnectionMessage(msg);

            super.onProgressUpdate(values);
        }
    }

    /**
     * ==================================================================================
     * Invoked by the TestInternetTask AsyncTask when the test is made.
     * This is in the main UI thread.
     */
    private void TestInternetMessage(String msg) {
        if (msg.equals("false")) {
            // Internet Connection is not present
            alert.showAlertDialog(this,
                    "Connection Failed",
                    "Internet Connection is NOT Available", false);
            //testText.setText(msg);
            isVFConnected = false;
            // stop executing code by return
        } else if (msg.equals("true")) {
            setStatus("Internet Connected!");
            // Internet Connection is not present
            //alert.showAlertDialog(this,
            // "Sending NMEA File",
            // "Internet Connection is Available", true);

            //testText.setText(msg);
            isVFConnected = true;
            // stop executing code by return
        }
    }

    /**
     * AsyncTask that connects to the Internet Server.
     * We test that a connection can be made.
     * The result of the test is sent back to the main ui thread.
     */
    private class TestInternetTask extends AsyncTask<Void, String, Void> {
        ConnectionDetector cd;
        // flag for Internet connection status
        Boolean isInternetPresent;
        String msg;

        @Override
        protected Void doInBackground(Void... arg) {
            // test for connection
            checkAvailability();
            if (isInternetPresent) {
                msg = "true";
                publishProgress(msg);
            } else {
                msg = "false";
                publishProgress(msg);
            }

            return null;
        }

        protected void checkAvailability() {
            cd = new ConnectionDetector(getApplicationContext());
            isInternetPresent = cd.isConnectingToInternet();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String msg = values[0];
            if (msg == null) return;
                // Invoke the TestInternetMessage callback for all other messages
            else
                TestInternetMessage(msg);

            super.onProgressUpdate(values);
        }

        /**
         * Set a flag to disconnect from the socket.
         */
    }

    /**
     * ==================================================================================
     * Invoked by the UploadTask AsyncTask. This is in the main UI thread.
     * This will confirm that the file has been successfully sent.
     */
    private void UploadMessage(String msg) {
        alert.showAlertDialog(this, msg, "", false);

        textTX.setText(msg);
    }

    /**
     * AsyncTask that connects to a Internet Server and sends the NMEA file by Multipart Form
     * The upload happens in a separate thread, so the main UI thread is not blocked.
     * However, the AsyncTask has a way of sending data back
     * to the UI thread. Under the hood, it is using Threads and Handlers.
     */
    private class UploadTask extends AsyncTask<Void, String, Void> {
        File zipFile;

        public UploadTask(File zipFile) {
            this.zipFile = zipFile;
        }

        @Override
        protected Void doInBackground(Void... arg) {
            Log.i(TAG, "Starting Sendfile");

            String charset = "UTF-8";

            if (zipFile!=null && zipFile.exists()) {
                Log.i(TAG, "Sending file:" + zipFile.getAbsolutePath());

                HttpUrl.Builder urlBuilder = HttpUrl.parse("https://www.venturefarther.com/upload/HandleDirectNMEAUpload.action").newBuilder();
                if (!api_key.equals("") ){
                urlBuilder.addQueryParameter("key", api_key);  //rlOhQw9gQeboCB6VWw9Y0TrEAG0yEHmm
                Log.i(TAG, api_key);}                          //rlOhQw9gQeboCB6VWw9Y0TrEAG0yEHmm
                else {
                    alert.showAlertDialog(getApplicationContext(),
                            "Missing API key",
                            "Please enter the API key using Preferences", false);
                    return null;
                }

                String requestURL = urlBuilder.build().toString();

                MediaType MEDIA_TYPE_ZIP = MediaType.parse("application/zip");
                OkHttpClient preconfiguredClient = new OkHttpClient();

                OkHttpClient client = trustAllSslClient(preconfiguredClient);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("attachment", zipFile.getName(),
                                RequestBody.create(MEDIA_TYPE_ZIP, zipFile))
                        .build();

                Request request = new Request.Builder()
                        .header("User-Agent", "VentureFarther")
                        .url(requestURL)
                        .post(requestBody)
                        .build();

                Response response;
                try {
                    response = client.newCall(request).execute();
                    Log.i(TAG,response.body().string());
                } catch (IOException e) {
                    Log.i(TAG,"Error uploading file.",e);
                }
            } else {
                Log.i(TAG, "ZipFile not available.");
                publishProgress("No file available");
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String msg = values[0];
            if (msg == null) return;

                // Invoke the UploadMessage callback for all other messages
            else
                UploadMessage(msg);

            super.onProgressUpdate(values);
        }
    }

    /**
     * =================================================================================
     * Invoked by the WiFiSocketTask AsyncTask when a newline-delimited message is received.
     * These are the NMEA sentences. This is in the main ui thread.
     */
    private void gotMessage(String msg) {
        textRX.setText(msg);
        Log.v(TAG, "[RX] " + msg);
    }

    /**
     * AsyncTask that connects to a remote host over WiFi and reads/writes the connection
     * using a socket. The read loop of the AsyncTask happens in a separate thread, so the
     * main UI thread is not blocked. However, the AsyncTask has a way of sending data back
     * to the UI thread. Under the hood, it is using Threads and Handlers.
     */
    private class WiFiSocketTask extends AsyncTask<Object, Object, File> {
        public AsyncResponse delegate = null;

        // Location of the remote host
        String address;
        int port;

        // Special messages denoting connection status
        private static final String PING_MSG = "SOCKET_PING";
        private static final String CONNECTED_MSG = "SOCKET_CONNECTED";
        private static final String DISCONNECTED_MSG = "SOCKET_DISCONNECTED";

        Socket socket = null;
        BufferedReader inStream = null;
        OutputStream outStream = null;

        // Signal to disconnect from the socket
        private boolean disconnectSignal = false;

        // Socket timeout - close if no messages received (ms)
        private int timeout = 5000;

        // Constructor
        WiFiSocketTask(String address, int port, AsyncResponse delegate) {
            this.address = address;
            this.port = port;
            this.delegate = delegate;
        }

        /**
         * Main method of AsyncTask, opens a socket and continuously reads from it
         */
        @Override
        protected File doInBackground(Object... arg) {
            File zipFile = null;

            try {
                // Open the socket and connect to it
                socket = new Socket();
                socket.connect(new InetSocketAddress(address, port), timeout);

                // Get the input and output streams
                inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                outStream = socket.getOutputStream();

                // Confirm that the socket opened
                if (socket.isConnected()) {
                    // Make sure the input stream becomes ready, or timeout
                    long start = System.currentTimeMillis();
                    while (!inStream.ready()) {
                        long now = System.currentTimeMillis();
                        if (now - start > timeout) {
                            Log.e(TAG, "Input stream timeout, disconnecting!");
                            disconnectSignal = true;
                            break;
                        }
                    }
                } else {
                    Log.e(TAG, "Socket did not connect!");
                    disconnectSignal = true;
                }

                //My guess create the file here, the filename should be a generated one, not static
                //The reason is this, whwn you are connected to the onboard wifi mux you will have no
                //internet, so the user would have to manually disconnect from the mux hotspot and connect
                //to the internet to do the upload. Also if the user does not currently have internet
                //we need to save the files up.
                File tempFile = getTempFile();

                try {
                    FileOutputStream stream = new FileOutputStream(tempFile);

                    // Read messages in a loop until disconnected
                    while (!disconnectSignal) {
                        // Parse a message with a newline character
                        String msg = inStream.readLine() + System.getProperty("line.separator");

                        // Send it to the UI thread
                        publishProgress(msg);

                        //write line to file
                        stream.write(msg.getBytes());

                        Log.i(TAG, "Writing to file:" + msg);
                    }

                    //Close the file here
                    stream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error in socket thread!", e);
                }

                //Add to zip
                String zipFileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".zip";
                zipFile = zip(tempFile, zipFileName);


                Log.i(TAG, "--1--" + zipFile.getAbsolutePath());

            } catch (Exception e) {
                Log.e(TAG, "Error in socket thread!", e);
            }

            // Send a disconnect message
            publishProgress(DISCONNECTED_MSG);

            // Once disconnected, try to close the streams
            try {
                if (socket != null) socket.close();
                if (inStream != null) inStream.close();
                if (outStream != null) outStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Error in socket thread!", e);
            }

            return zipFile;
        }

        private File getTempFile() {
            File file = null;
            try {
                String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".NMEA";
                file = File.createTempFile(fileName, null, getApplicationContext().getCacheDir());
                //Clean up this temp file when the app closes
                file.deleteOnExit();
            } catch (IOException e) {
                Log.e(TAG, "Error while creating temp file", e);
            }

            return file;
        }

        /**
         * This function runs in the UI thread but receives data from the
         * doInBackground() function running in a separate thread when
         * publishProgress() is called.
         */
        @Override
        protected void onProgressUpdate(Object... values) {
            Object msg = values[0];
            if (msg == null) return;

            // Handle meta-messages
            if (msg.equals(CONNECTED_MSG)) {
                connected();
            } else if (msg.equals(DISCONNECTED_MSG))
                disconnected();
            else if (msg.equals(PING_MSG)) {
            }

            // Invoke the gotMessage callback for all other messages
            else
                gotMessage(msg.toString());

            super.onProgressUpdate(values);
        }

        protected void onPostExecute(File zipFile) {
            delegate.processFinish(zipFile);
        }

        /**
         * Write a message to the connection. Runs in UI thread.
         */
        public void sendMessage(String data) {
            try {
                outStream.write(data.getBytes());
                outStream.write('\n');
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Set a flag to disconnect from the socket.
         */
        public void disconnect() {
            disconnectSignal = true;
        }
    }

    /**
     * ==================================================================================
     * For making the preferences ...
     */
    public static class MyPreferencesActivity extends PreferenceActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
        }

        public static class MyPreferenceFragment extends PreferenceFragment {
            @Override
            public void onCreate(final Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                addPreferencesFromResource(R.xml.preferences);
            }
        }
    }

    /*
     * This is very bad practice and should NOT be used in production.
     */
    private static final TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }
        }
    };

    private static final SSLContext trustAllSslContext;
    static {
        try {
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private static final SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();

    /*
     * This should not be used in production unless you really don't care
     * about the security. Use at your own risk.
     */
    public static OkHttpClient trustAllSslClient(OkHttpClient client) {
        Log.w("", "Using the trustAllSslClient is highly discouraged and should not be used in Production!");
        OkHttpClient.Builder builder = client.newBuilder();
        builder.sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        return builder.build();
    }
}