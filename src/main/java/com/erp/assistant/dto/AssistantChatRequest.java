package com.erp.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AssistantChatRequest {
    private String conversationId;

    @NotBlank(message = "Message is required")
    private String message;

    private String currentModule;
    private String currentScreen;
    /** Preferred reply language code, e.g. en, ar. */
    private String language;
    /**
     * Public frontend origin for in-app links (e.g. https://demo.sunwayerp.com).
     * When set, assistant markdown links use this host instead of inventing one.
     */
    private String appBaseUrl;
    private Map<String, Object> pageContext;
    private List<AssistantMessageDTO> history;
}
