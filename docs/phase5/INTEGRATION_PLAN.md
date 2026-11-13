# Phase 5 integration proposal — no merge authorized

Verified remote main refs on 2026-11-13: Android
`8eb4aceaf8bb36a8061c932b3121f898b81b4af3`, Desktop
`b0afdf3b42b6f55c2ae3d926f0d8c9df09255746`. Historical PRs and branches remain unchanged.
The two qualification branches descend from the existing Play/packaging tips, not main.

## Android ancestry and the nonidentical UX commit

- UX tip: `cb0189be978cfb49e238fda598c5153c9ab09af2`.
- Play tip: `2bfb615c8455253b0afbbfbd06a2136314c9eccd`.
- Common ancestor: `ae4943fe6a845051f06d68f66b5314b3f6f8529d`.
- Left/right commits: 1 / 2. The latest UX commit is NOT an ancestor of Play.
- Actual source diffs under app main Java, core, app local tests and instrumentation
  are empty. Both tips implement/test the same full-ROI source guard. Play's separate
  guard commit additionally updates its own release documents, so whole commit patch
  identity cannot be assumed.

A `git merge-tree --write-tree --messages` calculation (no commit, checkout or ref change)
finds a **CHANGELOG.md conflict** between the published tips. Source files auto-resolve;
that calculation is not an actual merge or its test result.

Recommended staged review after owner approval:

1. Review/integrate the complete UX branch, retaining its history and ownership tests.
2. Review the complete phase-5 Android qualification candidate, which descends from Play.
   Resolve the changelog conflict by preserving the Play/API36 entries, UX/presentation
   entries, ROI source-guard history and phase-5 qualification entries. Keep a single
   copy of identical entry text; do not drop either change or replay the code fix twice.
3. Run the complete tests/build/lint on the real resulting merge commit. The synthetic
   SDK36 checks require the explicit Java 21 host-test toolchain; app/core remain Java17.
4. Qualify the merged APK on the Pixel only after a new explicit availability confirmation.
   No automatic R4/R6 activation, physical recording or store submission follows.

A combined review/integration of the top candidate is possible if the owner explicitly
prefers it; it includes the same UX code but does not make cb0189b its exact ancestor.
Do not silently describe that alternative as preserving every commit's ancestry.
No rebase, cherry-pick, branch recreation or actual merge is performed in this phase.

## Desktop ancestry and proposed order

`35ee105c342903c0505eef648ede7f7745bc85ed` (workspace) IS an ancestor of
`8976eb7425763d12419d347c2f3d117e39b802f7` (packaging), with one packaging commit ahead.
The phase-5 Desktop qualification branch descends from that packaging tip.

Review the full required ancestry: latest R5 ownership/resource/hardening review
`63d8076c6297189ab6ec46747b78b4a32cd05407`, workspace, packaging, then phase-5 fixes/tests.
Do not cherry-pick only the shell onto an older reader or assume the old draft PR4 tip
already contains the later R5 fixes. Preserve the pinned reference reader/51 fixtures.
The top candidate may be integrated only after owner approval of all those layers;
no historical PR is changed here. Native Windows and physical interop remain separate.

## Current gates and owner decisions

- Complete unlocked native Linux/operator checks. X11 connectivity works, but the
  current session is locked and exposure-gated runs fail honestly; no unlock/security
  changes are attempted. Offscreen/widget results do not satisfy operator acceptance.
- Confirm Pixel availability later, then follow [the deferred physical plan](ANDROID_PHYSICAL_PLAN.md).
- Approve native Windows qualification/budget separately. No hosted CI remains authorized.
- Choose distribution licenses, branding/version/support/signing and privacy policy identity/URL.
  See [license review](../DISTRIBUTION_LICENSE_REVIEW.md).
- Choose a clean Linux build/ABI baseline and eventual AppImage/Flatpak scope; the current
  portable directory is a private host prototype, not cross-distro certification.

All scientific/wire/source invariants remain unchanged. No automatic merge, release,
issue/PR mutation, canonical fixture regeneration or production common recorder is authorized.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
