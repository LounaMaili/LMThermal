# R2 storage/backend and export evidence

**Test APK/JVM probes only; no recording UI or production storage feature.**

| Backend/proof | Result / assurance boundary |
|---|---|
| Pixel app-private file | Android synthetic packet and live runs append forward; body/footer FileDescriptor.sync, close and read-only reopen tested |
| Actual free space | Android StatFs.availableBytes queried; numeric packet report retained |
| Read-only no-copy access | Original app-private ParcelFileDescriptor opened read-only; attempted write refused and source unchanged. No mandatory full second copy to access |
| External no-copy URI grant | Not tested/shipped; existing LMTX FileProvider is not extended for R2 |
| Optional verified copy | JVM success, cancel, refusal, short write, failed close, readback mismatch and injected ENOSPC tested; source SHA immutable |
| Selected local SAF | Android Downloads local provider: verified exact export, reopen, seek, append, truncate, sync/close; cancellation and permission lifetime recorded separately |
| Remote/pipe provider | Untested; no canonical/durability support recommendation |

App-private recordings survive Activity/session transitions, but data-clear or
uninstall removes them. The existing Gradle connected test workflow uninstalls the
test/target APK, so evidence must be retrieved first. No backup/restore guarantee
was tested; private storage/URI access is not an independent backup. An access grant
has its own lifetime/permissions. A future product must document retention and backup.

## Capacity and refusal policy

Known startup bytes + reserve insufficient: Start blocked with reason. Unknown
capacity: label unknown, warn concisely, permit an otherwise supported sink; no
fabricated remaining duration. Ordinary available state is compact; low reserve
produces a separate warning. These pure state helpers are tested, not a polished UI.

Injected ENOSPC is distinguished from generic provider/storage refusal. Intake
stops on failed writer; pending queue/chunks are not counted saved. Prior commits
remain readable. A failed footer sync may leave valid forensic bytes but is still
an unacknowledged writer failure. No cross-provider POSIX rename/hard-link/seek
assumption is used, and a small spool cannot restore data a provider has lost.

Initial canonical backend recommendation is **app-owned local files**. SAF is an
optional independent, verified export; direct SAF canonical recording is not
recommended without provider-specific interruption/durability proof. Unknown
capacity alone does not refuse a tested otherwise usable export backend.

## Actual local Downloads probe

[Sanitized export result](reports/android-saf-success.json):
`com.android.providers.downloads.documents`, provider class
`com.android.providers.downloads.DownloadStorageProvider`. The operator selected
local Downloads/Téléchargements. Private destination URI is excluded from Git.

- Complete synthetic Full/DEFLATE source: 213,579 bytes, SHA-256
  `01c655277d25d962629882069379bdac0a76cf35c4d25f6be45692b8e5d9592c`.
- `rw`, `wa` and `wt` descriptors opened; seek-to-current-position succeeded.
- Readback exactly matched the source. A two-byte append produced 213,581 bytes;
  truncate/restore returned to the exact original bytes. Sync and close succeeded
  for each write; reopening succeeded. Source SHA remained unchanged.
- Provider capacity is unknown; the probe permits the selected otherwise supported
  destination. No remaining-duration estimate or provider free-space claim.
- The initial blank probe was a test-Activity system/action-bar overlap, verified
  through hierarchy/screenshot. Test-only insets and persistent button fixed it;
  no product UI or camera change.
- [Cancellation](reports/android-saf-cancel.json) reports `cancelled` and the same
  source SHA, with source immutability verified.
- No persistable permission was taken. After ending only the diagnostic test-app
  task with force-stop and reopening it, read access to the previous URI was denied
  with `SecurityException` ([permission result](reports/android-saf-permission.json)).
  This proves the tested temporary-grant lifetime, not every provider/OS policy.

Provider refusal, short write, failed close and readback mismatch are deterministic
JVM injections, not induced failures of this real Downloads provider. Remote/pipe
providers, direct SAF canonical recording, provider process kill and physical
power-loss durability remain untested and unsupported by this result.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
