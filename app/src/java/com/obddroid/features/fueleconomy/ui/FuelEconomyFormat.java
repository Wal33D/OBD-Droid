package com.obddroid.features.fueleconomy.ui;

/**
 * The fuel economy screen's plain-Java rules: reading numbers back from its text views for the report,
 * the time-to-empty text, the calibration VE and the info dialog text. No views, so they can be unit tested.
 */
final class FuelEconomyFormat {

    private FuelEconomyFormat() {
    }

    /**
     * The number in a displayed value ("23.4", "12 mph"), or NaN for empty, "--" or no digits.
     */
    static float parseNumber(CharSequence displayed) {
        String text = displayed != null ? displayed.toString().trim() : "";
        if (text.isEmpty() || "--".equals(text)) {
            return Float.NaN;
        }
        String normalized = text.replaceAll("[^0-9.\\-]", "");
        if (normalized.isEmpty()) {
            return Float.NaN;
        }
        try {
            return Float.parseFloat(normalized);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /** The trimmed text, or null for empty or "--". */
    static String cleanText(CharSequence displayed) {
        if (displayed == null) {
            return null;
        }
        String text = displayed.toString().trim();
        if (text.isEmpty() || "--".equals(text)) {
            return null;
        }
        return text;
    }

    /**
     * Time to empty: "h:mm" under 10 hours, decimal hours above, "--" without meaningful fuel flow
     * (0.05 gal/h or less).
     */
    static String timeToEmpty(float remainingGallons, float fuelFlowGalH) {
        if (!(fuelFlowGalH > 0.05f)) {
            return "--";
        }
        float hoursToEmpty = remainingGallons / fuelFlowGalH;
        if (hoursToEmpty < 10f) {
            int hours = (int) hoursToEmpty;
            int minutes = (int) ((hoursToEmpty - hours) * 60);
            return String.format("%d:%02d", hours, minutes);
        }
        return String.format("%.1f", hoursToEmpty);
    }

    /** VE scaled by actual/app MPG, kept within 50-130 %. */
    static float calibratedVE(float currentVE, float actualMpg, float appMpg) {
        float newVE = currentVE * (actualMpg / appMpg);
        return Math.max(50f, Math.min(newVE, 130f));
    }

    /** The info dialog: what the screen shows and which calculation method is in use. */
    static String infoMessage(boolean mapAvailable, boolean calibrated) {
        String calcMethod;
        String calcAccuracy;
        if (mapAvailable) {
            calcMethod = "MAP-based Speed-Density";
            calcAccuracy = calibrated ? "~95% (calibrated)" : "~88% (uncalibrated)";
        } else {
            calcMethod = "RPM/Load Estimation";
            calcAccuracy = "~65%";
        }

        return "This page provides comprehensive fuel economy tracking and analysis for your vehicle.\n\n" +
                "REAL-TIME METRICS:\n" +
                "• Instant MPG - Current fuel efficiency\n" +
                "• Average MPG - Overall fuel economy\n" +
                "• Fuel Level - Percentage remaining\n" +
                "• Range - Estimated miles to empty\n" +
                "• Fuel Flow - Current consumption rate (gal/h)\n" +
                "• Throttle Position - Current throttle %\n" +
                "• Time to Empty - Estimated time remaining\n\n" +
                "HISTORICAL CHART:\n" +
                "The bar chart shows fuel economy trends over three time periods:\n" +
                "• 0-5 min (Recent) - Last few minutes\n" +
                "• 0-30 min (Medium) - Last half hour\n" +
                "• 0-3 hours (Long) - Extended driving session\n\n" +
                "CALCULATION METHOD:\n" +
                "Method: " + calcMethod + "\n" +
                "Accuracy: " + calcAccuracy + "\n" +
                "Calibrated: " + (calibrated ? "Yes ✓" : "No") + "\n\n" +
                (mapAvailable ?
                "MAP-based Speed-Density uses:\n" +
                "• Manifold Absolute Pressure (MAP sensor)\n" +
                "• Intake Air Temperature (IAT sensor)\n" +
                "• Engine Speed (RPM)\n" +
                "• Volumetric Efficiency (VE)\n" +
                "• Stoichiometric ratio (14.7:1)\n\n" +
                "This is the same professional method used by Torque Pro and OBDLink.\n\n" +
                (calibrated ? "" : "TIP: Use the calibration tool (⚙️ icon) after your next fill-up to improve accuracy to 95%+!\n\n")
                :
                "Your vehicle doesn't have MAP/IAT sensors, so we use RPM and Load estimation. This is less accurate but still provides useful data.\n\n"
                ) +
                "NOTE: Ensure your vehicle is connected and Live Data is active for accurate readings.";
    }
}
