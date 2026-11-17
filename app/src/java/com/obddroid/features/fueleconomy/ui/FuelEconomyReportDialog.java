package com.obddroid.features.fueleconomy.ui;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.obddroid.R;
import com.obddroid.features.fueleconomy.data.FuelEconomyReportExporter;
import com.obddroid.utils.SnackbarHelper;

import org.json.JSONException;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * "Save report": the CSV/JSON chooser for the fuel economy screen, written by
 * {@link FuelEconomyReportExporter} from a snapshot taken when the format is picked.
 */
final class FuelEconomyReportDialog {

    private static final Logger log = Logger.getLogger(FuelEconomyReportDialog.class.getName());

    /** The screen's side of the report. */
    interface Source {
        /** Any fuel economy data collected or shown yet. */
        boolean hasData();

        FuelEconomyReportExporter.Snapshot snapshot();
    }

    private final Activity activity;
    private final FuelEconomyReportExporter exporter;
    private final Source source;

    FuelEconomyReportDialog(Activity activity, FuelEconomyReportExporter exporter, Source source) {
        this.activity = activity;
        this.exporter = exporter;
        this.source = source;
    }

    void show() {
        if (!source.hasData()) {
            SnackbarHelper.showInfo(activity, "Collect fuel economy data before saving a report.");
            return;
        }

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView titleView = dialog.findViewById(R.id.dialog_title);
        if (titleView != null) {
            titleView.setText(activity.getString(R.string.fuel_economy_report_title));
        }

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportCsv();
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportJson();
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void exportCsv() {
        FuelEconomyReportExporter.Snapshot snapshot = source.snapshot();
        try {
            String location = exporter.exportToCsv(snapshot);
            SnackbarHelper.showSuccess(activity, "Fuel economy report saved: " + location, SnackbarHelper.Duration.LONG);
        } catch (IOException e) {
            SnackbarHelper.showError(activity, "Failed to export fuel economy report: " + e.getMessage(), SnackbarHelper.Duration.LONG);
            log.severe("Fuel economy CSV export failed: " + e.getMessage());
        }
    }

    private void exportJson() {
        FuelEconomyReportExporter.Snapshot snapshot = source.snapshot();
        try {
            String location = exporter.exportToJson(snapshot);
            SnackbarHelper.showSuccess(activity, "Fuel economy report saved: " + location, SnackbarHelper.Duration.LONG);
        } catch (IOException | JSONException e) {
            SnackbarHelper.showError(activity, "Failed to export fuel economy report: " + e.getMessage(), SnackbarHelper.Duration.LONG);
            log.severe("Fuel economy JSON export failed: " + e.getMessage());
        }
    }
}
