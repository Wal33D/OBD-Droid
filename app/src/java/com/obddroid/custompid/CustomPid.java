package com.obddroid.custompid;

import java.io.Serializable;

/**
 * Model class representing a custom PID definition
 *
 * @author Wal33D
 */
public class CustomPid implements Serializable {
    private static final long serialVersionUID = 1L;

    // Database fields
    private long id;
    private String name;
    private String description;
    private String pidHex;              // e.g., "221234"
    private String formula;             // e.g., "(A*256+B)/10"
    private String units;               // e.g., "kPa", "°C", "PSI"
    private String vehicleMake;         // e.g., "Subaru", "BMW", null for universal
    private String vehicleModel;        // e.g., "WRX", "M3", null for any
    private String vehicleYears;        // e.g., "2015-2020", null for any
    private int minValue;               // Minimum expected value for validation
    private int maxValue;               // Maximum expected value for validation
    private int updatePeriod;           // Update period in ms (default 1000)
    private boolean enabled;            // Whether PID is active
    private long createdAt;
    private long updatedAt;

    // Runtime fields (not stored in DB)
    private transient Object cachedValue;

    public CustomPid() {
        this.id = -1;
        this.enabled = false;  // Disabled by default - users must explicitly enable
        this.updatePeriod = 1000;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    /**
     * Constructor for quick creation
     */
    public CustomPid(String name, String pidHex, String formula, String units) {
        this();
        this.name = name;
        this.pidHex = pidHex;
        this.formula = formula;
        this.units = units;
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getPidHex() {
        return pidHex;
    }

    public void setPidHex(String pidHex) {
        this.pidHex = pidHex;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Get PID as integer value for OBD requests
     */
    public int getPidInt() {
        try {
            return Integer.parseInt(pidHex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getVehicleMake() {
        return vehicleMake;
    }

    public void setVehicleMake(String vehicleMake) {
        this.vehicleMake = vehicleMake;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getVehicleYears() {
        return vehicleYears;
    }

    public void setVehicleYears(String vehicleYears) {
        this.vehicleYears = vehicleYears;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getMinValue() {
        return minValue;
    }

    public void setMinValue(int minValue) {
        this.minValue = minValue;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getUpdatePeriod() {
        return updatePeriod;
    }

    public void setUpdatePeriod(int updatePeriod) {
        this.updatePeriod = updatePeriod;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Object getCachedValue() {
        return cachedValue;
    }

    public void setCachedValue(Object cachedValue) {
        this.cachedValue = cachedValue;
    }

    /**
     * Check if PID matches given vehicle
     */
    public boolean matchesVehicle(String make, String model, int year) {
        // Universal PIDs match everything
        if (vehicleMake == null || vehicleMake.trim().isEmpty()) {
            return true;
        }

        // Check make
        if (!vehicleMake.equalsIgnoreCase(make)) {
            return false;
        }

        // Check model if specified
        if (vehicleModel != null && !vehicleModel.trim().isEmpty()) {
            if (!vehicleModel.equalsIgnoreCase(model)) {
                return false;
            }
        }

        // Check year range if specified
        if (vehicleYears != null && !vehicleYears.trim().isEmpty()) {
            return matchesYear(year);
        }

        return true;
    }

    /**
     * Check if year matches the year range
     */
    private boolean matchesYear(int year) {
        try {
            if (vehicleYears.contains("-")) {
                String[] parts = vehicleYears.split("-");
                int minYear = Integer.parseInt(parts[0].trim());
                int maxYear = Integer.parseInt(parts[1].trim());
                return year >= minYear && year <= maxYear;
            } else {
                int exactYear = Integer.parseInt(vehicleYears.trim());
                return year == exactYear;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return true; // If parsing fails, accept any year
        }
    }

    @Override
    public String toString() {
        return String.format("%s (PID: %s) [%s]", name, pidHex, units);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CustomPid other = (CustomPid) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
