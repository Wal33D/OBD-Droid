package com.obddroid.features.copilot.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.obddroid.utils.DebugLog;

/**
 * Polls the status of an Agent API run until completion.
 * Handles async run lifecycle: queued → in_progress → requires_action → completed/failed.
 *
 * Polling Strategy:
 * - Poll every 1 second while run is in progress
 * - Notify callbacks on status changes
 * - Support cancellation
 */
public class AgentRunPoller {

    private static final String TAG = "AgentRunPoller";
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int MAX_POLL_ATTEMPTS = 120; // 2 minutes max

    private final AgentApiClient apiClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isCancelled = false;

    public AgentRunPoller(AgentApiClient apiClient) {
        this.apiClient = apiClient;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Start polling a run until it completes.
     * Callbacks are invoked on the main thread.
     */
    public void pollRun(String threadId, String runId, RunCallback callback) {
        isCancelled = false;

        executor.execute(() -> {
            int attempts = 0;

            while (!isCancelled && attempts < MAX_POLL_ATTEMPTS) {
                try {
                    AgentApiClient.Run run = apiClient.retrieveRun(threadId, runId);

                    DebugLog.d(TAG, "Run " + runId + " status: " + run.status);

                    if (run.isCompleted()) {
                        notifyCallback(() -> callback.onCompleted(run));
                        return;
                    }

                    if (run.isFailed()) {
                        notifyCallback(() -> callback.onFailed(run, "Run failed with status: " + run.status));
                        return;
                    }

                    if (run.requiresAction()) {
                        notifyCallback(() -> callback.onRequiresAction(run));
                        return;
                    }

                    // Still in progress
                    notifyCallback(() -> callback.onStatusUpdate(run));

                    // Wait before next poll
                    Thread.sleep(POLL_INTERVAL_MS);
                    attempts++;

                } catch (InterruptedException e) {
                    Log.i(TAG, "Polling interrupted");
                    notifyCallback(() -> callback.onFailed(null, "Polling cancelled"));
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error polling run " + runId, e);
                    notifyCallback(() -> callback.onFailed(null, "Polling error: " + e.getMessage()));
                    return;
                }
            }

            if (attempts >= MAX_POLL_ATTEMPTS) {
                Log.w(TAG, "Run " + runId + " polling timed out after " + attempts + " attempts");
                notifyCallback(() -> callback.onFailed(null, "Run timed out"));
            }
        });
    }

    /**
     * Cancel ongoing polling.
     */
    public void cancel() {
        isCancelled = true;
    }

    /**
     * Shutdown the polling executor.
     * Call this when done with the poller.
     */
    public void shutdown() {
        isCancelled = true;
        executor.shutdown();
    }

    private void notifyCallback(Runnable runnable) {
        mainHandler.post(runnable);
    }

    /**
     * Callback interface for run status updates.
     */
    public interface RunCallback {
        /**
         * Called when the run completes successfully.
         */
        void onCompleted(AgentApiClient.Run run);

        /**
         * Called when the run requires action (tool calling).
         */
        void onRequiresAction(AgentApiClient.Run run);

        /**
         * Called when the run fails or times out.
         */
        void onFailed(AgentApiClient.Run run, String error);

        /**
         * Called periodically with status updates (optional, can be no-op).
         */
        default void onStatusUpdate(AgentApiClient.Run run) {
            // Default no-op implementation
        }
    }
}
