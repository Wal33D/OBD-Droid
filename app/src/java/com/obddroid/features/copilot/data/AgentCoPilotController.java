package com.obddroid.features.copilot.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.obddroid.features.copilot.data.CoPilotCallback;
import com.obddroid.features.copilot.data.tools.AgentToolExecutor;
import com.obddroid.scan.ScanResultsManager;
import com.obddroid.utils.DebugLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent API implementation of CoPilot controller.
 * Uses OpenAI Assistants API for persistent conversations with tool calling.
 *
 * Key differences from Chat Completions:
 * - Persistent threads (survives app restarts)
 * - Server-side message history (no context management)
 * - Built-in tool calling (AI can execute functions)
 * - VIN-based conversation resumption
 */
public class AgentCoPilotController {

    private static final String TAG = "AgentCoPilotController";
    private static final String ASSISTANT_NAME = "OBD-Droid CoPilot";
    private static final String ASSISTANT_INSTRUCTIONS =
        "You are an expert automotive diagnostic AI assistant integrated into the OBD-Droid app. " +
        "You help users diagnose vehicle issues, interpret scan data, and provide repair guidance.\n\n" +
        "Core Capabilities:\n" +
        "- Run comprehensive OBD scans using run_full_scan\n" +
        "- Retrieve and analyze scan results using get_scan_results\n" +
        "- Provide AI-powered diagnostic analysis using analyze_dtcs\n" +
        "- Export scan reports using export_report\n" +
        "- Clear fault codes using clear_fault_codes (requires user confirmation)\n" +
        "- Navigate app screens using open_screen\n\n" +
        "Guidelines:\n" +
        "- Be concise and practical - users need quick, actionable advice\n" +
        "- Always explain technical terms in simple language\n" +
        "- Prioritize safety - warn about risks and recommend professional help when appropriate\n" +
        "- Use tools proactively - if a user asks about fault codes, offer to run a scan\n" +
        "- Reference specific PIDs, DTCs, and data from scans when providing diagnoses\n" +
        "- Never guess - if you need scan data, use the tools to get it";

    private static AgentCoPilotController instance;

    public static synchronized AgentCoPilotController getInstance() {
        if (instance == null) {
            instance = new AgentCoPilotController();
        }
        return instance;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "agent-copilot-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Context appContext;
    private AgentApiClient apiClient;
    private AgentThreadManager threadManager;
    private AgentToolExecutor toolExecutor;

    private String assistantId;
    private String currentThreadId;
    private String currentVin;

    private AgentCoPilotController() {
    }

    public synchronized void initialize(Context context) {
        if (appContext != null) {
            return;
        }
        appContext = context.getApplicationContext();
        apiClient = new AgentApiClient(appContext);
        threadManager = new AgentThreadManager(appContext);
        toolExecutor = new AgentToolExecutor(appContext);

        // Initialize or retrieve assistant
        executor.execute(this::ensureAssistantCreated);
    }

    public synchronized void startSession(String vin, JSONObject vehicleMetadata) {
        if (appContext == null) {
            Log.w(TAG, "Controller not initialized");
            return;
        }

        if (vin == null || vin.isEmpty()) {
            vin = "session_" + System.currentTimeMillis();
        }

        final String finalVin = vin;
        currentVin = finalVin;

        // Get or create thread for this vehicle
        executor.execute(() -> {
            try {
                currentThreadId = threadManager.getOrCreateThread(finalVin, vehicleMetadata);
                Log.i(TAG, "Started session for VIN " + finalVin + ", thread: " + currentThreadId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start session", e);
            }
        });
    }

    public synchronized void endSession() {
        Log.i(TAG, "Ending session for VIN: " + currentVin);
        currentThreadId = null;
        currentVin = null;
    }

    public void sendUserMessage(String message, CoPilotCallback callback) {
        if (appContext == null) {
            callback.onError("CoPilot not initialized");
            return;
        }

        if (currentThreadId == null) {
            callback.onError("No active session. Connect to a vehicle first.");
            return;
        }

        executor.execute(() -> {
            try {
                DebugLog.d(TAG, "User message: " + message);

                // Add message to thread
                apiClient.createMessage(currentThreadId, "user", message);

                // Create and poll run
                AgentApiClient.Run run = apiClient.createRun(currentThreadId, assistantId);
                pollRunUntilComplete(run, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                callback.onError("Failed to send message: " + e.getMessage());
            }
        });
    }

    private void pollRunUntilComplete(AgentApiClient.Run initialRun, CoPilotCallback callback) {
        AgentRunPoller poller = new AgentRunPoller(apiClient);

        poller.pollRun(initialRun.threadId, initialRun.id, new AgentRunPoller.RunCallback() {
            @Override
            public void onCompleted(AgentApiClient.Run run) {
                try {
                    // Retrieve latest assistant message
                    List<AgentApiClient.Message> messages = apiClient.listMessages(run.threadId, 1);
                    if (!messages.isEmpty() && "assistant".equals(messages.get(0).role)) {
                        String response = messages.get(0).content;
                        DebugLog.d(TAG, "Assistant response: " + response.substring(0, Math.min(100, response.length())));
                        callback.onResponse(response);
                    } else {
                        callback.onError("No response from assistant");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error retrieving assistant response", e);
                    callback.onError("Failed to get response: " + e.getMessage());
                } finally {
                    poller.shutdown();
                }
            }

            @Override
            public void onRequiresAction(AgentApiClient.Run run) {
                try {
                    // Execute tools
                    List<AgentToolExecutor.ToolCallResult> toolResults =
                        toolExecutor.executeToolCalls(run.requiredAction);

                    // Convert to API format
                    List<AgentApiClient.ToolOutput> outputs = new ArrayList<>();
                    for (AgentToolExecutor.ToolCallResult result : toolResults) {
                        outputs.add(new AgentApiClient.ToolOutput(result.toolCallId, result.output));
                    }

                    // Submit tool outputs and continue polling
                    AgentApiClient.Run updatedRun = apiClient.submitToolOutputs(
                        run.threadId, run.id, outputs);
                    pollRunUntilComplete(updatedRun, callback);

                } catch (Exception e) {
                    Log.e(TAG, "Error executing tools", e);
                    callback.onError("Tool execution failed: " + e.getMessage());
                    poller.shutdown();
                }
            }

            @Override
            public void onFailed(AgentApiClient.Run run, String error) {
                callback.onError(error);
                poller.shutdown();
            }

            @Override
            public void onStatusUpdate(AgentApiClient.Run run) {
                DebugLog.d(TAG, "Run status: " + run.status);
            }
        });
    }

    private void ensureAssistantCreated() {
        try {
            // Check if assistant ID is saved
            assistantId = threadManager.getAssistantId();

            if (assistantId != null) {
                // Verify it still exists
                try {
                    apiClient.retrieveAssistant(assistantId);
                    Log.i(TAG, "Using existing assistant: " + assistantId);
                    attachLatestScanFileIfAvailable();
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "Saved assistant not found, creating new", e);
                }
            }

            // Create new assistant
            List<AgentApiClient.Tool> tools = toolExecutor.getToolSchemas();
            AgentApiClient.Assistant assistant = apiClient.createAssistant(
                ASSISTANT_NAME,
                ASSISTANT_INSTRUCTIONS,
                tools
            );

            assistantId = assistant.id;
            threadManager.saveAssistantId(assistantId);

            Log.i(TAG, "Created new assistant: " + assistantId);
            attachLatestScanFileIfAvailable();

        } catch (Exception e) {
            Log.e(TAG, "Failed to create assistant", e);
        }
    }

    /**
     * Sync the assistant's file_search resources with the most recent uploaded scan.
     * Safe to call multiple times; new uploads reset the attachment flag.
     */
    public void refreshFileSearchIndex() {
        executor.execute(this::attachLatestScanFileIfAvailable);
    }

    private void attachLatestScanFileIfAvailable() {
        if (appContext == null || assistantId == null) {
            return;
        }

        try {
            ScanResultsManager manager = ScanResultsManager.getInstance(appContext);
            ScanResultsManager.ScanSummary pending = manager.getLatestUploadedScanPendingAttachment();
            if (pending == null || TextUtils.isEmpty(pending.fileId)) {
                return;
            }

            apiClient.attachFileToAssistant(assistantId, pending.fileId);
            manager.markFileAttached(pending.scanId);
            Log.i(TAG, "Attached scan file to assistant: " + pending.fileId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to attach latest scan file to assistant", e);
        }
    }

    /**
     * Delete the conversation thread for a specific VIN.
     * Use for privacy/"forget me" functionality.
     */
    public void deleteConversation(String vin) {
        executor.execute(() -> {
            try {
                threadManager.deleteThread(vin);
                Log.i(TAG, "Deleted conversation for VIN: " + vin);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete conversation", e);
            }
        });
    }

    /**
     * Delete all conversation threads.
     * Use for "Delete All My Data" privacy option.
     */
    public void deleteAllConversations() {
        executor.execute(() -> {
            threadManager.deleteAllThreads();
            Log.i(TAG, "Deleted all conversations");
        });
    }
}
