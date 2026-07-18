package com.erp.service.purchase;

import com.erp.domain.inventory.Item;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.domain.purchase.PurchaseRequisitionStatus;
import com.erp.exception.ConflictException;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Prevents new purchase requisitions while the same item at a delivery warehouse
 * already has an open PR or PO.
 */
@Service
public class PurchaseProcurementDuplicateGuard {

    private static final List<PurchaseRequisitionStatus> ACTIVE_PR_STATUSES = List.of(
            PurchaseRequisitionStatus.DRAFT,
            PurchaseRequisitionStatus.SUBMITTED
    );

    private static final List<PurchaseOrderStatus> ACTIVE_PO_STATUSES = List.of(
            PurchaseOrderStatus.DRAFT,
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private final PurchaseRequisitionRepository requisitionRepo;
    private final PurchaseOrderRepository orderRepo;
    private final ItemRepository itemRepo;

    public PurchaseProcurementDuplicateGuard(
            PurchaseRequisitionRepository requisitionRepo,
            PurchaseOrderRepository orderRepo,
            ItemRepository itemRepo
    ) {
        this.requisitionRepo = requisitionRepo;
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
    }

    /**
     * @param excludeRequisitionId when updating an existing draft PR, exclude it from the check
     */
    public void assertNoPendingProcurement(
            Long companyId,
            Long warehouseId,
            List<Long> itemIds,
            Long excludeRequisitionId
    ) {
        if (companyId == null || warehouseId == null || itemIds == null || itemIds.isEmpty()) {
            return;
        }

        Set<Long> distinctItemIds = new LinkedHashSet<>(itemIds);
        List<String> lineDetails = new ArrayList<>();

        for (Long itemId : distinctItemIds) {
            if (itemId == null) {
                continue;
            }
            Optional<PurchaseRequisition> pendingPr = requisitionRepo.findFirstPendingForItemAtWarehouse(
                    companyId,
                    warehouseId,
                    itemId,
                    ACTIVE_PR_STATUSES,
                    excludeRequisitionId
            );
            Optional<PurchaseOrder> pendingPo = orderRepo.findFirstPendingForItemAtWarehouse(
                    companyId,
                    warehouseId,
                    itemId,
                    ACTIVE_PO_STATUSES
            ).filter(po -> excludeRequisitionId == null
                    || po.getSourceRequisition() == null
                    || !excludeRequisitionId.equals(po.getSourceRequisition().getId()));

            if (pendingPr.isEmpty() && pendingPo.isEmpty()) {
                continue;
            }

            String itemLabel = itemRepo.findById(itemId)
                    .map(Item::getName)
                    .orElse("Item #" + itemId);

            StringBuilder line = new StringBuilder(itemLabel).append(": ");
            if (pendingPr.isPresent()) {
                line.append("PR ").append(pendingPr.get().getRequisitionNumber());
            }
            if (pendingPo.isPresent()) {
                if (pendingPr.isPresent()) {
                    line.append(", ");
                }
                line.append("PO ").append(pendingPo.get().getOrderNumber());
            }
            lineDetails.add(line.toString());
        }

        if (!lineDetails.isEmpty()) {
            throw new ConflictException(
                    "There is a PR or PO pending for this item at the selected warehouse ("
                            + String.join("; ", lineDetails)
                            + "). Please complete these PR and PO before creating a new one.");
        }
    }
}
