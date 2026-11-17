package com.obddroid.features.copilot.data;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.obddroid.utils.OpenAiService;
import com.obddroid.utils.DebugLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Manages text-to-speech playback for CoPilot responses.
 * Uses OpenAI TTS API to generate natural-sounding voice responses.
 */
public class CoPilotTtsManager {

    private static final String TAG = "CoPilotTts";
    private static CoPilotTtsManager instance;

    private final Context context;
    private final OpenAiService openAiService;
    private MediaPlayer mediaPlayer;
    private boolean isEnabled = true;

    private CoPilotTtsManager(Context context) {
        this.context = context.getApplicationContext();
        this.openAiService = new OpenAiService(context);
    }

    public static synchronized CoPilotTtsManager getInstance(Context context) {
        if (instance == null) {
            instance = new CoPilotTtsManager(context);
        }
        return instance;
    }

    /**
     * Converts text to speech and plays it.
     * Returns a CompletableFuture that completes when playback finishes.
     */
    public CompletableFuture<Void> speak(String text, TtsCallback callback) {
        if (!isEnabled) {
            DebugLog.d(TAG, "TTS is disabled");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                if (callback != null) {
                    callback.onTtsStart();
                }

                // Get audio from OpenAI TTS
                DebugLog.d(TAG, "Generating speech for: " + text.substring(0, Math.min(50, text.length())) + "...");
                byte[] audioData = openAiService.textToSpeech(text);

                // Save to temporary file
                File tempFile = File.createTempFile("copilot_tts_", ".mp3", context.getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(audioData);
                }

                // Play audio
                playAudioFile(tempFile.getAbsolutePath(), callback);

            } catch (Exception e) {
                Log.e(TAG, "Failed to generate/play speech", e);
                if (callback != null) {
                    callback.onTtsError(e.getMessage());
                }
            }
        });
    }

    private void playAudioFile(String filePath, TtsCallback callback) {
        try {
            // Stop any existing playback
            stopPlayback();

            // Create and configure MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .build()
            );

            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();

            mediaPlayer.setOnCompletionListener(mp -> {
                DebugLog.d(TAG, "Playback completed");
                if (callback != null) {
                    callback.onTtsComplete();
                }
                stopPlayback();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                if (callback != null) {
                    callback.onTtsError("Playback error: " + what);
                }
                stopPlayback();
                return true;
            });

            mediaPlayer.start();
            DebugLog.d(TAG, "Started playback, duration: " + mediaPlayer.getDuration() + "ms");

        } catch (IOException e) {
            Log.e(TAG, "Failed to play audio file", e);
            if (callback != null) {
                callback.onTtsError(e.getMessage());
            }
        }
    }

    /**
     * Stops current playback.
     */
    public void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping playback", e);
            } finally {
                mediaPlayer = null;
            }
        }
    }

    /**
     * Pauses current playback.
     */
    public void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /**
     * Resumes paused playback.
     */
    public void resumePlayback() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /**
     * Checks if audio is currently playing.
     */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /**
     * Enables or disables TTS.
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            stopPlayback();
        }
    }

    /**
     * Checks if TTS is enabled.
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        stopPlayback();

        // Clean up old TTS cache files
        try {
            File cacheDir = context.getCacheDir();
            File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("copilot_tts_"));
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete cache file: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up TTS cache", e);
        }
    }

    /**
     * Callback interface for TTS events.
     */
    public interface TtsCallback {
        void onTtsStart();
        void onTtsComplete();
        void onTtsError(String error);
    }
}
