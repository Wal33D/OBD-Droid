package com.obddroid.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.obddroid.R;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.utils.DebugLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for displaying OBD data items in a staggered grid with sections
 */
public class ObdRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;
    private static final int VIEW_TYPE_MAP = 3;
    private static final int VIEW_TYPE_TILT = 4;

    private final Context context;
    private List<ListItem> items = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangedListener selectionListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    // Wrapper class for list items (either header, data, map, or tilt)
    private static class ListItem {
        final boolean isHeader;
        final boolean isFullWidth;
        final boolean isMap;
        final boolean isTilt;
        final String headerTitle;
        final EcuDataPv dataPv;

        ListItem(String headerTitle) {
            this.isHeader = true;
            this.isFullWidth = false;
            this.isMap = false;
            this.isTilt = false;
            this.headerTitle = headerTitle;
            this.dataPv = null;
        }

        ListItem(EcuDataPv dataPv, boolean isFullWidth) {
            this.isHeader = false;
            this.isFullWidth = isFullWidth;
            this.isMap = false;
            this.isTilt = false;
            this.headerTitle = null;
            this.dataPv = dataPv;
        }

        // Special constructor for map/tilt tiles
        private ListItem(boolean isMapTile, boolean isTiltTile) {
            this.isHeader = false;
            this.isFullWidth = false;
            this.isMap = isMapTile;
            this.isTilt = isTiltTile;
            this.headerTitle = null;
            this.dataPv = null;
        }

        static ListItem createMapTile() {
            return new ListItem(true, false);
        }

        static ListItem createTiltTile() {
            return new ListItem(false, true);
        }
    }

    public ObdRecyclerAdapter(Context context, TypedPvList<String, EcuDataPv> pvList, OnSelectionChangedListener listener) {
        this.context = context;
        this.selectionListener = listener;
        updateData(pvList);
    }

    public void updateData(TypedPvList<String, EcuDataPv> pvList) {
        items.clear();

        if (pvList == null || pvList.isEmpty()) {
            DebugLog.d("ObdRecyclerAdapter", "updateData: pvList is null or empty");
            notifyDataSetChanged();
            return;
        }

        DebugLog.d("ObdRecyclerAdapter", "updateData: pvList size = " + pvList.size());

        // Categorize items
        List<EcuDataPv> gpsItems = new ArrayList<>();
        List<EcuDataPv> motionItems = new ArrayList<>();
        List<EcuDataPv> temperatureItems = new ArrayList<>();
        List<EcuDataPv> pressureItems = new ArrayList<>();
        List<EcuDataPv> fuelSystemItems = new ArrayList<>();
        List<EcuDataPv> oxygenSensorItems = new ArrayList<>();
        List<EcuDataPv> testStatusItems = new ArrayList<>();
        List<EcuDataPv> diagnosticItems = new ArrayList<>();
        List<EcuDataPv> unidentifiedItems = new ArrayList<>();
        List<EcuDataPv> obdItems = new ArrayList<>();

        // Create a snapshot to avoid ConcurrentModificationException
        List<java.util.Map.Entry<String, EcuDataPv>> entries = new ArrayList<>(pvList.entrySetTyped());

        for (java.util.Map.Entry<String, EcuDataPv> entry : entries) {
            EcuDataPv pv = entry.getValue();
            String key = String.valueOf(entry.getKey());
            Object description = pv.get(EcuDataPv.FID_DESCRIPT);
            String desc = description != null ? String.valueOf(description) : "";
            String descUpper = desc.toUpperCase();

            // Categorize based on key or description
            // IMPORTANT: Check for test/monitor/system status FIRST before other categories
            if (descUpper.contains("NUMBER OF FAULT CODES") ||
                descUpper.contains("MIL STATUS") ||
                descUpper.contains("MALFUNCTION INDICATOR") ||
                (descUpper.contains("WARM") && descUpper.contains("SINCE")) ||
                (descUpper.contains("DISTANCE") && descUpper.contains("SINCE") && descUpper.contains("MIL"))) {
                // MIL, Fault Codes, Warm-ups since reset, and Distance since MIL activated go to Diagnostic Status section
                diagnosticItems.add(pv);
            } else if ((descUpper.contains("TEST") && descUpper.contains("STATUS")) ||
                       (descUpper.contains("MONITOR") && descUpper.contains("STATUS")) ||
                       (descUpper.contains("SYSTEM") && descUpper.contains("STATUS")) ||
                       descUpper.contains("MISFIRE")) {
                // Test/Monitor/System status fields + Misfire go to Readiness Monitors (full-width)
                testStatusItems.add(pv);
            } else if (key.startsWith("F100") || descUpper.contains("GPS")) {
                gpsItems.add(pv);
            } else if (key.startsWith("F200") || descUpper.contains("ACCEL") || descUpper.contains("GYRO")) {
                motionItems.add(pv);
            } else if ((descUpper.contains("TEMP") || descUpper.contains("TEMPERATURE")) &&
                       !descUpper.contains("SENSOR") &&
                       !descUpper.contains("CIRCUIT")) {
                // Temperature readings (coolant, intake air, oil, catalyst, transmission, etc.)
                temperatureItems.add(pv);
            } else if ((descUpper.contains("PRESSURE") || descUpper.contains("VACUUM")) &&
                       !descUpper.contains("SENSOR") &&
                       !descUpper.contains("CIRCUIT")) {
                // Pressure readings (fuel, manifold, barometric, evap, etc.)
                pressureItems.add(pv);
            } else if ((descUpper.contains("FUEL") &&
                       (descUpper.contains("TRIM") || descUpper.contains("LEVEL") ||
                        descUpper.contains("RATE") || descUpper.contains("RAIL") ||
                        descUpper.contains("TYPE"))) ||
                       descUpper.contains("INJECTOR") ||
                       descUpper.contains("ETHANOL")) {
                // Fuel system parameters (excluding "FUEL SYSTEM STATUS" which is caught above)
                fuelSystemItems.add(pv);
            } else if (descUpper.contains("OXYGEN SENSOR") && descUpper.contains("PRESENT")) {
                oxygenSensorItems.add(pv);
            } else if (desc.isEmpty() ||
                       descUpper.startsWith("PID ") ||
                       descUpper.startsWith("VID ") ||
                       descUpper.matches("^[0-9A-F]+$") ||
                       key.equals(desc)) {
                // Unidentified PIDs/VIDs: empty desc, starts with "PID "/"VID ", is hex value, or desc equals key
                unidentifiedItems.add(pv);
            } else {
                // Everything else goes into Live OBD Data
                obdItems.add(pv);
            }
        }

        // Build sectioned list - Diagnostic Status first
        if (!diagnosticItems.isEmpty()) {
            items.add(new ListItem("🔧 Diagnostic Status"));
            for (EcuDataPv pv : diagnosticItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        // Live OBD Data after Diagnostic Status
        if (!obdItems.isEmpty()) {
            items.add(new ListItem("🚗 Live OBD Data"));
            for (EcuDataPv pv : obdItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        // Temperature sensors section
        if (!temperatureItems.isEmpty()) {
            items.add(new ListItem("🌡️ Temperature Sensors"));
            for (EcuDataPv pv : temperatureItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        // Pressure sensors section
        if (!pressureItems.isEmpty()) {
            items.add(new ListItem("🔘 Pressure Sensors"));
            for (EcuDataPv pv : pressureItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        // Fuel system section
        if (!fuelSystemItems.isEmpty()) {
            items.add(new ListItem("⛽ Fuel System"));
            for (EcuDataPv pv : fuelSystemItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        // Oxygen sensors in specific section
        if (!oxygenSensorItems.isEmpty()) {
            items.add(new ListItem("💨 Oxygen Sensors"));
            for (EcuDataPv pv : oxygenSensorItems) {
                items.add(new ListItem(pv, true)); // FULL WIDTH
            }
        }

        // Test/Monitor/System status fields in separate section
        if (!testStatusItems.isEmpty()) {
            items.add(new ListItem("📋 Readiness Monitors"));
            for (EcuDataPv pv : testStatusItems) {
                items.add(new ListItem(pv, true)); // FULL WIDTH
            }
        }

        // Separate section for unidentified PIDs/VIDs
        if (!unidentifiedItems.isEmpty()) {
            items.add(new ListItem("❓ Unidentified PID/VID"));
            for (EcuDataPv pv : unidentifiedItems) {
                items.add(new ListItem(pv, true)); // FULL WIDTH
            }
        }

        // GPS Telemetry moved to bottom
        if (!gpsItems.isEmpty()) {
            items.add(new ListItem("📍 GPS Telemetry"));
            for (EcuDataPv pv : gpsItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
            // Add map preview tile at the end
            ListItem mapTile = ListItem.createMapTile();
            items.add(mapTile);
            DebugLog.d("ObdRecyclerAdapter", "Added map tile to items list. Map tile isMap=" + mapTile.isMap);
        }

        // Motion Telemetry at the very bottom
        if (!motionItems.isEmpty()) {
            items.add(new ListItem("📱 Motion Telemetry"));
            for (EcuDataPv pv : motionItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
            // Add tilt indicator tile at the end
            ListItem tiltTile = ListItem.createTiltTile();
            items.add(tiltTile);
            DebugLog.d("ObdRecyclerAdapter", "Added tilt tile to items list. Tilt tile isTilt=" + tiltTile.isTilt);
        }

        // Log categorization summary
        DebugLog.d("ObdRecyclerAdapter", String.format(
            "updateData summary: total=%d, diagnostic=%d, obd=%d, temp=%d, pressure=%d, fuel=%d, o2=%d, test=%d, unidentified=%d, gps=%d, motion=%d, finalItems=%d",
            pvList.size(), diagnosticItems.size(), obdItems.size(), temperatureItems.size(), pressureItems.size(),
            fuelSystemItems.size(), oxygenSensorItems.size(), testStatusItems.size(), unidentifiedItems.size(),
            gpsItems.size(), motionItems.size(), items.size()));

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ListItem item = items.get(position);
        if (item.isHeader) {
            return VIEW_TYPE_HEADER;
        } else if (item.isMap) {
            return VIEW_TYPE_MAP;
        } else if (item.isTilt) {
            return VIEW_TYPE_TILT;
        } else if (item.isFullWidth) {
            return 2; // Full-width item type
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.obd_section_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == VIEW_TYPE_MAP) {
            // Map tile layout
            View view = LayoutInflater.from(context).inflate(R.layout.gps_map_tile, parent, false);
            return new MapViewHolder(view);
        } else if (viewType == VIEW_TYPE_TILT) {
            // Tilt indicator tile layout
            View view = LayoutInflater.from(context).inflate(R.layout.motion_tilt_tile, parent, false);
            return new TiltViewHolder(view);
        } else if (viewType == 2) {
            // Full-width layout for diagnostic tests
            View view = LayoutInflater.from(context).inflate(R.layout.obd_item_full_width, parent, false);
            return new ItemViewHolder(view);
        } else {
            // Grid tile layout
            View view = LayoutInflater.from(context).inflate(R.layout.obd_item_grid, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = items.get(position);
        DebugLog.d("ObdRecyclerAdapter", String.format("onBindViewHolder pos=%d, isHeader=%s, isMap=%s, holder=%s",
            position, item.isHeader, item.isMap, holder.getClass().getSimpleName()));

        if (holder instanceof HeaderViewHolder) {
            // Bind header
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.title.setText(item.headerTitle);

            // Make header span full width
            StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams) headerHolder.itemView.getLayoutParams();
            layoutParams.setFullSpan(true);

        } else if (holder instanceof MapViewHolder) {
            // Bind map tile - normal grid size (NOT full width)
            MapViewHolder mapHolder = (MapViewHolder) holder;

            // Load map with GPS coordinates from the GPS telemetry data
            loadMapForGpsData(mapHolder);

        } else if (holder instanceof TiltViewHolder) {
            // Bind tilt indicator tile - normal grid size (NOT full width)
            TiltViewHolder tiltHolder = (TiltViewHolder) holder;

            // Update tilt indicator with acceleration data from motion telemetry
            loadTiltDataForMotion(tiltHolder);

        } else if (holder instanceof ItemViewHolder) {
            // Bind data item
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            EcuDataPv pv = item.dataPv;

            if (pv == null) return;

            // Set full-width for test items
            StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams) itemHolder.itemView.getLayoutParams();
            layoutParams.setFullSpan(item.isFullWidth);

            // Description
            Object description = pv.get(EcuDataPv.FID_DESCRIPT);
            itemHolder.label.setText(description != null ? String.valueOf(description) : "");

            // Format value
            Object colVal = pv.get(EcuDataPv.FID_VALUE);
            Object cnvObj = pv.get(EcuDataPv.FID_CNVID);
            String fmtText;

            try {
                if (cnvObj instanceof Conversion[] && ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem] != null) {
                    Conversion cnv = ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem];
                    fmtText = cnv.physToPhysFmtString((Number) colVal, (String) pv.get(EcuDataPv.FID_FORMAT));
                } else {
                    fmtText = String.valueOf(colVal);
                }
            } catch (Exception e) {
                fmtText = String.valueOf(colVal);
            }

            itemHolder.value.setText(fmtText);
            itemHolder.units.setText(pv.getUnits());

            // Color coding
            int pidColor = ColorAdapter.getItemColor(pv);
            itemHolder.value.setTextColor(pidColor);

            // Progress bar (optional)
            itemHolder.progressBar.setVisibility(View.GONE);

            // Selection state
            boolean isSelected = selectedPositions.contains(position);
            itemHolder.cardView.setCardBackgroundColor(isSelected ?
                Color.parseColor("#3D5AFE") :
                context.getResources().getColor(R.color.background_secondary));

            // Click and long-click to toggle selection
            View.OnClickListener toggleListener = v -> toggleSelection(itemHolder.getAdapterPosition());
            itemHolder.itemView.setOnClickListener(toggleListener);
            itemHolder.itemView.setOnLongClickListener(v -> {
                toggleSelection(itemHolder.getAdapterPosition());
                return true;
            });
        }
    }

    public void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }

        // Don't allow selection of headers
        if (items.get(position).isHeader) {
            return;
        }

        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }

        notifyDataSetChanged();

        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedPositions.size());
        }
    }

    public void clearSelection() {
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public int[] getSelectedPositions() {
        int[] positions = new int[selectedPositions.size()];
        int i = 0;
        for (int pos : selectedPositions) {
            positions[i++] = pos;
        }
        return positions;
    }

    /**
     * Get the actual EcuDataPv objects for selected positions (for chart compatibility)
     */
    public List<EcuDataPv> getSelectedItems() {
        List<EcuDataPv> selectedItems = new ArrayList<>();
        for (int pos : selectedPositions) {
            if (pos >= 0 && pos < items.size()) {
                ListItem item = items.get(pos);
                if (!item.isHeader && item.dataPv != null) {
                    selectedItems.add(item.dataPv);
                }
            }
        }
        return selectedItems;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Header ViewHolder
    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.section_title);
        }
    }

    // Map ViewHolder
    public static class MapViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView mapPreview;
        long lastUpdateTime = 0;
        double lastLatitude = 0.0;
        double lastLongitude = 0.0;

        public MapViewHolder(@NonNull View itemView) {
            super(itemView);
            mapPreview = itemView.findViewById(R.id.map_preview);
        }
    }

    // Tilt Indicator ViewHolder
    public static class TiltViewHolder extends RecyclerView.ViewHolder {
        com.obddroid.ui.views.TiltIndicatorView tiltIndicator;

        public TiltViewHolder(@NonNull View itemView) {
            super(itemView);
            tiltIndicator = itemView.findViewById(R.id.tilt_indicator);
        }
    }

    // Item ViewHolder
    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView label;
        TextView value;
        TextView units;
        ProgressBar progressBar;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            label = itemView.findViewById(R.id.obd_label);
            value = itemView.findViewById(R.id.obd_value);
            units = itemView.findViewById(R.id.obd_units);
            progressBar = itemView.findViewById(R.id.bar);
        }
    }

    /**
     * Load map tile with GPS coordinates from GPS telemetry data
     */
    private void loadMapForGpsData(MapViewHolder holder) {
        DebugLog.d("ObdRecyclerAdapter", "loadMapForGpsData called");

        try {
            // Find GPS lat/lon from ObdProt.PidPvs
            // GPS fields use sequential PIDs: F100=Lat, F101=Lon, F102=Alt, F103=Bearing, F104=Speed
            com.obddroid.ecu.EcuDataPv latPv = com.obddroid.obd.ObdProt.PidPvs.getTyped("F100.0.0");
            com.obddroid.ecu.EcuDataPv lonPv = com.obddroid.obd.ObdProt.PidPvs.getTyped("F101.0.0");

            DebugLog.d("ObdRecyclerAdapter", String.format("GPS PVs found: lat=%s, lon=%s", latPv != null, lonPv != null));

            if (latPv != null && lonPv != null) {
                Object latValue = latPv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE);
                Object lonValue = lonPv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE);

                DebugLog.d("ObdRecyclerAdapter", String.format("GPS values: lat=%s, lon=%s", latValue, lonValue));

                if (latValue instanceof Number && lonValue instanceof Number) {
                    double latitude = ((Number) latValue).doubleValue();
                    double longitude = ((Number) lonValue).doubleValue();

                    // Check if GPS coordinates are valid (not 0,0 or near 0,0 which indicates no GPS lock)
                    if (Math.abs(latitude) < 0.0001 && Math.abs(longitude) < 0.0001) {
                        DebugLog.d("ObdRecyclerAdapter", "Skipping map update - invalid GPS coordinates (0,0)");
                        return;
                    }

                    // Calculate distance from last position (in degrees, rough approximation)
                    double latDiff = Math.abs(latitude - holder.lastLatitude);
                    double lonDiff = Math.abs(longitude - holder.lastLongitude);
                    double distanceChanged = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);

                    // Check if location changed significantly (>0.001 degrees ≈ 100 meters)
                    boolean locationChangedSignificantly = distanceChanged > 0.001;

                    // Check if enough time has passed
                    long currentTime = System.currentTimeMillis();
                    boolean enoughTimePassed = (currentTime - holder.lastUpdateTime) >= 15000;

                    // Update if: first load, location changed significantly, or 15 seconds passed
                    boolean shouldUpdate = (holder.lastUpdateTime == 0) || locationChangedSignificantly || enoughTimePassed;

                    if (!shouldUpdate) {
                        DebugLog.d("ObdRecyclerAdapter", "Skipping map update - no significant change");
                        return;
                    }

                    DebugLog.d("ObdRecyclerAdapter", String.format("Loading map for coords: lat=%f, lon=%f (distance changed: %f)",
                        latitude, longitude, distanceChanged));

                    // Update tracking variables
                    holder.lastUpdateTime = currentTime;
                    holder.lastLatitude = latitude;
                    holder.lastLongitude = longitude;

                    // Load map tile at zoom level 15 (street level)
                    com.obddroid.utils.MapTileHelper.loadMapTile(latitude, longitude, 15, holder.mapPreview);

                    // Make map clickable to open external maps app
                    holder.itemView.setOnClickListener(v -> openExternalMap(latitude, longitude));
                } else {
                    android.util.Log.w("ObdRecyclerAdapter", "GPS values are not numbers");
                }
            } else {
                android.util.Log.w("ObdRecyclerAdapter", "GPS PVs are null");
            }
        } catch (Exception e) {
            android.util.Log.e("ObdRecyclerAdapter", "Failed to load GPS map: " + e.getMessage(), e);
        }
    }

    /**
     * Open external maps app with GPS coordinates
     */
    private void openExternalMap(double latitude, double longitude) {
        try {
            android.net.Uri geoUri = android.net.Uri.parse(String.format("geo:%f,%f?z=15", latitude, longitude));
            android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri);
            context.startActivity(mapIntent);
        } catch (Exception e) {
            android.util.Log.e("ObdRecyclerAdapter", "Failed to open map: " + e.getMessage());
        }
    }

    /**
     * Load tilt indicator with acceleration data from motion telemetry
     */
    private void loadTiltDataForMotion(TiltViewHolder holder) {
        DebugLog.d("ObdRecyclerAdapter", "loadTiltDataForMotion called");

        try {
            // Find acceleration X/Y from ObdProt.PidPvs
            // Motion fields use F2xx PIDs: F200=AccelX, F201=AccelY, F202=AccelZ
            com.obddroid.ecu.EcuDataPv accelXPv = com.obddroid.obd.ObdProt.PidPvs.getTyped("F200.0.0");
            com.obddroid.ecu.EcuDataPv accelYPv = com.obddroid.obd.ObdProt.PidPvs.getTyped("F201.0.0");

            DebugLog.d("ObdRecyclerAdapter", String.format("Accel PVs found: X=%s, Y=%s", accelXPv != null, accelYPv != null));

            if (accelXPv != null && accelYPv != null) {
                Object accelXValue = accelXPv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE);
                Object accelYValue = accelYPv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE);

                DebugLog.d("ObdRecyclerAdapter", String.format("Accel values: X=%s, Y=%s", accelXValue, accelYValue));

                if (accelXValue instanceof Number && accelYValue instanceof Number) {
                    float accelX = ((Number) accelXValue).floatValue();
                    float accelY = ((Number) accelYValue).floatValue();

                    DebugLog.d("ObdRecyclerAdapter", String.format("Updating tilt indicator: X=%f, Y=%f", accelX, accelY));

                    // Update the tilt indicator view
                    holder.tiltIndicator.setAcceleration(accelX, accelY);
                } else {
                    android.util.Log.w("ObdRecyclerAdapter", "Acceleration values are not numbers");
                }
            } else {
                android.util.Log.w("ObdRecyclerAdapter", "Acceleration PVs are null");
            }
        } catch (Exception e) {
            android.util.Log.e("ObdRecyclerAdapter", "Failed to load tilt data: " + e.getMessage(), e);
        }
    }
}
