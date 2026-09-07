package com.neuroplan.auth.ai;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class GeminiAiClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public AiProviderResponse generateJson(String systemPrompt, String userPrompt,
                                           Map<String, Object> responseSchema) {
        return generateJson(systemPrompt, userPrompt, properties.getMaxCompletionTokens(), responseSchema);
    }

    public AiProviderResponse generateJson(String systemPrompt, String userPrompt,
                                           int maxCompletionTokens, Map<String, Object> responseSchema) {
        if (!properties.configured()) {
            throw new AiProviderException("NOT_CONFIGURED", null, "Gemini API 설정이 준비되지 않았습니다.");
        }

        Map<String, Object> textFormat = new LinkedHashMap<>();
        textFormat.put("mimeType", "APPLICATION_JSON");
        textFormat.put("schema", responseSchema);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", Math.max(maxCompletionTokens, 1));
        generationConfig.put("responseFormat", Map.of("text", textFormat));

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )),
                "generationConfig", generationConfig
        );

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.endpoint()))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("x-goog-api-key", properties.getApiKey())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String responseBody = response.body();
            if (status < 200 || status >= 300) {
                throw new AiProviderException(httpErrorCode(status), status, providerErrorMessage(responseBody));
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidate = root.path("candidates").path(0);
            if (candidate.isMissingNode()) {
                String blockReason = root.path("promptFeedback").path("blockReason").asText("");
                if (!blockReason.isBlank()) {
                    throw new AiProviderException("SAFETY_BLOCK", status,
                            "Gemini 안전 정책으로 응답이 차단되었습니다: " + sanitize(blockReason));
                }
                throw new AiProviderException("EMPTY_RESPONSE", status, "Gemini 응답 후보가 비어 있습니다.");
            }

            StringBuilder content = new StringBuilder();
            candidate.path("content").path("parts").forEach(part -> {
                if (part.path("thought").asBoolean(false)) return;
                String text = part.path("text").asText("");
                if (!text.isBlank()) content.append(text);
            });
            if (content.isEmpty()) {
                throw new AiProviderException("EMPTY_RESPONSE", status, "Gemini 응답 내용이 비어 있습니다.");
            }

            JsonNode usage = root.path("usageMetadata");
            int inputTokens = usage.path("promptTokenCount").asInt(0);
            int outputTokens = usage.path("candidatesTokenCount").asInt(0);
            int totalTokens = usage.path("totalTokenCount").asInt(inputTokens + outputTokens);
            return new AiProviderResponse(
                    content.toString(),
                    inputTokens,
                    outputTokens,
                    BigDecimal.valueOf(Math.max(totalTokens, inputTokens + outputTokens)),
                    response.headers().firstValue("x-goog-request-id")
                            .or(() -> response.headers().firstValue("x-request-id"))
                            .orElse(null),
                    status,
                    candidate.path("finishReason").asText("")
            );
        } catch (AiProviderException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new AiProviderException("TIMEOUT", null, "Gemini API 연결 시간이 초과되었습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("INTERRUPTED", null, "Gemini API 요청이 중단되었습니다.");
        } catch (IOException exception) {
            throw new AiProviderException("NETWORK_ERROR", null, "Gemini API 네트워크 요청에 실패했습니다.");
        } catch (Exception exception) {
            throw new AiProviderException("INVALID_RESPONSE", null, "Gemini API 응답을 해석하지 못했습니다.");
        }
    }

    private String httpErrorCode(int status) {
        if (status == 400) return "INVALID_REQUEST";
        if (status == 401 || status == 403) return "AUTH_ERROR";
        if (status == 404) return "MODEL_NOT_FOUND";
        if (status == 429) return "RATE_LIMIT";
        if (status >= 500) return "PROVIDER_UNAVAILABLE";
        return "HTTP_ERROR";
    }

    private String providerErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText("");
            if (message.isBlank()) message = root.path("message").asText("");
            return sanitize(message);
        } catch (Exception ignored) {
            return sanitize(responseBody);
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "Gemini API 요청에 실패했습니다.";
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() > 480 ? normalized.substring(0, 480) : normalized;
    }

    public record AiProviderResponse(
            String content,
            int inputTokens,
            int outputTokens,
            BigDecimal usageUnits,
            String providerRequestId,
            int httpStatus,
            String finishReason
    ) {
        public int totalTokens() {
            return usageUnits == null
                    ? Math.max(inputTokens, 0) + Math.max(outputTokens, 0)
                    : Math.max(usageUnits.intValue(), 0);
        }

        public boolean outputLimitReached() {
            return "MAX_TOKENS".equalsIgnoreCase(finishReason)
                    || "LENGTH".equalsIgnoreCase(finishReason);
        }

        public boolean completedNormally() {
            return finishReason == null || finishReason.isBlank() || "STOP".equalsIgnoreCase(finishReason);
        }
    }

    public static class AiProviderException extends RuntimeException {
        private final String code;
        private final Integer httpStatus;

        public AiProviderException(String code, Integer httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() { return code; }
        public Integer httpStatus() { return httpStatus; }
    }
}
