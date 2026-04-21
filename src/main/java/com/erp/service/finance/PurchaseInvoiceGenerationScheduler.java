package com.erp.service.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Generates the purchase (AP) invoice PDF after the purchase order persistence transaction commits,
 * so the PO row is visible to {@link InvoicePDFService} and a PDF failure does not roll back the PO.
 */
@Component
public class PurchaseInvoiceGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurchaseInvoiceGenerationScheduler.class);

    private final InvoiceService invoiceService;

    public PurchaseInvoiceGenerationScheduler(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    public void schedulePurchaseInvoiceAfterCommit(Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invoiceService.createOrGetGeneratedPurchaseInvoiceForPurchaseOrder(purchaseOrderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    invoiceService.createOrGetGeneratedPurchaseInvoiceForPurchaseOrder(purchaseOrderId);
                } catch (Exception e) {
                    log.error(
                            "Failed to generate purchase invoice for purchase order id={}. "
                                    + "Create or refresh the invoice from Finance if needed.",
                            purchaseOrderId,
                            e
                    );
                }
            }
        });
    }
}
