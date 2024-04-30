package com.inseye.serviceclientdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.GazeData;
import com.inseye.shared.communication.IBuiltInCalibrationCallback;
import com.inseye.shared.communication.IEyetrackerEventListener;
import com.inseye.shared.communication.IServiceBuiltInCalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.inseye.shared.communication.TrackerAvailability;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private final String TAG = MainActivity.class.toString();
    private boolean serviceBound = false;
    private ISharedService inseyeServiceClient;
    private GazeDataReader gazeDataReader;
    private TextView statusTextView, gazeDataTextView;
    private Button calibrateButton, subGazeDataButton, unsubGazeDataButton;
    private final Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        calibrateButton = findViewById(R.id.calibButton);
        subGazeDataButton = findViewById(R.id.subscribeGazeButton);
        unsubGazeDataButton = findViewById(R.id.unsubscribeGazeButton);
        statusTextView = findViewById(R.id.textViewStatus);
        gazeDataTextView = findViewById(R.id.textViewGazeData);
        calibrateButton.setOnClickListener( view -> RunCalibration());
        subGazeDataButton.setOnClickListener( view -> SubscribeGazeData());
        unsubGazeDataButton.setOnClickListener( view -> UnsubscribeGazeData());

        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(this.getString(com.inseye.shared.R.string.service_package_name), this.getString(com.inseye.shared.R.string.service_class_name));
        serviceIntent.setComponent(component);
        bindService(serviceIntent, inseyeServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection inseyeServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            inseyeServiceClient = ISharedService.Stub.asInterface(iBinder);
            serviceBound = true;
            Toast.makeText(MainActivity.this, "Inseye Service Connected", Toast.LENGTH_SHORT).show();


            //get current tracker status
            try {
                statusTextView.setText(inseyeServiceClient.getTrackerAvailability().toString());

            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

            //subscribe to tracker status event
            try {
                inseyeServiceClient.subscribeToEyetrackerEvents(new IEyetrackerEventListener.Stub() {
                    @Override
                    public void handleTrackerAvailabilityChanged(TrackerAvailability availability) throws RemoteException {
                        mainLooperHandler.post(() -> statusTextView.setText(availability.toString()));
                    }
                });
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Toast.makeText(MainActivity.this, "Inseye Service Disconnected", Toast.LENGTH_SHORT).show();
            serviceBound = false;
            inseyeServiceClient = null;
        }
    };

    private void UnsubscribeGazeData() {
        if(!serviceBound) {
            Toast.makeText(this, "not bound to service", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            inseyeServiceClient.stopStreamingGazeData();
            if(gazeDataReader != null) gazeDataReader.close();
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void SubscribeGazeData() {
        if(!serviceBound) {
            Toast.makeText(this, "not bound to service", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            IntActionResult result = inseyeServiceClient.startStreamingGazeData();
            if(result.success) {
                int udpPort = result.value;
                Log.i(TAG, "port:" + udpPort);
                gazeDataReader = new GazeDataReader(udpPort, gazeData -> {
                    if(gazeData.timeMilli % 10 == 0) {
                        mainLooperHandler.post(() -> gazeDataTextView.setText(String.format(Locale.US, "Left Eye:   X:%6.2f Y:%6.2f\nRight Eye: X:%6.2f Y:%6.2f\nEvent: %s\nTime: %d",
                                gazeData.left_x, gazeData.left_y, gazeData.right_x, gazeData.right_y, gazeData.event, gazeData.timeMilli)));
                    }
                });
                gazeDataReader.start();
                Toast.makeText(this, "udp port: " + result.value, Toast.LENGTH_SHORT).show();

            } else {
                Log.e(TAG, "gaze stream error: " + result.errorMessage);
                Toast.makeText(this, "gaze stream error: " + result.errorMessage, Toast.LENGTH_SHORT).show();


            }

        } catch (RemoteException | SocketException | UnknownHostException e) {
            Log.e(TAG, e.toString());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();

        }
    }

    private void RunCalibration() {
        if(!serviceBound) {
            Toast.makeText(this, "not bound to service", Toast.LENGTH_SHORT).show();
            return;
        }

        ActionResult result = new ActionResult();
        try {
            IServiceBuiltInCalibrationCallback calibrationAbortHandler = inseyeServiceClient.startBuiltInCalibrationProcedure(result, new IBuiltInCalibrationCallback.Stub() {
                @Override
                public void finishCalibration(ActionResult calibrationResult) throws RemoteException {
                    Log.i(TAG, calibrationResult.toString());
                    if(calibrationResult.successful) {
                        Log.e(TAG, "calibration success");
                        Toast.makeText(MainActivity.this, "calibration success", Toast.LENGTH_SHORT).show();


                    }
                    else {
                        Log.e(TAG, "calibration fail: " + calibrationResult.errorMessage);
                        Toast.makeText(MainActivity.this, "calibration fail: " + calibrationResult.errorMessage, Toast.LENGTH_SHORT).show();


                    }
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "calibration remote exception: " + e);
            return;
        }
        if(result.successful) {
            Log.i(TAG, "calibration init success");
            Toast.makeText(MainActivity.this, "calibration init success", Toast.LENGTH_SHORT).show();
            //calibrationAbortHandler could be used to abort calibration
        }
        else {
            Log.e(TAG, "calibration init fail: " + result.errorMessage);
            Toast.makeText(this, "calibration init fail: " + result.errorMessage, Toast.LENGTH_SHORT).show();
            //calibrationAbortHandler is null
        }
    }

    @Override
    protected void onResume() {
        if(serviceBound) {
            try {
                statusTextView.setText(inseyeServiceClient.getTrackerAvailability().toString());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try {
            if(serviceBound) {
                inseyeServiceClient.unsubscribeFromEyetrackerEvents();
                if(gazeDataReader != null)
                    gazeDataReader.close();
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        unbindService(inseyeServiceConnection);
        super.onDestroy();
    }
}