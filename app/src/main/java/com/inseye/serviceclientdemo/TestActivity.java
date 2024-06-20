package com.inseye.serviceclientdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.inseye.sdk.GazeDataReader;
import com.inseye.sdk.InseyeSDK;
import com.inseye.sdk.InseyeTracker;
import com.inseye.sdk.InseyeTrackerException;
import com.inseye.sdk.RedPointView;
import com.inseye.sdk.ScreenUtils;
import com.inseye.shared.communication.GazeData;
import com.inseye.shared.communication.TrackerAvailability;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

public class TestActivity extends AppCompatActivity implements GazeDataReader.IGazeData, InseyeTracker.IEyeTrackerStatusListener {
    private final String TAG = TestActivity.class.toString();
    private TextView statusTextView, gazeDataTextView;
    private Button calibrateButton, subGazeDataButton, unsubGazeDataButton;
    private RedPointView redPointView; // Custom view for displaying gaze point

    private final Handler mainLooperHandler = new Handler(Looper.getMainLooper()); // Handler for UI thread

    private InseyeSDK inseyeSDK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display

        requestWindowFeature(Window.FEATURE_NO_TITLE); // Remove title
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN); // Set fullscreen

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); // Hide navigation and enable immersive mode

        setContentView(R.layout.activity_test); // Set layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); // Adjust padding for system bars
            return insets;
        });

        // Initialize UI components
        calibrateButton = findViewById(R.id.calibButton);
        subGazeDataButton = findViewById(R.id.subscribeGazeButton);
        unsubGazeDataButton = findViewById(R.id.unsubscribeGazeButton);
        statusTextView = findViewById(R.id.textViewStatus);
        gazeDataTextView = findViewById(R.id.textViewGazeData);
        redPointView = findViewById(R.id.redPointView);

        inseyeSDK = new InseyeSDK(this); // Initialize SDK

        // Asynchronously get eye tracker instance
        inseyeSDK.getEyeTracker().thenAccept(insEyeTracker -> {
            statusTextView.setText(insEyeTracker.getTrackerAvailability().name()); // Display tracker availability

            insEyeTracker.subscribeToTrackerStatus(this); // Subscribe to tracker status updates

            calibrateButton.setOnClickListener(v -> {
                insEyeTracker.startCalibration().thenAccept(result -> {
                    Toast.makeText(this, result.toString(), Toast.LENGTH_SHORT).show(); // Show calibration result
                });
            });

            subGazeDataButton.setOnClickListener(v -> {
                try {
                    insEyeTracker.subscribeToGazeData(this);
                } catch (InseyeTrackerException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show(); // Handle subscription error
                }
            });

            unsubGazeDataButton.setOnClickListener(v -> insEyeTracker.unsubscribeFromGazeData()); // Unsubscribe from gaze data

        }).exceptionally(throwable -> {
            Toast.makeText(this, throwable.getMessage(), Toast.LENGTH_SHORT).show(); // Handle exceptions
            Log.e(TAG, throwable.getMessage());
            return null;
        });
    }

    @Override
    protected void onDestroy() {
        inseyeSDK.disposeEyeTracker(); // Dispose of the eye tracker
        super.onDestroy();
    }

    @Override
    public void nextGazeDataReady(GazeData gazeData) {
        // Update gaze data on the UI thread
        mainLooperHandler.post(() -> gazeDataTextView.setText(String.format("Left Eye:   X:%6.2f Y:%6.2f\nRight Eye: X:%6.2f Y:%6.2f\nEvent: %s\nTime: %d",
                gazeData.left_x, gazeData.left_y, gazeData.right_x, gazeData.right_y, gazeData.event, gazeData.timeMilli)));

        // Calculate average gaze coordinates
        float avgGazeX = (gazeData.left_x + gazeData.right_x) / 2f;
        float avgGazeY = (gazeData.left_y + gazeData.right_y) / 2f;

        // Convert gaze data to screen space and then to view space
        Vector2D screenSpaceGaze = ScreenUtils.angleToScreenSpace(avgGazeX, avgGazeY, this);
        Vector2D viewSpaceGaze = ScreenUtils.screenSpaceToViewSpace(redPointView, screenSpaceGaze);

        // Update red point view with new gaze coordinates
        redPointView.post(() -> redPointView.setPoint((float) viewSpaceGaze.getX(), (float) viewSpaceGaze.getY(), this));
    }

    @Override
    public void onTrackerAvailabilityChanged(TrackerAvailability availability) {
        // Update tracker availability status on the UI thread
        mainLooperHandler.post(() -> statusTextView.setText(availability.name()));
    }
}
