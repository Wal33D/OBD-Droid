package com.obddroid.ui.activities;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ListAdapter;

import android.app.AlertDialog;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import com.obddroid.R;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.ui.adapters.ColorAdapter;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.ui.components.AutoHider;
import com.obddroid.utils.ExportTask;
import com.obddroid.utils.HelpDialogUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

/**
 * Activity that displays real-time line charts of OBD sensor data.
 * Migrated from AChartEngine to MPAndroidChart for modern Android compatibility.
 */
public class ChartActivity extends AppCompatActivity {

    public static final long MIN_UPDATE_TIME = 1000;
    public static final String POSITIONS = "POSITIONS";

    /** Line style patterns */
    private static final int STYLE_SOLID = 0;
    private static final int STYLE_DASHED = 1;
    private static final int STYLE_DOTTED = 2;

    private final TreeSet<Integer> pidNumbers = new TreeSet<>();
    private LineChart chart;
    private AutoHider toolBarHider;
    private static WakeLock wakeLock;
    private static ListAdapter mAdapter = null;
    private final List<ChartSeriesData> seriesDataList = new ArrayList<>();

    private long startTime;

    /**
     * Holder for chart series data with associated PID info
     */
    private static class ChartSeriesData {
        EcuDataPv pv;
        List<Entry> entries;
        int color;
        int lineStyle;
        String label;

        ChartSeriesData(EcuDataPv pv, int color, int lineStyle) {
            this.pv = pv;
            this.entries = new ArrayList<>();
            this.color = color;
            this.lineStyle = lineStyle;
            this.label = pv.get(EcuDataPv.FID_DESCRIPT) + " (" + pv.get(EcuDataPv.FID_UNITS) + ")";
        }
    }

    public static ListAdapter getAdapter() {
        return mAdapter;
    }

    public static void setAdapter(ListAdapter adapter) {
        mAdapter = adapter;
    }

    private static int getLineStyle(int id) {
        int[] styles = {STYLE_SOLID, STYLE_DASHED, STYLE_DOTTED};
        return styles[(id / ColorAdapter.colors.length) % styles.length];
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chart, menu);
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);

        // Set status bar and navigation bar colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#212121"));
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
        }

        // Apply fullscreen based on preference
        if (MainActivity.prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false)) {
            WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (windowInsetsController != null) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
                windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }

        // Keep screen on for vehicle diagnostics
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Prevent activity from falling asleep
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = Objects.requireNonNull(powerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, getString(R.string.app_name));
        wakeLock.acquire();

        // Hide action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        setTitle(R.string.chart);
        setContentView(R.layout.activity_chart);

        chart = findViewById(R.id.chart);
        startTime = System.currentTimeMillis();

        // Get PIDs to be shown
        int[] positions = getIntent().getIntArrayExtra(POSITIONS);
        if (positions != null) {
            setupChart();
            setupChartData(positions);
            MainActivity.setFixedPids(pidNumbers);
        }

        // Setup auto-hide toolbar if enabled
        if (MainActivity.prefs.getBoolean(MainActivity.PREF_AUTOHIDE, false)) {
            int timeout = Integer.parseInt(
                MainActivity.prefs.getString(MainActivity.PREF_AUTOHIDE_DELAY, "15"));
            toolBarHider = new AutoHider(this, mHandler, timeout * 1000L);
            toolBarHider.start(1000);
            chart.setOnTouchListener(toolBarHider);
        }
    }

    /**
     * Configure chart appearance and behavior
     */
    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.parseColor("#212121"));

        // X-Axis (Time)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.DKGRAY);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
            @Override
            public String getFormattedValue(float value) {
                return sdf.format(new Date((long) value));
            }
        });

        // Y-Axis (Left)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.DKGRAY);

        // Y-Axis (Right)
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setTextColor(Color.WHITE);
        rightAxis.setDrawGridLines(false);

        // Legend
        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setWordWrapEnabled(true);
    }

    /**
     * Set up chart data series for selected PIDs
     */
    private void setupChartData(int[] positions) {
        pidNumbers.clear();
        seriesDataList.clear();

        for (int position : positions) {
            EcuDataPv currPv = (EcuDataPv) mAdapter.getItem(position);
            if (currPv == null) continue;

            int pid = currPv.getAsInt(EcuDataPv.FID_PID);
            pidNumbers.add(pid);

            int color = ColorAdapter.getItemColor(currPv);
            int lineStyle = getLineStyle(pid != 0 ? pid : position);

            ChartSeriesData seriesData = new ChartSeriesData(currPv, color, lineStyle);

            // Add initial data point
            float initialValue = Float.parseFloat(currPv.get(EcuDataPv.FID_VALUE).toString());
            seriesData.entries.add(new Entry(startTime, initialValue));

            seriesDataList.add(seriesData);
        }

        updateChartDisplay();
    }

    /**
     * Update chart display with current data
     */
    private void updateChartDisplay() {
        List<LineDataSet> dataSets = new ArrayList<>();

        for (ChartSeriesData seriesData : seriesDataList) {
            LineDataSet dataSet = new LineDataSet(seriesData.entries, seriesData.label);
            dataSet.setColor(seriesData.color);
            dataSet.setCircleColor(seriesData.color);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawCircles(false);  // Don't draw circles for performance
            dataSet.setDrawValues(false);
            dataSet.setMode(LineDataSet.Mode.LINEAR);

            // Apply line style
            switch (seriesData.lineStyle) {
                case STYLE_DASHED:
                    dataSet.enableDashedLine(10f, 5f, 0f);
                    break;
                case STYLE_DOTTED:
                    dataSet.enableDashedLine(2f, 5f, 0f);
                    break;
                case STYLE_SOLID:
                default:
                    dataSet.disableDashedLine();
                    break;
            }

            dataSets.add(dataSet);
        }

        LineData lineData = new LineData(dataSets.toArray(new LineDataSet[0]));
        chart.setData(lineData);
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    /**
     * Update data points from current PV values
     */
    private void updateDataPoints() {
        long currentTime = System.currentTimeMillis();
        boolean dataChanged = false;

        for (ChartSeriesData seriesData : seriesDataList) {
            Object raw = seriesData.pv.get(EcuDataPv.FID_VALUE);
            if (raw == null) {
                continue; // no value yet
            }
            try {
                float value = Float.parseFloat(raw.toString());
                seriesData.entries.add(new Entry(currentTime, value));

                // Limit to last 300 points (5 minutes at 1Hz)
                if (seriesData.entries.size() > 300) {
                    seriesData.entries.remove(0);
                }

                dataChanged = true;
            } catch (NumberFormatException e) {
                // Skip invalid values
            }
        }

        if (dataChanged) {
            updateChartDisplay();
        }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MainActivity.MESSAGE_UPDATE_VIEW:
                    updateDataPoints();
                    break;

                case MainActivity.MESSAGE_TOOLBAR_VISIBLE:
                    ActionBar ab = getSupportActionBar();
                    if (ab != null) {
                        ab.hide();
                    }
                    break;
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.share) {
            new ExportTask(this).execute(seriesDataList);
        } else if (item.getItemId() == R.id.action_info) {
            showInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (toolBarHider != null) {
            toolBarHider.cancel();
            toolBarHider = null;
        }
        ObdProt.resetFixedPid();
        wakeLock.release();
        super.onDestroy();
    }

    private final Timer refreshTimer = new Timer();

    private final TimerTask updateTask = new TimerTask() {
        @Override
        public void run() {
            Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_UPDATE_VIEW);
            mHandler.sendMessage(msg);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        try {
            refreshTimer.schedule(updateTask, 0, 1000);
        } catch (IllegalStateException e) {
            // Exception ignored
        }
    }

    @Override
    protected void onStop() {
        refreshTimer.purge();
        super.onStop();
    }

    private void showInfoDialog() {
        HelpDialogUtils.showHelpDialog(
            this,
            R.string.chart_info_title,
            R.string.chart_info_message,
            R.string.chart_info_ack,
            android.R.drawable.ic_menu_info_details
        );
    }
}
