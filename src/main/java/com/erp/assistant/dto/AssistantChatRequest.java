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
    private Map<String, Object> pageContext;
    private List<AssistantMessageDTO> history;
}
