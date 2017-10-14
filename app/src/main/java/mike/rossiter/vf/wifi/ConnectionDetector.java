package mike.rossiter.vf.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

public class ConnectionDetector {

    public Context _context;

    public ConnectionDetector(Context context){
        this._context = context;
    }


    /**
     * Checking for all connection
     * **/

    public boolean isConnectingToInternet()  {

        try {
            HttpURLConnection urlConnection = (HttpURLConnection)
                    (new URL("http://clients3.google.com/generate_204")
                            .openConnection());
            urlConnection.setRequestProperty("User-Agent", "Android");
            urlConnection.setRequestProperty("Connection", "close");
            urlConnection.setConnectTimeout(1500);
            urlConnection.connect();
            if (urlConnection.getResponseCode() == 204 &&
                    urlConnection.getContentLength() == 0) {
                return true;
            } else {
                return false;
            }

        } catch (IOException e) {
            Log.e("Network Checker", "Error checking internet connection");
        }
        return false;
    }

    public boolean isConnectingToNMEA()  {

        Socket socket = null;
        BufferedReader inStream = null;
        OutputStream outStream = null;

        boolean disconnectSignal = true;

        // Socket timeout - close if no messages received (ms)
        int timeout = 5000;

        try {
            // Open the socket and connect to it
            socket = new Socket();
            InetSocketAddress mySocket = InetSocketAddress.createUnresolved(WiFiActivity.ip_host, 10110);
            socket.connect(mySocket, timeout);

            // Get the input and output streams
            inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outStream = socket.getOutputStream();


            // Confirm that the socket opened
            if (socket.isConnected()) {
                // Make sure the input stream becomes ready, or timeout

                disconnectSignal = false;


            } else {

                disconnectSignal = true;

            }
            // Read messages in a loop until disconnected
            if (!disconnectSignal) {
                // Parse a message with a newline character
               // String msg = inStream.readLine();
                return true;
            }

        } catch (IOException e) {
                return true;
        }

        return false;

    }

}