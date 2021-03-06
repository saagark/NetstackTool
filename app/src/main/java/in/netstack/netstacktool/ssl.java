package in.netstack.netstacktool;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

//import android.support.v4.app.Fragment;

/**
 * Created by aseem on 23-09-2016.
 */

import static in.netstack.netstacktool.common.hideKeyboard;

public class ssl extends Fragment{
    private static final String TAG = "SSL";
    static final String SERVERIP = "172.217.26.206"; // this is from Saved State
    static final String GSERVERIP = "172.217.26.206"; //index for Bundles
    EditText ssl_server;
    TextView ssl_report;
    client myClient;
    String serverIP;

    public interface historyEventListener {public void historyEvent(String s);}
    historyEventListener eventListener;
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            eventListener = (historyEventListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement historyEventListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final TextView ssl_report;

        // Inflate the layout for this fragment
        final View v = inflater.inflate(R.layout.ssl_fragment, container, false);

        ssl_server = (EditText) v.findViewById(R.id.ssl_server);
              serverIP = ssl_server.getText().toString();  // save it as a class variable
        ssl_report = (TextView) v.findViewById(R.id.ssl_report);

        if (savedInstanceState != null) {
            // Restore value of members from saved state
            ssl_server.setText(savedInstanceState.getString(SERVERIP));
            Log.d(TAG, "Restoring Server IP from Saved State" + ssl_server.getText().toString());
            serverIP = ssl_server.getText().toString();  // save it as a class variable
        }

        Button connect_button = (Button) v.findViewById(R.id.ssl_connect);
        connect_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideKeyboard(v.getContext());
                eventListener.historyEvent(ssl_server.getText().toString());  // send event to Activity
                Log.d(TAG, "starting ssl server IP: " + ssl_server.getText().toString());
                myClient = new client(ssl_server.getText().toString(), 443,
                        ssl_report, ssl_report);
                myClient.execute();
            }
        });
        Button start_button = (Button) v.findViewById(R.id.ssl_tests1);
        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "send data: " + serverIP);
                if (myClient == null)
                    Toast.makeText(v.getContext(), "ssl: no connection", Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(v.getContext(), "ssl send data", Toast.LENGTH_SHORT).show();
            }
        });
        return v;
    }
    public void sendKeepalive() {
        short pktSize = 0;
        // Create a non-direct ByteBuffer with a 10 byte capacity
        // The underlying storage is a byte array.
        ByteBuffer buffer = ByteBuffer.allocate(pktSize);
        buffer.order(ByteOrder.BIG_ENDIAN);

        Log.d(TAG, String.valueOf(buffer.position()));
        buffer.putShort(pktSize);
        Log.d(TAG, String.valueOf(buffer.position()));
        buffer.put((byte)(4)); // KeepAlive
        Log.d(TAG, String.valueOf(buffer.position()));
        Log.d(TAG, "Number of bytes sent: " + String.valueOf(buffer.position()));
        buffer.flip();
        if (myClient != null)
            myClient.SendDataToNetwork(buffer.array());
    }

      private void appendToOutput(String str) {
        Log.d(TAG, "appendToOutput 2 called with: " + str);
        ssl_report = (TextView) getActivity().findViewById(R.id.ssl_report);
        if(ssl_report == null) return;
        ssl_report.append(str);
        ssl_report.setMovementMethod(new ScrollingMovementMethod());
    }

    public void appendToOutput(byte[] data) {
        String str;
        if (data[0] != -1) {
            ;  // since all BGP messages has FF as marker, which is -1
            // To print other messages - non data
            try {
                str = new String(data, "UTF8");
                appendToOutput(str);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }return;
        }

        ssl_report = (TextView) getActivity().findViewById(R.id.ssl_report);
        if(ssl_report == null) return;
        Log.d(TAG, "Received: " + data[0] + "  " + data[18]);
        switch(data[18]) {
            case 1: ssl_report.append("\n" + "Recvd Open"); break;
            case 2: ssl_report.append("\n" + "Recvd Update"); break;
            case 3: ssl_report.append("\n" + "Recvd Notify"); break;
            case 4: ssl_report.append("\n" + "Recvd Keepalive"); break;
            case 5: ssl_report.append("\n" + "Recvd Route Refresh"); break;
            default: ssl_report.append("\n" + "Recvd Unknown Type: " + data[18]); break;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle bundle = this.getArguments();
        if (bundle != null) {
            serverIP = bundle.getString(GSERVERIP, "0.0.0.0");
            Log.d(TAG, " !!!!! Bundle is not null: Setting SSL Server IP from settings" + serverIP);
            ssl_server = (EditText) getActivity().findViewById(R.id.ssl_server);
            ssl_server.setText(serverIP);
        } else {
            Log.d(TAG, "!!!! Bundle is null");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putString(SERVERIP, serverIP);
        Log.d(TAG, "Saving Server IP" + serverIP);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public class client extends AsyncTask<String, byte[], Boolean> {
        private static final String TAG = "SSL Client";
        String dstAddress;
        int dstPort;
        String response = "";
        boolean connected = false;
        InetAddress in = null;
        Socket socket = null;
        InputStream is;
        OutputStream os;
        byte buffer[] = new byte[4096];

        TextView textResponse, progress;
        client(String addr, int port, TextView response, TextView progress) {
            dstAddress = addr;
            dstPort = port;
            this.textResponse = response;
            this.progress = progress;
        }
        protected Boolean doInBackground(String... arg0) {
            String Str1 = new String("Trying Connect....");
            String Str2 = new String("Connected !");

            try {
                Log.d(TAG, "doInBackground called with: " + dstAddress);
                System.arraycopy(Str1.getBytes("UTF-8"), 0, buffer, 0, Str1.length());
                publishProgress(buffer);
                socket = new Socket(dstAddress, dstPort);
                Log.d(TAG, "socket connected");
                Arrays.fill(buffer, (byte) 0); // zero out the buffer
                System.arraycopy(Str2.getBytes("UTF-8"), 0, buffer, 0, Str2.length());
                publishProgress(buffer);
                is = socket.getInputStream();
                os = socket.getOutputStream();
                //This is blocking
                int read;
                while((read = is.read(buffer, 0, 512)) > 0 ) {
                    byte[] idata = new byte[read];
                    Log.i(TAG, "!!!!      Recvd data bytes: " + read);
                    System.arraycopy(buffer, 0, idata, 0, read); // since buffer could be overwritten
                    publishProgress(idata);
                }
            } catch (Exception e) {
                Log.e("ClientActivity", "C: Error", e);
                connected = false;
                return false;
            } finally {
                try {
                    is.close();
                    os.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Finished");
            }
            return true;
        }

        public boolean SendDataToNetwork(final byte[] cmd) { //You run this from the main thread.
            if ((socket != null) && (socket.isConnected() == true)) {
                Log.d(TAG, "SendDataToNetwork: Writing received message to socket");
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            //for (int index = 0; index < 20; index++) {
                            //   Log.i(TAG, String.format("0x%20x", cmd[index])); }
                            os.write(cmd);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            Log.i(TAG, "SendDataToNetwork: Message send failed. Caught an exception");
                        }
                    }
                }
                ).start();
                return true;
            }
            else {
                Log.i(TAG, "SendDataToNetwork: Cannot send message. Socket is closed");
            }
            return false;
        }

        @Override
        protected void onProgressUpdate(byte[]... values) {
            if (values.length > 0) {
                Log.d(TAG, "onProgressUpdate: " + values[0].length + " bytes received.");
                appendToOutput(values[0]);
            }
        }
        protected void onPostExecute(Boolean result) {
            Log.d("ClientActivity", "onPostExecute called for " + result);
            if (result == true)
                textResponse.setText("Disconnected to Port");
            else
                textResponse.setText("Disconnection Failed to Port");
            super.onPostExecute(result);
        }
    } // end async task class
} // end activity
