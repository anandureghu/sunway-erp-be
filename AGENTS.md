# Backend agents

Read [CLAUDE.md](./CLAUDE.md).

## Cursor rules (`.cursor/rules/`)

| File | Scope |
|------|--------|
| `project.mdc` | Always — stack, modules, errors, Flyway, auth |
| `capture-knowledge.mdc` | Always — append to rules/skills when you add undocumented patterns |
| `git-commits.mdc` | Always — commit policy |

After non-trivial work, check whether anything you did is missing from the table above or from **Skills**; if so, update docs in the same session (`capture-knowledge.mdc`).

## Skills (`.cursor/skills/`)

| Skill | When |
|-------|------|
| `erp-development` | Any backend feature, API, service, or migration work |

Frontend is a separate repo: `../frontend`.
