# Audit: 0e07c456-add-pr-test-and-apk-artifact-workflow

Result: PASS — task moved to `.agents/tasks/taskmaster/`.

Verified:
- Implementing commit `00d02d8a492e59ca40217606362022872ba98201` changes only the task record and adds `.github/workflows/pr-test-and-apk-artifact.yml`; it does not change `daily-beta-release.yml` or `app/build.gradle.kts`.
- The new workflow runs for pull requests targeting `main`, uses Java 17 Temurin and `android-actions/setup-android@v3`, and installs `platforms;android-37.0` with `build-tools;36.0.0` in both jobs.
- The test job runs `./gradlew --no-daemon test`; the build job waits for it, builds `:app:assembleDebug` with the existing CI version properties, and uploads `app/build/outputs/apk/debug/*.apk` through `actions/upload-artifact@v4` with `if-no-files-found: error` and a PR-derived artifact name.
- Re-ran `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon test` (BUILD SUCCESSFUL, 24 actionable tasks).
- Re-ran `ANDROID_HOME=/tmp/agents-artifacts/android-sdk ./gradlew --no-daemon -PciVersionCode=7 -PciVersionName=0.1.0-pr.7 :app:assembleDebug` (BUILD SUCCESSFUL, 36 actionable tasks).

Not verified:
- A remote GitHub Actions PR run; no PR can be opened from this environment. The task's local CI-equivalent verification satisfies its stated alternative acceptance criterion.

Follow-up: none.
