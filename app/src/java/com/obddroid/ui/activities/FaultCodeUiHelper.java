package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.app.SearchManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.obddroid.R;
import com.obddroid.ecu.EcuCodeItem;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.FreezeFrameManager;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvList;
import com.obddroid.services.CommService;
import com.obddroid.services.FaultCodeService;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.services.VehicleManager;
import com.obddroid.features.copilot.ui.CoPilotActivity;
import com.obddroid.utils.VehicleData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI helper for displaying and interacting with fault code related dialogs.
 */
final class FaultCodeUiHelper
{
    private static final Logger log = Logger.getLogger(FaultCodeUiHelper.class.getName());
    private static final Map<String, String> NONDA_VIDEO_MAP = buildNondaVideoMap();

    private FaultCodeUiHelper()
    {
        // Utility class
    }

    /**
     * Show fault code options modal for FaultCodesActivity.
     * This is the new standalone Fault Codes page.
     */
    static void showFaultCodeOptionsModal(FaultCodesActivity activity,
                                          FaultCodeService.FaultCodeInfo faultCode)
    {
        // Check connection state
        boolean isConnected = CommService.elm != null &&
                (CommService.elm.getStatus() == ElmProt.STAT.CONNECTED ||
                 CommService.elm.getStatus() == ElmProt.STAT.ECU_DETECTED);

        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_fault_code_options, null);

        TextView codeNumber = dialogView.findViewById(R.id.fault_code_number);
        TextView codeDesc = dialogView.findViewById(R.id.fault_code_description);
        TextView codeType = dialogView.findViewById(R.id.fault_code_type);
        ImageView statusIcon = dialogView.findViewById(R.id.fault_code_status_icon);

        codeNumber.setText(faultCode.code);
        codeDesc.setText(faultCode.description);

        // Set code type and icon based on pending status
        if (faultCode.isPending)
        {
            codeType.setText("Pending Code");
            statusIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
            statusIcon.setColorFilter(Color.parseColor("#FF9800"));
        }
        else
        {
            codeType.setText("Confirmed Code");
            statusIcon.setImageResource(android.R.drawable.ic_menu_myplaces);
            statusIcon.setColorFilter(Color.parseColor("#F57C00"));
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();

        // Set transparent background to prevent white corners
        if (dialog.getWindow() != null)
        {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View freezeFrameOption = dialogView.findViewById(R.id.option_freeze_frame);
        View searchOption = dialogView.findViewById(R.id.option_search_web);
        View nondaOption = dialogView.findViewById(R.id.option_watch_nonda);
        View askCopilotOption = dialogView.findViewById(R.id.option_ask_ai);
        View copyOption = dialogView.findViewById(R.id.option_copy_code);
        Button closeButton = dialogView.findViewById(R.id.btn_close);

        TextView freezeStatus = dialogView.findViewById(R.id.freeze_frame_status);
        if (!isConnected || !faultCode.hasFreeze)
        {
            if (freezeStatus != null)
            {
                freezeStatus.setText(!isConnected ? "Connect to vehicle first" : "No freeze frame available");
            }
            freezeFrameOption.setAlpha(0.5f);
            freezeFrameOption.setEnabled(false);
        }
        else
        {
            freezeFrameOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                showFreezeFrameDialogForCode(activity, faultCode);
            });
        }

        searchOption.setOnClickListener(v ->
        {
            dialog.dismiss();
            searchFaultCodeOnWeb(activity, faultCode.code);
        });

        if (nondaOption != null)
        {
            TextView nondaStatus = dialogView.findViewById(R.id.nonda_video_status);
            boolean hasDirectVideo = hasDirectNondaVideo(faultCode.code);
            if (nondaStatus != null)
            {
                nondaStatus.setText(hasDirectVideo
                        ? "Watch nonda's step-by-step guide"
                        : "Search nonda's channel for this code");
            }

            nondaOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                String videoUrl = getNondaVideoUrl(faultCode.code);
                if (videoUrl == null)
                {
                    SnackbarHelper.showWarning(activity, "Could not open YouTube for this code");
                    return;
                }
                if (!hasDirectVideo)
                {
                    SnackbarHelper.showInfo(activity, "Opening nonda search results for " + faultCode.code);
                }
                launchNondaVideo(activity, videoUrl);
            });
        }

        // Ask CoPilot - launches CoPilot with fault code context
        if (askCopilotOption != null)
        {
            askCopilotOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                launchCoPilotForFaultCode(activity, faultCode.code, faultCode.description);
            });
        }

        copyOption.setOnClickListener(v ->
        {
            copyFaultCodeToClipboard(activity, faultCode.code, faultCode.description);
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Show fault code options modal for MainActivity.
     * This is the legacy implementation.
     */
    static void showFaultCodeOptionsModal(MainActivity activity,
                                          ObdItemAdapter adapter,
                                          int position,
                                          ElmProt.STAT ecuConnectionState)
    {
        Object item = adapter.getItem(position);
        if (!(item instanceof EcuCodeItem))
        {
            SnackbarHelper.showWarning(activity, "No fault code data available for this item");
            return;
        }

        EcuCodeItem dfc = (EcuCodeItem) item;
        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_fault_code_options, null);

        TextView codeNumber = dialogView.findViewById(R.id.fault_code_number);
        TextView codeDesc = dialogView.findViewById(R.id.fault_code_description);
        TextView codeType = dialogView.findViewById(R.id.fault_code_type);
        ImageView statusIcon = dialogView.findViewById(R.id.fault_code_status_icon);

        String code = String.valueOf(dfc.get(EcuCodeItem.FID_CODE));
        String description = String.valueOf(dfc.get(EcuCodeItem.FID_DESCRIPT));

        codeNumber.setText(code);
        codeDesc.setText(description);

        Integer svc = (Integer) dfc.get(EcuCodeItem.FID_STATUS);
        if (svc != null)
        {
            switch (svc)
            {
                case ObdProt.OBD_SVC_PENDINGCODES:
                    codeType.setText("Pending Code");
                    statusIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
                    statusIcon.setColorFilter(Color.parseColor("#FF9800"));
                    break;
                case ObdProt.OBD_SVC_PERMACODES:
                    codeType.setText("Permanent Code");
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                    statusIcon.setColorFilter(Color.parseColor("#F44336"));
                    break;
                default:
                    codeType.setText("Confirmed Code");
                    statusIcon.setImageResource(android.R.drawable.ic_menu_myplaces);
                    statusIcon.setColorFilter(Color.parseColor("#F57C00"));
                    break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();

        // Set transparent background to prevent white corners
        if (dialog.getWindow() != null)
        {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View freezeFrameOption = dialogView.findViewById(R.id.option_freeze_frame);
        View searchOption = dialogView.findViewById(R.id.option_search_web);
        View nondaOption = dialogView.findViewById(R.id.option_watch_nonda);
        View askCopilotOption = dialogView.findViewById(R.id.option_ask_ai);
        View copyOption = dialogView.findViewById(R.id.option_copy_code);
        Button closeButton = dialogView.findViewById(R.id.btn_close);

        boolean canViewFreezeFrames = (ecuConnectionState == ElmProt.STAT.CONNECTED ||
                                       ecuConnectionState == ElmProt.STAT.ECU_DETECTED);

        TextView freezeStatus = dialogView.findViewById(R.id.freeze_frame_status);
        if (!canViewFreezeFrames)
        {
            if (freezeStatus != null)
            {
                freezeStatus.setText("Connect to vehicle first");
            }
            freezeFrameOption.setAlpha(0.5f);
            freezeFrameOption.setEnabled(false);
        }
        else
        {
            freezeFrameOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                showFreezeFrameDialog(activity, position, dfc);
            });
        }

        searchOption.setOnClickListener(v ->
        {
            dialog.dismiss();
            searchFaultCodeOnWeb(activity, dfc);
        });

        if (nondaOption != null)
        {
            TextView nondaStatus = dialogView.findViewById(R.id.nonda_video_status);
            boolean hasDirectVideo = hasDirectNondaVideo(code);
            if (nondaStatus != null)
            {
                nondaStatus.setText(hasDirectVideo
                        ? "Watch nonda's step-by-step guide"
                        : "Search nonda's channel for this code");
            }

            nondaOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                String videoUrl = getNondaVideoUrl(code);
                if (videoUrl == null)
                {
                    SnackbarHelper.showWarning(activity, "Could not open YouTube for this code");
                    return;
                }
                if (!hasDirectVideo)
                {
                    SnackbarHelper.showInfo(activity, "Opening nonda search results for " + code);
                }
                launchNondaVideo(activity, videoUrl);
            });
        }

        // Ask CoPilot - launches CoPilot with fault code context
        if (askCopilotOption != null)
        {
            askCopilotOption.setOnClickListener(v ->
            {
                dialog.dismiss();
                launchCoPilotForFaultCode(activity, code, description);
            });
        }

        copyOption.setOnClickListener(v ->
        {
            copyFaultCodeToClipboard(activity, code, description);
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void showFreezeFrameDialog(MainActivity activity,
                                              int dtcIndex,
                                              EcuCodeItem dfc)
    {
        try
        {
            LayoutInflater inflater = activity.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_freeze_frame_data, null);

            TextView codeText = dialogView.findViewById(R.id.freeze_frame_code);
            String codeInfo = dfc.get(EcuCodeItem.FID_CODE) + " - " + dfc.get(EcuCodeItem.FID_DESCRIPT);
            codeText.setText(codeInfo);

            View loadingContainer = dialogView.findViewById(R.id.loading_container);
            View dataContainer = dialogView.findViewById(R.id.data_container);
            View noDataContainer = dialogView.findViewById(R.id.no_data_container);
            LinearLayout dataList = dialogView.findViewById(R.id.freeze_frame_data_list);
            Button closeButton = dialogView.findViewById(R.id.btn_close);
            Button refreshButton = dialogView.findViewById(R.id.btn_refresh);

            AlertDialog freezeDialog = new AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .create();

            // Set transparent background to prevent white corners
            if (freezeDialog.getWindow() != null)
            {
                freezeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            closeButton.setOnClickListener(v -> freezeDialog.dismiss());

            Consumer<Boolean> loadFreezeFrameData = forceRefreshObj ->
            {
                boolean forceRefresh = Boolean.TRUE.equals(forceRefreshObj);
                int frameIndex = Math.max(dtcIndex, 0);

                activity.runOnUiThread(() ->
                {
                    loadingContainer.setVisibility(View.VISIBLE);
                    dataContainer.setVisibility(View.GONE);
                    noDataContainer.setVisibility(View.GONE);
                    dataList.removeAllViews();
                    refreshButton.setVisibility(View.GONE);
                });

                try
                {
                    // Try cached data first when not forcing a refresh
                    if (!forceRefresh)
                    {
                        PvList cachedFrame = CommService.elm.getCachedFreezeFrame(frameIndex);
                        if (cachedFrame == null || cachedFrame.isEmpty())
                        {
                            FreezeFrameManager manager = CommService.elm.getFreezeFrameManager();
                            if (manager != null)
                            {
                                FreezeFrameManager.FreezeFrameData cachedData =
                                    manager.getFreezeFrame(frameIndex);
                                if (cachedData != null && cachedData.data != null && !cachedData.data.isEmpty())
                                {
                                    cachedFrame = cachedData.data;
                                }
                            }
                        }

                        if (cachedFrame != null && !cachedFrame.isEmpty())
                        {
                            final PvList displayData = cachedFrame;
                            activity.runOnUiThread(() ->
                            {
                                populateFreezeFrameList(activity, dataList, displayData);
                                loadingContainer.setVisibility(View.GONE);
                                dataContainer.setVisibility(View.VISIBLE);
                                noDataContainer.setVisibility(View.GONE);
                                refreshButton.setVisibility(View.VISIBLE);
                            });
                            return;
                        }
                    }

                    // Request fresh data from the ECU
                    CommService.elm.requestFreezeFrameSnapshot(frameIndex);

                    PvList freezeFrameData = null;
                    long start = System.currentTimeMillis();
                    while (System.currentTimeMillis() - start < 3000)
                    {
                        freezeFrameData = CommService.elm.getCachedFreezeFrame(frameIndex);
                        if (freezeFrameData != null && !freezeFrameData.isEmpty())
                        {
                            break;
                        }
                        try
                        {
                            Thread.sleep(200);
                        }
                        catch (InterruptedException ie)
                        {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (freezeFrameData == null || freezeFrameData.isEmpty())
                    {
                        // Fallback to data service if cache is empty
                        freezeFrameData = ObdProt.getDataService().getFreezeFrameData(frameIndex);
                    }

                    final PvList displayData = freezeFrameData;
                    activity.runOnUiThread(() ->
                    {
                        if (displayData != null && !displayData.isEmpty())
                        {
                            populateFreezeFrameList(activity, dataList, displayData);
                            loadingContainer.setVisibility(View.GONE);
                            dataContainer.setVisibility(View.VISIBLE);
                            noDataContainer.setVisibility(View.GONE);
                            refreshButton.setVisibility(View.VISIBLE);
                        }
                        else
                        {
                            log.warning("Freeze Frame: No data available after request");
                            loadingContainer.setVisibility(View.GONE);
                            noDataContainer.setVisibility(View.VISIBLE);
                            refreshButton.setVisibility(View.VISIBLE);
                        }
                    });
                }
                catch (Exception e)
                {
                    log.log(Level.SEVERE, "Show freeze frame modal", e);
                    activity.runOnUiThread(() ->
                            SnackbarHelper.showError(activity,
                                    "Error showing freeze frame data: " + e.getMessage()));
                }
                finally
                {
                    // No service state change required when using snapshot requests
                }
            };

            refreshButton.setOnClickListener(v -> new Thread(() -> loadFreezeFrameData.accept(Boolean.TRUE)).start());
            new Thread(() -> loadFreezeFrameData.accept(Boolean.FALSE)).start();

            freezeDialog.show();
            if (freezeDialog.getWindow() != null)
            {
                freezeDialog.getWindow().setLayout(
                        (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9),
                        (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.7));
            }
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Show freeze frame modal", e);
            SnackbarHelper.showError(activity, "Error showing freeze frame data: " + e.getMessage());
        }
    }

    private static void populateFreezeFrameList(Context context,
                                                LinearLayout dataList,
                                                PvList freezeFrameData)
    {
        LayoutInflater inflater = LayoutInflater.from(context);

        if (freezeFrameData == null || freezeFrameData.isEmpty())
        {
            return;
        }

        for (Object valueObj : freezeFrameData.values())
        {
            if (!(valueObj instanceof EcuDataPv))
            {
                continue;
            }

            EcuDataPv pv = (EcuDataPv) valueObj;

            View itemView = inflater.inflate(R.layout.freeze_frame_data_item, dataList, false);
            TextView label = itemView.findViewById(R.id.data_label);
            TextView value = itemView.findViewById(R.id.data_value);

            Object descObj = pv.get(EcuDataPv.FID_DESCRIPT);
            String labelText = descObj != null ? descObj.toString() : null;

            if (labelText == null || labelText.trim().isEmpty() || labelText.equals("null"))
            {
                Object key = pv.get(EcuDataPv.FID_PID);
                if (key instanceof Number)
                {
                    labelText = String.format("PID 0x%02X", ((Number) key).intValue());
                }
                else if (key != null)
                {
                    labelText = "PID " + key;
                }
                else
                {
                    labelText = "PID";
                }
            }
            label.setText(labelText);

            Object dataValue = pv.get(EcuDataPv.FID_VALUE);
            Object units = pv.get(EcuDataPv.FID_UNITS);

            String displayValue;
            if (dataValue == null)
            {
                displayValue = "N/A";
            }
            else if (dataValue instanceof byte[])
            {
                byte[] bytes = (byte[]) dataValue;
                StringBuilder hex = new StringBuilder("0x");
                for (byte b : bytes)
                {
                    hex.append(String.format("%02X", b & 0xFF));
                }
                displayValue = hex.toString();
            }
            else
            {
                displayValue = dataValue.toString();
            }

            if (units != null && !units.toString().isEmpty())
            {
                displayValue += " " + units;
            }
            value.setText(displayValue);

            dataList.addView(itemView);
        }
    }

    private static void searchFaultCodeOnWeb(Context context, String code)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, "OBD " + code);
            context.startActivity(intent);
        }
        catch (ActivityNotFoundException | SecurityException e)
        {
            log.log(Level.SEVERE, "WebSearch DFC", e);
            SnackbarHelper.showError(context, e.getMessage());
        }
    }

    private static void searchFaultCodeOnWeb(Context context, EcuCodeItem dfc)
    {
        searchFaultCodeOnWeb(context, String.valueOf(dfc.get(EcuCodeItem.FID_CODE)));
    }

    private static void copyFaultCodeToClipboard(Context context, String code, String description)
    {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null)
        {
            SnackbarHelper.showWarning(context, "Clipboard not available on this device");
            return;
        }

        String text = code + " - " + description;
        ClipData clip = ClipData.newPlainText("OBD Fault Code", text);
        clipboard.setPrimaryClip(clip);
        SnackbarHelper.showSuccess(context, "Copied: " + code);
    }

    static boolean hasDirectNondaVideo(String faultCode)
    {
        String normalized = normalizeFaultCode(faultCode);
        return normalized != null && NONDA_VIDEO_MAP.containsKey(normalized);
    }

    static String getNondaVideoUrl(String faultCode)
    {
        String normalized = normalizeFaultCode(faultCode);
        if (normalized == null)
        {
            return null;
        }

        String direct = NONDA_VIDEO_MAP.get(normalized);
        if (direct != null)
        {
            return direct;
        }

        String encodedQuery = Uri.encode("nonda " + normalized);
        return "https://www.youtube.com/results?search_query=" + encodedQuery;
    }

    private static void launchNondaVideo(Context context, String url)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        }
        catch (ActivityNotFoundException | SecurityException ex)
        {
            log.log(Level.WARNING, "Failed to open nonda video link: " + url, ex);
            SnackbarHelper.showError(context, "Unable to open YouTube. Please try again later.");
        }
    }

    private static String normalizeFaultCode(String faultCode)
    {
        if (faultCode == null)
        {
            return null;
        }
        String trimmed = faultCode.trim();
        if (trimmed.isEmpty())
        {
            return null;
        }
        return trimmed.toUpperCase(Locale.US);
    }

    private static Map<String, String> buildNondaVideoMap()
    {
        Map<String, String> map = new HashMap<>();
        addNondaVideo(map, "P0030", "https://www.youtube.com/watch?v=Slk-wbFSOdg");
        addNondaVideo(map, "P0031", "https://www.youtube.com/watch?v=Slk-wbFSOdg");
        addNondaVideo(map, "P0032", "https://www.youtube.com/watch?v=Slk-wbFSOdg");
        addNondaVideo(map, "P0036", "https://www.youtube.com/watch?v=L8PMVL1bIyU");
        addNondaVideo(map, "P0037", "https://www.youtube.com/watch?v=L8PMVL1bIyU");
        addNondaVideo(map, "P0038", "https://www.youtube.com/watch?v=L8PMVL1bIyU");
        addNondaVideo(map, "P0050", "https://www.youtube.com/watch?v=YpW4LPtOtqM");
        addNondaVideo(map, "P0051", "https://www.youtube.com/watch?v=YpW4LPtOtqM");
        addNondaVideo(map, "P0052", "https://www.youtube.com/watch?v=YpW4LPtOtqM");
        addNondaVideo(map, "P0056", "https://www.youtube.com/watch?v=F1PgHzhgsto");
        addNondaVideo(map, "P0057", "https://www.youtube.com/watch?v=F1PgHzhgsto");
        addNondaVideo(map, "P0058", "https://www.youtube.com/watch?v=F1PgHzhgsto");
        addNondaVideo(map, "P0059", "https://www.youtube.com/watch?v=xxidKT-s-yU");
        addNondaVideo(map, "P0060", "https://www.youtube.com/watch?v=xxidKT-s-yU");
        addNondaVideo(map, "P0061", "https://www.youtube.com/watch?v=xxidKT-s-yU");
        addNondaVideo(map, "P0062", "https://www.youtube.com/watch?v=geRCXs2RjDo");
        addNondaVideo(map, "P0063", "https://www.youtube.com/watch?v=geRCXs2RjDo");
        addNondaVideo(map, "P0064", "https://www.youtube.com/watch?v=geRCXs2RjDo");
        addNondaVideo(map, "P0110", "https://www.youtube.com/watch?v=3LBsXeKMZX8");
        addNondaVideo(map, "P0111", "https://www.youtube.com/watch?v=3LBsXeKMZX8");
        addNondaVideo(map, "P0112", "https://www.youtube.com/watch?v=3LBsXeKMZX8");
        addNondaVideo(map, "P0113", "https://www.youtube.com/watch?v=3LBsXeKMZX8");
        addNondaVideo(map, "P0114", "https://www.youtube.com/watch?v=3LBsXeKMZX8");
        addNondaVideo(map, "P0300", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0301", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0302", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0303", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0304", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0305", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0306", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0307", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0308", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0309", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0310", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0311", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0312", "https://www.youtube.com/watch?v=-3dBSdomeDM");
        addNondaVideo(map, "P0420", "https://www.youtube.com/watch?v=ESGuybe1Usw");
        addNondaVideo(map, "P0430", "https://www.youtube.com/watch?v=ESGuybe1Usw");
        addNondaVideo(map, "P0450", "https://www.youtube.com/watch?v=sO_uZsiuFD4");
        addNondaVideo(map, "P0451", "https://www.youtube.com/watch?v=sO_uZsiuFD4");
        addNondaVideo(map, "P0452", "https://www.youtube.com/watch?v=sO_uZsiuFD4");
        addNondaVideo(map, "P0453", "https://www.youtube.com/watch?v=sO_uZsiuFD4");
        addNondaVideo(map, "P0454", "https://www.youtube.com/watch?v=sO_uZsiuFD4");
        return Collections.unmodifiableMap(map);
    }

    private static void addNondaVideo(Map<String, String> map, String code, String url)
    {
        if (code == null || url == null)
        {
            return;
        }
        map.put(code.toUpperCase(Locale.US), url);
    }

    static void showFreezeFrameDialogForCode(AppCompatActivity activity,
                                              FaultCodeService.FaultCodeInfo faultCode)
    {
        // Find the DTC index from the fault code
        // For now, we'll use 0 as the index since we don't have the exact position
        // This can be improved later if needed
        int dtcIndex = 0;

        try
        {
            LayoutInflater inflater = activity.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_freeze_frame_data, null);

            TextView codeText = dialogView.findViewById(R.id.freeze_frame_code);
            String codeInfo = faultCode.code + " - " + faultCode.description;
            codeText.setText(codeInfo);

            View loadingContainer = dialogView.findViewById(R.id.loading_container);
            View dataContainer = dialogView.findViewById(R.id.data_container);
            View noDataContainer = dialogView.findViewById(R.id.no_data_container);
            LinearLayout dataList = dialogView.findViewById(R.id.freeze_frame_data_list);
            Button closeButton = dialogView.findViewById(R.id.btn_close);
            Button refreshButton = dialogView.findViewById(R.id.btn_refresh);

            AlertDialog freezeDialog = new AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .create();

            // Set transparent background to prevent white corners
            if (freezeDialog.getWindow() != null)
            {
                freezeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            closeButton.setOnClickListener(v -> freezeDialog.dismiss());

            Consumer<Boolean> loadFreezeFrameData = forceRefreshObj ->
            {
                boolean forceRefresh = Boolean.TRUE.equals(forceRefreshObj);
                int frameIndex = Math.max(dtcIndex, 0);

                activity.runOnUiThread(() ->
                {
                    loadingContainer.setVisibility(View.VISIBLE);
                    dataContainer.setVisibility(View.GONE);
                    noDataContainer.setVisibility(View.GONE);
                    dataList.removeAllViews();
                    refreshButton.setVisibility(View.GONE);
                });

                try
                {
                    if (!forceRefresh)
                    {
                        PvList cachedFrame = CommService.elm.getCachedFreezeFrame(frameIndex);
                        if (cachedFrame == null || cachedFrame.isEmpty())
                        {
                            FreezeFrameManager manager = CommService.elm.getFreezeFrameManager();
                            if (manager != null)
                            {
                                FreezeFrameManager.FreezeFrameData cachedData =
                                    manager.getFreezeFrame(frameIndex);
                                if (cachedData != null && cachedData.data != null && !cachedData.data.isEmpty())
                                {
                                    cachedFrame = cachedData.data;
                                }
                            }
                        }

                        if (cachedFrame != null && !cachedFrame.isEmpty())
                        {
                            final PvList displayData = cachedFrame;
                            activity.runOnUiThread(() ->
                            {
                                populateFreezeFrameList(activity, dataList, displayData);
                                loadingContainer.setVisibility(View.GONE);
                                dataContainer.setVisibility(View.VISIBLE);
                                noDataContainer.setVisibility(View.GONE);
                                refreshButton.setVisibility(View.VISIBLE);
                            });
                            return;
                        }
                    }

                    CommService.elm.requestFreezeFrameSnapshot(frameIndex);

                    PvList freezeFrameData = null;
                    long start = System.currentTimeMillis();
                    while (System.currentTimeMillis() - start < 3000)
                    {
                        freezeFrameData = CommService.elm.getCachedFreezeFrame(frameIndex);
                        if (freezeFrameData != null && !freezeFrameData.isEmpty())
                        {
                            break;
                        }
                        try
                        {
                            Thread.sleep(200);
                        }
                        catch (InterruptedException ie)
                        {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (freezeFrameData == null || freezeFrameData.isEmpty())
                    {
                        freezeFrameData = ObdProt.getDataService().getFreezeFrameData(frameIndex);
                    }

                    final PvList displayData = freezeFrameData;
                    activity.runOnUiThread(() ->
                    {
                        if (displayData != null && !displayData.isEmpty())
                        {
                            populateFreezeFrameList(activity, dataList, displayData);
                            loadingContainer.setVisibility(View.GONE);
                            dataContainer.setVisibility(View.VISIBLE);
                            noDataContainer.setVisibility(View.GONE);
                            refreshButton.setVisibility(View.VISIBLE);
                        }
                        else
                        {
                            log.warning("Freeze Frame: No data available after request");
                            loadingContainer.setVisibility(View.GONE);
                            noDataContainer.setVisibility(View.VISIBLE);
                            refreshButton.setVisibility(View.VISIBLE);
                        }
                    });
                }
                catch (Exception e)
                {
                    log.log(Level.SEVERE, "Show freeze frame modal", e);
                    activity.runOnUiThread(() ->
                            SnackbarHelper.showError(activity,
                                    "Error showing freeze frame data: " + e.getMessage()));
                }
            };

            refreshButton.setOnClickListener(v -> new Thread(() -> loadFreezeFrameData.accept(Boolean.TRUE)).start());
            new Thread(() -> loadFreezeFrameData.accept(Boolean.FALSE)).start();

            freezeDialog.show();
            if (freezeDialog.getWindow() != null)
            {
                freezeDialog.getWindow().setLayout(
                        (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9),
                        (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.7));
            }
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Show freeze frame modal", e);
            SnackbarHelper.showError(activity, "Error showing freeze frame data: " + e.getMessage());
        }
    }

    /**
     * Launch CoPilot with a pre-filled message about the fault code.
     * This allows the Agent API to analyze the code with full conversation context and tool access.
     */
    static void launchCoPilotForFaultCode(AppCompatActivity activity, String code, String description)
    {
        try
        {
            // Build detailed fault code message with vehicle context if available
            StringBuilder message = new StringBuilder();
            message.append("I have a fault code: ").append(code);
            if (description != null && !description.isEmpty())
            {
                message.append(" - ").append(description);
            }

            // Add vehicle context if available
            try
            {
                VehicleManager vehicleManager = VehicleManager.getInstance();
                if (vehicleManager.isVehicleConnected())
                {
                    VehicleData vData = vehicleManager.getCurrentVehicleData();
                    if (vData != null && vData.getDisplayName() != null && !vData.getDisplayName().isEmpty())
                    {
                        message.append("\n\nVehicle: ").append(vData.getDisplayName());
                    }
                }
            }
            catch (Exception e)
            {
                // Continue without vehicle info
            }

            message.append("\n\nCan you analyze this code and tell me what it means, what might be causing it, and what I should do?");

            // Launch CoPilot with the fault code message
            Intent intent = new Intent(activity, CoPilotActivity.class);
            intent.putExtra(CoPilotActivity.EXTRA_INITIAL_MESSAGE, message.toString());
            activity.startActivity(intent);
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Failed to launch CoPilot", e);
            SnackbarHelper.showError(activity, "Error launching CoPilot: " + e.getMessage());
        }
    }
}
