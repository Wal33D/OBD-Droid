package com.obddroid.ui.components;

import com.obddroid.utils.VehicleData;


/**
 * The text rules of {@link VehicleInfoFooter}: what the collapsed header says in each connection state,
 * how decoded NHTSA fields are tidied for display, and which Mode 9 descriptions are shown. Plain Java,
 * no views, so the rules can be unit tested.
 */
final class VehicleInfoFormat {

    static final String CYAN = "#00ACC1";
    static final String ORANGE = "#FFA726";
    static final String GREEN = "#4CAF50";
    static final String GREY = "#888888";

    private VehicleInfoFormat() {
    }

    /** What the collapsed header shows when no decoded vehicle is on screen. */
    static final class ConnectionLook {
        final String iconText;
        final String iconColor;
        final String title;
        final String status;
        /** Colour of the status line and of the status dot. */
        final String statusColor;

        ConnectionLook(String iconText, String iconColor, String title, String status, String statusColor) {
            this.iconText = iconText;
            this.iconColor = iconColor;
            this.title = title;
            this.status = status;
            this.statusColor = statusColor;
        }
    }

    /**
     * The header for a connection without a decoded vehicle.
     * @param ecuConnected the adapter reached an ECU
     * @param vinRetrievalFailed the ECU answered but does not support Mode 9 (no VIN)
     */
    static ConnectionLook connectionLook(boolean ecuConnected, boolean vinRetrievalFailed) {
        if (!ecuConnected) {
            return new ConnectionLook("?", "#666666", "No Vehicle Connected",
                    "Waiting for OBD connection...", GREY);
        }
        if (vinRetrievalFailed) {
            return new ConnectionLook("OBD", ORANGE, "ECU Connected",
                    "Vehicle Info not available (Mode 9 not supported)", ORANGE);
        }
        return new ConnectionLook("OBD", CYAN, "ECU Connected", "Retrieving vehicle information...", CYAN);
    }

    /** "2015 Honda Civic": year, make and model, each only when known. */
    static String headline(VehicleData data) {
        StringBuilder info = new StringBuilder();
        if (data.make != null && !data.make.isEmpty()) {
            info.append(data.make);
        }
        if (data.model != null && !data.model.isEmpty()) {
            if (info.length() > 0) info.append(" ");
            info.append(data.model);
        }
        if (data.modelYear != null && !data.modelYear.isEmpty()) {
            info.insert(0, data.modelYear + " ");
        }
        return info.toString();
    }

    /** The line under the headline: engine (or "Connected") and the last six characters of the VIN. */
    static String statusLine(String engineDescription, String vin) {
        String vinSuffix = (vin != null && vin.length() >= 6) ? (" • VIN: " + vin.substring(vin.length() - 6)) : "";
        if (engineDescription != null && !engineDescription.isEmpty()) {
            return engineDescription + vinSuffix;
        }
        return "Connected" + vinSuffix;
    }

    /**
     * A VIN in a Mode 9 value: the description names the vehicle identification and the value is 17
     * characters once trimmed. Returns the trimmed VIN, or null.
     */
    static String vinFromMode9(String description, Object value) {
        if (description != null && description.toLowerCase().contains("vehicle identification")
                && value != null && value.toString().trim().length() == 17) {
            return value.toString().trim();
        }
        return null;
    }

    /** The Mode 9 entry that holds the VIN itself. */
    static boolean isMode9Vin(String description) {
        return description.toLowerCase().equals("vehicle identification number");
    }

    /**
     * Other VIN-related Mode 9 entries listed under Vehicle Identification: not the VIN itself, not
     * calibration data, not message or item counts.
     */
    static boolean isExtraVinField(String description) {
        String lower = description.toLowerCase();
        return (lower.contains("vehicle identification") || lower.contains("vin"))
                && !lower.contains("calibration")
                && !lower.equals("vehicle identification number")
                && !lower.contains("number of")
                && !lower.contains("message count");
    }

    /**
     * Mode 9 entries shown under System Counters (ignition and monitoring counters). VIN fields, protocol
     * counts, emission monitors, calibration and ECU data are not shown in the footer.
     */
    static boolean isSystemCounter(String description) {
        if (description.toLowerCase().contains("vehicle identification")
                || description.toLowerCase().contains("vin")) {
            return false;
        }
        if (description.contains("Message count") || description.contains("Number of")
                || description.contains("counts_") || description.contains("numitems")
                || description.contains("length")) {
            return false;
        }
        if (description.contains("Monitor") || description.contains("COMP")
                || description.contains("Catalyst") || description.contains("O2")
                || description.contains("EGR") || description.contains("EVAP")
                || description.contains("AIR") || description.contains("Exhaust")
                || description.contains("Boost") || description.contains("Fuel")
                || description.contains("NMHC") || description.contains("NOx")
                || description.contains("PM Filter")) {
            return false;
        }
        return description.contains("Counter") || description.contains("CNTR")
                || description.contains("Ignition") || description.contains("OBD Monitoring Conditions");
    }

    /** The manufacturer without corporate suffixes ("HONDA MOTOR CO., LTD" keeps "HONDA MOTOR CO."). */
    static String manufacturerName(String manufacturer) {
        if (manufacturer == null) {
            return null;
        }
        return manufacturer
                .replace(" OF NORTH AMERICA", "")
                .replace(", INC.", "")
                .replace(" INC.", "")
                .replace(", LLC", "")
                .replace(" LLC", "")
                .replace(", LTD", "")
                .replace(" LTD", "")
                .replace(" CORPORATION", "")
                .replace(" CORP.", "")
                .trim();
    }

    /** "Sport Utility Vehicle (SUV)/Multi-Purpose Vehicle (MPV)" keeps the part before the slash. */
    static String bodyClass(String bodyClass) {
        if (bodyClass != null && bodyClass.contains("/")) {
            return bodyClass.split("/")[0].trim();
        }
        return bodyClass;
    }

    /** Puts a parenthesised qualifier of the vehicle type on its own line. */
    static String vehicleType(String vehicleType) {
        if (vehicleType != null && vehicleType.contains("(")) {
            return vehicleType.replace("(", "\n(");
        }
        return vehicleType;
    }

    /** An emoji for the body class, a car when nothing matches; null without a body class. */
    static String vehicleEmoji(String bodyClass) {
        if (bodyClass == null) return null;

        String lower = bodyClass.toLowerCase();
        if (lower.contains("suv") || lower.contains("sport utility")) {
            return "🚙";
        } else if (lower.contains("truck") || lower.contains("pickup")) {
            return "🛻";
        } else if (lower.contains("van") || lower.contains("minivan")) {
            return "🚐";
        } else if (lower.contains("sedan")) {
            return "🚗";
        } else if (lower.contains("coupe") || lower.contains("sports car") || lower.contains("sport")) {
            return "🏎️";
        } else if (lower.contains("wagon")) {
            return "🚙";
        } else if (lower.contains("convertible") || lower.contains("cabriolet")) {
            return "🚘";
        } else if (lower.contains("hatchback")) {
            return "🚗";
        } else if (lower.contains("bus")) {
            return "🚌";
        } else if (lower.contains("motorcycle")) {
            return "🏍️";
        }
        return "🚗";
    }
}
