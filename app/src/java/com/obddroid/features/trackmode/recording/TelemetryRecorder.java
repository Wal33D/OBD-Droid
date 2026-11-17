package com.obddroid.features.trackmode.recording;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.features.trackmode.data.LapTime;
import com.obddroid.features.trackmode.detection.LapTimingManager;
import com.obddroid.utils.DebugLog;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Records telemetry data from GPS, sensors, and OBD for track sessions
 * Integrates with the app's existing live data system
 */
public class TelemetryRecorder implements LocationListener, SensorEventListener {

    private static final String TAG = "TelemetryRecorder";
    private static final int RECORDING_RATE_HZ = 10; // 10 Hz recording rate

    private final Context context;
    private final LocationManager locationManager;
    private final SensorManager sensorManager;
    private final LapTimingManager lapTimingManager;
    private ObdProt obdProtocol;

    // Sensors
    private Sensor accelerometer;
    private Sensor gyroscope;

    // Recording state
    private boolean isRecording = false;
    private ScheduledExecutorService recordingExecutor;

    // Latest telemetry values
    private Location currentLocation;
    private float currentSpeed = 0; // km/h
    private float currentAccelX = 0; // m/s^2
    private float currentAccelY = 0; // m/s^2
    private float currentAccelZ = 0; // m/s^2
    private float currentGyroX = 0; // rad/s
    private float currentGyroY = 0; // rad/s
    private float currentGyroZ = 0; // rad/s
    private float currentAzimuth = 0; // degrees
    private float currentPitch = 0; // degrees
    private float currentRoll = 0; // degrees

    // OBD data
    private float currentRpm = 0;
    private float currentThrottle = 0;
    private float currentEngineLoad = 0;
    private float currentCoolantTemp = 0;
    private float currentIntakeTemp = 0;
    private int currentGear = 0;

    // Calculated values
    private float gForceLateral = 0;
    private float gForceLongitudinal = 0;
    private float gForceVertical = 0;

    public TelemetryRecorder(Context context, LapTimingManager lapTimingManager) {
        this.context = context;
        this.lapTimingManager = lapTimingManager;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Get sensors
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    /**
     * Start recording telemetry
     */
    @android.annotation.SuppressLint("MissingPermission")
    public void startRecording(ObdProt obdProtocol) {
        if (isRecording) return;

        this.obdProtocol = obdProtocol;
        isRecording = true;

        // Start GPS location updates
        if (locationManager != null) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    100, // 100ms minimum time
                    0,   // 0 meters minimum distance
                    this
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to start GPS", e);
            }
        }

        // Start sensor updates
        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
            }
        }

        // Start recording thread
        startRecordingThread();

        DebugLog.d(TAG, "Started telemetry recording");
    }

    /**
     * Stop recording telemetry
     */
    public void stopRecording() {
        if (!isRecording) return;

        isRecording = false;

        // Stop GPS location updates
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }

        // Stop sensor updates
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // Stop recording thread
        if (recordingExecutor != null) {
            recordingExecutor.shutdown();
            try {
                if (!recordingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    recordingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                recordingExecutor.shutdownNow();
            }
        }

        DebugLog.d(TAG, "Stopped telemetry recording");
    }

    /**
     * Start the recording thread that samples data at fixed rate
     */
    private void startRecordingThread() {
        recordingExecutor = Executors.newSingleThreadScheduledExecutor();

        recordingExecutor.scheduleAtFixedRate(() -> {
            if (!isRecording) return;

            // Create telemetry point with current data
            LapTime.TelemetryPoint point = createTelemetryPoint();

            // Add to current lap
            lapTimingManager.addTelemetryPoint(point);

            // Update OBD data if available
            updateObdData();

        }, 0, 1000 / RECORDING_RATE_HZ, TimeUnit.MILLISECONDS);
    }

    /**
     * Create telemetry point from current data
     */
    private LapTime.TelemetryPoint createTelemetryPoint() {
        long timestamp = System.currentTimeMillis();

        LapTime.TelemetryPoint point = new LapTime.TelemetryPoint(
            timestamp,
            currentLocation != null ? currentLocation.getLatitude() : 0,
            currentLocation != null ? currentLocation.getLongitude() : 0
        );

        // GPS data
        if (currentLocation != null) {
            point.altitude = currentLocation.getAltitude();
            point.speed = currentSpeed;
            point.bearing = currentLocation.getBearing();
            point.accuracy = currentLocation.getAccuracy();
        }

        // OBD data
        point.rpm = currentRpm;
        point.throttlePosition = currentThrottle;
        point.engineLoad = currentEngineLoad;
        point.coolantTemp = currentCoolantTemp;
        point.intakeTemp = currentIntakeTemp;
        point.gear = currentGear;

        // Motion sensor data
        point.gForceLateral = gForceLateral;
        point.gForceLongitudinal = gForceLongitudinal;
        point.gForceVertical = gForceVertical;
        point.yaw = currentGyroZ * 57.2958f; // Convert rad/s to deg/s
        point.pitch = currentPitch;
        point.roll = currentRoll;

        return point;
    }

    /**
     * Update OBD data from vehicle live data
     */
    @SuppressWarnings({"deprecation", "unchecked"})
    private void updateObdData() {
        // Access live OBD data the same way FuelEconomyActivity does
        try {
            // Get RPM from PID 0x0C - Key format is "PID.SENSOR.BANK"
            EcuDataPv rpmPv = ObdProt.PidPvs.getTyped("0C.0.0");
            if (rpmPv != null) {
                Object value = rpmPv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    currentRpm = Float.parseFloat(value.toString());
                }
            }

            // Get throttle position from PID 0x11
            EcuDataPv throttlePv = ObdProt.PidPvs.getTyped("11.0.0");
            if (throttlePv != null) {
                Object value = throttlePv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    currentThrottle = Float.parseFloat(value.toString());
                }
            }

            // Get engine load from PID 0x04
            EcuDataPv loadPv = ObdProt.PidPvs.getTyped("04.0.0");
            if (loadPv != null) {
                Object value = loadPv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    currentEngineLoad = Float.parseFloat(value.toString());
                }
            }

            // Get coolant temperature from PID 0x05
            EcuDataPv coolantPv = ObdProt.PidPvs.getTyped("05.0.0");
            if (coolantPv != null) {
                Object value = coolantPv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    currentCoolantTemp = Float.parseFloat(value.toString());
                }
            }

            // Get intake temperature from PID 0x0F
            EcuDataPv intakePv = ObdProt.PidPvs.getTyped("0F.0.0");
            if (intakePv != null) {
                Object value = intakePv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    currentIntakeTemp = Float.parseFloat(value.toString());
                }
            }

            // Get vehicle speed from PID 0x0D (as backup/verification)
            EcuDataPv speedPv = ObdProt.PidPvs.getTyped("0D.0.0");
            if (speedPv != null) {
                Object value = speedPv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float obdSpeed = Float.parseFloat(value.toString());
                    // Use OBD speed if GPS is not available
                    if (currentLocation == null) {
                        currentSpeed = obdSpeed;
                    }
                }
            }

            // Calculate gear from speed and RPM
            if (currentRpm > 0 && currentSpeed > 0) {
                currentGear = calculateGear(currentSpeed, currentRpm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating OBD data", e);
        }
    }

    /**
     * Calculate gear based on speed and RPM
     */
    private int calculateGear(float speedKmh, float rpm) {
        if (rpm < 500) return 0; // Neutral/clutch

        // Gear ratios vary by vehicle, this is approximate
        float ratio = (speedKmh * 1000 / 60) / (float)(rpm * 2 * Math.PI * 0.3); // Assuming 0.3m tire radius

        if (ratio < 0.3) return 1;
        else if (ratio < 0.5) return 2;
        else if (ratio < 0.7) return 3;
        else if (ratio < 0.9) return 4;
        else if (ratio < 1.2) return 5;
        else return 6;
    }

    /**
     * Calculate G-forces from accelerometer
     */
    private void calculateGForces() {
        // Convert m/s^2 to G (9.81 m/s^2 = 1G)
        gForceLateral = currentAccelX / 9.81f;
        gForceLongitudinal = currentAccelY / 9.81f;
        gForceVertical = (currentAccelZ - 9.81f) / 9.81f; // Subtract gravity
    }

    // LocationListener implementation
    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        currentSpeed = location.getSpeed() * 3.6f; // m/s to km/h

        // Update lap timing manager
        lapTimingManager.onLocationUpdate(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Not used
    }

    @Override
    public void onProviderEnabled(String provider) {
        DebugLog.d(TAG, "GPS provider enabled");
    }

    @Override
    public void onProviderDisabled(String provider) {
        DebugLog.d(TAG, "GPS provider disabled");
    }

    // SensorEventListener implementation
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            currentAccelX = event.values[0];
            currentAccelY = event.values[1];
            currentAccelZ = event.values[2];
            calculateGForces();
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            currentGyroX = event.values[0];
            currentGyroY = event.values[1];
            currentGyroZ = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // Getters for current values
    public float getCurrentSpeed() { return currentSpeed; }
    public float getCurrentRpm() { return currentRpm; }
    public float getCurrentThrottle() { return currentThrottle; }
    public float getGForceLateral() { return gForceLateral; }
    public float getGForceLongitudinal() { return gForceLongitudinal; }
    public float getGForceVertical() { return gForceVertical; }
    public Location getCurrentLocation() { return currentLocation; }
    public boolean isRecording() { return isRecording; }

    /**
     * Get maximum G-force magnitude
     */
    public float getMaxGForce() {
        return (float) Math.sqrt(
            gForceLateral * gForceLateral +
            gForceLongitudinal * gForceLongitudinal +
            gForceVertical * gForceVertical
        );
    }
}