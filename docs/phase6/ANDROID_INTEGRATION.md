# Phase 6 local Android integration — no main/PR merge

The owner requested a separate local integration of UX, Play and qualification. This
candidate starts at phase5 `8739275ea0b0e3092fbe444a040432e82efd0f30` and retains UX
`cb0189be978cfb49e238fda598c5153c9ab09af2` as its second parent. Both original histories,
the Play tip `2bfb615c8455253b0afbbfbd06a2136314c9eccd` and their source guards survive.
No main, published historical branch, old PR or recording prototype is integrated/changed.
The explicitly requested local history combination is not approval to merge a PR/main.

## Conflict review

The actual local merge has two conflicts, rather than just the phase4 dry calculation:

- `CHANGELOG.md`: retain all phase5, Play, UX, source-guard and release-privacy entries.
  The other parent's conflicting portion is empty; no unique UX entry is discarded.
- `docs/ANDROID_PRODUCT_WORKSPACE.md`: retain the shared source-guard explanation and
  all phase5 host evidence appended after it. Again the other side adds no competing rule.

The two files' resolved content matches the phase5 parent before this phase's explicit
documentation additions. App main Java/Kotlin, camera/core/USB, thermometry, native C/C++,
queues, ownership generations and memory policy are unchanged by this integration.
No new R4/R6 dependency or production LMTR control is introduced.

## Host software result

The combined code passes **285 JVM/Robolectric executions: 234 core + 51 app**, with
zero failures/errors/skips. Actual ThermalScreen actions now also run with French
320×568 portrait and English 640×320 landscape at 200% text, on simulated API32/36.
The existing English portrait/French landscape cases remain. Each no-camera section
keeps the thermal stage at the same nonzero bounds. Explicit Connect, source failure,
unavailable measurement, native center/full ROI, independent colors and Close are tested.
These are programmatic host checks, not touch/TalkBack or ART/visible-latency certification.

Debug/instrumentation/unsigned release APK, unsigned AAB and debug/release lint pass on
the combined worktree. The host sandbox uses Java21; production bytecode stays Java17,
minSdk26, target/compile36, arm64 and the established native libraries. Exact release
SHA-256/ELF/ZIP receipts and test XML stay private. No signing key or delivery is created.

## Physical/performance gate remains deferred

No ADB command, phone query, pairing, APK installation, ART run or camera action is
authorized now. Follow [the explicitly deferred plan](../phase5/ANDROID_PHYSICAL_PLAN.md)
only after new owner confirmation of Pixel availability and exclusive camera ownership.
Record acquisition/callback cadence, render preparation, publication/rejections, observed
screen freshness and Java/native/GC evidence separately. Host elapsed test time does not
measure those. Keep normal preview and all safety/admission limits. No physical Full LMTR.

Distribution decisions remain owner-controlled: [licensing](../DISTRIBUTION_LICENSE_REVIEW.md),
identity/icons/version, privacy contact/policy, signing custody/backups and actual supported
platform matrix. Builds do not constitute Play certification or independent calibration.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
