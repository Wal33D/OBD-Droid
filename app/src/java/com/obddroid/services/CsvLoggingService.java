package com.obddroid.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.obddroid.R;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvChange;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.common.ProcessVariables.PvChangeType;
import com.obddroid.common.ProcessVariables.TypedPvChangeListener;
import com.obddroid.ui.activities.MainActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Foreground service that records live PID updates into CSV files.
 *
 * This consolidated class includes all CSV logging functionality:
 * - CsvLoggingService: Main foreground service
 * - CsvData: In-memory CSV data structure
 * - CsvLoggingState: Shared state container
 * - CsvWriterThread: Background writer thread
 * - CsvLoggingController: Public API for starting/stopping
 */
public class CsvLoggingService extends Service implements TypedPvChangeListener {

    public static final String ACTION_START = "com.obddroid.action.CSV_LOGGING_START";
    public static final String ACTION_STOP = "com.obddroid.action.CSV_LOGGING_STOP";
    public static final String ACTION_STATUS_BROADCAST = "com.obddroid.action.CSV_LOGGING_STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_FILE_NAME = "file_name";

    public static final int STATUS_STARTED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final int STATUS_AUTO_PAUSED = 3;
    public static final int STATUS_ERROR = 4;

    private static final String CHANNEL_ID = "csv_logging";
    private static final int NOTIFICATION_ID = 3433;
    private static final int MIN_ROW_TIMEOUT_MS = 100;
    private static final int MAX_SEGMENT_TIMEOUT_MS = 10_000;
    private static final int AUTOMATIC_PAUSE_MS = 60_000;

    private final Object lock = new Object();
    private final Map<EcuDataPv, PvChangeListener> pvListeners = new ConcurrentHashMap<>();
    private Handler handler;
    private CsvWriterThread writer;
    private CsvData segment;
    private long lastSaved;
    private long lastWritten;

    private final Runnable automaticTimer = new Runnable() {
        @Override
        public void run() {
            boolean shouldAutoPause = false;
            synchronized (lock) {
                if (!CsvLoggingState.isRecording()) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (segment != null && segment.hasPendingData() && now - lastSaved >= MIN_ROW_TIMEOUT_MS) {
                    segment.saveRow();
                    CsvLoggingState.incrementDataRow();
                    lastSaved = now;
                }
                if (segment != null && segment.size() > 0 && now - lastWritten >= MAX_SEGMENT_TIMEOUT_MS) {
                    writeSegmentLocked();
                }
                updateActiveNotificationLocked();
                if (now - lastWritten >= AUTOMATIC_PAUSE_MS) {
                    shouldAutoPause = true;
                }
            }
            if (shouldAutoPause) {
                stopRecordingInternal(true);
            } else if (handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        stopRecordingInternal(false);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopRecordingInternal(false);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startRecording();
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void pvChanged(PvChange change) {
        if (!CsvLoggingState.isRecording()) {
            return;
        }

        PvChangeType primary = change.getPrimaryType();
        if (primary == PvChangeType.ADDED || primary == PvChangeType.MODIFIED) {
            Object candidate = change.getValue();
            if (!(candidate instanceof EcuDataPv)) {
                Object key = change.getKey();
                candidate = key != null ? ObdProt.PidPvs.get(key) : null;
            }
            if (candidate instanceof EcuDataPv) {
                attachToPv((EcuDataPv) candidate);
                synchronized (lock) {
                    refreshColumnsLocked();
                }
            }
        } else if (primary == PvChangeType.CLEARED || primary == PvChangeType.REMOVED) {
            synchronized (lock) {
                detachAllPvsLocked();
                segment = new CsvData();
            }
        }
    }

    private void startRecording() {
        synchronized (lock) {
            if (CsvLoggingState.isRecording()) {
                updateActiveNotificationLocked();
                return;
            }

            File directory = getExternalFilesDir(null);
            if (directory == null) {
                sendStatusBroadcast(STATUS_ERROR, null);
                stopSelf();
                return;
            }

            ensureNotificationChannel();

            segment = new CsvData();
            writer = new CsvWriterThread(directory);
            writer.start();
            CsvLoggingState.resetCounters();
            CsvLoggingState.setLastFileName(null);
            CsvLoggingState.setRecording(true);
            lastSaved = System.currentTimeMillis();
            lastWritten = lastSaved;

            attachExistingPvsLocked();
            ObdProt.PidPvs.addPvChangeListener(this, PvChangeEvent.PV_ALLEVENTS);

            Notification notification = createActiveNotification();
            // connectedDevice: logging streams from the OBD adapter (manifest foregroundServiceType).
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            handler.postDelayed(automaticTimer, 1000);
        }

        sendStatusBroadcast(STATUS_STARTED, null);
    }

    private void stopRecordingInternal(boolean dueToInactivity) {
        CsvWriterThread writerToClose;
        String fileName = null;
        File outputFile = null;

        synchronized (lock) {
            if (!CsvLoggingState.isRecording()) {
                return;
            }

            if (handler != null) {
                handler.removeCallbacks(automaticTimer);
            }

            if (segment != null && segment.hasPendingData()) {
                segment.saveRow();
                CsvLoggingState.incrementDataRow();
            }
            writeSegmentLocked();

            ObdProt.PidPvs.removePvChangeListener(this);
            detachAllPvsLocked();

            writerToClose = writer;
            segment = new CsvData();
            writer = null;

            CsvLoggingState.setRecording(false);
        }

        if (writerToClose != null) {
            outputFile = writerToClose.getOutputFile();
            if (outputFile != null) {
                fileName = outputFile.getName();
                CsvLoggingState.setLastFileName(fileName);
            }
            writerToClose.closeAsync();
            writerToClose.quitSafely();
        }

        stopForeground(true);

        if (outputFile != null && outputFile.exists()) {
            showFinishedNotification(outputFile, dueToInactivity);
        }

        if (dueToInactivity) {
            sendStatusBroadcast(STATUS_AUTO_PAUSED, fileName);
        } else {
            sendStatusBroadcast(STATUS_STOPPED, fileName);
        }

        stopSelf();
    }

    private void attachExistingPvsLocked() {
        Collection collection = ObdProt.PidPvs.values();
        if (collection == null) {
            return;
        }
        for (Object value : collection) {
            if (value instanceof EcuDataPv) {
                attachToPv((EcuDataPv) value);
            }
        }
        refreshColumnsLocked();
    }

    private void attachToPv(EcuDataPv pv) {
        if (pv == null || pvListeners.containsKey(pv)) {
            return;
        }
        PvChangeListener listener = event -> {
            if (!CsvLoggingState.isRecording()) {
                return;
            }
            if (EcuDataPv.FIELDS[EcuDataPv.FID_VALUE].equals(event.getKey())) {
                handleDataSample(pv, event.getValue(), event.getTime());
            }
        };
        pv.addPvChangeListener(listener, PvChangeEvent.PV_MODIFIED);
        pvListeners.put(pv, listener);
    }

    private void detachAllPvsLocked() {
        for (Map.Entry<EcuDataPv, PvChangeListener> entry : pvListeners.entrySet()) {
            entry.getKey().removePvChangeListener(entry.getValue());
        }
        pvListeners.clear();
    }

    private void handleDataSample(EcuDataPv pv, Object value, long timestamp) {
        String mnemonic = resolveMnemonic(pv);
        String formattedValue = formatValue(pv, value);
        synchronized (lock) {
            if (segment == null) {
                segment = new CsvData();
            }
            segment.setData(mnemonic, formattedValue);
            CsvLoggingState.incrementDataPoint();
            long now = timestamp != 0 ? timestamp : System.currentTimeMillis();
            if (now - lastSaved >= MIN_ROW_TIMEOUT_MS) {
                segment.saveRow();
                CsvLoggingState.incrementDataRow();
                lastSaved = now;
            }
            if (segment.size() >= 1000) {
                writeSegmentLocked();
            }
        }
    }

    private void writeSegmentLocked() {
        if (writer == null || segment == null || segment.size() == 0) {
            return;
        }
        CsvData current = segment;
        segment = new CsvData(current);
        writer.write(current);
        lastWritten = System.currentTimeMillis();
    }

    private void refreshColumnsLocked() {
        if (segment == null) {
            segment = new CsvData();
        }
        Collection collection = ObdProt.PidPvs.values();
        if (collection == null || collection.isEmpty()) {
            return;
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (Object value : collection) {
            if (value instanceof EcuDataPv) {
                ordered.add(resolveMnemonic((EcuDataPv) value));
            }
        }
        if (!ordered.isEmpty()) {
            segment.setColumns(new ArrayList<>(ordered));
        }
    }

    private String resolveMnemonic(EcuDataPv pv) {
        Object mnemonicObj = pv.get(EcuDataPv.FID_MNEMONIC);
        if (mnemonicObj instanceof String && !TextUtils.isEmpty((String) mnemonicObj)) {
            return (String) mnemonicObj;
        }
        Object descriptor = pv.get(EcuDataPv.FID_DESCRIPT);
        if (descriptor instanceof String && !TextUtils.isEmpty((String) descriptor)) {
            return (String) descriptor;
        }
        Object pid = pv.get(EcuDataPv.FID_PID);
        return "PID_" + pid;
    }

    private String formatValue(EcuDataPv pv, Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.csv_logging_title),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.csv_logging_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createActiveNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, CsvLoggingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String text = getString(
            R.string.csv_logging_notification_text,
            CsvLoggingState.getDataPoints(),
            CsvLoggingState.getDataRows()
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.csv_logging_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_csv_24)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_save_24,
                getString(R.string.csv_logging_stop),
                stopPendingIntent
            )
            .build();
    }

    private void updateActiveNotificationLocked() {
        Notification notification = createActiveNotification();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
    }

    private void showFinishedNotification(File file, boolean dueToInactivity) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            200,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri uri = FileProvider.getUriForFile(
            this,
            getPackageName() + ".provider",
            file
        );
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(
            this,
            201,
            Intent.createChooser(shareIntent, getString(R.string.csv_logging_share)),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = getString(R.string.csv_logging_finished_text, file.getName());
        if (dueToInactivity) {
            contentText = getString(R.string.csv_logging_auto_paused, file.getName());
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.csv_logging_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_csv_24)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_share_24,
                getString(R.string.csv_logging_share),
                sharePendingIntent
            )
            .build();

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
    }

    private void sendStatusBroadcast(int status, @Nullable String fileName) {
        Intent broadcast = new Intent(ACTION_STATUS_BROADCAST);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_STATUS, status);
        if (fileName != null) {
            broadcast.putExtra(EXTRA_FILE_NAME, fileName);
        }
        sendBroadcast(broadcast);
    }

    // ========== NESTED CLASSES ==========

    /**
     * Append-only in-memory table that aggregates the most recent snapshot of PID data.
     * Rows are flushed to disk by {@link CsvWriterThread}.
     */
    static final class CsvData {

        private List<String> columns;
        private Map<String, String> columnInstances;
        private Map<String, String> latestValues;
        private Map<Long, Map<String, String>> rows;
        private boolean hasNewColumns;
        private boolean hasPendingData;

        CsvData() {
            this.columns = new ArrayList<>();
            this.columnInstances = new HashMap<>();
            this.latestValues = new HashMap<>();
            this.rows = new HashMap<>();
            this.hasNewColumns = false;
            this.hasPendingData = false;
        }

        CsvData(List<String> columns) {
            this();
            setColumns(columns);
        }

        CsvData(CsvData previous) {
            this.columns = new ArrayList<>(previous.columns);
            this.columnInstances = new HashMap<>(previous.columnInstances);
            this.latestValues = new HashMap<>(previous.latestValues);
            this.rows = new HashMap<>();
            this.hasNewColumns = false;
            this.hasPendingData = false;
        }

        void setColumns(List<String> columns) {
            this.columns = new ArrayList<>(columns);
            this.columnInstances = new HashMap<>(this.columns.size());
            for (String key : this.columns) {
                this.columnInstances.put(key, key);
            }
            this.hasNewColumns = false;
        }

        void setData(String key, String value) {
            if (key == null) {
                return;
            }
            String resolvedKey = columnInstances.get(key);
            if (resolvedKey == null) {
                columns.add(key);
                columnInstances.put(key, key);
                resolvedKey = key;
                hasNewColumns = true;
            }
            latestValues.put(resolvedKey, value);
            hasPendingData = true;
        }

        void saveRow() {
            if (!hasPendingData) {
                return;
            }
            long timestamp = System.currentTimeMillis();
            Map<String, String> row = new HashMap<>(latestValues);
            rows.put(timestamp, row);
            hasPendingData = false;
        }

        long getStartTime() {
            if (rows.isEmpty()) {
                return System.currentTimeMillis();
            }
            Long[] timestamps = rows.keySet().toArray(new Long[0]);
            Arrays.sort(timestamps);
            return timestamps[0];
        }

        long getEndTime() {
            if (rows.isEmpty()) {
                return System.currentTimeMillis();
            }
            Long[] timestamps = rows.keySet().toArray(new Long[0]);
            Arrays.sort(timestamps);
            return timestamps[timestamps.length - 1];
        }

        int size() {
            return rows.size();
        }

        boolean hasNewColumns() {
            return hasNewColumns;
        }

        boolean hasPendingData() {
            return hasPendingData;
        }

        void writeOutput(OutputStreamWriter writer, boolean includeHeader) throws IOException {
            if (includeHeader) {
                StringBuilder header = new StringBuilder();
                header.append("timestamp");
                for (String column : columns) {
                    header.append(',');
                    header.append(quoteCell(column));
                }
                header.append("\r\n");
                writer.write(header.toString());
            }

            Long[] timestamps = rows.keySet().toArray(new Long[0]);
            Arrays.sort(timestamps);
            for (Long timestamp : timestamps) {
                Map<String, String> row = rows.get(timestamp);
                if (row == null) {
                    continue;
                }
                StringBuilder dataRow = new StringBuilder();
                dataRow.append(timestamp);
                for (String column : columns) {
                    dataRow.append(',');
                    dataRow.append(quoteCell(row.get(column)));
                }
                dataRow.append("\r\n");
                writer.write(dataRow.toString());
            }
        }

        private static String quoteCell(String value) {
            if (value == null) {
                return "";
            }
            boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
            String escaped = value.replace("\"", "\"\"");
            if (needsQuote) {
                return "\"" + escaped + "\"";
            }
            return escaped;
        }
    }

    /**
     * In-memory state container for the CSV logging feature.
     * Provides quick access for UI components without needing to bind to the service.
     */
    public static final class CsvLoggingState {

        private static final AtomicBoolean recording = new AtomicBoolean(false);
        private static final AtomicInteger dataPoints = new AtomicInteger();
        private static final AtomicInteger dataRows = new AtomicInteger();
        private static final AtomicReference<String> lastFileName = new AtomicReference<>();

        private CsvLoggingState() {
            // utility holder
        }

        public static boolean isRecording() {
            return recording.get();
        }

        static void setRecording(boolean active) {
            recording.set(active);
        }

        public static int getDataPoints() {
            return dataPoints.get();
        }

        public static int getDataRows() {
            return dataRows.get();
        }

        static void resetCounters() {
            dataPoints.set(0);
            dataRows.set(0);
        }

        static void incrementDataPoint() {
            dataPoints.incrementAndGet();
        }

        static void incrementDataRow() {
            dataRows.incrementAndGet();
        }

        public static String getLastFileName() {
            return lastFileName.get();
        }

        static void setLastFileName(String fileName) {
            lastFileName.set(fileName);
        }
    }

    /**
     * Serialises {@link CsvData} segments to disk on a background thread.
     */
    static final class CsvWriterThread extends HandlerThread {

        private static final String TAG = "CsvWriterThread";

        private final List<CsvData> queue;
        private Handler handler;
        private final File path;
        private File outputFile;
        private final SimpleDateFormat timestampFormatter;
        private OutputStreamWriter writer;
        private boolean alreadyLoggedError;

        CsvWriterThread(File path) {
            super("CsvWriterThread");
            this.queue = new ArrayList<>();
            this.path = path;
            this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss'Z'", Locale.US);
            this.alreadyLoggedError = false;
            testWrite();
        }

        private void testWrite() {
            if (path == null) {
                Log.e(TAG, "External files directory unavailable");
                return;
            }
            try {
                File destination = new File(path, "test.txt");
                OutputStreamWriter test = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(destination)));
                test.write("Test write\n");
                test.close();
                //noinspection ResultOfMethodCallIgnored
                destination.delete();
            } catch (IOException e) {
                Log.e(TAG, "Error confirming write permission", e);
            }
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            handler = new Handler(getLooper());
        }

        void write(CsvData data) {
            synchronized (queue) {
                queue.add(data);
            }
            if (handler != null) {
                handler.post(this::writeOut);
            }
        }

        private OutputStreamWriter openWriter(CsvData segment) throws IOException {
            if (path == null) {
                throw new IOException("No storage directory available");
            }
            Date timestamp = new Date(segment.getStartTime());
            String filename = "obddroid_" + timestampFormatter.format(timestamp) + ".csv";
            File destination = new File(path, filename);
            this.outputFile = destination;
            return new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(destination)));
        }

        String getFilename() {
            return outputFile != null ? outputFile.getName() : null;
        }

        File getOutputFile() {
            return outputFile;
        }

        boolean isOpen() {
            return writer != null;
        }

        private void writeOut() {
            CsvData segment;
            synchronized (queue) {
                if (queue.isEmpty()) {
                    return;
                }
                segment = queue.remove(0);
            }

            if (segment != null && segment.size() > 0) {
                writeOut(segment);

                synchronized (queue) {
                    if (!queue.isEmpty() && handler != null) {
                        handler.removeCallbacks(this::writeOut);
                        handler.post(this::writeOut);
                    }
                }
            }
        }

        private void writeOut(CsvData segment) {
            try {
                if (writer != null && segment.hasNewColumns()) {
                    writer.close();
                    writer = null;
                }

                if (writer == null) {
                    writer = openWriter(segment);
                    segment.writeOutput(writer, true);
                } else {
                    segment.writeOutput(writer, false);
                }
                writer.flush();
            } catch (IOException e) {
                if (!alreadyLoggedError) {
                    Log.w(TAG, "Error while outputting csv data", e);
                } else {
                    Log.w(TAG, "Error while outputting csv data: " + e.getMessage());
                }
                alreadyLoggedError = true;
            }
        }

        void closeAsync() {
            if (handler != null) {
                handler.post(this::attemptClose);
            }
        }

        private void attemptClose() {
            boolean isEmpty;
            synchronized (queue) {
                isEmpty = queue.isEmpty();
            }
            if (isEmpty) {
                closeNow();
            } else if (handler != null) {
                handler.post(this::attemptClose);
            }
        }

        private void closeNow() {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error while finishing writing csv data", e);
                }
                writer = null;
                outputFile = null;
            }
        }

        @Override
        public boolean quitSafely() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                return super.quitSafely();
            } else {
                if (handler != null) {
                    handler.post(this::quit);
                }
                return true;
            }
        }
    }

    /**
     * Entry-point helpers for starting or stopping the CSV logging foreground service.
     */
    public static final class CsvLoggingController {

        private CsvLoggingController() {
            // utility holder
        }

        public static void toggleLogging(Context context) {
            if (CsvLoggingState.isRecording()) {
                stopLogging(context);
            } else {
                startLogging(context);
            }
        }

        public static void startLogging(Context context) {
            Intent intent = new Intent(context, CsvLoggingService.class);
            intent.setAction(CsvLoggingService.ACTION_START);
            startService(context, intent);
        }

        public static void stopLogging(Context context) {
            Intent intent = new Intent(context, CsvLoggingService.class);
            intent.setAction(CsvLoggingService.ACTION_STOP);
            startService(context, intent);
        }

        private static void startService(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        }
    }
}
