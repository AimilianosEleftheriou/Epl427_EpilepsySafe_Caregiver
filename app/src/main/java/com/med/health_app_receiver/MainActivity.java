package com.med.health_app_receiver;

import android.app.*;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private TextView statusText, messageText;
    private static final String CHANNEL_ID = "receiver_alerts";

    private static final int REQ_CODE_BLUETOOTH = 1002;
    private static final int REQ_CODE_NOTIFICATIONS = 1001;
    private BluetoothServerSocket serverSocket;
    private boolean keepListening = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        messageText = findViewById(R.id.messageText);
        createNotificationChannel();

        requestNecessaryPermissions();
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_CODE_NOTIFICATIONS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQ_CODE_BLUETOOTH);
        } else {
            startBluetoothServer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_BLUETOOTH) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText.setText("Bluetooth permission granted ✅");
                startBluetoothServer();
            } else {
                statusText.setText("Bluetooth permission denied ❌");
            }
        }
    }

    private void startBluetoothServer() {
        new Thread(() -> {
            try {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> statusText.setText("Permission not granted."));
                    return;
                }

                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        "HealthReceiver", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

                runOnUiThread(() -> statusText.setText("Waiting for connection..."));

                while (keepListening) {
                    BluetoothSocket socket = serverSocket.accept();
                    handleConnection(socket);
                }
            } catch (Exception e) {
                Log.e("ReceiverApp", "Server error: ", e);
                runOnUiThread(() -> statusText.setText("Connection failed."));
            }
        }).start();
    }

    private void handleConnection(BluetoothSocket socket) {
        try {
            runOnUiThread(() -> statusText.setText("Connected to sender. Receiving message..."));

            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytes;

            while ((bytes = inputStream.read(buffer)) != -1) {
                String message = new String(buffer, 0, bytes);
                Log.d("ReceiverApp", "📥 Message received: " + message);
                runOnUiThread(() -> messageText.setText(message));
                sendNotification(message);
            }

            runOnUiThread(() -> statusText.setText("Disconnected. Waiting for next message..."));

            runOnUiThread(() -> {
                statusText.setText("Disconnected. Waiting for next message...");
                messageText.setText(""); // Optional: clear last message
            });

            inputStream.close();
            socket.close();

        } catch (IOException e) {
            Log.e("ReceiverApp", "❌ Connection error: ", e);
//            runOnUiThread(() -> statusText.setText("Error: " + e.getMessage()));
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Bluetooth Alerts", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void sendNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Received Alert")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        keepListening = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            Log.e("ReceiverApp", "Failed to close server socket", e);
        }
    }
}
