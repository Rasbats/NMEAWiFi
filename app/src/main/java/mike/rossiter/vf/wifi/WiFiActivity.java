package mike.rossiter.vf.wifi;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
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
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.List;

import static android.app.PendingIntent.getActivity;
import android.view.View.OnClickListener;

/**
 * Simple UI demonstrating how to open a serial communication link to a
 * remote host over WiFi, send and receive messages, and update the display.
 *
 * Author: Hayk Martirosyan
 */

public class WiFiActivity extends Activity {

    // Tag for logging
    private final String TAG = getClass().getSimpleName();
    private static final int BUFFER = 2048;
    public File testFile;
    public File zipFile;
    public BufferedWriter myWriter;
    SharedPreferences preferences;
    SharedPreferences myprefs;
    boolean isVFConnected;
    boolean isNMEAConnected;
    boolean isDisconnected;
    public static String ip_host;
    public static String  ip_port;


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

    public void onPublishUpdate(String... values){

        Toast.makeText(getApplicationContext(),"error",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences: {

                Intent intent = new Intent();
                intent.setClassName(this, "mike.rossiter.vf.wifi.WiFiActivity$MyPreferencesActivity");
                startActivity(intent);

                //Toast.makeText(getApplicationContext(),"here",
                      //  Toast.LENGTH_LONG).show();

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
        //ReadPreferences();

        boolean isAbleToWrite;
        isAbleToWrite = isExternalStorageWritable();

        isVFConnected = false;
        isNMEAConnected = false;
        isDisconnected = false;
        /*
        ) {
            Toast.makeText(getApplicationContext(), "We are able to write!",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "NOT able to write!",
                    Toast.LENGTH_LONG).show();
        }
        */

    }

    public static long getFolderSize(File f) {
        long size = 0;
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                size += getFolderSize(file);
            }
        } else {
            size=f.length();
        }
        return size;
    }
    /*

    public String getFileSize(File file){
        String value=null;
        long Filesize=getFolderSize(file)/1024;//call function and convert bytes into Kb
        if(Filesize>=1024)
            value=Filesize/1024+" Mb";
        else
            value=Filesize+" Kb";

        return value;
    }
*/
    @Override
    public void onResume(){
        super.onResume();
        ReadPreferences();
       // Toast.makeText(getApplicationContext(), "here",
                //Toast.LENGTH_LONG).show();
    }

    public void ReadPreferences() {

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        ip_port = preferences.getString("port", "");
        ip_host = preferences.getString("host", "");

        if (ip_port == ""){
            alert.showAlertDialog(this,
                    "Missing NMEA Connection Data",
                    "Please enter the NMEA Device Port using Preferences", false);

        }

        if (ip_host == "") {
            alert.showAlertDialog(this,
                    "Missing NMEA Connection Data",
                    "Please enter the NMEA Device Host using Preferences", false);

        }

            editTextAddress.setText(ip_host);
            editTextPort.setText((ip_port));

       // Toast.makeText(this, ip_port,
              //  Toast.LENGTH_LONG).show();
    }


    public void openWifiSettings(){

        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.wifi.WifiSettings");
        intent.setComponent(cn);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity( intent);

    }
/*
    public void WritePrefs(int i){
        //prefs.edit().putInt(FileKey, i).apply();
    }

    public int ReadPrefs(){

        int i = 0;

      //  int myInt = prefs.getInt(FileKey, i );
        return i;
    }


    public static final String PREFS_NAME = "preferences";

    public void onWrite(View v){

        SharedPreferences pref = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putString("Name", "Elena");
        editor.apply();
    }

    public void onRead(View v){
        myprefs = PreferenceManager.getDefaultSharedPreferences(this);

        String s = myprefs.getString("Name", null);
        Toast.makeText(this, s,
          Toast.LENGTH_LONG).show();

    }

    */
    /**
     * Helper function, print a status to both the UI and program log.
     */
    void setStatus(String s) {
        Log.v(TAG, s);
        textStatus.setText(s);
    }

    private boolean isExternalStorageWritable() {
        try {

            testFile = new File(this.getExternalFilesDir(null), "TestFile2.txt");
            if (!testFile.exists())
                testFile.createNewFile();
            // Adds a line to the trace file
            myWriter = new BufferedWriter(new FileWriter(testFile, true /*append*/));
            return true;
        } catch (IOException e) {
            Log.e("ReadWriteFile", "Unable to write to the TestFile.txt file.");
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

        if (ip_port == "" || host == "") {
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

            connected();

            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    if (isNMEAConnected) {
                        wifiTask = new WiFiSocketTask(host, port);
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
        try {
            // Creates a file in the primary external storage space of the
            // current application.
            // If the file does not exists, it is created.
            //String s = this.getFilesDir().getAbsolutePath();
            //Toast.makeText(getApplicationContext(),s,
            //Toast.LENGTH_LONG).show();

            myWriter.close();
            String[] filearray = new String[1];
            filearray[0] = testFile.getAbsolutePath();



            File sdCardDir = Environment.getExternalStorageDirectory();

            File directory = new File(sdCardDir+File.separator+"venturefarther");

            if (!directory.exists()) {
                directory.mkdirs();
            }

            zipFile = new File(sdCardDir, "/venturefarther/vf.zip");

            long fs = getFolderSize(testFile);
            /*
            if (fs < 50){
                alert.showAlertDialog(this,
                        "File not saved",
                        "Recordings greater than 50kb are required!", false);
                testFile.delete();
                return;
            }
*/
            //Toast.makeText(getApplicationContext(),"The File Size is " + fs,
           // Toast.LENGTH_LONG).show();


            zip(filearray, zipFile.getAbsolutePath());
            testFile.delete();

            alert.showAlertDialog(this,
                    "File saved",
                    "NMEA Recording is ready for transfer!", false);
            // Refresh the data so it can seen when the device is plugged in a
            // computer. You may have to unplug and replug the device to see the
            // latest changes. This is not necessary if the user should not modify
            // the files.
            MediaScannerConnection.scanFile(this,
                    new String[]{testFile.toString()},
                    null,
                    null);
        } catch (IOException e) {

            //Toast.makeText(getApplicationContext(),"unable to write!",
            //Toast.LENGTH_LONG).show();
            Log.e("ReadWriteFile", "Unable to write to the TestFile.txt file.");
        }
    }

    private void FileWrite(String m) {

        try {
            myWriter.write(m);
            myWriter.write("\n");
        } catch (IOException e) {
            Log.e("ReadWriteFile", "Unable to write.");
        }
    }

    public void zip(String[] _files, String zipFileName) {

        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(zipFileName);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    dest));
            byte data[] = new byte[BUFFER];

            for (int i = 0; i < _files.length; i++) {
                Log.v("Compress", "Adding: " + _files[i]);
                FileInputStream fi = new FileInputStream(_files[i]);
                origin = new BufferedInputStream(fi, BUFFER);

                ZipEntry entry = new ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;

                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The method attached to the test internet method
     *
     */


    public void onTestNMEA(View v) {

        // Location of the NMEA host

        String address = preferences.getString("host","");
        String port = preferences.getString("port","");

        try {

            if (port == "" || address == ""){
                alert.showAlertDialog(this,
                        "Missing NMEA Connection Data",
                        "Please enter the NMEA Device Address/Port using Preferences", false);
                return;
            }

        else {

            int i_port = Integer.parseInt(port);
            myNMEATest = new TestNMEAConnectionTask(address, i_port);
            myNMEATest.execute();

        }

        } catch (Exception e) {
                e.printStackTrace();
            }

    }

    public void onTestInternet(View v) {

        // Location of the remote host
        try {
            myInternetTest = new TestInternetTask();
            myInternetTest.execute();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * The method attached to 'buttonSendFile'.
     */


    public void onUploadFile(View v) {
        myInternetTest = new TestInternetTask();
        myUpload = new UploadTask();

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
     * Carry out a test for a working Internet connection in an AsyncTask.
     * If successful start the UploadTask in a new thread..
     *
     */
/*
    public void UploadFileMethod() {

        try {

            if (isVFConnected) {

                try {
                    myUpload = new UploadTask();
                    myUpload.execute();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else return;


        } catch (Exception e) {
            e.printStackTrace();
        }

        //myInternetTest.cancel(true);
        //myInternetTest = null;


    }

    public String getTextFileData(String fileName) {

        // Get the dir of SD Card
        File sdCardDir = Environment.getExternalStorageDirectory();

        // Get The Text file
        File txtFile = new File(sdCardDir, fileName);

        // Read the file Contents in a StringBuilder Object
        StringBuilder text = new StringBuilder();

        String f = sdCardDir.getAbsolutePath();

        Toast.makeText(getApplicationContext(),f,
                Toast.LENGTH_LONG).show();

        try {

            BufferedReader reader = new BufferedReader(new FileReader(txtFile));

            String line;

            while ((line = reader.readLine()) != null) {
                text.append(line + '\n');
            }
            reader.close();


        } catch (IOException e) {
            Log.e("C2c", "Error occured while reading text file!!");
            Toast.makeText(getApplicationContext(),"error",
                    Toast.LENGTH_LONG).show();
        }

        return text.toString();

    }*/

    /** ==================================================================================
     * Invoked by the TestNMEAConnection Task AsyncTask when the test is made.
     * This is in the main UI thread.
     */


    private void TestNMEAConnectionMessage(String msg) {

        //testText.setText(msg);

        if (msg == "false") {
            // Internet Connection is not present
            alert.showAlertDialog(this,
                    "Connection Failed",
                   "NMEA Connection is NOT Available", false);
            //testText.setText(msg);
            isNMEAConnected = false;
            // stop executing code by return
            return;
        } else if (msg == "true"){
            setStatus("NMEA Connection");
            // Internet Connection is not present
           // alert.showAlertDialog(this,
                    //"NMEA Connection",
                    //"NMEA Connection is Available", true);

            //testText.setText(msg);
            isNMEAConnected = true;
            // stop executing code by return
            return;
        }


    }

    /**
     * AsyncTask that connects to NMEA.
     * We test that a connection can be made.
     * The result of the test is sent back to the main ui thread.
     *
     */

    public class TestNMEAConnectionTask extends AsyncTask<Void, String, Void> {

        // Location of the remote host
        String address ;
        int port ;

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
                    e.printStackTrace();
                    Log.e(TAG, "Error in socket thread!");
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




    /** ==================================================================================
     * Invoked by the TestInternetTask AsyncTask when the test is made.
     * This is in the main UI thread.
     */


    private void TestInternetMessage(String msg) {

        if (msg == "false") {
            // Internet Connection is not present
            alert.showAlertDialog(this,
                    "Connection Failed",
                    "Internet Connection is NOT Available", false);
            //testText.setText(msg);
            isVFConnected = false;
            // stop executing code by return
            return;
        } else if (msg == "true"){

            setStatus("Internet Connected!");
            // Internet Connection is not present
            //alert.showAlertDialog(this,
                   // "Sending NMEA File",
                   // "Internet Connection is Available", true);

            //testText.setText(msg);
            isVFConnected = true;
            // stop executing code by return
            return;
        }


    }

    /**
     * AsyncTask that connects to the Internet Server.
     * We test that a connection can be made.
     * The result of the test is sent back to the main ui thread.
     *
     */

    public class TestInternetTask extends AsyncTask<Void, String, Void> {

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
            }
            else {
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

    /** ==================================================================================
     * Invoked by the UploadTask AsyncTask. This is in the main UI thread.
     * This will confirm that the file has been successfully sent.
     */

    private void UploadMessage(String msg) {

        alert.showAlertDialog(this,
                 msg,
                    "", false);

        textTX.setText(msg);
    }

    /**
     * This program demonstrates a usage of the MultipartUtility class.
     * @author www.codejava.net
     *
     */

    /**
     * AsyncTask that connects to a Internet Server and sends the NMEA file by Multipart Form
     * The upload happens in a separate thread, so the main UI thread is not blocked.
     * However, the AsyncTask has a way of sending data back
     * to the UI thread. Under the hood, it is using Threads and Handlers.
     */

    public class UploadTask extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... arg) {

            final String boundary;
            final String LINE_FEED = "\r\n";
            HttpURLConnection httpConn;
            OutputStream outputStream;
            PrintWriter writer;

            FileInputStream is;
            BufferedReader reader;
            String charset = "UTF-8";


            // Get the dir of SD Card
            File sdCardDir = Environment.getExternalStorageDirectory();

            // Get The Text file
            File uploadFile = new File(sdCardDir, "/venturefarther/vf.zip");


            if (uploadFile.exists()) {

                String requestURL = "https://www.venturefarther.com/upload/HandleDirectNMEAUpload.action";
                //String requestURL = "http://posttestserver.com/post.php";

                try {


                    MultipartUtility multipart = new MultipartUtility(requestURL, charset);

                    multipart.addHeaderField("User-Agent", "CodeJava");
                    multipart.addHeaderField("Test-Header", "Header-Value");

                    multipart.addFormField("key", "");
                    multipart.addFilePart("file", uploadFile);


                    List<String> response = multipart.finish();

                    publishProgress(response.get(0));

                } catch (IOException e) {
                    Log.e(TAG, "Error in multipart!");
                }
            }
            else {

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

    /**  =================================================================================
     * Invoked by the WiFiSocketTask AsyncTask when a newline-delimited message is received.
     * These are the NMEA sentences. This is in the main ui thread.
     */
    private void gotMessage(String msg) {

            textRX.setText(msg);
            FileWrite(msg);
            Log.v(TAG, "[RX] " + msg);

    }


    /**
     * AsyncTask that connects to a remote host over WiFi and reads/writes the connection
     * using a socket. The read loop of the AsyncTask happens in a separate thread, so the
     * main UI thread is not blocked. However, the AsyncTask has a way of sending data back
     * to the UI thread. Under the hood, it is using Threads and Handlers.
     */

    public class WiFiSocketTask extends AsyncTask<Void, String, Void> {

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
        WiFiSocketTask(String address, int port) {
            this.address = address;
            this.port = port;
        }

        /**
         * Main method of AsyncTask, opens a socket and continuously reads from it
         */
        @Override
        protected Void doInBackground(Void... arg) {

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

                // Read messages in a loop until disconnected
                while (!disconnectSignal) {

                    // Parse a message with a newline character
                    String msg = inStream.readLine();

                    // Send it to the UI thread
                    publishProgress(msg);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error in socket thread!");
            }

            // Send a disconnect message
            publishProgress(DISCONNECTED_MSG);

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

        /**
         * This function runs in the UI thread but receives data from the
         * doInBackground() function running in a separate thread when
         * publishProgress() is called.
         */
        @Override
        protected void onProgressUpdate(String... values) {

            String msg = values[0];
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
                gotMessage(msg);

            super.onProgressUpdate(values);
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

    /** ==================================================================================
     *  For making the preferences ...
     */

    public static class MyPreferencesActivity extends PreferenceActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
        }



        public static class MyPreferenceFragment extends PreferenceFragment
        {
            @Override
            public void onCreate(final Bundle savedInstanceState)
            {
                super.onCreate(savedInstanceState);
                addPreferencesFromResource(R.xml.preferences);
            }
        }

    }

}

