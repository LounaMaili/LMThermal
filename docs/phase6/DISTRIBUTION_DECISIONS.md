# Phase 6 distribution decisions — owner review, not a release

No license, final identity, signing key or public delivery is selected/created here.
Use the [component license review](../DISTRIBUTION_LICENSE_REVIEW.md) and the actual
binary ledger, not just the list of installed build packages. Hosting source on GitHub
is not a substitute for granting an application license or satisfying binary obligations.

| Decision | Evidence/preparation | Owner action before delivery |
| --- | --- | --- |
| Android project terms | Kotlin/AndroidX plus separate vendored libusb/libuvc and exact notices | Confirm copyright authority; choose application terms and source/relink delivery obligations |
| Desktop project terms | Current PyQt wheels GPLv3; commercial PyQt is a separate route; Qt has its own module/third-party conditions | Choose GPL-compatible source delivery or applicable commercial rights; audit remaining native components independently |
| Native library obligations | Rootless Ubuntu candidate maps 202 native COLLECT library/extension entries to 116 wheel, 85 Debian and one CPython origin; actual source/delivered hashes and Debian copyright notices retained | Review complete source/notices/replacement/relink/installation obligations; build inventory is not legal certification |
| Identity/icons/version | Android `org.lmthermal.app`, versionCode1 / `0.1.0-foundation`; current technical adaptive icon. Desktop private installer `0.1.0-preview`, no final branding icon | Approve identity, icon/copyright, release version sequence, support contact and channels |
| Android privacy | No INTERNET permission/account/automatic telemetry in current manifest; CAMERA/USB are normal explicit permissions. External SAF/share providers have independent behavior | Supply legal identity/contact/effective date/public policy URL, reconcile the signed app and Data safety declaration with [the draft](../PRIVACY_POLICY_DRAFT.md) |
| Android signing | Optional external properties, no generated/uploaded permanent key; no debug-signing fallback for release | Choose owner-controlled signing/upload-key custody, encrypted independent backups, recovery test and rotation/access procedures; never commit secrets |
| Windows signing/installer | User-only Inno template, no elevation/driver/service/firewall; unsigned build preparation | Native Windows11 acceptance, publisher identity/certificate decision, protected backups and verification of final signed bytes |
| Linux support | Old userland GLIBC2.35 candidate and native Fedora qualification; full Qt plugin set retained | Approve ABI/architecture/plugin policy and actual clean-distro/graphics matrix before advertising broad support |
| Windows support | Thirteen x64/CPython3.15 wheel inputs hashed, exact-source script and installer/operator recipe prepared | Run on real Windows11; WSL, Linux wheel downloads and old LMTX-only evidence do not qualify the new workspace |
| Android hardware support | arm64, USB Host, min26/target36; static four-library/16KiB APK/AAB checks | New Pixel availability confirmation, physical UI/permissions/lifecycle and visible-performance gates; no generic-camera or 16KiB runtime claim from compilation |

The Desktop executable/bootloader is an additional delivered ELF with its own exact
source/build receipt and PyInstaller exception notice. The native-library mapping counts
do not mean that every runtime/legal/source-delivery question is closed. Bundled Qt
plugins, fonts, image codecs, libc++/C++ runtimes and system interfaces need the final
release inventory. No manufacturer APK library or physical scene is added to a package.

For signing backups, keep owner secrets out of Git, CI logs, app artifacts and evidence
packets. Record public certificate fingerprints and source/artifact hashes separately.
Do not create a key just to complete a checklist. A same-filesystem private evidence copy
is not an independent backup. No Play Console, public release or application upload is
authorized by this document.

Production LMTR recording/R4/R6 and physical Full recording remain separately gated.
Privacy, interface responsiveness, exact storage parity and calibrated accuracy are
different claims; none follows automatically from a successfully finalized build.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
