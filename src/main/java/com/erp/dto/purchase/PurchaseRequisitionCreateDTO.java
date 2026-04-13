package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequisitionCreateDTO {

    /** COA leg that is debited on approval (e.g. expense or inventory). */
    private Long debitAccountId;
    /** COA leg that is credited on approval (e.g. accounts payable). */
    private Long creditAccountId;

    private Long preferredSupplierId;
    private Long departmentId;
    /** When set, PR is created on behalf of this user; otherwise current user. */
    private Long requestedByUserId;

    private List<PurchaseRequisitionItemDTO> items;
}
