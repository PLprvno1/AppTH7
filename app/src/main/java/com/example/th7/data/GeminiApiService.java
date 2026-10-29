package com.example.th7.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small synchronous REST client. Call from a worker thread, never from the UI thread. */
public final class GeminiApiService {
    public static final String MODEL = "gemini-3.8-flash";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    public enum Error {
        MISSING_KEY, TIMEOUT, NETWORK, BAD_REQUEST, UNAUTHORIZED, FORBIDDEN,
        MODEL_UNAVAILABLE, RATE_LIMIT, SERVER, SAFETY, INCOMPLETE_RESPONSE, INVALID_RESPONSE, OTHER_HTTP
    }

    public static final class ApiException extends Exception {
        public final Error error;
        public final int httpStatus;
        ApiException(Error error) { this(error, 0, null); }
        ApiException(Error error, Throwable cause) { this(error, 0, cause); }
        ApiException(Error error, int httpStatus) { this(error, httpStatus, null); }
        private ApiException(Error error, int httpStatus, Throwable cause) {
            super(error.name(), cause); this.error = error; this.httpStatus = httpStatus;
        }
    }

    public String generate(String apiKey, String prompt) throws ApiException {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new ApiException(Error.MISSING_KEY);
        HttpURLConnection connection = null;
        try {
            JSONObject request = new JSONObject();
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
            JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts));
            JSONObject thinkingConfig = new JSONObject().put("thinkingLevel", "LOW");
            JSONObject generationConfig = new JSONObject()
                    // The limit includes internal thinking tokens; 2048 can be consumed
                    // before Gemini emits any user-visible text.
                    .put("maxOutputTokens", 8192)
                    .put("thinkingConfig", thinkingConfig);
            request.put("contents", contents).put("generationConfig", generationConfig);

            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("x-goog-api-key", apiKey.trim());

            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw mapHttpError(status);
            String responseBody;
            try (InputStream input = connection.getInputStream()) {
                responseBody = readLimited(input);
            }
            return extractText(responseBody);
        } catch (SocketTimeoutException timeout) {
            throw new ApiException(Error.TIMEOUT, timeout);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(Error.NETWORK, e);
        } catch (JSONException e) {
            throw new ApiException(Error.INVALID_RESPONSE, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ApiException mapHttpError(int status) {
        if (status == 400) return new ApiException(Error.BAD_REQUEST, status);
        if (status == 401) return new ApiException(Error.UNAUTHORIZED, status);
        if (status == 403) return new ApiException(Error.FORBIDDEN, status);
        if (status == 404) return new ApiException(Error.MODEL_UNAVAILABLE, status);
        if (status == 429) return new ApiException(Error.RATE_LIMIT, status);
        if (status >= 500) return new ApiException(Error.SERVER, status);
        return new ApiException(Error.OTHER_HTTP, status);
    }

    private String readLimited(InputStream input) throws IOException, ApiException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new ApiException(Error.INVALID_RESPONSE);
            result.write(buffer, 0, read);
        }
        return new String(result.toByteArray(), StandardCharsets.UTF_8);
    }

    private String extractText(String responseBody) throws ApiException {
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                JSONObject feedback = response.optJSONObject("promptFeedback");
                if (feedback != null && "SAFETY".equals(feedback.optString("blockReason"))) {
                    throw new ApiException(Error.SAFETY);
                }
                throw new ApiException(Error.INVALID_RESPONSE);
            }
            StringBuilder text = new StringBuilder();
            boolean blocked = false;
            boolean outputLimitReached = false;
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject candidate = candidates.optJSONObject(i);
                if (candidate == null) continue;
                if ("SAFETY".equals(candidate.optString("finishReason"))) blocked = true;
                if ("MAX_TOKENS".equals(candidate.optString("finishReason"))) outputLimitReached = true;
                JSONObject content = candidate.optJSONObject("content");
                JSONArray outputParts = content == null ? null : content.optJSONArray("parts");
                if (outputParts == null) continue;
                for (int j = 0; j < outputParts.length(); j++) {
                    JSONObject part = outputParts.optJSONObject(j);
                    if (part == null || part.optBoolean("thought", false)) continue;
                    String partText = part.optString("text", "").trim();
                    if (!partText.isEmpty()) {
                        if (text.length() > 0) text.append('\n');
                        text.append(partText);
                    }
                }
            }
            if (text.length() == 0) {
                throw new ApiException(blocked ? Error.SAFETY
                        : outputLimitReached ? Error.INCOMPLETE_RESPONSE : Error.INVALID_RESPONSE);
            }
            return text.toString();
        } catch (JSONException e) {
            throw new ApiException(Error.INVALID_RESPONSE, e);
        }
    }
}
