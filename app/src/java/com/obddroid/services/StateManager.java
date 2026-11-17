package com.obddroid.services;

import com.obddroid.ecu.EcuManager;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;

import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Centralized state manager for OBD-II protocol and adapter state.
 *
 * PURPOSE:
 * - Provides a single source of truth for state cleanup
 * - Ensures all state is properly reset when returning to main screen
 * - Prevents state corruption from affecting multiple activities
 *
 * DESIGN PRINCIPLES:
 * - Defensive: Assume every mode can corrupt state
 * - Atomic: All cleanup happens in one operation
 * - Logged: Every cleanup step is logged for debugging
 * - Fail-safe: Errors don't prevent partial cleanup
 *
 * USAGE:
 * When returning to MainActivity from any OBD service mode:
 *   StateManager.cleanupState(previousServiceMode);
 *
 * This will:
 * 1. Stop the current service
 * 2. Clear all OBD data collections (PidPvs, VidPvs, TidPvs, etc.)
 * 3. Clear ECU addresses from ElmProt
 * 4. Reset ELM327 adapter state if needed
 * 5. Clear application-level caches
 * 6. Validate that cleanup succeeded
 */
public class StateManager {

    private static final Logger log = Logger.getLogger("StateManager");

    /**
     * State cleanup levels - progressively more aggressive
     */
    public enum CleanupLevel {
        SOFT,       // Clear data only, keep adapter state
        NORMAL,     // Clear data + reset service mode
        HARD,       // Clear data + reset adapter state
        FULL        // Nuclear option: full ELM reset
    }

    /**
     * Track which service we're coming FROM to determine cleanup needs
     */
    public enum StateRisk {
        SAFE,       // OBD_SVC_NONE, OBD_SVC_DATA - safe modes
        MODERATE,   // OBD_SVC_VEH_INFO, OBD_SVC_FREEZEFRAME - might accumulate state
        HIGH,       // OBD_SVC_READ_CODES, OBD_SVC_PENDINGCODES - can leave filters set
        CRITICAL    // OBD_SVC_CTRL_MODE - can corrupt everything
    }

    // Track the last active service mode
    private static int lastServiceMode = ObdProt.OBD_SVC_NONE;
    private static long lastServiceChangeTime = 0;

    /**
     * Determine state risk level based on service mode
     */
    public static StateRisk assessRisk(int serviceMode) {
        switch (serviceMode) {
            case ObdProt.OBD_SVC_NONE:
            case ObdProt.OBD_SVC_DATA:
                return StateRisk.SAFE;

            case ObdProt.OBD_SVC_VEH_INFO:
            case ObdProt.OBD_SVC_FREEZEFRAME:
                return StateRisk.MODERATE;

            case ObdProt.OBD_SVC_READ_CODES:
            case ObdProt.OBD_SVC_PENDINGCODES:
            case ObdProt.OBD_SVC_PERMACODES:
                return StateRisk.HIGH;

            case ObdProt.OBD_SVC_CTRL_MODE:
                return StateRisk.CRITICAL;

            default:
                return StateRisk.HIGH; // Unknown modes are risky
        }
    }

    /**
     * Determine required cleanup level based on previous service
     */
    public static CleanupLevel determineCleanupLevel(int previousService) {
        StateRisk risk = assessRisk(previousService);

        switch (risk) {
            case SAFE:
                return CleanupLevel.SOFT;
            case MODERATE:
                return CleanupLevel.NORMAL;
            case HIGH:
                return CleanupLevel.HARD;
            case CRITICAL:
                return CleanupLevel.FULL;
            default:
                return CleanupLevel.NORMAL;
        }
    }

    /**
     * Main cleanup method - called when returning to MainActivity
     *
     * @param fromService The service mode we're coming FROM
     * @return true if cleanup succeeded, false if errors occurred
     */
    public static boolean cleanupState(int fromService) {
        CleanupLevel level = determineCleanupLevel(fromService);

        log.info("════════════════════════════════════════════════════");
        log.info("STATE CLEANUP INITIATED");
        log.info("From Service: " + ObdProt.getServiceName(fromService));
        log.info("Risk Level: " + assessRisk(fromService));
        log.info("Cleanup Level: " + level);
        log.info("════════════════════════════════════════════════════");

        boolean success = true;

        // Step 1: Stop current service immediately
        success &= stopCurrentService();

        // Step 2: Clear data collections based on cleanup level
        success &= clearDataCollections(level);

        // Step 3: Clear ECU addresses (ONLY for FULL cleanup - Test Control)
        // Preserving ECU addresses for normal navigation allows ECU Modules to work correctly
        if (level == CleanupLevel.FULL) {
            success &= clearEcuAddresses();
        }

        // Step 4: Reset ELM adapter state if needed
        if (level == CleanupLevel.HARD || level == CleanupLevel.FULL) {
            success &= resetAdapterState(level);
        }

        // Step 5: Clear application-level state
        success &= clearApplicationState();

        // Step 6: Validate cleanup succeeded
        success &= validateCleanState();

        log.info("════════════════════════════════════════════════════");
        log.info("STATE CLEANUP COMPLETE: " + (success ? "SUCCESS" : "ERRORS OCCURRED"));
        log.info("════════════════════════════════════════════════════");

        return success;
    }

    /**
     * Step 1: Stop the current OBD service
     */
    private static boolean stopCurrentService() {
        try {
            log.info("[1/6] Stopping current service...");

            if (CommService.elm != null) {
                int currentService = CommService.elm.getService();
                if (currentService != ObdProt.OBD_SVC_NONE) {
                    log.info("  → Stopping service: " + ObdProt.getServiceName(currentService));
                    CommService.elm.setService(ObdProt.OBD_SVC_NONE, false);
                }
            }

            log.info("  ✓ Service stopped");
            return true;
        } catch (Exception e) {
            log.severe("  ✗ Failed to stop service: " + e.getMessage());
            return false;
        }
    }

    /**
     * Step 2: Clear OBD data collections
     */
    private static boolean clearDataCollections(CleanupLevel level) {
        try {
            log.info("[2/6] Clearing data collections (level: " + level + ")...");

            int clearedCount = 0;

            // Always clear PidPvs (Mode 1 live data)
            if (ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty()) {
                log.info("  → Clearing PidPvs: " + ObdProt.PidPvs.size() + " items");
                ObdProt.PidPvs.clear();
                clearedCount++;
            }

            // Clear VidPvs (Mode 9 vehicle info) for NORMAL+ cleanup
            if (level != CleanupLevel.SOFT && ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty()) {
                log.info("  → Clearing VidPvs: " + ObdProt.VidPvs.size() + " items");
                ObdProt.VidPvs.clear();
                clearedCount++;
            }

            // Clear TidPvs (Mode 8 test control) for NORMAL+ cleanup
            if (level != CleanupLevel.SOFT && ObdProt.TidPvs != null && !ObdProt.TidPvs.isEmpty()) {
                log.info("  → Clearing TidPvs: " + ObdProt.TidPvs.size() + " items");
                ObdProt.TidPvs.clear();
                clearedCount++;
            }

            // Clear fault codes for HARD+ cleanup
            if ((level == CleanupLevel.HARD || level == CleanupLevel.FULL) &&
                ObdProt.tCodes != null && !ObdProt.tCodes.isEmpty()) {
                log.info("  → Clearing tCodes: " + ObdProt.tCodes.size() + " items");
                ObdProt.tCodes.clear();
                clearedCount++;
            }

            log.info("  ✓ Cleared " + clearedCount + " data collections");
            return true;
        } catch (Exception e) {
            log.severe("  ✗ Failed to clear data: " + e.getMessage());
            return false;
        }
    }

    /**
     * Step 3: Clear ECU addresses from ElmProt (CRITICAL!)
     */
    private static boolean clearEcuAddresses() {
        try {
            log.info("[3/6] Clearing ECU addresses...");

            if (CommService.elm != null) {
                // Use reflection to access and clear ecuAddresses
                java.lang.reflect.Field addressField = ElmProt.class.getDeclaredField("ecuAddresses");
                addressField.setAccessible(true);
                Object addresses = addressField.get(CommService.elm);

                if (addresses instanceof TreeSet) {
                    @SuppressWarnings("unchecked")
                    TreeSet<Integer> ecuAddresses = (TreeSet<Integer>) addresses;

                    if (!ecuAddresses.isEmpty()) {
                        log.info("  → Clearing " + ecuAddresses.size() + " ECU addresses");
                        ecuAddresses.clear();
                    }
                }
            }

            // Also clear EcuManager
            EcuManager.getInstance().clear();

            log.info("  ✓ ECU addresses cleared");
            return true;
        } catch (ReflectiveOperationException | SecurityException e) {
            log.severe("  ✗ Failed to clear ECU addresses: " + e.getMessage());
            return false;
        }
    }

    /**
     * Step 4: Reset ELM327 adapter state
     */
    private static boolean resetAdapterState(CleanupLevel level) {
        try {
            log.info("[4/6] Resetting adapter state (level: " + level + ")...");

            if (CommService.elm == null) {
                log.warning("  ! ELM not available, skipping adapter reset");
                return true;
            }

            if (level == CleanupLevel.FULL) {
                // FULL reset: ATZ (complete adapter reboot)
                log.info("  → Sending ATZ (full reset)");
                CommService.elm.sendTelegram("ATZ".toCharArray());
                Thread.sleep(2000); // Wait for adapter to restart

                log.info("  → Sending ATD (reset to defaults)");
                CommService.elm.sendTelegram("ATD".toCharArray());
                Thread.sleep(500);

            } else if (level == CleanupLevel.HARD) {
                // HARD reset: ATD (reset to defaults)
                log.info("  → Sending ATD (reset to defaults)");
                CommService.elm.sendTelegram("ATD".toCharArray());
                Thread.sleep(500);
            }

            // Always ensure headers are disabled
            log.info("  → Disabling headers (ATH0)");
            CommService.elm.sendTelegram("ATH0".toCharArray());
            Thread.sleep(200);

            log.info("  ✓ Adapter state reset");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.severe("  ✗ Failed to reset adapter: " + e.getMessage());
            return false;
        }
    }

    /**
     * Step 5: Clear application-level state
     */
    private static boolean clearApplicationState() {
        try {
            log.info("[5/6] Clearing application state...");

            // Clear ECU manager (redundant with step 3, but ensures it's clean)
            EcuManager.getInstance().clear();

            // Note: Don't clear VehicleManager - VIN should persist across mode switches

            log.info("  ✓ Application state cleared");
            return true;
        } catch (Exception e) {
            log.severe("  ✗ Failed to clear application state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Step 6: Validate that state is actually clean
     */
    private static boolean validateCleanState() {
        try {
            log.info("[6/6] Validating clean state...");

            boolean isClean = true;

            // Check service is NONE
            if (CommService.elm != null && CommService.elm.getService() != ObdProt.OBD_SVC_NONE) {
                log.warning("  ! Service not set to NONE: " + CommService.elm.getService());
                isClean = false;
            }

            // Check PidPvs is empty
            if (ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty()) {
                log.warning("  ! PidPvs not empty: " + ObdProt.PidPvs.size() + " items remain");
                isClean = false;
            }

            // Check ecuAddresses is empty
            try {
                java.lang.reflect.Field addressField = ElmProt.class.getDeclaredField("ecuAddresses");
                addressField.setAccessible(true);
                Object addresses = addressField.get(CommService.elm);

                if (addresses instanceof TreeSet) {
                    @SuppressWarnings("unchecked")
                    TreeSet<Integer> ecuAddresses = (TreeSet<Integer>) addresses;

                    if (!ecuAddresses.isEmpty()) {
                        log.warning("  ! ecuAddresses not empty: " + ecuAddresses.size() + " addresses remain");
                        isClean = false;
                    }
                }
            } catch (ReflectiveOperationException | SecurityException e) {
                log.warning("  ! Could not validate ecuAddresses: " + e.getMessage());
            }

            if (isClean) {
                log.info("  ✓ State validation PASSED");
            } else {
                log.warning("  ✗ State validation FAILED - residual state detected");
            }

            return isClean;
        } catch (Exception e) {
            log.severe("  ✗ Failed to validate state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Track service mode changes for cleanup decision making
     */
    public static void onServiceChanged(int newService) {
        lastServiceMode = newService;
        lastServiceChangeTime = System.currentTimeMillis();
        log.fine("Service changed to: " + ObdProt.getServiceName(newService));
    }

    /**
     * Get the last service mode (for cleanup decisions)
     */
    public static int getLastServiceMode() {
        return lastServiceMode;
    }

    /**
     * Get time since last service change (milliseconds)
     */
    public static long getTimeSinceLastServiceChange() {
        return System.currentTimeMillis() - lastServiceChangeTime;
    }
}
