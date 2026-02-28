# Midori AI Radio Contributor Guide

This repository is an Android app (Kotlin + Jetpack Compose) built with Gradle. Use this guide as the source of truth for contributor workflow in this project.

---

## Where to Look for Guidance
- **`app/src/main/`**: Android application code, UI, and resources.
- **`app/src/test/`**: Unit tests.
- **`app/build.gradle.kts` + root Gradle files**: Build/dependency configuration.
- **`buildapk.sh` and `scripts/run-build-konsole.sh`**: Dockerized APK build/install workflow.
- **`.agents/modes/`**: Contributor role instructions.
- Additional directories may include their own `AGENTS.md`. Those files take precedence for the directory tree they reside in.

---

## Development Basics
- Use Java 17 and the checked-in Gradle wrapper (`./gradlew`).
- Verification-first: confirm current behavior in the codebase before changing code; reproduce/confirm the issue (or missing behavior); verify the fix with clear checks.
- No broad fallbacks: do not add “fallback behavior everywhere”; only add a narrow fallback when the task explicitly requires it, and justify it.
- No backward compatibility shims by default: do not preserve old code paths “just in case”; only add compatibility layers when the task explicitly requires it.
- Minimal documentation, minimal logging: prefer reading code and docstrings; do not add docs/logs unless required to diagnose a specific issue or prevent a crash.
- Run the project checks that apply to your change and record exact commands:
  - `./gradlew test`
  - `./gradlew :app:assembleDebug`
  - `./buildapk.sh` (only when APK/device install validation is needed and Docker + adb are available)
- Keep code, configuration, and documentation changes in sync. When you update behavior, review nearby docs for accuracy.
- Use structured commit messages such as `[TYPE] Concise summary` and keep pull request descriptions short and outcome focused.
- Break large efforts into reviewable commits or tasks. Reference related issues, design docs, or feedback files directly in your commits and PRs.
- Respect repository style guides. If none exist yet, update `AGENTS.md` and the relevant mode docs with short, direct rules.

---

## Task and Planning Etiquette
- Place actionable work items in `.agents/tasks/` using unique filename prefixes (for example, generate a short hex string with `openssl rand -hex 4`).
- Move completed items into a dedicated archive such as `.agents/tasks/done/` to keep the active queue focused.
- Capture brainstorming notes, prompt drafts, audits, and reviews in their dedicated `.agents/` subdirectories so future contributors can trace decisions.

---

## Communication
- Use commit messages, pull request comments, and task-file updates as the communication channel for start/progress/completion updates.
- Summarize significant updates and include verification commands/results so other contributors can reproduce your checks.

---

## Contributor Modes
This repository currently uses the following contributor modes. Review the matching file in `.agents/modes/` before beginning a task.

- **Task Master Mode** (`.agents/modes/TASKMASTER.md`)
- **Manager Mode** (`.agents/modes/MANAGER.md`)
- **Coder Mode** (`.agents/modes/CODER.md`)
- **Auditor Mode** (`.agents/modes/AUDITOR.md`)

---

## Maintaining This Guide
1. Keep commands and paths aligned with the current Gradle/Android project structure.
2. Update mode files when responsibilities or workflow expectations change.
3. Keep instructions short, concrete, and reproducible.
