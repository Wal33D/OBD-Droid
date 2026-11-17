package com.obddroid.services;

import android.util.Log;
import com.obddroid.interfaces.RawTelegramListener;
import com.obddroid.utils.DebugLog;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Isolated ECU discovery service using Mode 9 with headers enabled.
 *
 * DESIGN PRINCIPLE: This service is completely isolated from the rest of the app.
 * - Does NOT modify VidPvs or existing Mode 9 flow
 * - Only used by ECU Modules page (EcuListActivity)
 * - Can be disabled without affecting any other functionality
 *
 * HOW IT WORKS:
 * 1. Temporarily enables CAN headers (ATH1)
 * 2. Sends Mode 9 requests (0904, 090A for calibration ID and ECU name)
 * 3. Receives RAW responses WITH headers via RawTelegramListener
 * 4. Parses ECU addresses from CAN headers (e.g., "7E8 03 490201...")
 * 5. Builds map of ECU address → ECU info
 * 6. Restores headers to disabled state (ATH0)
 * 7. Returns results via CompletableFuture
 *
 */
public class EcuDiscoveryService implements RawTelegramListener {
    private static final String TAG = "EcuDiscoveryService";
    private static final Logger log = Logger.getLogger(TAG);

    // Discovery state
    private final Map<Integer, EcuDiscoveryInfo> discoveredEcus = new HashMap<>();
    private CompletableFuture<Map<Integer, EcuDiscoveryInfo>> currentDiscovery;
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);

    // Per-ECU multiline buffers to handle concurrent responses from multiple ECUs
    private final Map<Integer, String> multilineBuffers = new HashMap<>();

    /**
     * ECU information discovered via Mode 9
     */
    public static class EcuDiscoveryInfo {
        public final int address;
        public String name;
        public String calibrationId;
        public String calibrationId2;
        public String cvn;

        public EcuDiscoveryInfo(int address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return String.format("ECU 0x%X: name=%s, calId=%s, calId2=%s, cvn=%s",
                address, name, calibrationId, calibrationId2, cvn);
        }
    }

    /**
     * Discover all ECUs on the CAN bus using Mode 9 with headers enabled.
     *
     * This is a non-blocking operation that returns immediately with a CompletableFuture.
     * The discovery process runs in a background thread.
     *
     * @return CompletableFuture that completes with map of ECU address → info
     */
    public CompletableFuture<Map<Integer, EcuDiscoveryInfo>> discoverEcus() {
        if (isDiscovering.get()) {
            Log.w(TAG, "Discovery already in progress");
            return currentDiscovery;
        }

        if (CommService.elm == null) {
            Log.e(TAG, "ELM protocol not available - cannot discover ECUs");
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        currentDiscovery = new CompletableFuture<>();
        discoveredEcus.clear();
        multilineBuffers.clear();

        new Thread(() -> {
            try {
                isDiscovering.set(true);
                Log.i(TAG, "========== ECU DISCOVERY START ==========");

                // Add ourselves as a RAW telegram listener to receive responses WITH headers
                CommService.elm.addRawTelegramListener(this);
                Log.i(TAG, "Registered as RAW telegram listener");

                // Step 1: Enable headers to capture ECU addresses
                Log.i(TAG, "Step 1: Enabling headers (ATH1)");
                sendRawCommand("ATH1");
                Thread.sleep(1000);  // Wait for header enable (increased for slower adapters)

                // Step 2: Request Mode 9 PID 04 (Calibration ID)
                Log.i(TAG, "Step 2: Requesting Calibration ID (0904)");
                sendRawCommand("0904");
                Thread.sleep(1200);  // Wait for all ECU multiline responses (increased for reliability)

                // Step 3: Request Mode 9 PID 06 (CVN - Calibration Verification)
                Log.i(TAG, "Step 3: Requesting CVN (0906)");
                sendRawCommand("0906");
                Thread.sleep(1200);  // Wait for all ECU multiline responses (increased for reliability)

                // Step 4: Request Mode 9 PID 0A (ECU Name)
                Log.i(TAG, "Step 4: Requesting ECU Name (090A)");
                sendRawCommand("090A");
                Thread.sleep(1200);  // Wait for all ECU multiline responses (increased for reliability)

                // Step 6: Process any pending multiline buffers
                if (!multilineBuffers.isEmpty()) {
                    Log.i(TAG, "Step 6: Processing " + multilineBuffers.size() + " pending multiline buffers");
                    for (Map.Entry<Integer, String> entry : multilineBuffers.entrySet()) {
                        int addr = entry.getKey();
                        String ecuBuffer = entry.getValue();
                        DebugLog.d(TAG, "Processing buffer for ECU 0x" + Integer.toHexString(addr).toUpperCase());
                        String assembledMessage = assembleMultilineMessage(ecuBuffer, addr);
                        if (!assembledMessage.isEmpty()) {
                            parseMode9Response(addr, assembledMessage);
                        }
                    }
                    multilineBuffers.clear();
                }

                // Step 7: Complete discovery
                Log.i(TAG, "========== ECU DISCOVERY COMPLETE ==========");
                Log.i(TAG, "Discovered " + discoveredEcus.size() + " ECUs:");
                for (EcuDiscoveryInfo info : discoveredEcus.values()) {
                    Log.i(TAG, "  " + info.toString());
                }

                currentDiscovery.complete(new HashMap<>(discoveredEcus));

            } catch (Exception e) {
                Log.e(TAG, "ECU discovery failed: " + e.getMessage(), e);
                currentDiscovery.completeExceptionally(e);
            } finally {
                // CRITICAL: Always restore ELM state, even on exceptions/timeouts
                // This prevents leaving headers enabled, which breaks normal OBD communication
                try {
                    Log.i(TAG, "Step 5 (cleanup): Disabling headers (ATH0)");
                    sendRawCommand("ATH0");
                    Thread.sleep(500);  // Wait for headers to disable
                    Log.i(TAG, "Headers disabled successfully");
                } catch (Exception cleanup) {
                    Log.e(TAG, "Failed to disable headers during cleanup: " + cleanup.getMessage());
                }

                // Remove raw listener
                if (CommService.elm != null) {
                    CommService.elm.removeRawTelegramListener(this);
                    Log.i(TAG, "Unregistered RAW telegram listener");
                }

                isDiscovering.set(false);
            }
        }, "ECU-Discovery-Thread").start();

        return currentDiscovery;
    }

    /**
     * Send raw command to ELM adapter
     */
    private void sendRawCommand(String command) {
        if (CommService.elm != null) {
            CommService.elm.sendTelegram(command.toCharArray());
            DebugLog.d(TAG, "TX: " + command);
        }
    }

    /**
     * Handle incoming RAW telegram responses from ELM adapter.
     * This is called by ElmProt BEFORE header stripping, giving us access to
     * complete responses including CAN headers (e.g., "7E8 03 490201...").
     *
     * CRITICAL: This method receives responses WITH headers when ATH1 is enabled!
     */
    @Override
    public int handleRawTelegram(char[] buffer) {
        String response = new String(buffer).trim();

        if (response.isEmpty()) {
            return 0;
        }

        DebugLog.d(TAG, "RX: " + response);

        // Process any pending multiline buffers when we see the prompt
        if (response.equals(">")) {
            if (!multilineBuffers.isEmpty()) {
                DebugLog.d(TAG, "Prompt received - processing " + multilineBuffers.size() + " pending multiline buffers");
                for (Map.Entry<Integer, String> entry : multilineBuffers.entrySet()) {
                    int addr = entry.getKey();
                    String ecuBuffer = entry.getValue();
                    DebugLog.d(TAG, String.format("Processing buffer for ECU 0x%X, buffer length: %d", addr, ecuBuffer.length()));
                    String assembledMessage = assembleMultilineMessage(ecuBuffer, addr);
                    DebugLog.d(TAG, "Assembled message: " + assembledMessage);
                    if (!assembledMessage.isEmpty()) {
                        DebugLog.d(TAG, "Calling parseMode9Response for ECU 0x" + Integer.toHexString(addr).toUpperCase());
                        parseMode9Response(addr, assembledMessage);
                    } else {
                        Log.w(TAG, "Assembled message is EMPTY for ECU 0x" + Integer.toHexString(addr).toUpperCase());
                    }
                }
                multilineBuffers.clear();
            }
            return response.length();
        }

        // Ignore other AT command responses (OK, etc.)
        if (response.startsWith("OK") || response.startsWith("ATH") ||
            response.startsWith("SEARCH")) {
            return response.length();
        }

        // Parse ECU address from header (if present)
        int ecuAddress = parseEcuAddress(response);

        // Handle ISO-TP multiline responses (per-ECU buffering)
        if (isMultilineStart(response)) {
            // Start of multiline message - store in ECU-specific buffer
            multilineBuffers.put(ecuAddress, response);
            DebugLog.d(TAG, "Multiline START for ECU 0x" + Integer.toHexString(ecuAddress).toUpperCase());
            return response.length();
        } else if (isMultilineContinuation(response) && multilineBuffers.containsKey(ecuAddress)) {
            // Consecutive frame - append to ECU-specific buffer
            String existing = multilineBuffers.get(ecuAddress);
            multilineBuffers.put(ecuAddress, existing + response);
            DebugLog.d(TAG, "Multiline CONTINUE for ECU 0x" + Integer.toHexString(ecuAddress).toUpperCase());
            return response.length();
        } else if (!multilineBuffers.isEmpty()) {
            // Got a non-consecutive frame - process ALL pending multiline buffers
            DebugLog.d(TAG, "Multiline END - processing " + multilineBuffers.size() + " assembled messages");
            for (Map.Entry<Integer, String> entry : multilineBuffers.entrySet()) {
                int addr = entry.getKey();
                String ecuBuffer = entry.getValue();
                String assembledMessage = assembleMultilineMessage(ecuBuffer, addr);
                if (!assembledMessage.isEmpty()) {
                    parseMode9Response(addr, assembledMessage);
                }
            }
            multilineBuffers.clear();
            // Fall through to process current response
        }

        // Parse Mode 9 data if we have an ECU address (single-frame responses)
        if (ecuAddress != -1 && multilineBuffers.isEmpty()) {
            parseMode9Response(ecuAddress, response);
        }

        return response.length();
    }

    /**
     * Assemble a complete message from ISO-TP multiline frames.
     *
     * Input buffer contains concatenated frames like:
     * "7E81017490A0145434D7E821002D456E67696E7E82265436F6E74726F7E8236C0000..."
     *
     * This method:
     * 1. Splits the buffer into individual frames
     * 2. Extracts data from each frame (removing headers)
     * 3. Concatenates all data portions
     * 4. Returns assembled message ready for parsing
     *
     * @param buffer Concatenated multiline frames
     * @param ecuAddr ECU address for validation
     * @return Assembled data as hex string (e.g., "490A0145434D002D456E67696E65436F6E74726F6C")
     */
    private String assembleMultilineMessage(String buffer, int ecuAddr) {
        StringBuilder assembled = new StringBuilder();
        String ecuHex = String.format("%03X", ecuAddr);

        int pos = 0;
        while (pos < buffer.length()) {
            // Each frame should start with ECU address
            if (pos + 3 > buffer.length()) break;

            String frameEcu = buffer.substring(pos, pos + 3);
            if (!frameEcu.equalsIgnoreCase(ecuHex)) {
                Log.w(TAG, "Unexpected ECU in multiline buffer: " + frameEcu + " (expected " + ecuHex + ")");
                break;
            }

            // Get frame type
            if (pos + 5 > buffer.length()) break;
            String pci = buffer.substring(pos + 3, pos + 5);

            int dataStart;
            int frameLength;

            if (pci.startsWith("10")) {
                // First frame: ECU(3) + PCI(2) + length(2) + data(12)
                dataStart = pos + 7;
                frameLength = 19;  // CAN frame: 3 ECU + 16 data bytes (8 bytes = 16 hex chars)
            } else if (pci.startsWith("2")) {
                // Consecutive frame: ECU(3) + PCI(2) + data(14)
                dataStart = pos + 5;
                frameLength = 19;  // CAN frame: 3 ECU + 16 data bytes (8 bytes = 16 hex chars)
            } else {
                Log.w(TAG, "Unexpected PCI in multiline: " + pci);
                break;
            }

            // Extract data from this frame
            int dataEnd = Math.min(pos + frameLength, buffer.length());
            if (dataStart < buffer.length()) {
                String frameData = buffer.substring(dataStart, dataEnd);
                assembled.append(frameData);
                DebugLog.d(TAG, "  Frame data: " + frameData);
            }

            // Move to next frame
            pos += frameLength;
        }

        String result = assembled.toString();
        DebugLog.d(TAG, "Assembled multiline message: " + result);
        return result;
    }

    /**
     * Parse ECU address from response with header.
     *
     * Expected formats:
     * - "7E8 03 49 04 01..." (with spaces)
     * - "7E803490401..." (no spaces)
     * - "18DAF110 03 49 04..." (29-bit CAN)
     *
     * @return ECU address (0x7E8, 0x7E9, etc.) or -1 if no header found
     */
    private int parseEcuAddress(String response) {
        try {
            // Remove any leading/trailing whitespace
            response = response.trim();

            // Format with space: "7E8 03 49..."
            if (response.length() >= 4 && response.charAt(3) == ' ') {
                String addrHex = response.substring(0, 3);
                return Integer.parseInt(addrHex, 16);
            }
            // Format without space: "7E803490A..." - check if first 3 chars look like CAN address
            else if (response.length() >= 3) {
                String potentialAddr = response.substring(0, 3);
                int addr = Integer.parseInt(potentialAddr, 16);
                // Valid OBD ECU addresses are typically 0x7E0-0x7EF or 0x7F0-0x7FF
                if ((addr >= 0x7E0 && addr <= 0x7FF) || (addr >= 0x18D && addr <= 0x18F)) {
                    return addr;
                }
            }
            // 29-bit CAN format: "18DAF110 03 49..."
            else if (response.length() >= 9 && response.charAt(8) == ' ') {
                // For 29-bit, we'll use the functional address portion
                String addrHex = response.substring(0, 8);
                return Integer.parseInt(addrHex, 16);
            }
        } catch (NumberFormatException e) {
            DebugLog.d(TAG, "Could not parse ECU address from: " + response);
        }

        return -1;  // No header found
    }

    /**
     * Check if this is the start of an ISO-TP multiline response.
     *
     * ISO-TP Format (no spaces):
     * - Single frame: [ECU] [0X] [data...] where X = length (0-7)
     * - First frame:  [ECU] [10] [length] [data...]
     * - Consecutive:  [ECU] [2X] [data...] where X = sequence (0-F)
     *
     * Example: "7E81017490A..." = 7E8 (ECU) + 10 (first frame) + 17 (length) + data
     */
    private boolean isMultilineStart(String response) {
        // Remove ECU address (first 3 hex chars)
        if (response.length() < 5) return false;

        // Check if PCI byte indicates first frame (starts with "10")
        String withoutAddr = response.substring(3);
        return withoutAddr.startsWith("10");
    }

    /**
     * Check if this is a consecutive frame of an ISO-TP multiline response.
     * Format: [ECU] [2X] [data...] where X is sequence number (0-F)
     */
    private boolean isMultilineContinuation(String response) {
        if (response.length() < 5) return false;

        // Check if PCI byte indicates consecutive frame (starts with "2")
        String withoutAddr = response.substring(3);
        return withoutAddr.startsWith("2");
    }

    /**
     * ISO-TP multiline messages don't have an explicit "end" marker.
     * Assembly completes when we stop receiving consecutive frames.
     */
    private boolean isMultilineEnd(String response) {
        // Not used for ISO-TP - we rely on timeout or next single-frame message
        return false;
    }

    /**
     * Parse Mode 9 response to extract data.
     *
     * Handles both:
     * - Raw responses with headers: "7E81017490A..." or "7E8 03 49..."
     * - Assembled multiline data: "490A0145434D002D..."
     */
    private void parseMode9Response(int ecuAddress, String response) {
        DebugLog.d(TAG, String.format("parseMode9Response() called: ECU=0x%X, response=%s", ecuAddress, response));
        String data;

        // Check if response already starts with Mode 9 service (49)
        if (response.startsWith("49")) {
            // Already headerless (assembled multiline data) - use as-is
            data = response;
            DebugLog.d(TAG, "  Starts with 49, using as-is (assembled data)");
        } else if (response.length() >= 3 && response.substring(0, 3).matches("[07][EF][0-9A-F]")) {
            // Starts with ECU address pattern (7E8, 7E9, 7EB, etc.) - remove header
            data = removeHeader(response);
            DebugLog.d(TAG, "  Detected ECU header, removed it. Data: " + data);
        } else {
            // Unknown format - try as-is
            data = response;
            DebugLog.d(TAG, "  Unknown format, trying as-is: " + data);
        }

        // Check if this is a Mode 9 response (starts with or contains 49)
        if (!data.startsWith("49") && !data.contains("49")) {
            DebugLog.d(TAG, "  Not a Mode 9 response, skipping");
            return;
        }
        DebugLog.d(TAG, "  Is Mode 9 response, processing...");

        // Get or create ECU info
        EcuDiscoveryInfo info = getOrCreate(ecuAddress);

        // Parse based on PID
        if (data.contains("49 04") || data.contains("4904")) {
            // Calibration ID (PID 04) - can have multiple calibration IDs
            String[] calIds = parseCalibrationIds(data);
            if (calIds.length > 0 && calIds[0] != null && !calIds[0].isEmpty()) {
                info.calibrationId = calIds[0];
                Log.i(TAG, String.format("ECU 0x%X Cal ID: %s", ecuAddress, calIds[0]));
            }
            if (calIds.length > 1 && calIds[1] != null && !calIds[1].isEmpty()) {
                info.calibrationId2 = calIds[1];
                Log.i(TAG, String.format("ECU 0x%X Cal ID 2: %s", ecuAddress, calIds[1]));
            }
        } else if (data.contains("49 0A") || data.contains("490A")) {
            // ECU Name (PID 0A)
            String name = parseEcuName(data);
            if (name != null && !name.isEmpty()) {
                info.name = name;
                Log.i(TAG, String.format("ECU 0x%X Name: %s", ecuAddress, name));
            }
        } else if (data.contains("49 06") || data.contains("4906")) {
            // CVN (PID 06)
            String cvn = parseCvn(data);
            if (cvn != null && !cvn.isEmpty()) {
                info.cvn = cvn;
                Log.i(TAG, String.format("ECU 0x%X CVN: %s", ecuAddress, cvn));
            }
        }
    }

    /**
     * Remove ECU address and ISO-TP frame headers from response.
     *
     * ISO-TP Frame Formats (no spaces):
     * - Single frame: [ECU 3hex] [0X] [data...]
     * - First frame:  [ECU 3hex] [10] [len 2hex] [data...]
     * - Consecutive:  [ECU 3hex] [2X] [data...]
     *
     * Examples:
     * - "7E8 03 49 04..." → "49 04..." (single frame with spaces)
     * - "7E801490A01ECM" → "490A01ECM" (single frame, 01 = 1 byte follows, skip it)
     * - "7E81017490A..." → "490A..." (first frame, skip 7E8 + 10 + 17)
     * - "7E821002D45..." → "002D45..." (consecutive, skip 7E8 + 21)
     */
    private String removeHeader(String response) {
        // Format with spaces: "7E8 03 49 04..." → "49 04..."
        if (response.length() >= 4 && response.charAt(3) == ' ') {
            int dataStart = response.indexOf(' ', 4) + 1;
            return response.substring(dataStart);
        }

        // Format without spaces - need to detect frame type
        if (response.length() < 5) {
            return response;
        }

        String pci = response.substring(3, 5);  // Protocol Control Information

        if (pci.startsWith("10")) {
            // First frame: skip ECU (3) + PCI (2) + length (2) = 7 chars
            return response.length() > 7 ? response.substring(7) : "";
        } else if (pci.startsWith("2")) {
            // Consecutive frame: skip ECU (3) + PCI (2) = 5 chars
            return response.length() > 5 ? response.substring(5) : "";
        } else if (pci.startsWith("0")) {
            // Single frame: skip ECU (3) + PCI (2) = 5 chars
            return response.length() > 5 ? response.substring(5) : "";
        } else {
            // Unknown format, skip ECU address only
            return response.substring(3);
        }
    }

    /**
     * Parse Calibration IDs from Mode 9 PID 04 response.
     * Can return multiple calibration IDs if the ECU has multiple calibrations.
     *
     * Format examples:
     * - "49 04 01 XXXX..." - Single calibration ID (message count = 01)
     * - "49 04 01 XXXX... 49 04 02 YYYY..." - Multiple calibration IDs in one response
     *
     * @return Array of calibration IDs (max 2)
     */
    private String[] parseCalibrationIds(String data) {
        java.util.List<String> calIds = new java.util.ArrayList<>();

        try {
            // Remove spaces
            String cleanData = data.replaceAll("\\s+", "");

            // Find all occurrences of "4904" in the data
            int pos = 0;
            while (pos < cleanData.length()) {
                int start = cleanData.indexOf("4904", pos);
                if (start == -1) break;

                // Skip "4904" + message count byte (01, 02, etc.)
                int dataStart = start + 6;
                if (dataStart >= cleanData.length()) break;

                // Find the next "4904" or end of string to determine where this cal ID ends
                int nextStart = cleanData.indexOf("4904", dataStart);
                int dataEnd = (nextStart != -1) ? nextStart : cleanData.length();

                // Extract hex data for this calibration ID
                String hexData = cleanData.substring(dataStart, dataEnd);
                String calId = hexToAscii(hexData);

                if (calId != null && !calId.trim().isEmpty()) {
                    calIds.add(calId.trim());
                    DebugLog.d(TAG, "  Parsed Cal ID #" + (calIds.size()) + ": " + calId);
                }

                // Move to next occurrence
                pos = dataStart;

                // Limit to 2 calibration IDs max
                if (calIds.size() >= 2) break;
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse calibration IDs: " + e.getMessage());
        }

        return calIds.toArray(new String[0]);
    }

    /**
     * Parse ECU Name from Mode 9 PID 0A response
     * Format: "49 0A 01 XXXX..." where XXXX is ASCII hex
     */
    private String parseEcuName(String data) {
        try {
            // Remove spaces
            data = data.replaceAll("\\s+", "");

            // Find "490A" and extract data after it
            int start = data.indexOf("490A");
            if (start == -1) return null;

            // Skip "490A" + message count byte (01)
            start += 6;
            if (start >= data.length()) return null;

            // Extract hex data and convert to ASCII
            String hexData = data.substring(start);
            return hexToAscii(hexData);

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse ECU name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse CVN from Mode 9 PID 06 response
     */
    private String parseCvn(String data) {
        try {
            // Remove spaces
            data = data.replaceAll("\\s+", "");

            // Find "4906" and extract data after it
            int start = data.indexOf("4906");
            if (start == -1) return null;

            // Skip "4906" + message count byte (01)
            start += 6;
            if (start >= data.length()) return null;

            // CVN is typically 4 bytes (8 hex chars)
            String hexData = data.substring(start, Math.min(start + 8, data.length()));
            return hexData.toUpperCase();

        } catch (Exception e) {
            Log.w(TAG, "Failed to parse CVN: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert hex string to ASCII, filtering out non-printable characters.
     *
     * IMPORTANT: ECU names can contain embedded nulls as separators!
     * Example: "ECM\0-EngineControl\0\0" → "ECM-EngineControl"
     * So we can't stop at the first null byte.
     */
    private String hexToAscii(String hex) {
        StringBuilder ascii = new StringBuilder();
        int consecutiveNulls = 0;

        // Process pairs of hex digits
        for (int i = 0; i < hex.length() - 1; i += 2) {
            String hexByte = hex.substring(i, i + 2);
            try {
                int charCode = Integer.parseInt(hexByte, 16);

                if (charCode == 0x00) {
                    consecutiveNulls++;
                    // Stop if we hit 2+ consecutive nulls (end of string padding)
                    if (consecutiveNulls >= 2) {
                        break;
                    }
                    // Skip single nulls (they're separators in ECU names)
                    continue;
                } else {
                    consecutiveNulls = 0;  // Reset null counter

                    // Only include printable ASCII (0x20-0x7E)
                    if (charCode >= 0x20 && charCode <= 0x7E) {
                        ascii.append((char) charCode);
                    }
                }
            } catch (NumberFormatException e) {
                // Skip invalid hex
            }
        }

        return ascii.toString().trim();
    }

    /**
     * Get or create EcuDiscoveryInfo for given address
     */
    private EcuDiscoveryInfo getOrCreate(int ecuAddress) {
        return discoveredEcus.computeIfAbsent(ecuAddress, EcuDiscoveryInfo::new);
    }

    /**
     * Check if discovery is currently in progress
     */
    public boolean isDiscovering() {
        return isDiscovering.get();
    }
}
