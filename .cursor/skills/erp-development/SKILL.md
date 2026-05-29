---
name: erp-development
description: >-
  Implements and fixes features across the Sunway Spring Boot ERP backend (HR,
  payroll, inventory, purchase, sales, finance, appraisal, security). Use when
  editing com.erp controllers, services, entities, migrations, or REST APIs.
---

# Sunway backend development

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

- `@HrPermission` / `PermissionCheckService` / `EmployeeAccessGuard` where HR routes need guards

## Avoid

- Cross-module cycles (e.g. payment ↔ invoice — use `@Lazy` or extract shared service if already established)
- Committing without user request
- Unscoped list endpoints (always filter by company for tenant data)

## Verify

```bash
mvn compile -DskipTests
```

Restart app if migration added.
