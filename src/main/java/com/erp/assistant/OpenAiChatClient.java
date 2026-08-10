package com.erp.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiChatClient {
    private final AssistantOpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public OpenAiChatResult complete(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        return complete(messages, tools, false);
    }

    /**
     * @param jsonObject when true, requests {@code response_format: json_object} so the model
     *                   returns a single JSON object (useful for structured mapping tasks).
     */
    public OpenAiChatResult complete(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            boolean jsonObject
    ) {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Assistant is not configured. Set OPENAI_API_KEY on the backend.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        if (jsonObject) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(properties.chatCompletionsUrl()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build assistant request", ex);
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "OpenAI request failed: " + compactError(response.body()));
            }
            return parseResponse(response.body());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Assistant provider is unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Assistant provider request was interrupted", ex);
        }
    }

    private OpenAiChatResult parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Assistant provider returned no message");
        }

        String content = message.path("content").isTextual() ? message.path("content").asText() : "";
        Map<String, Object> rawAssistantMessage = objectMapper.convertValue(
                message,
                new TypeReference<Map<String, Object>>() {}
        );

        List<OpenAiToolCall> toolCalls = new ArrayList<>();
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode call : calls) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String arguments = call.path("function").path("arguments").asText("{}");
                if (!id.isBlank() && !name.isBlank()) {
                    toolCalls.add(new OpenAiToolCall(id, name, arguments));
                }
            }
        }

        return new OpenAiChatResult(content, rawAssistantMessage, toolCalls);
    }

    private String compactError(String body) {
        if (body == null || body.isBlank()) {
            return "empty error body";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText();
            if (!message.isBlank()) {
                return message;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to trimmed raw response.
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    public record OpenAiChatResult(
            String content,
            Map<String, Object> rawAssistantMessage,
            List<OpenAiToolCall> toolCalls
    ) {}

    public record OpenAiToolCall(String id, String name, String argumentsJson) {}
}
