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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.inseye.shared.ISharedServiceTagSource;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.IBuiltInCalibrationCallback;
import com.inseye.shared.communication.IEyetrackerEventListener;
import com.inseye.shared.communication.IServiceBuiltInCalibrationCallback;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.TrackerAvailability;

public class MainActivity extends AppCompatActivity {
    private final String TAG = MainActivity.class.toString();
    private boolean serviceBound = false;
    private ISharedService trackerService;

    private TextView textView;
    private Button calibrateButton;

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

        calibrateButton = findViewById(R.id.button);
        textView = findViewById(R.id.textView);

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

                    //calibrationCallback could be used to abort calibration
                }
                else {
                    Log.e(TAG, "calibration init fail: " + result.errorMessage);
                    Toast.makeText(MainActivity.this, "calibration init fail: " + result.errorMessage, Toast.LENGTH_SHORT).show();

                    //calibrationCallback is null
                }

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
            Toast.makeText(MainActivity.this, "service connected", Toast.LENGTH_SHORT).show();
            try {
                textView.setText(trackerService.getTrackerAvailability().toString());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

            try {
                trackerService.subscribeToEyetrackerEvents(new IEyetrackerEventListener.Stub() {
                    @Override
                    public void handleTrackerAvailabilityChanged(TrackerAvailability availability) throws RemoteException {
                        Handler h = new Handler(Looper.getMainLooper());
                        h.post(() -> textView.setText(availability.toString()));
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
                textView.setText(trackerService.getTrackerAvailability().toString());
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
        unbindService(inseyeServiceConnection);
        super.onDestroy();
    }
}