package com.obddroid.scan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.preference.PreferenceManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.obddroid.R;
import com.obddroid.obd.ElmProt;
import com.obddroid.scan.stages.ComponentTestStage;
import com.obddroid.scan.stages.FreshEcuDiscoveryStage;
import com.obddroid.scan.stages.FaultCodeStage;
import com.obddroid.scan.stages.FreezeFrameStage;
import com.obddroid.scan.stages.LiveDataStage;
import com.obddroid.scan.stages.MonitorTestStage;
import com.obddroid.scan.stages.PendingDtcStage;
import com.obddroid.scan.stages.PermanentDtcStage;
import com.obddroid.scan.stages.VehicleInfoStage;
import com.obddroid.services.CommService;
import com.obddroid.ui.activities.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that orchestrates a full vehicle scan.
 * Runs multiple diagnostic stages sequentially and generates a comprehensive report.
 */
public class ScanOrchestrator extends Service {

    private static final String TAG = "ScanOrchestrator";
    private static final String CHANNEL_ID = "scan_orchestrator_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ScanContext scanContext;
    private ScanProgressListener progressListener;
    private volatile boolean isScanning = false;

    public interface ScanProgressListener {
        void onScanStarted(int totalStages);
        void onStageStarted(int stageIndex, String stageName);
        void onStageCompleted(int stageIndex, StageResult result);
        void onScanCompleted(ScanReport report);
        void onScanCancelled();
        void onScanFailed(Exception error);
    }

    public class LocalBinder extends Binder {
        public ScanOrchestrator getService() {
            return ScanOrchestrator.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground to prevent being killed during scan
        // connectedDevice: the scan talks to the OBD adapter (manifest foregroundServiceType).
        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification("Scan in progress..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        return START_NOT_STICKY;
    }

    public void setProgressListener(ScanProgressListener listener) {
        this.progressListener = listener;
    }

    public void startScan(ScanConfiguration configuration) {
        if (isScanning) {
            throw new IllegalStateException("Scan already in progress");
        }

        ElmProt elm = CommService.elm;
        if (elm == null) {
            notifyFailed(new IllegalStateException("No ELM327 connection available"));
            return;
        }

        isScanning = true;

        executor.execute(() -> {
            try {
                executeScan(elm, configuration);
            } catch (Exception e) {
                Log.e(TAG, "Scan failed", e);
                notifyFailed(e);
            } finally {
                isScanning = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE);
                } else {
                    @SuppressWarnings("deprecation")
                    boolean removeNotification = true;
                    stopForeground(removeNotification);
                }
                stopSelf();
            }
        });
    }

    private void executeScan(ElmProt elm, ScanConfiguration configuration) {
        String scanId = generateScanId();
        scanContext = new ScanContext(this, scanId, elm, configuration);

        // Define scan stages
        List<ScanStage> stages = buildStageList();

        // Filter out skipped stages
        List<ScanStage> activeStages = new ArrayList<>();
        for (ScanStage stage : stages) {
            if (!stage.shouldSkip(scanContext)) {
                activeStages.add(stage);
            }
        }

        int totalStages = activeStages.size();
        notifyProgress(listener -> listener.onScanStarted(totalStages));

        List<StageExecutionRecord> results = new ArrayList<>();

        for (int i = 0; i < activeStages.size(); i++) {
            if (scanContext.isCancelled()) {
                notifyProgress(listener -> listener.onScanCancelled());
                return;
            }

            ScanStage stage = activeStages.get(i);
            int stageIndex = i;

            updateNotification("Stage " + (i + 1) + "/" + totalStages + ": " + stage.getDisplayName());
            notifyProgress(listener -> listener.onStageStarted(stageIndex, stage.getDisplayName()));

            try {
                long startTime = System.currentTimeMillis();
                StageResult result = stage.execute(scanContext);
                long duration = System.currentTimeMillis() - startTime;

                results.add(new StageExecutionRecord(stage, result, startTime, duration));
                notifyProgress(listener -> listener.onStageCompleted(stageIndex, result));

                if (result.isFatal()) {
                    Log.e(TAG, "Fatal error in stage " + stage.getId() + ", aborting scan");
                    break;
                }

            } catch (InterruptedException e) {
                Log.i(TAG, "Scan cancelled during stage: " + stage.getId());
                notifyProgress(listener -> listener.onScanCancelled());
                return;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in stage " + stage.getId(), e);
                StageResult errorResult = StageResult.failed("Unexpected error: " + e.getMessage(), e);
                results.add(new StageExecutionRecord(stage, errorResult,
                    System.currentTimeMillis(), 0));
            }
        }

        // Build final report
        ScanReport report = new ScanReport(scanId, scanContext, results);

        try {
            ReportBuilder builder = new ReportBuilder(this);
            ReportArtifacts artifacts = builder.build(report);
            report.attachArtifacts(artifacts);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate report artifacts for scan " + scanId, e);
        }

        // CRITICAL: Clean up service state to prevent excessive cycling
        // Stages may have left the service in an active polling mode (OBD_SVC_DATA, OBD_SVC_VEH_INFO, etc).
        // When returning to MainActivity, rapid service switching can trigger the adapter's excessive
        // cycling protection, causing disconnection. Set service to NONE to ensure clean handoff.
        if (CommService.elm != null) {
            Log.i(TAG, "Scan complete - setting service to NONE to prevent excessive cycling");
            CommService.elm.setService(com.obddroid.obd.ObdProt.OBD_SVC_NONE);
        }

        notifyProgress(listener -> listener.onScanCompleted(report));
    }

    private List<ScanStage> buildStageList() {
        List<ScanStage> stages = new ArrayList<>();
        // Use FRESH ECU discovery (queries vehicle directly, not cached data)
        stages.add(new FreshEcuDiscoveryStage());
        stages.add(new VehicleInfoStage());
        stages.add(new LiveDataStage());
        stages.add(new FaultCodeStage());
        stages.add(new PendingDtcStage());
        stages.add(new PermanentDtcStage());
        stages.add(new FreezeFrameStage());
        stages.add(new MonitorTestStage());
        stages.add(new ComponentTestStage());
        return stages;
    }

    public void cancelScan() {
        if (scanContext != null) {
            scanContext.cancel();
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    private void notifyProgress(ProgressNotifier notifier) {
        if (progressListener != null) {
            notifier.notify(progressListener);
        }
    }

    private void notifyFailed(Exception error) {
        if (progressListener != null) {
            progressListener.onScanFailed(error);
        }
    }

    private interface ProgressNotifier {
        void notify(ScanProgressListener listener);
    }

    private String generateScanId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return "scan_" + sdf.format(new Date());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Vehicle Scan",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Full vehicle diagnostic scan progress");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Full Vehicle Scan")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_device_connected)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }

    static class StageExecutionRecord {
        final ScanStage stage;
        final StageResult result;
        final long startTime;
        final long durationMs;

        StageExecutionRecord(ScanStage stage, StageResult result, long startTime, long durationMs) {
            this.stage = stage;
            this.result = result;
            this.startTime = startTime;
            this.durationMs = durationMs;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("stageId", stage.getId());
            obj.put("stageName", stage.getDisplayName());
            obj.put("status", result.getStatus().name());
            obj.put("message", result.getMessage());
            obj.put("startTime", startTime);
            obj.put("durationMs", durationMs);
            if (result.getData() != null) {
                obj.put("data", result.getData());
            }
            return obj;
        }
    }
}
