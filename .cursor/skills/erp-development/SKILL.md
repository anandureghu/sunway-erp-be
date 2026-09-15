---
name: erp-development
description: >-
  Implements and fixes features across the Sunway Spring Boot ERP backend (HR,
  payroll, inventory, purchase, sales, finance, appraisal, security). Use when
  editing com.erp controllers, services, entities, migrations, or REST APIs.
---

# Sunway backend development

## Maintain docs

Before closing a task: if you used a pattern **not** already in `.cursor/rules/` or this skill, append it per `.cursor/rules/capture-knowledge.mdc` (usually under **Captured patterns** below or `project.mdc`).

## Find the right module

1. Locate `controller/<module>/` for the REST path (e.g. `/api/purchase/`, `/api/sales/`).
2. Implement in matching `service/<module>/` and `repo/<module>/`.
3. Mirror existing DTO and entity patterns in that folder.

## Common workflows

**New field on existing entity**

1. Flyway migration (nullable when possible)
2. Entity + DTO + service mapping + controller if exposed
3. `mvn compile -DskipTests`

**New endpoint**

1. DTO in `dto/<module>/`
2. Service method with `@Transactional` and company scoping
3. Controller mapping under `/api/...`

**Business rule rejection**

```java
throw new ConflictException("Clear user-facing message");
```

**Finance / COA**

- `TransactionService.applyPostingToCoa`: debit `-= amount`, credit `+= amount`
- `CoaBalanceRules.assertSufficientBalance` before posting
- Reversals: swap debit/credit legs (see existing cancel-reversal methods)

**Permissions**

- `@RequiresPermission` / `PermissionCheckService` / `EmployeeAccessGuard` where routes need module guards

## Avoid

- Cross-module cycles (e.g. payment ↔ invoice — use `@Lazy` or extract shared service if already established)
- Committing without user request
- Unscoped list endpoints (always filter by company for tenant data)

## Verify

```bash
mvn compile -DskipTests
```

Restart app if migration added.

## Captured patterns

<!-- Agents: prepend new bullets here (newest first). Do not duplicate project.mdc. -->

- **Payroll processing history** — `/employees/{employeeId}/salary/payroll/history` returns the current calendar month's records by default for payroll generation. Pass `all=true` only for the employee Payroll History screen or reporting flows that explicitly need prior months; payroll records are never deleted.

- **Operational employee lists** — default employee directory, department, manager, and paginated APIs include Active, On Leave, and Under Probation staff, but exclude Inactive, Resigned, Terminated, Retired, and archived records. Keep dedicated history/archive APIs for inactive records; do not delete them. Benefits adjustments must enforce the same eligibility server-side.

- **Leave-policy role fallback** — resolve policies per leave type using the employee's HR `companyRole` first, then legacy/security `role` only when that leave type is absent from the HR role. Use the same resolver for available types, previews/applications, balance initialization, policy saves, and policy deletion; otherwise an employee can see a leave type that cannot be applied or receive the wrong balance.

- **Session idle timeout vs max-shift auto check-out** — `Company.sessionIdleTimeoutMinutes` / `HrPoliciesDTO.sessionIdleTimeoutMinutes` is ERP UI session security (frontend signs out after inactivity). Allowed: null/0 (Off), 15, 20, 30; invalid values → `IllegalArgumentException` → 400. Do **not** wire this into attendance/timesheet check-out. Attendance auto check-out is `autoCheckoutAfterHours` only (allowed: 8, 10, 12; default 10) via `V20260915__auto_checkout_after_hours.sql`. Legacy `max_shift_checkout_grace_minutes` is unused.
- **Multi-tenancy hardening pass** — audited and fixed two systemic gaps: (1) generated business codes (`invoice_id`, `transaction_code`, `order_number`, `sku`, etc.) were globally unique in the DB while their generators reset per company, causing false "already exists" collisions across tenants; (2) several services fetched entities by plain `findById` with no check that the row belongs to the caller's company. See `multi-tenancy.mdc` for the full rules and the checklist to run before shipping tenant-scoped changes.
- **Purchase orders (draft)** — `PUT` update may change `supplierId` via `applyDraftSupplierChange` only while status is `DRAFT`; use `ConflictException` if not draft. See `PurchaseOrderService`, `PurchaseOrderUpdateDTO`.
- **PO → AP** — Vendor payable + generated purchase invoice are created in `onReleasedToSupplier` when status becomes `CONFIRMED`. AP vendor payments and purchase invoices list only CONFIRMED+ POs. `confirmVendorPayment` requires released PO. `cancel` blocked with `ConflictException` after AP payment confirmed.
- **PO payment receipts** — On `confirmVendorPayment`: regenerate GENERATED purchase invoice PDF (RECEIPT badge) + `VendorPaymentReceiptPdfService` → `payments/{id}/pdf`. PO DTO includes `purchaseInvoiceId`, `vendorPaymentId`.
- **PR line items** — Include `itemName` on `PurchaseRequisitionItemDTO` in `toDTO` (PO items already expose `itemName` in `PurchaseOrderItemDTO`).
