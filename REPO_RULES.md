# LMThermal — Repository Rules

Any agent, contributor, or tool working on this repository **MUST** follow these rules.

## Code Comments

- **All code comments MUST be in English.** No exceptions.
- Every function, class, and non-obvious logic block must have a clear comment.
- Comments should explain *why*, not just *what*.
- Use docstrings (JSDoc/Python docstring/KDoc depending on language) for public APIs.

## Documentation Maintenance

When making changes, you MUST update all affected documentation:

| Change | Documentation to update |
|--------|------------------------|
| New feature added | `README.md` (plan checklist), `SPECIFICATION.md` |
| Hardware/protocol discovery | `docs/HARDWARE.md` |
| Bug fix or behavior change | `CHANGELOG.md` |
| New dependency or tool | `README.md` (stack section) |
| API or interface change | `docs/SPECIFICATION.md` |
| Configuration change | `docs/CONFIGURATION.md` (create if needed) |
| Phase completion | `README.md` (check boxes) |

### Rule of thumb
If a change would confuse someone reading the repo 6 months from now, document it.

## CHANGELOG.md

Keep a `CHANGELOG.md` at the repo root using this format:

```markdown
# Changelog

## [Unreleased]
### Added
- New feature descriptions

### Changed
- Modified behavior descriptions

### Fixed
- Bug fix descriptions

## [0.1.0] - YYYY-MM-DD
...
```

Update `[Unreleased]` with every meaningful commit. Don't wait for releases.

## Commit Messages

- Use English
- Be descriptive: `"Add Y16 raw temperature capture via vendor commands"` not `"update"`
- Prefix with scope when relevant: `docs:`, `hardware:`, `feat:`, `fix:`

## Before Every Commit

Checklist (automated mentally, not by a hook):
- [ ] Code commented in English
- [ ] Affected docs updated
- [ ] CHANGELOG.md updated (if meaningful change)
- [ ] No secrets/credentials in code (use config files or env vars)

## Branch Naming

- `feat/short-description` — new features
- `fix/short-description` — bug fixes
- `docs/short-description` — documentation only
- `proto/short-description` — prototyping / experimentation

## Languages

- Code comments: **English**
- Documentation (README, specs, docs): **English**
- Commit messages: **English**
- Issues & discussions: French or English (contributor's choice)
