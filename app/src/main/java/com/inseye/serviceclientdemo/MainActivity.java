package com.inseye.serviceclientdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
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
    private ISharedService trackerService;

    private GazeDataReader gazeDataReader;

    private TextView statusTextView, gazeDataTextView;
    private Button calibrateButton, subGazeDataButton, unsubGazeDataButton;


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

        calibrateButton = findViewById(R.id.calibButton);
        subGazeDataButton = findViewById(R.id.subscribeGazeButton);
        unsubGazeDataButton = findViewById(R.id.unsubscribeGazeButton);
        statusTextView = findViewById(R.id.textViewStatus);
        gazeDataTextView = findViewById(R.id.textViewGazeData);

        calibrateButton.setOnClickListener(view -> {

            if(serviceBound) {
                ActionResult result = new ActionResult();
                IServiceBuiltInCalibrationCallback calibrationCallback;
                try {
                    calibrationCallback = trackerService.startBuiltInCalibrationProcedure(result, new IBuiltInCalibrationCallback.Stub() {
                        @Override
                        public void finishCalibration(ActionResult calibrationResult) throws RemoteException {
                            Log.i(TAG, calibrationResult.toString());
                            if(calibrationResult.successful) {
                                Log.e(TAG, "calibration success");
                                Snackbar.make(view, "calibration success", Snackbar.LENGTH_SHORT).show();

                            }
                            else {
                                Log.e(TAG, "calibration fail: " + calibrationResult.errorMessage);
                                Snackbar.make(view, "calibration fail: " + calibrationResult.errorMessage, Snackbar.LENGTH_SHORT).show();


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

                    //calibrationCallback could be used to abort calibration
                }
                else {
                    Log.e(TAG, "calibration init fail: " + result.errorMessage);
                    Snackbar.make(view, "calibration init fail: " + result.errorMessage, Snackbar.LENGTH_SHORT).show();

                    //calibrationCallback is null
                }

            }
        });

        subGazeDataButton.setOnClickListener( view -> {
            if(!serviceBound) return;
            try {
                IntActionResult result = trackerService.startStreamingGazeData();
                if(result.success) {
                    int udpPort = result.value;
                    Log.i(TAG, "port:" + udpPort);
                    Handler handler = new Handler(Looper.getMainLooper());

                    gazeDataReader = new GazeDataReader(udpPort, gazeData -> {
                        if(gazeData.timeMilli % 10 < 2) {
                            handler.post(() -> gazeDataTextView.setText(String.format(Locale.US, "Left Eye:   X:%6.2f Y:%6.2f\nRight Eye: X:%6.2f Y:%6.2f\nEvent: %s\nTime: %d",
                                    gazeData.left_x, gazeData.left_y, gazeData.right_x, gazeData.right_y, gazeData.event, gazeData.timeMilli)));
                        }
                    });
                    gazeDataReader.start();
                    Snackbar.make(view, "udp port: " + result.value, Snackbar.LENGTH_SHORT).show();

                } else {
                    Log.e(TAG, "gaze stream error: " + result.errorMessage);
                    Snackbar.make(view, "gaze stream error: " + result.errorMessage, Snackbar.LENGTH_SHORT).show();

                }

            } catch (RemoteException | SocketException | UnknownHostException e) {
                Log.e(TAG, e.toString());
                Snackbar.make(view, e.getMessage(), Snackbar.LENGTH_SHORT).show();
            }

        });

        unsubGazeDataButton.setOnClickListener( view -> {
            if(!serviceBound) return;

            try {
                trackerService.stopStreamingGazeData();
                if(gazeDataReader != null) gazeDataReader.close();
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        });

        //this should be a part of sdk module
        Resources res = this.getResources();
        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(res.getString(com.inseye.shared.R.string.service_package_name), res.getString(com.inseye.shared.R.string.service_class_name)); // service_package_name and service_class_name are defined in this package res/values/strings.xml
        serviceIntent.setComponent(component);
        bindService(serviceIntent, inseyeServiceConnection, Context.BIND_AUTO_CREATE);

    }

    private final ServiceConnection inseyeServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            trackerService = ISharedService.Stub.asInterface(iBinder);
            serviceBound = true;
            Snackbar.make(MainActivity.this.statusTextView, "Inseye Service connected", Snackbar.LENGTH_SHORT).show();

            try {
                statusTextView.setText(trackerService.getTrackerAvailability().toString());

            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }


            try {
                trackerService.subscribeToEyetrackerEvents(new IEyetrackerEventListener.Stub() {
                    @Override
                    public void handleTrackerAvailabilityChanged(TrackerAvailability availability) throws RemoteException {
                        Handler h = new Handler(Looper.getMainLooper());
                        h.post(() -> statusTextView.setText(availability.toString()));
                    }
                });
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Toast.makeText(MainActivity.this, "service disconnected", Toast.LENGTH_SHORT).show();
            serviceBound = false;
            trackerService = null;
        }
    };

    @Override
    protected void onResume() {
        if(serviceBound) {
            try {
                statusTextView.setText(trackerService.getTrackerAvailability().toString());
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
                trackerService.unsubscribeFromEyetrackerEvents();
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