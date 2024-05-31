package com.inseye.serviceclientdemo;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.inseye.shared.communication.ActionResult;
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
    private ISharedService inseyeServiceClient;
    private InseyeServiceBinder inseyeServiceBinder;
    private GazeDataReader gazeDataReader;

    //view
    private TextView statusTextView, gazeDataTextView;
    private Button calibrateButton, subGazeDataButton, unsubGazeDataButton;
    private RedPointView redPointView;
    private final Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_main);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        calibrateButton = findViewById(R.id.calibButton);
        subGazeDataButton = findViewById(R.id.subscribeGazeButton);
        unsubGazeDataButton = findViewById(R.id.unsubscribeGazeButton);
        statusTextView = findViewById(R.id.textViewStatus);
        gazeDataTextView = findViewById(R.id.textViewGazeData);
        calibrateButton.setOnClickListener( view -> RunCalibration());
        subGazeDataButton.setOnClickListener( view -> SubscribeGazeData());
        unsubGazeDataButton.setOnClickListener( view -> UnsubscribeGazeData());

        redPointView = findViewById(R.id.redPointView);

        inseyeServiceBinder = new InseyeServiceBinder(this);
        inseyeServiceBinder.bind(new InseyeServiceBinder.IServiceBindCallback() {
            @Override
            public void serviceConnected(ISharedService service) {
                inseyeServiceClient = service;
                Toast.makeText(MainActivity.this, "inseye service connected", Toast.LENGTH_SHORT).show();

                //get current tracker status
                try {
                    statusTextView.setText(inseyeServiceClient.getTrackerAvailability().toString());
                    if(inseyeServiceClient.getTrackerAvailability() == TrackerAvailability.Available)
                        SubscribeGazeData();

                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }

                //subscribe to tracker status event

                try {
                    inseyeServiceClient.subscribeToEyetrackerEvents(new IEyetrackerEventListener.Stub() {
                        @Override
                        public void handleTrackerAvailabilityChanged(TrackerAvailability availability) {
                            mainLooperHandler.post(() -> statusTextView.setText(availability.toString()));
                        }
                    });
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void serviceDisconnected() {
                Toast.makeText(MainActivity.this, "inseye service disconnected", Toast.LENGTH_SHORT).show();
                inseyeServiceClient = null;
            }

            @Override
            public void serviceError(Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void UnsubscribeGazeData() {
        if(!inseyeServiceBinder.isConnected()) {
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
        if(!inseyeServiceBinder.isConnected()) {
            Toast.makeText(this, "not bound to service", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            IntActionResult result = inseyeServiceClient.startStreamingGazeData();
            if(result.success) {
                int udpPort = result.value;
                Log.i(TAG, "port:" + udpPort);
                gazeDataReader = new GazeDataReader(udpPort, gazeData -> {
                        mainLooperHandler.post(() -> gazeDataTextView.setText(String.format(Locale.US, "Left Eye:   X:%6.2f Y:%6.2f\nRight Eye: X:%6.2f Y:%6.2f\nEvent: %s\nTime: %d",
                                gazeData.left_x, gazeData.left_y, gazeData.right_x, gazeData.right_y, gazeData.event, gazeData.timeMilli)));

                    float avgGazeX = (gazeData.left_x + gazeData.right_x) / 2f;
                    float avgGazeY = (gazeData.left_y + gazeData.right_y) / 2f;
                    // gaze data in radians where (0,0) is in screen center
                    redPointView.post(()-> redPointView.setPoint(avgGazeX, avgGazeY));
                });
                gazeDataReader.start();
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
        if(!inseyeServiceBinder.isConnected()) {
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
        if(inseyeServiceBinder.isConnected()) {
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
            if(inseyeServiceBinder.isConnected()) {
                inseyeServiceClient.unsubscribeFromEyetrackerEvents();
                if(gazeDataReader != null)
                    gazeDataReader.close();
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        inseyeServiceBinder.unbind();
        super.onDestroy();
    }
}