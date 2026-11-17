package com.obddroid.features.trackmode.detection;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.obddroid.features.trackmode.data.LapTime;
import com.obddroid.features.trackmode.data.Track;
import com.obddroid.features.trackmode.data.TrackSession;
import com.obddroid.utils.DebugLog;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages lap timing, finish line detection, and sector splits
 */
public class LapTimingManager {
    private static final String TAG = "LapTimingManager";

    // Detection parameters
    private static final double FINISH_LINE_DETECTION_RADIUS = 20.0; // meters
    private static final double SECTOR_DETECTION_RADIUS = 20.0; // meters
    private static final long MIN_LAP_TIME = 30000; // Minimum 30 seconds for valid lap
    private static final double MIN_CROSSING_SPEED = 5.0; // km/h minimum to count crossing

    private Track currentTrack;
    private TrackSession currentSession;
    private LapTime currentLap;
    private LapTime bestLap;

    // State tracking
    private Location lastLocation;
    private Location finishLineCrossLocation;
    private boolean hasPassedFinishLine = false;
    private boolean isLapInProgress = false;
    private int currentSector = 0;
    private long lastSectorTime = 0;

    // Predictive timer
    private double lapProgress = 0; // 0-100%
    private long predictedLapTime = 0;
    private long deltaTime = 0; // +/- milliseconds vs best lap

    // Listeners
    private List<LapTimingListener> listeners = new ArrayList<>();
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // Timing thread
    private Thread timingThread;
    private volatile boolean isRunning = false;

    public interface LapTimingListener {
        void onLapStarted(int lapNumber);
        void onLapCompleted(LapTime lap);
        void onSectorCompleted(int sector, long sectorTime);
        void onFinishLineApproaching(double distanceMeters);
        void onLapTimeUpdate(long currentLapTime, long predictedTotal, long delta);
        void onLapInvalidated(String reason);
    }

    public LapTimingManager() {
    }

    /**
     * Start timing session
     */
    public void startSession(Track track, TrackSession session) {
        this.currentTrack = track;
        this.currentSession = session;
        this.bestLap = null;
        this.isRunning = true;

        // Load best lap if exists
        if (session.getBestLap() != null) {
            this.bestLap = session.getBestLap();
        }

        startTimingThread();
        DebugLog.d(TAG, "Started timing session at " + track.getName());
    }

    /**
     * Stop timing session
     */
    public void stopSession() {
        isRunning = false;
        if (timingThread != null) {
            try {
                timingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping timing thread", e);
            }
        }

        // Complete any in-progress lap
        if (currentLap != null && currentLap.getStatus() == LapTime.LapStatus.IN_PROGRESS) {
            currentLap.setStatus(LapTime.LapStatus.DNF);
            currentLap.setValid(false);
            notifyLapCompleted(currentLap);
        }

        DebugLog.d(TAG, "Stopped timing session");
    }

    /**
     * Process new GPS location
     */
    public void onLocationUpdate(Location location) {
        if (currentTrack == null || !isRunning) return;

        // Check minimum speed
        if (location.hasSpeed() && location.getSpeed() * 3.6 < MIN_CROSSING_SPEED) {
            return;
        }

        // Check finish line crossing
        if (checkFinishLineCrossing(location)) {
            handleFinishLineCrossing(location);
        }

        // Check sector crossings
        if (isLapInProgress && currentLap != null) {
            checkSectorCrossings(location);
            updateLapProgress(location);
            updatePredictiveLapTime();
        }

        // Check if approaching finish line
        double distanceToFinish = getDistanceToFinishLine(location);
        if (distanceToFinish < 100 && distanceToFinish > 0) {
            notifyFinishLineApproaching(distanceToFinish);
        }

        lastLocation = location;
    }

    /**
     * Check if the vehicle crossed the finish line
     */
    private boolean checkFinishLineCrossing(Location location) {
        if (lastLocation == null) return false;

        // Method 1: Check if we crossed the finish line segment
        if (currentTrack.crossesFinishLine(lastLocation, location)) {
            DebugLog.d(TAG, "Crossed finish line (line intersection detected)");
            return true;
        }

        // Method 2: Check if we're within radius of finish line
        if (currentTrack.isNearFinishLine(location) && !hasPassedFinishLine) {
            hasPassedFinishLine = true;
            finishLineCrossLocation = location;
            DebugLog.d(TAG, "Near finish line (radius detection)");
            return true;
        } else if (!currentTrack.isNearFinishLine(location) && hasPassedFinishLine) {
            // We've moved away from finish line
            hasPassedFinishLine = false;
        }

        return false;
    }

    /**
     * Handle finish line crossing
     */
    private void handleFinishLineCrossing(Location location) {
        long crossingTime = System.currentTimeMillis();

        if (isLapInProgress && currentLap != null) {
            // Complete current lap
            long lapTime = crossingTime - currentLap.getStartTime();

            // Validate lap time
            if (lapTime < MIN_LAP_TIME) {
                Log.w(TAG, "Lap too short: " + lapTime + "ms");
                currentLap.setValid(false);
                notifyLapInvalidated("Lap too short (< 30 seconds)");
            }

            // Complete the lap
            currentLap.setEndTime(crossingTime);
            currentLap.completeLap();
            currentSession.completeLap();

            // Check if this is a new best lap
            if (currentLap.isValid() &&
                (bestLap == null || currentLap.getLapTime() < bestLap.getLapTime())) {
                bestLap = currentLap;
                DebugLog.d(TAG, "New best lap: " + LapTime.formatTime(bestLap.getLapTime()));
            }

            notifyLapCompleted(currentLap);
        }

        // Start new lap
        startNewLap();
    }

    /**
     * Start a new lap
     */
    private void startNewLap() {
        currentLap = currentSession.startNewLap();
        currentSector = 0;
        lastSectorTime = currentLap.getStartTime();
        isLapInProgress = true;
        lapProgress = 0;
        deltaTime = 0;

        notifyLapStarted(currentLap.getLapNumber());
        DebugLog.d(TAG, "Started lap " + currentLap.getLapNumber());
    }

    /**
     * Check for sector line crossings
     */
    private void checkSectorCrossings(Location location) {
        if (currentTrack.getSectors().isEmpty()) return;

        int detectedSector = currentTrack.getSectorForLocation(location);
        if (detectedSector > currentSector) {
            // Crossed into new sector
            long sectorTime = System.currentTimeMillis() - lastSectorTime;
            currentLap.addSectorTime(System.currentTimeMillis() - currentLap.getStartTime());

            notifySectorCompleted(currentSector, sectorTime);

            currentSector = detectedSector;
            lastSectorTime = System.currentTimeMillis();

            DebugLog.d(TAG, "Completed sector " + (currentSector - 1) + " in " +
                      LapTime.formatTime(sectorTime));
        }
    }

    /**
     * Update lap progress percentage
     */
    private void updateLapProgress(Location location) {
        if (currentLap == null || currentTrack.getLength() <= 0) return;

        // Simple progress based on distance traveled
        double distanceTraveled = 0;
        List<LapTime.TelemetryPoint> points = currentLap.getTelemetryPoints();

        if (!points.isEmpty()) {
            Location start = new Location("");
            start.setLatitude(points.get(0).latitude);
            start.setLongitude(points.get(0).longitude);

            for (int i = 1; i < points.size(); i++) {
                Location prev = new Location("");
                prev.setLatitude(points.get(i-1).latitude);
                prev.setLongitude(points.get(i-1).longitude);

                Location curr = new Location("");
                curr.setLatitude(points.get(i).latitude);
                curr.setLongitude(points.get(i).longitude);

                distanceTraveled += prev.distanceTo(curr);
            }

            lapProgress = Math.min(100, (distanceTraveled / currentTrack.getLength()) * 100);
        }
    }

    /**
     * Update predictive lap time based on best lap
     */
    private void updatePredictiveLapTime() {
        if (currentLap == null || bestLap == null) return;

        long currentLapTime = System.currentTimeMillis() - currentLap.getStartTime();

        // Get best lap telemetry at current progress
        LapTime.TelemetryPoint bestPoint = bestLap.getTelemetryAtProgress(lapProgress);
        if (bestPoint != null) {
            long bestTimeAtProgress = bestPoint.timestamp - bestLap.getStartTime();
            deltaTime = currentLapTime - bestTimeAtProgress;

            // Predict total lap time based on current delta
            double remainingProgress = 100 - lapProgress;
            long remainingTime = (long)(bestLap.getLapTime() * (remainingProgress / 100.0));
            predictedLapTime = currentLapTime + remainingTime;
        } else {
            // Simple prediction based on progress
            if (lapProgress > 0) {
                predictedLapTime = (long)(currentLapTime * (100.0 / lapProgress));
            }
        }

        notifyLapTimeUpdate(currentLapTime, predictedLapTime, deltaTime);
    }

    /**
     * Get distance to finish line
     */
    private double getDistanceToFinishLine(Location location) {
        if (currentTrack.getFinishLine() == null) return -1;

        float[] results = new float[1];
        Location.distanceBetween(
            location.getLatitude(), location.getLongitude(),
            currentTrack.getFinishLine().latitude,
            currentTrack.getFinishLine().longitude,
            results
        );

        return results[0];
    }

    /**
     * Timing update thread
     */
    private void startTimingThread() {
        timingThread = new Thread(() -> {
            while (isRunning) {
                if (isLapInProgress && currentLap != null) {
                    updatePredictiveLapTime();
                }

                try {
                    Thread.sleep(100); // Update 10 times per second
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        timingThread.start();
    }

    /**
     * Add telemetry point to current lap
     */
    public void addTelemetryPoint(LapTime.TelemetryPoint point) {
        if (currentLap != null && isLapInProgress) {
            currentLap.addTelemetryPoint(point);
        }
    }

    // Listener management
    public void addListener(LapTimingListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LapTimingListener listener) {
        listeners.remove(listener);
    }

    // Notification methods
    private void notifyLapStarted(int lapNumber) {
        uiHandler.post(() -> {
            for (LapTimingListener listener : listeners) {
                listener.onLapStarted(lapNumber);
            }
        });
    }

    private void notifyLapCompleted(LapTime lap) {
        uiHandler.post(() -> {
            for (LapTimingListener listener : listeners) {
                listener.onLapCompleted(lap);
            }
        });
    }

    private void notifySectorCompleted(int sector, long sectorTime) {
        uiHandler.post(() -> {
            for (LapTimingListener listener : listeners) {
                listener.onSectorCompleted(sector, sectorTime);
            }
        });
    }

    private void notifyFinishLineApproaching(double distance) {
        uiHandler.post(() -> {
            for (LapTimingListener listener : listeners) {
                listener.onFinishLineApproaching(distance);
            }
        });
    }

    private void notifyLapTimeUpdate(long current, long predicted, long delta) {
        uiHandler.post(() -> {
            for (LapTimingListener listener : listeners) {
                listener.onLapTimeUpdate(current, predicted, delta);
            }
        });
    }

    private void notifyLapInvalidated(String reason) {
        uiHandler.post(() -> {
            for (LapTimingListener listener : listeners) {
                listener.onLapInvalidated(reason);
            }
        });
    }

    // Getters
    public LapTime getCurrentLap() { return currentLap; }
    public LapTime getBestLap() { return bestLap; }
    public boolean isLapInProgress() { return isLapInProgress; }
    public double getLapProgress() { return lapProgress; }
    public long getDeltaTime() { return deltaTime; }
    public long getPredictedLapTime() { return predictedLapTime; }
}