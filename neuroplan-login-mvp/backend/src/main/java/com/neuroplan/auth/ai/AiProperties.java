package com.neuroplan.auth.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled = true;
    private String provider = "GEMINI";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    private String promptVersion = "neuroplan-0.8.0-gemini-v2";
    private int maxCompletionTokens = 1200;
    private int quizMaxCompletionTokens = 1600;
    private int dailyTokenLimit = 30000;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getMaxCompletionTokens() { return maxCompletionTokens; }
    public void setMaxCompletionTokens(int maxCompletionTokens) { this.maxCompletionTokens = maxCompletionTokens; }
    public int getQuizMaxCompletionTokens() { return quizMaxCompletionTokens; }
    public void setQuizMaxCompletionTokens(int quizMaxCompletionTokens) { this.quizMaxCompletionTokens = quizMaxCompletionTokens; }
    public int getDailyTokenLimit() { return dailyTokenLimit; }
    public void setDailyTokenLimit(int dailyTokenLimit) { this.dailyTokenLimit = dailyTokenLimit; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }

    public String endpoint() {
        return baseUrl.replaceAll("/+$", "") + "/models/" + model + ":generateContent";
    }

    public boolean configured() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && model != null && !model.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}
