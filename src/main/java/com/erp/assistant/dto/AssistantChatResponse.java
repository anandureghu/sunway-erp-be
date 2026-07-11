package com.erp.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantChatResponse {
    private String conversationId;
    private String message;
    private String model;
    private boolean configured;
    private String error;
    private List<AssistantToolCallDTO> toolCalls;
    private List<AssistantLinkDTO> links;
}
