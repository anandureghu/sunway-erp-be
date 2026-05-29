# Sunway ERP — backend

Spring Boot API (`com.erp`). Cursor: `.cursor/rules/`, `.cursor/skills/erp-development/`.

## Commands

```bash
mvn spring-boot:run
mvn compile -DskipTests
mvn flyway:migrate
```

## Architecture

Controllers → services (`@Transactional`) → repositories → JPA entities. DTOs for API.

## Product areas

HR & employees · payroll · appraisal · inventory · purchase · sales · finance (GL, AR, AP, budgets) · company settings · auth/permissions

## Conventions

- Tenant scope via `AuthContext` / `getCurrentCompanyId()`
- Errors: `ConflictException` (409), `NotFoundException` (404), body `{ "message": "..." }`
- Migrations: `src/main/resources/db/migration/VYYYYMMDD__*.sql`
- Match existing code in the module you touch

## Git

Branch `develop`. Commit only when asked; no Co-authored-by lines.

See [AGENTS.md](./AGENTS.md) for rule/skill index.
