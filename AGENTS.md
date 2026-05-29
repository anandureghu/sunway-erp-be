# Backend agents

Read [CLAUDE.md](./CLAUDE.md).

## Cursor rules (`.cursor/rules/`)

| File | Scope |
|------|--------|
| `project.mdc` | Always — stack, modules, errors, Flyway, auth |
| `git-commits.mdc` | Always — commit policy |

## Skills (`.cursor/skills/`)

| Skill | When |
|-------|------|
| `erp-development` | Any backend feature, API, service, or migration work |

Frontend is a separate repo: `../frontend`.
