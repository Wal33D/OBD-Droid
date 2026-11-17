package com.obddroid.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.automotivelogolibrary.AutomotiveLogoLibraryAndroid;
import com.obddroid.utils.VehicleData;

/**
 * The always-visible row of {@link VehicleInfoFooter}: manufacturer logo (or a letter), the vehicle line,
 * the status line, the expand arrow and the status dot. It builds its views and shows each state; the
 * footer decides which state applies. Texts and colours come from {@link VehicleInfoFormat}.
 */
final class VehicleInfoHeader {

    private final Context context;
    private final LinearLayout row;
    private final LinearLayout iconContainer;
    private final ImageView manufacturerLogo;
    private final TextView manufacturerIcon;
    private final TextView vehicleInfo;
    private final TextView connectionStatus;
    private final TextView expandIndicator;
    private final View statusDot;

    VehicleInfoHeader(Context context) {
        this.context = context;

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(2), dp(16), dp(2));

        // Manufacturer icon: a PNG logo, or a letter when there is none (one shown at a time)
        iconContainer = new LinearLayout(context);
        iconContainer.setGravity(Gravity.CENTER);
        iconContainer.setBackground(null);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(dp(60), dp(60));
        iconContainerParams.rightMargin = dp(12);

        manufacturerLogo = new ImageView(context);
        manufacturerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        manufacturerLogo.setPadding(0, 0, 0, 0);
        manufacturerLogo.setBackground(null);
        manufacturerLogo.setVisibility(View.GONE);

        manufacturerIcon = new TextView(context);
        manufacturerIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        manufacturerIcon.setTextColor(Color.parseColor(VehicleInfoFormat.CYAN));
        manufacturerIcon.setTypeface(Typeface.DEFAULT_BOLD);
        manufacturerIcon.setGravity(Gravity.CENTER);
        manufacturerIcon.setText("?");

        iconContainer.addView(manufacturerLogo, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));
        iconContainer.addView(manufacturerIcon, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );

        vehicleInfo = new TextView(context);
        vehicleInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        vehicleInfo.setTextColor(Color.parseColor("#FFFFFF"));
        vehicleInfo.setTypeface(Typeface.DEFAULT_BOLD);
        vehicleInfo.setText("No Vehicle Connected");
        vehicleInfo.setSingleLine(true);
        vehicleInfo.setEllipsize(TextUtils.TruncateAt.END);

        connectionStatus = new TextView(context);
        connectionStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        connectionStatus.setTextColor(Color.parseColor(VehicleInfoFormat.GREY));
        connectionStatus.setText("Waiting for OBD connection...");
        connectionStatus.setSingleLine(true);

        infoContainer.addView(vehicleInfo);
        infoContainer.addView(connectionStatus);

        row.addView(iconContainer, iconContainerParams);
        row.addView(infoContainer, infoParams);

        expandIndicator = new TextView(context);
        expandIndicator.setText("▼");
        expandIndicator.setTextColor(Color.parseColor(VehicleInfoFormat.GREY));
        expandIndicator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        expandParams.leftMargin = dp(8);
        expandParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(expandIndicator, expandParams);

        // Connectivity dot, grey while disconnected
        statusDot = new View(context);
        statusDot.setBackgroundResource(android.R.drawable.presence_offline);
        statusDot.getBackground().setTint(Color.parseColor(VehicleInfoFormat.GREY));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.leftMargin = dp(8);
        dotParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(statusDot, dotParams);
    }

    /** The row to add to the footer; it is the click target for expanding. */
    LinearLayout view() {
        return row;
    }

    /** VIN read, NHTSA decode started: "VIN: ..." with an orange status. */
    void showVin(String vin) {
        vehicleInfo.setText("VIN: " + vin);
        showDecoding();
    }

    /** Orange "Decoding vehicle information..." status. */
    void showDecoding() {
        setStatus("Decoding vehicle information...", VehicleInfoFormat.ORANGE);
    }

    /** A decode error in red; the footer reverts to the connection state later. */
    void showDecodingError(String error) {
        setStatus(error, "#FF5722");
    }

    /** A VIN without decoded details. The dot keeps its colour. */
    void showUndecodedVin(String vin) {
        vehicleInfo.setText("VIN: " + vin);
        connectionStatus.setText("Unable to decode vehicle details");
        connectionStatus.setTextColor(Color.parseColor(VehicleInfoFormat.GREY));
        expandIndicator.setVisibility(View.VISIBLE);
    }

    /** The decoded vehicle: logo or initial, "year make model", engine and VIN tail, green status. */
    void showVehicle(String vin, VehicleData vehicle) {
        expandIndicator.setVisibility(View.VISIBLE);

        String make = vehicle.make;
        Drawable logoDrawable = AutomotiveLogoLibraryAndroid.getLogoDrawable(context, make);
        if (logoDrawable != null) {
            manufacturerLogo.setImageDrawable(logoDrawable);
            manufacturerLogo.setVisibility(View.VISIBLE);
            manufacturerIcon.setVisibility(View.GONE);
            iconContainer.setBackground(null);
        } else if (make != null && !make.isEmpty()) {
            manufacturerIcon.setText(make.substring(0, 1).toUpperCase());
            manufacturerIcon.setTextColor(Color.parseColor(VehicleInfoFormat.CYAN));
            manufacturerIcon.setVisibility(View.VISIBLE);
            manufacturerLogo.setVisibility(View.GONE);
            // Lighter than the footer so the letter stands out
            iconContainer.setBackgroundColor(Color.parseColor("#2E2E2E"));
        }

        vehicleInfo.setText(VehicleInfoFormat.headline(vehicle));
        vehicleInfo.setTextColor(Color.parseColor("#FFFFFF"));
        setStatus(VehicleInfoFormat.statusLine(vehicle.getEngineDescription(), vin), VehicleInfoFormat.GREEN);
    }

    /** Showing the vehicle failed. The dot keeps its colour. */
    void showVehicleError() {
        vehicleInfo.setText("Error reading vehicle data");
        connectionStatus.setText("Please reconnect");
        connectionStatus.setTextColor(Color.parseColor("#F44336"));
        expandIndicator.setVisibility(View.VISIBLE);
    }

    /** No decoded vehicle: disconnected, waiting for the VIN, or no Mode 9. */
    void showConnection(VehicleInfoFormat.ConnectionLook look) {
        manufacturerLogo.setVisibility(View.GONE);
        manufacturerIcon.setVisibility(View.VISIBLE);
        manufacturerIcon.setText(look.iconText);
        manufacturerIcon.setTextColor(Color.parseColor(look.iconColor));
        vehicleInfo.setText(look.title);
        setStatus(look.status, look.statusColor);
        expandIndicator.setVisibility(View.VISIBLE);
    }

    /** Turns the arrow to point up while expanded. */
    void animateArrow(boolean expanded) {
        expandIndicator.animate()
            .rotation(expanded ? 180f : 0f)
            .setDuration(400)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    /** Points the arrow down at once (collapse without animation). */
    void resetArrow() {
        expandIndicator.setRotation(0f);
    }

    private void setStatus(String text, String color) {
        connectionStatus.setText(text);
        connectionStatus.setTextColor(Color.parseColor(color));
        statusDot.getBackground().setTint(Color.parseColor(color));
    }

    private int dp(int dp) {
        return dpToPx(context, dp);
    }

    static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
        );
    }
}
