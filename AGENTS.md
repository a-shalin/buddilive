# Project Instructions

## Minimal-change policy

- For small requests, make the smallest correct change and stop.
- Prefer editing existing code over introducing new abstractions.
- Update only the files directly required for the task.
- Do not refactor unrelated code.
- Do not rename symbols, move files, or reorganize modules unless the task requires it.
- Keep diffs small and easy to review.
- Run only the smallest relevant verification for the edited area.
- If a broader cleanup seems useful, propose it separately instead of bundling it into the change.

## Architecture
- See [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) for high-level architecture overview and design decisions.

## Commits
- Do not add "Co-Authored-By" lines to commit messages.

## Code Style
- Do not add comments to self-obvious methods and code.

## Testing
- See [doc/E2E.md](doc/E2E.md) for E2E testing guide, architecture, and key lessons.
