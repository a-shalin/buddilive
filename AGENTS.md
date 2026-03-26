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

## Key architecture/conventions to follow
- **Code documentation**: add only when behavior is non-obvious; avoid documenting variables, arguments, method names.
- **Java**:
    - Prefer `final` for method parameters and local variables unless mutation is required
    - Do not add hardcoded string constants in code; use an `enum` for defined sets of values, or a `static final` class constant for a one-off value
    - Use lambdas for anonymous classes and method references wherever possible
- **For newly written SQL**:
    - Lowercase keywords, table names, column names, and aliases
    - Snake_case identifiers
    - Single quotes for strings
    - Explicit aliases and readable indentation
    - CTEs for deeply nested logic
- **JavaScript, ExtJS**
    - For local variables in new code use `const` by default, `let` if assignment is required.
- **File Formatting**:
    - avoid trailing blank lines at end of file.
    - Java/JS
        - keep a blank line before each method declaration, except when it is the first method in a block;
        - use one space after operators and commas (if not at end of line), and one space before `{`;
        - no space between method name and opening parenthesis.
- before submitting, verify all newly added/edited lines follow these rules.
- See [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) for high-level architecture overview and design decisions.

## Commits
- Do not add "Co-Authored-By" lines to commit messages.

## Code Style
- Do not add comments to self-obvious methods and code.

## Testing
- See [doc/E2E.md](doc/E2E.md) for E2E testing guide, architecture, and key lessons.
- For bug fixes, run the relevant test first and confirm it fails, then fix the bug, rerun, and confirm it passes.
- **No `Thread.sleep()` in Selenium tests.** Wait on an event, listener, or poll a condition with `wait.until()` instead.
