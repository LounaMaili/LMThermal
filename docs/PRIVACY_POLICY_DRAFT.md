# Privacy policy content — draft for owner/legal review

Not published or accepted. The owner must provide legal identity, contact, effective date,
public URL and in-app access before using this as a privacy policy. Reconcile it with the
signed app and Google Play Data safety form; this document does not make that declaration.

LMThermal processes external thermal-camera imagery and measurements on the Android
device. It does not currently require an app account or include an automatic analytics/
crash-upload service. The current manifest has no INTERNET permission. Android camera
and per-device USB access are requested to open the attached external camera explicitly.
Discovery does not initialize radiometry. Declining permission prevents acquisition.

Thermal scenes, settings, ROI, raw/native evidence, provenance and stored temperatures
may reveal sensitive information. Still captures are prepared in app-private storage.
The user chooses whether and where to save through Android's document picker and whether
to share a finalized file. A chosen cloud storage provider or recipient can transfer and
retain it under its own policy. Files are not anonymized merely because temperatures
are native-equivalent or the image is thermal.

Private staging is bounded/cleaned by exporter operations; cancellation attempts cleanup.
A failed external-provider cleanup can leave a partial file, which the app reports.
Exported files remain at the chosen destination and must be removed there by the user;
uninstalling LMThermal does not delete those independent copies or recipients' copies.
App-private data can be cleared through Android settings/uninstallation. App backup is
disabled, subject to OS/device behavior. No retention promise is made for external providers.

Developer builds retain bounded local numerical diagnostics. Release disables the dedicated
NumericEvidence serializer/file logger, but technical/errors/session counters can still
appear in Android logs. No automatic upload is added. Logs and captures should be reviewed
before voluntary sharing for support. Final source/build/dependency and logging review is
required before the owner publishes a policy or accepts a Data safety declaration.

This app does not establish independently calibrated absolute physical temperatures.
**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
