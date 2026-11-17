package com.obddroid.utils;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

/**
 * Service for OpenAI Text-to-Speech API.
 * Provides voice synthesis for CoPilot AI Assistant.
 *
 * Note: All conversational AI (fault code analysis, diagnostics, etc.)
 * now uses the Agent API via CoPilotController. This service is TTS-only.
 */
public class OpenAiService
{
    private static final Logger log = Logger.getLogger(OpenAiService.class.getName());
    private static final String OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech";
    private static final String DEFAULT_TTS_MODEL = "tts-1";
    private static final String DEFAULT_VOICE = "alloy";  // Options: alloy, echo, fable, onyx, nova, shimmer
    private static final int TIMEOUT_MS = 30000;

    private final SecurePreferences securePreferences;

    public OpenAiService(Context context)
    {
        this.securePreferences = new SecurePreferences(context);
    }

    /**
     * Checks if the OpenAI API key is configured.
     */
    public boolean isApiKeyConfigured()
    {
        return securePreferences.hasOpenAiApiKey();
    }

    /**
     * Converts text to speech using OpenAI's TTS API.
     * Returns audio data as byte array (MP3 format).
     *
     * @param text The text to convert to speech
     * @param voice The voice to use (alloy, echo, fable, onyx, nova, shimmer)
     * @return Audio data as byte array in MP3 format
     * @throws Exception if the API call fails
     */
    public byte[] textToSpeech(String text, String voice) throws Exception {
        String apiKey = securePreferences.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("OpenAI API key not configured. Please set it in settings.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(OPENAI_TTS_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            // Build request JSON
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", DEFAULT_TTS_MODEL);
            requestBody.put("input", text);
            requestBody.put("voice", voice != null ? voice : DEFAULT_VOICE);
            requestBody.put("response_format", "mp3");
            requestBody.put("speed", 1.0);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorMessage = readErrorStream(connection);
                log.severe("OpenAI TTS API error: " + responseCode + " - " + errorMessage);
                throw new Exception("TTS API error: " + errorMessage);
            }

            // Read audio data
            java.io.InputStream inputStream = connection.getInputStream();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            buffer.flush();

            return buffer.toByteArray();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to call OpenAI TTS API", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Converts text to speech using the default voice.
     */
    public byte[] textToSpeech(String text) throws Exception {
        return textToSpeech(text, DEFAULT_VOICE);
    }

    /**
     * Reads the error stream from a failed HTTP request.
     */
    private String readErrorStream(HttpURLConnection connection)
    {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8)))
        {
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
            {
                error.append(line);
            }
            return error.toString();
        }
        catch (IOException e)
        {
            return "Unknown error";
        }
    }
}
