package com.erp.domain.history;

import java.util.EnumSet;
import java.util.Set;

public enum HistoryEntityType {
    SALES_ORDER(HistoryModule.INVENTORY),
    PURCHASE_ORDER(HistoryModule.INVENTORY),
    PURCHASE_REQUISITION(HistoryModule.INVENTORY),
    STOCK_VARIANCE(HistoryModule.INVENTORY),
    GOODS_RECEIPT(HistoryModule.INVENTORY),
    PICKLIST(HistoryModule.INVENTORY),
    SALES_INVOICE(HistoryModule.FINANCE),
    CUSTOMER_PAYMENT(HistoryModule.FINANCE),
    PURCHASE_INVOICE(HistoryModule.FINANCE),
    VENDOR_PAYMENT(HistoryModule.FINANCE),
    JOURNAL_ENTRY(HistoryModule.FINANCE),
    TRANSACTION(HistoryModule.FINANCE),
    BUDGET_DISTRIBUTION(HistoryModule.FINANCE);

    private final HistoryModule module;

    HistoryEntityType(HistoryModule module) {
        this.module = module;
    }

    public HistoryModule getModule() {
        return module;
    }

    public static Set<HistoryEntityType> forModule(HistoryModule module) {
        EnumSet<HistoryEntityType> types = EnumSet.noneOf(HistoryEntityType.class);
        for (HistoryEntityType type : values()) {
            if (type.module == module) {
                types.add(type);
            }
        }
        return types;
    }
}
