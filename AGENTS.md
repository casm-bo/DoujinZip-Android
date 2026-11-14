# Default Behavior

## Repository Content and Privacy

- Keep commits limited to application code and files needed to build, test, run, release, or maintain repository tooling.
- Do not add plans, investigation reports, measurement logs, screenshots, recordings, or temporary documentation. Keep these outside the repository.
- Never commit real personal information, local user paths, device identifiers, connection credentials, tokens, or private keys. Use clearly synthetic test fixtures.
- Inspect staged changes for personal information and secrets before every commit.
- Work in the current subscription branch for related changes. Do not create a separate branch or worktree unless the user requests one.

## Task Organization and Commits

Before making changes, divide the requested work into logical, independently verifiable categories.

For each category:

1. Complete only that category.
2. Run the smallest relevant verification or test.
3. Review the changed files and exclude unrelated user changes.
4. Commit automatically after verification succeeds.

Commit rules:

- Create one atomic commit per completed category.
- Use Conventional Commits with a concise message, such as `feat:`, `fix:`, `refactor:`, `test:`, or `docs:`.
- Never commit unrelated, pre-existing, or user-owned changes.
- Do not commit incomplete or failing work.
- Do not amend, squash, rebase, or push unless explicitly requested.
- If changes cannot be separated safely, stop and ask before committing.

## Android Device Testing

- After UI tests, uninstall `com.doujinzip.android.uitest` and `com.doujinzip.android.uitest.test` from every device or emulator used, even if tests fail or are interrupted. Verify that both packages are gone before handing back the device. Preserve the installed debug and release apps.

## Default Skills

Always use:

- Ponytail
- Caveman

These should be applied automatically unless I explicitly disable them.

Be concise, but never omit important reasoning, trade-offs, risks, or implementation details when they are necessary for making a correct decision.

## Superpowers

Do NOT use the Superpowers workflow automatically.

Before using Superpowers, ask for my permission.

Only suggest Superpowers when you genuinely believe the task would significantly benefit from it (for example: large architecture changes, major refactoring, or complex multi-step implementations).

Wait for my approval before invoking any Superpowers workflow.

## Changelog and Release Notes

- For each version, show each non-empty category once as a heading in this order: `✨ 기능`, `🐞 버그 수정`, `🎨 UI 개선`. Summarize its changes in a single bullet below the heading, keeping the key user-facing details. Apply this format to the in-app changelog, `CHANGELOG.md`, and release notes.
- Put layout, scaling, language-list presentation, controls, and visual contrast under `UI 개선`, rather than separate screen or language categories.
- Keep entries concise and user-facing. Use the same approved wording in the app changelog and release notes; Android also updates `CHANGELOG.md`.
- Show the proposed changelog to the user and obtain approval before version commits, tags, or release publication. Approval of the wording authorizes the requested release to proceed.
