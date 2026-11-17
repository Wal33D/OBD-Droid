package com.obddroid.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.io.IOException;

/**
 * Online VIN decoder using NHTSA vPIC API
 *
 * Provides complete vehicle data when internet is available.
 * Should be used as primary decoder with offline fallback.
 *
 * API Endpoint: https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/{VIN}?format=json
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class NhtsaVINDecoder {

    private static final String TAG = "NhtsaVINDecoder";
    private static final String NHTSA_API_URL = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/";
    private static final int TIMEOUT_MS = 3000; // 3 seconds - reduced for better UX

    private final Context context;
    private final Gson gson;

    public NhtsaVINDecoder(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
    }

    /**
     * Decode VIN using NHTSA online API
     *
     * @param vin Vehicle Identification Number
     * @return VehicleData with complete information, or null if failed
     */
    public VehicleData decode(String vin) {
        if (!isNetworkAvailable()) {
            DebugLog.d(TAG, "Network not available, cannot use NHTSA API");
            return null;
        }

        try {
            DebugLog.d(TAG, "Decoding VIN online: " + vin);
            String jsonResponse = fetchVinData(vin);
            return parseNhtsaResponse(jsonResponse, vin);
        } catch (IOException e) {
            Log.w(TAG, "Failed to decode VIN online: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetch VIN data from NHTSA API
     */
    private String fetchVinData(String vin) throws IOException {
        URL url = new URL(NHTSA_API_URL + vin + "?format=json");
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode);
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parse NHTSA JSON response into VehicleData
     */
    private VehicleData parseNhtsaResponse(String json, String vin) {
        NhtsaResponse response = gson.fromJson(json, NhtsaResponse.class);
        if (response == null || response.Results == null) {
            return null;
        }

        VehicleData data = new VehicleData();
        data.vin = vin;
        data.setValid(true);
        data.dataSource = "NHTSA API (Online)";

        // Map all fields from NHTSA response
        for (NhtsaField field : response.Results) {
            if (field.Value == null || field.Value.trim().isEmpty()) {
                continue; // Skip empty values
            }

            String value = field.Value.trim();

            switch (field.Variable) {
                case "Make":
                    data.make = value;
                    break;
                case "Model":
                    data.model = value;
                    break;
                case "Model Year":
                    data.modelYear = value;
                    break;
                case "Series":
                    data.series = value;
                    break;
                case "Trim":
                    data.trim = value;
                    break;
                case "Body Class":
                    data.bodyClass = value;
                    break;
                case "Vehicle Type":
                    data.vehicleType = value;
                    break;
                case "Doors":
                    data.doors = value;
                    break;
                case "Wheel Base (inches) From":
                case "Wheel Base Type":
                    data.wheelBase = value;
                    break;
                case "Engine Model":
                    data.engineModel = value;
                    break;
                case "Engine Number of Cylinders":
                    data.engineCylinders = value;
                    break;
                case "Displacement (L)":
                    data.displacementL = value;
                    break;
                case "Displacement (CC)":
                    data.displacementCC = value;
                    break;
                case "Engine Configuration":
                    data.engineConfiguration = value;
                    break;
                case "Fuel Type - Primary":
                    data.fuelTypePrimary = value;
                    data.fuelType = value; // Also populate fuelType
                    break;
                case "Turbo":
                    data.turbo = value;
                    break;
                case "Drive Type":
                    data.driveType = value;
                    break;
                case "Transmission Style":
                    data.transmissionStyle = value;
                    data.transmission = value; // Also populate transmission
                    break;
                case "Transmission Speeds":
                    data.transmissionSpeeds = value;
                    break;
                case "Gross Vehicle Weight Rating From":
                    data.gvwr = value;
                    break;
                case "Manufacturer Name":
                    data.manufacturer = value;
                    break;
                case "Plant Country":
                    data.plantCountry = value;
                    break;
                case "Plant State":
                    data.plantState = value;
                    break;
                case "Plant City":
                    data.plantCity = value;
                    break;
                case "Engine Manufacturer":
                    data.engineManufacturer = value;
                    break;
                case "Engine Stroke Cycles":
                    data.engineStrokeCycles = value;
                    break;
                case "Engine Brake (hp) From":
                    data.engineBrakeHp = value;
                    break;
                case "Top Speed (MPH)":
                    data.topSpeed = value;
                    break;
                case "Fuel Delivery / Fuel Injection Type":
                    data.fuelDeliveryType = value;
                    break;
                case "Valve Train Design":
                    data.valveTrainDesign = value;
                    break;
                case "Cooling Type":
                    data.coolingType = value;
                    break;
                case "Axles":
                    data.axles = value;
                    break;

                // Dimensions
                case "Number of Seats":
                    data.numberOfSeats = value;
                    break;
                case "Number of Seat Rows":
                    data.numberOfSeatRows = value;
                    break;
                case "Wheel Size Front (inches)":
                    data.wheelSizeFront = value;
                    break;
                case "Wheel Size Rear (inches)":
                    data.wheelSizeRear = value;
                    break;
                case "Number of Wheels":
                    data.numberOfWheels = value;
                    break;

                // Steering
                case "Steering Location":
                    data.steeringLocation = value;
                    break;

                // Safety Features - Standard
                case "Anti-lock Braking System (ABS)":
                    data.abs = value;
                    break;
                case "Electronic Stability Control (ESC)":
                    data.esc = value;
                    break;
                case "Traction Control":
                    data.tractionControl = value;
                    break;
                case "Dynamic Brake Support (DBS)":
                    data.dynamicBrakeSupport = value;
                    break;
                case "Pretensioner":
                    data.pretensioner = value;
                    break;
                case "Seat Belt Type":
                    data.seatBeltType = value;
                    break;
                case "Other Restraint System Info":
                    data.otherRestraintInfo = value;
                    break;
                case "Front Air Bag Locations":
                    data.frontAirBagLocations = value;
                    break;
                case "Side Air Bag Locations":
                    data.sideAirBagLocations = value;
                    break;
                case "Backup Camera":
                    data.backupCamera = value;
                    break;
                case "Automatic Crash Notification (ACN) / Advanced Automatic Crash Notification (AACN)":
                    data.automaticCrashNotification = value;
                    break;
                case "Daytime Running Light (DRL)":
                    data.daytimeRunningLight = value;
                    break;
                case "Semiautomatic Headlamp Beam Switching":
                    data.semiautomaticHeadlampBeamSwitching = value;
                    break;
                case "Auto-Reverse System for Windows and Sunroofs":
                    data.autoReverseSystem = value;
                    break;
                case "Tire Pressure Monitoring System (TPMS) Type":
                    data.tpmsType = value;
                    break;

                // Safety Features - Optional/Advanced
                case "Adaptive Cruise Control (ACC)":
                    data.adaptiveCruiseControl = value;
                    break;
                case "Crash Imminent Braking (CIB)":
                    data.crashImminentBraking = value;
                    break;
                case "Forward Collision Warning (FCW)":
                    data.forwardCollisionWarning = value;
                    break;
                case "Pedestrian Automatic Emergency Braking (PAEB)":
                    data.pedestrianAEB = value;
                    break;
                case "Blind Spot Warning (BSW)":
                    data.blindSpotWarning = value;
                    break;
                case "Lane Departure Warning (LDW)":
                    data.laneDepartureWarning = value;
                    break;
                case "Lane Keeping Assistance (LKA)":
                    data.laneKeepingAssistance = value;
                    break;
                case "Parking Assist":
                    data.parkingAssist = value;
                    break;
                case "Keyless Ignition":
                    data.keylessIgnition = value;
                    break;
                case "Adaptive Driving Beam (ADB)":
                    data.adaptiveDrivingBeam = value;
                    break;
                case "Active Safety System Note":
                    data.activeSafetyNote = value;
                    break;

                // Pricing
                case "Base Price ($)":
                    data.basePrice = value;
                    break;
            }
        }

        DebugLog.d(TAG, "✓ Decoded online: " + data.modelYear + " " + data.make + " " + data.model);
        return data;
    }

    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /**
     * NHTSA API Response structure
     */
    private static class NhtsaResponse {
        @SerializedName("Count")
        int Count;

        @SerializedName("Message")
        String Message;

        @SerializedName("Results")
        List<NhtsaField> Results;
    }

    /**
     * Individual field in NHTSA response
     */
    private static class NhtsaField {
        @SerializedName("Variable")
        String Variable;

        @SerializedName("Value")
        String Value;

        @SerializedName("ValueId")
        String ValueId;
    }
}
