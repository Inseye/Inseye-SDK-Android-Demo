package com.inseye.client_demo;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

import com.inseye.sdk.GazeDataExtension;
import com.inseye.sdk.GazeDataReader;
import com.inseye.sdk.InseyeTracker;
import com.inseye.sdk.InseyeSDK;
import com.inseye.sdk.InseyeTrackerException;
import com.inseye.sdk.ScreenUtils;
import com.inseye.shared.communication.GazeData;
import com.inseye.shared.communication.TrackerAvailability;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

public class TestActivity extends AppCompatActivity implements GazeDataReader.IGazeData, InseyeTracker.IEyeTrackerStatusListener {

    private static final String TAG = TestActivity.class.getSimpleName();
    private TextView statusTextView, gazeDataTextView, additionalInfoTextView;
    private Button calibrateButton, subGazeDataButton, unsubGazeDataButton;
    private OverlayRedPointView redPointView;

    private InseyeSDK inseyeSDK;
    private InseyeTracker inseyeTracker;
    private ScreenUtils screenUtils;
    private boolean isGazeDataSubscribed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_test);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        initializeUI();
        initializeInseyeSDK();
        setButtonListeners();
    }

    private void initializeUI() {
        calibrateButton = findViewById(R.id.calibButton);
        subGazeDataButton = findViewById(R.id.subscribeGazeButton);
        unsubGazeDataButton = findViewById(R.id.unsubscribeGazeButton);
        statusTextView = findViewById(R.id.textViewStatus);
        gazeDataTextView = findViewById(R.id.textViewGazeData);
        additionalInfoTextView = findViewById(R.id.additionalInfoText);
        redPointView = findViewById(R.id.redPointView);

    }

    private void initializeInseyeSDK() {
        inseyeSDK = new InseyeSDK(this);

        inseyeSDK.getEyeTracker().thenAccept(insEyeTracker -> {
            this.inseyeTracker = insEyeTracker;
            this.screenUtils = insEyeTracker.getScreenUtils();
            updateStatusText(insEyeTracker.getTrackerAvailability().name());

            updateAdditionalInfo();

            Vector2D center = screenUtils.angleToAbsoluteScreenSpace(Vector2D.ZERO);
            redPointView.post(() -> redPointView.setPoint(center));

            insEyeTracker.subscribeToTrackerStatus(this);

        }).exceptionally(throwable -> {
            Toast.makeText(this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error initializing InseyeSDK", throwable);
            return null;
        });
    }

    private void setButtonListeners() {
        calibrateButton.setOnClickListener(v -> {
            if (inseyeTracker != null) {
                inseyeTracker.startCalibration().thenAccept(result -> {
                    if (!result.successful)
                        Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });

        subGazeDataButton.setOnClickListener(v -> {
            if (!isGazeDataSubscribed && inseyeTracker != null) {
                try {
                    inseyeTracker.startStreamingGazeData();
                    inseyeTracker.subscribeToGazeData(this);
                    isGazeDataSubscribed = true;
                } catch (InseyeTrackerException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        unsubGazeDataButton.setOnClickListener(v -> {
            if (isGazeDataSubscribed && inseyeTracker != null) {
                inseyeTracker.stopStreamingGazeData();
                inseyeTracker.unsubscribeFromGazeData(this);
                isGazeDataSubscribed = false;
            }
        });
    }

    private void updateStatusText(String status) {
        statusTextView.post(() -> statusTextView.setText("Status: " + status));
    }

    private void updateAdditionalInfo() {
        if (inseyeTracker == null) return;
        additionalInfoTextView.post(() -> additionalInfoTextView.setText(String.format("Other Info\n\nDominant Eye: %s\nFov: %s\nService: %s\nCalibration: %s\nFirmware: %s",
                inseyeTracker.getDominantEye(), inseyeTracker.getVisibleFov(), inseyeTracker.getServiceVersion(), inseyeTracker.getCalibrationVersion(), inseyeTracker.getFirmwareVersion())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inseyeSDK.isServiceConnected() && inseyeTracker != null) {
            updateStatusText(inseyeTracker.getTrackerAvailability().name());
            updateAdditionalInfo();
        }
    }

    @Override
    protected void onPause() {
        if (isGazeDataSubscribed && inseyeTracker != null) {
            inseyeTracker.stopStreamingGazeData();
            inseyeTracker.unsubscribeFromGazeData(this);
            isGazeDataSubscribed = false;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inseyeSDK.dispose();
        super.onDestroy();
    }

    @Override
    public void nextGazeDataReady(GazeData gazeData) {
        if (inseyeTracker == null) return;
        gazeDataTextView.post(() ->
                gazeDataTextView.setText(String.format("Gaze Data\n\nLeft Eye:   X:%6.2f Y:%6.2f\nRight Eye:   X:%6.2f Y:%6.2f\nEvent: %s\nTime: %d",
                gazeData.left_x, gazeData.left_y, gazeData.right_x, gazeData.right_y, gazeData.event, gazeData.timeMilli)));
        Vector2D gazeMidPoint = GazeDataExtension.getGazeCombined(gazeData);
        Vector2D gazeViewSpace = screenUtils.angleToAbsoluteScreenSpace(gazeMidPoint);

        redPointView.post(() -> redPointView.setPoint(gazeViewSpace));
    }

    @Override
    public void onTrackerAvailabilityChanged(TrackerAvailability availability) {
        updateStatusText(availability.name());
    }
}
