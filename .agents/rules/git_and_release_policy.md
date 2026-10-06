# Git Push & Release Policy

## Rule: Manual User Approval Required Before Push or Release
- **Constraint:** Do not automatically run `git push`, push git tags, or publish/update GitHub releases.
- **Workflow:**
  1. Complete code changes, verification, and local builds (e.g. `assembleRelease` or `assembleDebug`).
  2. Inform the user that the build is ready for local testing.
  3. Wait for the user to test and confirm.
  4. Only push to GitHub or publish a release when the user explicitly instructs (e.g., "push koro", "release koro").
