package com.erp.assistant;

import com.erp.assistant.OpenAiChatClient.OpenAiChatResult;
import com.erp.assistant.OpenAiChatClient.OpenAiToolCall;
import com.erp.assistant.dto.AssistantChatRequest;
import com.erp.assistant.dto.AssistantChatResponse;
import com.erp.assistant.dto.AssistantLinkDTO;
import com.erp.assistant.dto.AssistantMessageDTO;
import com.erp.assistant.dto.AssistantToolCallDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistantChatService {
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_TOOL_ROUNDS = 3;
    private static final int MAX_RESPONSE_LINKS = 25;
    private static final Pattern MARKDOWN_LINK_URL = Pattern.compile(
            "\\]\\(\\s*(https?://[^\\s)]+)\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private final AssistantOpenAiProperties openAiProperties;
    private final OpenAiChatClient openAiChatClient;
    private final AssistantToolService toolService;
    private final ObjectMapper objectMapper;
    private final String configuredPublicBaseUrl;

    public AssistantChatService(
            AssistantOpenAiProperties openAiProperties,
            OpenAiChatClient openAiChatClient,
            AssistantToolService toolService,
            ObjectMapper objectMapper,
            @Value("${app.public-base-url:http://localhost:5173}") String configuredPublicBaseUrl
    ) {
        this.openAiProperties = openAiProperties;
        this.openAiChatClient = openAiChatClient;
        this.toolService = toolService;
        this.objectMapper = objectMapper;
        this.configuredPublicBaseUrl = configuredPublicBaseUrl;
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String conversationId = request.getConversationId() == null || request.getConversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getConversationId();
        String appBaseUrl = resolveAppBaseUrl(request);

        if (!openAiProperties.isConfigured()) {
            return AssistantChatResponse.builder()
                    .conversationId(conversationId)
                    .configured(false)
                    .model(openAiProperties.getModel())
                    .message("Assistant is not configured yet. Set OPENAI_API_KEY on the backend, then restart the API.")
                    .error("OPENAI_API_KEY is missing")
                    .toolCalls(List.of())
                    .links(List.of())
                    .build();
        }

        List<Map<String, Object>> messages = buildMessages(request, appBaseUrl);
        List<Map<String, Object>> toolDefinitions = toolService.openAiToolDefinitions();
        List<AssistantToolCallDTO> toolTrace = new ArrayList<>();
        List<AssistantLinkDTO> links = new ArrayList<>();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            OpenAiChatResult result = openAiChatClient.complete(messages, toolDefinitions);
            if (result.toolCalls().isEmpty()) {
                List<AssistantLinkDTO> responseLinks = dedupeLinks(links);
                return AssistantChatResponse.builder()
                        .conversationId(conversationId)
                        .configured(true)
                        .model(openAiProperties.getModel())
                        .message(enrichMessageWithLinks(
                                nonBlank(result.content(), "I could not generate a response."),
                                responseLinks,
                                appBaseUrl
                        ))
                        .toolCalls(toolTrace)
                        .links(responseLinks)
                        .build();
            }

            messages.add(result.rawAssistantMessage());

            for (OpenAiToolCall toolCall : result.toolCalls()) {
                ToolExecution execution = executeTool(toolCall, appBaseUrl);
                toolTrace.add(AssistantToolCallDTO.builder()
                        .name(toolCall.name())
                        .argumentsJson(toolCall.argumentsJson())
                        .status(execution.status())
                        .build());
                links.addAll(execution.links());

                messages.add(mapOf(
                        "role", "tool",
                        "tool_call_id", toolCall.id(),
                        "content", execution.contentJson()
                ));
            }
        }

        List<AssistantLinkDTO> responseLinks = dedupeLinks(links);
        return AssistantChatResponse.builder()
                .conversationId(conversationId)
                .configured(true)
                .model(openAiProperties.getModel())
                .message(enrichMessageWithLinks(
                        "I gathered ERP data, but the assistant needed too many tool steps. Please try a more specific question.",
                        responseLinks,
                        appBaseUrl
                ))
                .toolCalls(toolTrace)
                .links(responseLinks)
                .build();
    }

    private ToolExecution executeTool(OpenAiToolCall toolCall, String appBaseUrl) {
        try {
            Object result = toolService.execute(toolCall.name(), toolCall.argumentsJson());
            List<AssistantLinkDTO> links = buildLinks(toolCall.name(), result, appBaseUrl);
            return new ToolExecution(
                    "success",
                    toJson(mapOf("ok", true, "data", result, "links", links)),
                    links
            );
        } catch (Exception ex) {
            return new ToolExecution("error", toJson(mapOf(
                    "ok", false,
                    "error", ex.getMessage() == null ? "Tool execution failed" : ex.getMessage()
            )), List.of());
        }
    }

    private List<AssistantLinkDTO> buildLinks(String toolName, Object result, String appBaseUrl) {
        JsonNode data = objectMapper.valueToTree(result);
        List<AssistantLinkDTO> links = new ArrayList<>();

        switch (toolName) {
            case "get_invoice_status" -> addInvoiceLink(data, links, appBaseUrl);
            case "get_recent_invoices" -> data.path("invoices")
                    .forEach(invoice -> addInvoiceLink(invoice, links, appBaseUrl));
            case "search_inventory_items" -> data.path("items")
                    .forEach(item -> addInventoryItemLink(item, links, appBaseUrl));
            case "get_inventory_item_stock" -> addInventoryItemLink(data.path("item"), links, appBaseUrl);
            case "get_low_stock_items" -> data.path("items")
                    .forEach(item -> addInventoryItemLink(item, links, appBaseUrl));
            case "get_inventory_summary" -> {
                links.add(AssistantLinkDTO.builder()
                        .label("Open inventory reports")
                        .url(toAppUrl("/inventory/reports", appBaseUrl))
                        .type("inventory-report")
                        .build());
                data.path("lowStockItems").forEach(item -> addInventoryItemLink(item, links, appBaseUrl));
                data.path("topStockLinesByValue").forEach(item -> addInventoryItemLink(item, links, appBaseUrl));
            }
            case "get_my_leave_balance" -> addEmployeeLeaveLink(data, links, appBaseUrl);
            case "get_pending_leave_approvals" -> data.path("approvals")
                    .forEach(approval -> addEmployeeLeaveLink(approval, links, appBaseUrl));
            default -> {
                // No detail page mapping for this tool yet.
            }
        }

        return dedupeLinks(links);
    }

    private String enrichMessageWithLinks(String message, List<AssistantLinkDTO> links, String appBaseUrl) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String enriched = rewriteInventedAbsoluteUrls(message, appBaseUrl);
        if (links == null || links.isEmpty()) {
            return enriched;
        }

        for (AssistantLinkDTO link : links) {
            if (link == null || link.getUrl() == null || link.getUrl().isBlank()) {
                continue;
            }
            if (enriched.contains("](" + link.getUrl() + ")")) {
                continue;
            }

            String target = linkTargetText(link);
            if (target.isBlank()) {
                continue;
            }

            Pattern pattern = Pattern.compile("(?i)(^|[^\\w/-])(" + Pattern.quote(target) + ")(?=$|[^\\w/-])");
            Matcher matcher = pattern.matcher(enriched);
            if (!matcher.find()) {
                continue;
            }

            String replacement = matcher.group(1)
                    + "["
                    + escapeMarkdownLabel(matcher.group(2))
                    + "]("
                    + link.getUrl()
                    + ")";
            enriched = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        }

        return enriched;
    }

    /**
     * Models sometimes invent hosts like https://erp.example.com. Rewrite those
     * markdown link targets onto the configured app base (or relative path).
     */
    private String rewriteInventedAbsoluteUrls(String message, String appBaseUrl) {
        Matcher matcher = MARKDOWN_LINK_URL.matcher(message);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String absolute = matcher.group(1);
            String path = extractAppPath(absolute);
            if (path == null) {
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replacement = "](" + toAppUrl(path, appBaseUrl) + ")";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private String extractAppPath(String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(absoluteUrl.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return null;
            }
            if (!(path.startsWith("/sales/")
                    || path.startsWith("/inventory/")
                    || path.startsWith("/hr/")
                    || path.startsWith("/finance/")
                    || path.startsWith("/purchase/")
                    || path.startsWith("/employees/"))) {
                return null;
            }
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (Exception ex) {
            return null;
        }
    }

    private String linkTargetText(AssistantLinkDTO link) {
        String label = link.getLabel() == null ? "" : link.getLabel().trim();
        return label.replaceFirst("(?i)^open\\s+", "").trim();
    }

    private String escapeMarkdownLabel(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private void addInvoiceLink(JsonNode invoice, List<AssistantLinkDTO> links, String appBaseUrl) {
        long id = invoice.path("id").asLong(0);
        if (id <= 0) {
            return;
        }

        String invoiceCode = invoice.path("invoiceId").asText("Invoice " + id);
        String type = invoice.path("type").asText("");
        String path = "PURCHASE".equalsIgnoreCase(type)
                ? "/inventory/purchase/invoices/" + id
                : "/sales/invoices/" + id;

        links.add(AssistantLinkDTO.builder()
                .label("Open " + invoiceCode)
                .url(toAppUrl(path, appBaseUrl))
                .type("invoice")
                .build());
    }

    private void addInventoryItemLink(JsonNode item, List<AssistantLinkDTO> links, String appBaseUrl) {
        long id = firstPositiveLong(item, "id", "itemId", "itemID", "item_id", "inventoryItemId");
        String pathOrUrl = firstNonBlank(
                item.path("detailUrl").asText(null),
                item.path("detail_url").asText(null),
                item.path("url").asText(null),
                id > 0 ? "/inventory/stocks/" + id : null
        );

        if (pathOrUrl.isBlank()) {
            return;
        }

        String url = pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")
                ? pathOrUrl
                : toAppUrl(pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl, appBaseUrl);

        String label = firstNonBlank(
                item.path("sku").asText(null),
                item.path("itemSku").asText(null),
                item.path("item_sku").asText(null),
                item.path("name").asText(null),
                item.path("itemName").asText(null),
                item.path("item_name").asText(null),
                "item " + id
        );

        links.add(AssistantLinkDTO.builder()
                .label("Open " + label)
                .url(url)
                .type("inventory-item")
                .build());
    }

    private void addEmployeeLeaveLink(JsonNode node, List<AssistantLinkDTO> links, String appBaseUrl) {
        long employeeId = node.path("employeeId").asLong(0);
        if (employeeId <= 0) {
            return;
        }

        links.add(AssistantLinkDTO.builder()
                .label("Open leave details")
                .url(toAppUrl("/hr/employees/" + employeeId + "/leaves", appBaseUrl))
                .type("leave")
                .build());
    }

    private List<AssistantLinkDTO> dedupeLinks(List<AssistantLinkDTO> links) {
        List<AssistantLinkDTO> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AssistantLinkDTO link : links) {
            if (link == null || link.getUrl() == null || link.getUrl().isBlank()) {
                continue;
            }
            String key = link.getUrl() + "|" + link.getLabel();
            if (seen.add(key)) {
                unique.add(link);
            }
            if (unique.size() >= MAX_RESPONSE_LINKS) {
                break;
            }
        }
        return unique;
    }

    private String resolveAppBaseUrl(AssistantChatRequest request) {
        String fromRequest = request != null ? normalizeBaseUrl(request.getAppBaseUrl()) : null;
        if (fromRequest != null) {
            return fromRequest;
        }
        return normalizeBaseUrl(configuredPublicBaseUrl);
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return null;
        }
        return trimmed;
    }

    private String toAppUrl(String path, String appBaseUrl) {
        String normalizedPath = path == null || path.isBlank()
                ? "/"
                : (path.startsWith("/") ? path : "/" + path);
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return normalizedPath;
        }
        return appBaseUrl + normalizedPath;
    }

    private long firstPositiveLong(JsonNode node, String... fields) {
        for (String field : fields) {
            long value = node.path(field).asLong(0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private List<Map<String, Object>> buildMessages(AssistantChatRequest request, String appBaseUrl) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(mapOf("role", "system", "content", systemPrompt(request, appBaseUrl)));

        List<AssistantMessageDTO> history = request.getHistory() == null ? List.of() : request.getHistory();
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (AssistantMessageDTO item : history.subList(start, history.size())) {
            String role = normalizeRole(item.getRole());
            String content = trimTo(item.getContent(), 2000);
            if (role != null && content != null && !content.isBlank()) {
                messages.add(mapOf("role", role, "content", content));
            }
        }

        messages.add(mapOf("role", "user", "content", trimTo(request.getMessage(), 4000)));
        return messages;
    }

    private String systemPrompt(AssistantChatRequest request, String appBaseUrl) {
        String linkRule = appBaseUrl == null
                ? "When tool results include links, use the exact relative paths (starting with /). Never invent a hostname."
                : "When tool results include links, use those URLs exactly. App base URL is "
                        + appBaseUrl
                        + ". Never invent other hostnames such as erp.example.com.";

        return """
                You are the embedded Sunway ERP assistant.
                Answer employee questions using ERP tool results when company data is requested.
                Never invent HR, inventory, finance, salary, invoice, approval, or stock data.
                If no tool supports the request, say that the assistant is not connected to that data yet.
                This version is read-only. Do not claim you created, updated, approved, deleted, exported, or sent anything.
                If a tool returns a permission error, explain that the user does not have access to that ERP data.
                RAG/document search is not enabled yet, so policy/manual questions should be answered as not connected yet.
                Format successful ERP results as compact Markdown using short paragraphs, numbered lists, bullets, and bold labels where helpful.
                %s
                Keep answers concise, operational, and include the most important IDs/statuses/amounts when available.
                Always reply in %s. Keep ERP codes, IDs, SKUs, statuses, currency amounts, and URLs in their original form.

                Current ERP page context:
                Module: %s
                Screen: %s
                Page context JSON: %s
                """.formatted(
                linkRule,
                replyLanguageInstruction(request.getLanguage()),
                safe(request.getCurrentModule()),
                safe(request.getCurrentScreen()),
                toJson(request.getPageContext() == null ? Map.of() : request.getPageContext())
        );
    }

    private String replyLanguageInstruction(String languageCode) {
        String code = languageCode == null ? "" : languageCode.trim().toLowerCase();
        return switch (code) {
            case "ar", "arabic" -> "Arabic (العربية)";
            default -> "English";
        };
    }

    private String normalizeRole(String role) {
        if ("user".equals(role) || "assistant".equals(role)) {
            return role;
        }
        return null;
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"ok\":false,\"error\":\"Failed to serialize assistant data\"}";
        }
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private record ToolExecution(String status, String contentJson, List<AssistantLinkDTO> links) {}
}
