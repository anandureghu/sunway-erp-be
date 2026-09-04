# Sales reserve-on-confirm & related fulfillment fixes

**Date:** 2026-09-05  
**Status:** Approved  
**Scope:** Sales order confirm stock behavior, SO line item display, PO qty-on-order, picklist archive gate

## Problem

Ali Hashi (2026-09-02) requested:

1. Sales order lines: show item code; rename “Item” → “Item Name”
2. On sales order confirm: reserve quantity in the warehouse
3. When PO is released to supplier: update qty on order
4. Picklist Archive must not appear until the order is delivered (not merely picked)

Today, confirm **FIFO-consumes** on-hand immediately. `item_warehouse_stock.reserved` exists but is unused by sales. Qty-on-order includes `APPROVED` POs before release. Archive is offered for any `PICKED` picklist.

## Goals

- Confirm reserves stock; dispatch consumes on-hand and clears reserve
- Available-to-promise remains `max(0, onHand − reserved)`
- COGS / FIFO move from confirm to dispatch
- UI and API gates match the product rules above

## Non-goals

- Migrating historically confirmed SOs that already consumed stock
- Soft-allocating specific batch numbers at confirm time
- Changing invoice creation timing on confirm

---

## 1. Stock lifecycle (reserve → consume)

### Confirm (`SalesOrderService.confirm`)

For each line warehouse:

1. Assert available ≥ qty (`assertAvailableForSale` / equivalent using onHand − reserved)
2. Increase `item_warehouse_stock.reserved` by qty (and keep item-level reserved aggregates in sync if the service already does)
3. Do **not** call `consumeFifo` / `decreaseForConfirmedSale`
4. Leave `cogsAmount` / `fifoUnitCost` unset until dispatch

### Cancel confirmed SO (`SalesOrderService.cancel`)

If status was `CONFIRMED` and stock was reserved (not yet consumed):

- Decrease `reserved` by line qty
- Do **not** call batch restore for these orders (no prior consume)

If an open picklist exists, keep existing block. Cancelled picklist does not release reserve; SO cancel does.

### Dispatch (`ShipmentService.dispatch`)

When shipment moves `CREATED` → `DISPATCHED`:

1. For each shipment/picklist line: `consumeFifo` against the line warehouse
2. Decrease `reserved` by the same qty
3. Write COGS / fifo unit cost onto the related sales order items
4. Remove the comment that stock was already consumed at confirm

### Picklist mark-picked / cancel

- **Mark picked:** status only; no stock mutation
- **Cancel picklist:** status only; reserve stays with the confirmed SO until SO cancel or dispatch

### New stock helpers

Add focused methods on `ItemWarehouseStockService` (names illustrative):

- `reserveForSale(itemId, warehouseId, qty, companyId)`
- `releaseReservation(itemId, warehouseId, qty, companyId)`

Consume path keeps existing `consumeFifo` + on-hand decrease; dispatch additionally calls `releaseReservation` after (or as part of) consume so reserved never exceeds on-hand mid-transaction.

**Invariant:** after dispatch, reserved decreases by qty and on-hand decreases by qty; available unchanged by the pair of operations relative to pre-confirm available (aside from other concurrent activity).

---

## 2. Sales order line UI — item code + label

### Backend

- Add `itemSku` (String) to `SalesOrderItemResponseDTO`
- Populate in `SalesOrderService.toDTO` from `item.getSku()`

### Frontend

- Extend `SalesOrderItemResponseDTO` in API types
- `sales-order-detail-items.tsx`: column header **Item Name**; show SKU above or beside name (SKU as primary code line, name as title — match inventory patterns if any)
- Align dialog copy in `sales-order-details-dialog.tsx` if it still says “Item” without SKU

---

## 3. PO qty on order — count from release

### Behavior

`quantityOnOrder` / report `totalOnOrder` must sum open remaining qty only for:

- `CONFIRMED`
- `PARTIALLY_RECEIVED`

**Exclude** `APPROVED` (approved but not yet released to supplier).

### Touchpoints

- `ItemService.loadOnOrderByItem` status list
- `InventoryReportService` open-order status list
- Any matching repository query callers

Release (`PurchaseOrderService.confirm`: APPROVED → CONFIRMED) is the moment qty-on-order increases; no separate counter column required.

---

## 4. Picklist Archive — only after delivery

### Backend (`PicklistService.archive`)

Allow archive when:

- Picklist status is `CANCELLED`, **or**
- Picklist status is `PICKED` **and** a shipment exists with status `DELIVERED`

Reject (Conflict) if `PICKED` with no shipment, or shipment not yet `DELIVERED` (and not cancelling via CANCELLED picklist path).

### Frontend

- `createPicklistColumns` / bulk select: show Archive only when delivered (or cancelled)
- Prefer exposing `shipmentStatus` (or `delivered`) on `PicklistResponseDTO` so the UI does not guess from `shipmentId` alone

---

## Error handling

- Confirm: insufficient available → clear error, no partial reserve (transaction rollback)
- Dispatch: insufficient on-hand/batches for reserved qty → fail dispatch (data inconsistency; should be rare if reserves were held)
- Archive: Conflict with explicit message when not delivered

## Testing (manual / focused)

1. Confirm SO → reserved up, on-hand unchanged, available down
2. Dispatch → on-hand down, reserved down, COGS set on lines
3. Cancel confirmed SO (no picklist) → reserved released
4. Approve PO → qty on order unchanged; release → qty on order up
5. Picked picklist without delivery → no Archive; after DELIVERED → Archive OK
6. SO detail shows SKU + “Item Name”

## Out of scope notes

Existing confirmed orders that already consumed stock are left as-is; no backfill of `reserved`.
