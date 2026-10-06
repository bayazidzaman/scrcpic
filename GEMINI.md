# Agent Behavioral Rules & Safety Policies

## 1. Git Push & Release Policy (STRICT CONSTRAINT)
- **NO AUTOMATIC PUSH OR RELEASE:**
  The agent must NEVER execute `git push`, update/push remote git tags, or create/update releases on GitHub unless the USER explicitly gives a direct command to do so (e.g., "push koro", "release koro", "github a push koro").
- **USER TESTING FIRST:**
  The user must always test changes locally on their device before any remote push or release occurs.
- **LOCAL WORKFLOW ONLY:**
  After completing any code modifications, building APKs, or local testing:
  - Build, compile, and prepare APKs locally.
  - Inform the user of what was changed and where the local APK / build output is located.
  - STOP and wait for the user to test and confirm.
  - Only execute `git push` or GitHub release actions after receiving explicit user permission.
