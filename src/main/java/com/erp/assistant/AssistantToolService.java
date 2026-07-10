package com.erp.assistant;

import com.erp.domain.Employee;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.dto.inventory.InventoryLowStockItemDTO;
import com.erp.dto.inventory.InventoryReportSummaryDTO;
import com.erp.dto.inventory.InventoryReportTotalsDTO;
import com.erp.dto.inventory.InventoryTopStockLineDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemWarehouseStockRowDTO;
import com.erp.security.context.AuthContext;
import com.erp.service.LeaveService;
import com.erp.service.finance.InvoiceService;
import com.erp.service.inventory.InventoryReportService;
import com.erp.service.inventory.ItemService;
import com.erp.service.inventory.ItemWarehouseStockService;
import com.erp.service.security.PermissionCheckService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssistantToolService {
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 25;

    private final ObjectMapper objectMapper;
    private final AuthContext authContext;
    private final PermissionCheckService permissionCheckService;
    private final LeaveService leaveService;
    private final ItemService itemService;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final InventoryReportService inventoryReportService;
    private final InvoiceService invoiceService;

    public List<Map<String, Object>> openAiToolDefinitions() {
        return List.of(
                functionTool(
                        "get_my_leave_balance",
                        "Get the authenticated employee's available leave balances by leave type.",
                        objectSchema(Map.of(), List.of())
                ),
                functionTool(
                        "get_pending_leave_approvals",
                        "Get leave requests waiting for the authenticated approver.",
                        objectSchema(Map.of(), List.of())
                ),
                functionTool(
                        "search_inventory_items",
                        "Search inventory items by SKU, name, category, brand, or barcode.",
                        objectSchema(Map.of(
                                "query", stringProperty("Search text such as item name, SKU, category, brand, or barcode."),
                                "limit", integerProperty("Maximum number of items to return.")
                        ), List.of("query"))
                ),
                functionTool(
                        "get_inventory_item_stock",
                        "Get stock by warehouse for one inventory item. Use itemId when available, otherwise SKU or query.",
                        objectSchema(Map.of(
                                "itemId", integerProperty("Inventory item id."),
                                "sku", stringProperty("Exact item SKU when known."),
                                "query", stringProperty("Item name or partial SKU when exact identifiers are unknown.")
                        ), List.of())
                ),
                functionTool(
                        "get_inventory_summary",
                        "Get company inventory totals, stock value, top stock lines, and low-stock count.",
                        objectSchema(Map.of(), List.of())
                ),
                functionTool(
                        "get_low_stock_items",
                        "Get inventory items whose available quantity is at or below reorder level.",
                        objectSchema(Map.of(
                                "limit", integerProperty("Maximum number of low stock items to return.")
                        ), List.of())
                ),
                functionTool(
                        "get_invoice_status",
                        "Get status and outstanding amount for an invoice by database id or invoice code.",
                        objectSchema(Map.of(
                                "invoiceId", integerProperty("Internal invoice database id."),
                                "invoiceCode", stringProperty("Business invoice code such as INV-1001.")
                        ), List.of())
                ),
                functionTool(
                        "get_recent_invoices",
                        "List recent invoices, optionally filtered by status such as UNPAID, PAID, PARTIALLY_PAID, or CANCELLED.",
                        objectSchema(Map.of(
                                "status", stringProperty("Optional invoice status filter."),
                                "limit", integerProperty("Maximum number of invoices to return.")
                        ), List.of())
                )
        );
    }

    public Object execute(String toolName, String argumentsJson) {
        JsonNode args = parseArguments(argumentsJson);
        return switch (toolName) {
            case "get_my_leave_balance" -> getMyLeaveBalance();
            case "get_pending_leave_approvals" -> getPendingLeaveApprovals();
            case "search_inventory_items" -> searchInventoryItems(args);
            case "get_inventory_item_stock" -> getInventoryItemStock(args);
            case "get_inventory_summary" -> getInventorySummary();
            case "get_low_stock_items" -> getLowStockItems(args);
            case "get_invoice_status" -> getInvoiceStatus(args);
            case "get_recent_invoices" -> getRecentInvoices(args);
            default -> throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
        };
    }

    private Map<String, Object> getMyLeaveBalance() {
        Employee employee = authContext.getCurrentEmployee();
        if (employee == null || employee.getId() == null) {
            throw new AccessDeniedException("Current employee record was not found");
        }

        return mapOf(
                "employeeId", employee.getId(),
                "employeeName", employeeName(employee),
                "balances", leaveService.listBalances(employee.getId())
        );
    }

    private Map<String, Object> getPendingLeaveApprovals() {
        return mapOf("approvals", leaveService.getPendingApprovalsForCurrentApprover());
    }

    private Map<String, Object> searchInventoryItems(JsonNode args) {
        requireAny(AppModule.INVENTORY_ITEM, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        String query = text(args, "query");
        int limit = limit(args, DEFAULT_LIMIT);
        String normalized = normalize(query);

        List<Map<String, Object>> items = itemService.listForCompany().stream()
                .filter(item -> matchesItem(item, normalized))
                .limit(limit)
                .map(this::itemSummary)
                .toList();

        return mapOf("query", query, "items", items);
    }

    private Map<String, Object> getInventoryItemStock(JsonNode args) {
        requireAny(AppModule.INVENTORY_ITEM, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        requireAny(AppModule.INVENTORY_STOCK, AppAction.VIEW_ALL, AppAction.VIEW_OWN);

        ItemResponseDTO item = resolveItem(args);
        List<ItemWarehouseStockRowDTO> stockRows =
                itemWarehouseStockService.listStockForItem(item.getId(), authContext.getCurrentCompanyId());

        int onHand = stockRows.stream().mapToInt(row -> nz(row.getQuantityOnHand())).sum();
        int reserved = stockRows.stream().mapToInt(row -> nz(row.getReserved())).sum();
        int available = stockRows.stream().mapToInt(row -> nz(row.getAvailable())).sum();

        return mapOf(
                "item", itemSummary(item),
                "totals", mapOf(
                        "quantityOnHand", onHand,
                        "reserved", reserved,
                        "available", available,
                        "reorderLevel", item.getReorderLevel()
                ),
                "warehouseStock", stockRows
        );
    }

    private Map<String, Object> getInventorySummary() {
        requireAny(AppModule.INVENTORY_STOCK, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        InventoryReportSummaryDTO summary = inventoryReportService.buildSummary(null, null);
        InventoryReportTotalsDTO totals = summary.getTotals();

        return mapOf(
                "generatedAt", summary.getGeneratedAt(),
                "totals", totals,
                "lowStockItemCount", summary.getLowStockItemCount(),
                "lowStockItems", summary.getLowStockItems().stream()
                        .limit(10)
                        .map(this::lowStockItemSummary)
                        .toList(),
                "topStockLinesByValue", summary.getTopStockLinesByValue().stream()
                        .map(this::topStockLineSummary)
                        .toList()
        );
    }

    private Map<String, Object> getLowStockItems(JsonNode args) {
        requireAny(AppModule.INVENTORY_STOCK, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        int limit = limit(args, DEFAULT_LIMIT);
        InventoryReportSummaryDTO summary = inventoryReportService.buildSummary(null, null);
        List<Map<String, Object>> items = summary.getLowStockItems().stream()
                .limit(limit)
                .map(this::lowStockItemSummary)
                .toList();

        return mapOf(
                "lowStockItemCount", summary.getLowStockItemCount(),
                "items", items
        );
    }

    private Map<String, Object> getInvoiceStatus(JsonNode args) {
        requireAny(AppModule.FINANCE_INVOICE, AppAction.VIEW_ALL, AppAction.VIEW_OWN);

        InvoiceResponse invoice;
        if (args.hasNonNull("invoiceId")) {
            invoice = invoiceService.getInvoiceById(args.path("invoiceId").asLong());
        } else {
            String invoiceCode = text(args, "invoiceCode");
            if (invoiceCode.isBlank()) {
                throw new IllegalArgumentException("invoiceId or invoiceCode is required");
            }
            invoice = invoiceService.getInvoiceByCode(invoiceCode);
        }

        return invoiceSummary(invoice);
    }

    private Map<String, Object> getRecentInvoices(JsonNode args) {
        requireAny(AppModule.FINANCE_INVOICE, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        String status = normalize(text(args, "status"));
        int limit = limit(args, DEFAULT_LIMIT);

        List<Map<String, Object>> invoices = invoiceService.listInvoicesForCurrentCompany(null).stream()
                .filter(invoice -> status == null || status.equals(normalize(invoice.getStatus())))
                .limit(limit)
                .map(this::invoiceSummary)
                .toList();

        return mapOf("status", status, "invoices", invoices);
    }

    private ItemResponseDTO resolveItem(JsonNode args) {
        if (args.hasNonNull("itemId")) {
            return itemService.getItem(args.path("itemId").asLong());
        }

        String sku = normalize(text(args, "sku"));
        String query = normalize(text(args, "query"));

        return itemService.listForCompany().stream()
                .filter(item -> {
                    if (sku != null && sku.equals(normalize(item.getSku()))) {
                        return true;
                    }
                    return query != null && matchesItem(item, query);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching inventory item was found"));
    }

    private void requireAny(AppModule module, AppAction... actions) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionCheckService.hasAny(auth, module, actions)) {
            throw new AccessDeniedException("Missing permission for " + module);
        }
    }

    private JsonNode parseArguments(String argumentsJson) {
        try {
            String json = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid tool arguments", ex);
        }
    }

    private boolean matchesItem(ItemResponseDTO item, String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        return contains(item.getSku(), normalizedQuery)
                || contains(item.getName(), normalizedQuery)
                || contains(item.getCategory(), normalizedQuery)
                || contains(item.getSubCategory(), normalizedQuery)
                || contains(item.getBrand(), normalizedQuery)
                || contains(item.getBarcode(), normalizedQuery);
    }

    private Map<String, Object> itemSummary(ItemResponseDTO item) {
        return mapOf(
                "id", item.getId(),
                "sku", item.getSku(),
                "name", item.getName(),
                "category", item.getCategory(),
                "brand", item.getBrand(),
                "quantity", item.getQuantity(),
                "available", item.getAvailable(),
                "reserved", item.getReserved(),
                "reorderLevel", item.getReorderLevel(),
                "status", item.getStatus(),
                "warehouseName", item.getWarehouse_name(),
                "detailUrl", inventoryItemUrl(item.getId())
        );
    }

    private Map<String, Object> lowStockItemSummary(InventoryLowStockItemDTO item) {
        return mapOf(
                "id", item.getItemId(),
                "itemId", item.getItemId(),
                "sku", item.getSku(),
                "name", item.getName(),
                "available", item.getAvailable(),
                "reorderLevel", item.getReorderLevel(),
                "detailUrl", inventoryItemUrl(item.getItemId())
        );
    }

    private Map<String, Object> topStockLineSummary(InventoryTopStockLineDTO item) {
        return mapOf(
                "id", item.getItemId(),
                "itemId", item.getItemId(),
                "sku", item.getSku(),
                "name", item.getName(),
                "warehouseId", item.getWarehouseId(),
                "warehouseName", item.getWarehouseName(),
                "quantityOnHand", item.getQuantityOnHand(),
                "valueAtCost", item.getValueAtCost(),
                "detailUrl", inventoryItemUrl(item.getItemId())
        );
    }

    private String inventoryItemUrl(Long itemId) {
        return itemId == null || itemId <= 0 ? null : "/inventory/stocks/" + itemId;
    }

    private Map<String, Object> invoiceSummary(InvoiceResponse invoice) {
        return mapOf(
                "id", invoice.getId(),
                "invoiceId", invoice.getInvoiceId(),
                "type", invoice.getType(),
                "toParty", invoice.getToParty(),
                "status", invoice.getStatus(),
                "invoiceDate", invoice.getInvoiceDate(),
                "dueDate", invoice.getDueDate(),
                "paidDate", invoice.getPaidDate(),
                "amount", money(invoice.getAmount()),
                "openAmount", money(invoice.getOpenAmount()),
                "outstanding", money(invoice.getOutstanding()),
                "currencyCode", invoice.getCurrencyCode(),
                "orderNumber", invoice.getOrderNumber(),
                "supplierName", invoice.getSupplierName()
        );
    }

    private int limit(JsonNode args, int fallback) {
        int raw = args.hasNonNull("limit") ? args.path("limit").asInt(fallback) : fallback;
        return Math.max(1, Math.min(raw, MAX_LIMIT));
    }

    private String text(JsonNode args, String field) {
        return args.hasNonNull(field) ? args.path(field).asText("").trim() : "";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String normalizedQuery) {
        String normalizedValue = normalize(value);
        return normalizedValue != null && normalizedValue.contains(normalizedQuery);
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String employeeName(Employee employee) {
        String first = employee.getFirstName() == null ? "" : employee.getFirstName();
        String last = employee.getLastName() == null ? "" : employee.getLastName();
        String fullName = (first + " " + last).trim();
        return fullName.isBlank() ? employee.getEmployeeNo() : fullName;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Object> functionTool(
            String name,
            String description,
            Map<String, Object> parameters
    ) {
        return mapOf(
                "type", "function",
                "function", mapOf(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    private Map<String, Object> objectSchema(
            Map<String, Object> properties,
            List<String> required
    ) {
        return mapOf(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false
        );
    }

    private Map<String, Object> stringProperty(String description) {
        return mapOf("type", "string", "description", description);
    }

    private Map<String, Object> integerProperty(String description) {
        return mapOf("type", "integer", "description", description);
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
