# Distribution license review — owner decisions pending

Review date: 2026-11-13. This is an engineering inventory and a proposed compliance
worklist, not a selected product license or legal certification. Neither repository
currently has a top-level application LICENSE. Confirm copyright/contributor authority
before choosing terms; the wire specification and a private build do not grant a new license.

## Evidence and component boundaries

| Component | Observed terms / scope | Distribution work |
| --- | --- | --- |
| PyQt6 6.12.0 wheel | Installed metadata: GPL-3.0-only; upstream offers GPLv3 or separate commercial terms | Choose a compatible product route or obtain applicable commercial rights; never infer LGPL from Qt's license |
| Qt 6.12 wheel/native plugins | Installed metadata: LGPLv3; Qt also has GPL-only and third-party components | Inventory actual shipped modules/plugins, retain notices and meet applicable replacement/source/installation obligations; commercial Qt is a separate decision |
| OpenCV 5.0.0.93 headless wheel | OpenCV >=4.5 uses Apache-2.0; wheel includes separate third-party notices/codecs | Retain exact wheel notices and audit native codecs; headless removes Qt GUI duplication, not all third-party obligations |
| NumPy 2.5.4 | Actual metadata is a compound expression: BSD-3-Clause, 0BSD, MIT, Zlib, CC0-1.0 | Retain complete distribution notices and inspect bundled native numerical libraries |
| Pillow 12.3.0 | Installed metadata: MIT-CMU | Review bundled image codecs as well as Pillow's notice |
| Python 3.15.2 | Runtime's complete license text exists outside venv, under the platform stdlib | Include that exact text; do not assume pip metadata covers the interpreter |
| PyInstaller 6.22.3 | GPL with a bundling exception; build tool/bootloader, not a PyQt licensing waiver | Preserve build provenance; its exception does not remove other dependencies' conditions |
| Android libusb 1.0.29 | Vendored source headers: LGPL-2.1-or-later | Keep matching source, notices, modifications/build recipes and required reverse-engineering/relink rights available |
| Android libuvc snapshot | Vendored BSD-3-Clause | Retain copyright/conditions/disclaimer and nonendorsement terms |
| libuvc utlist helper | Its actual embedded BSD-style header has one source-retention condition | Keep the exact notice; do not silently relabel it as libuvc's three-clause notice |
| AndroidX/Compose/Kotlin/coroutines | Existing dependency provenance identifies Apache-2.0 | Review exact release dependency graph and notices, including native graphics-path |
| SDK/NDK/AGP, JUnit/Robolectric/UI tests | Development/test tooling, not automatically all shipped runtime components | Separate build inventory from app/bundle SBOM; review NDK runtime notices actually delivered |

The Android native libraries remain separate shared objects and exact vendored source/build
recipes are in `third_party/README.md` and CMake. APK assets already retain native notices.
No manufacturer thermometry APK binary is included. Shipping readable source alone is not
a substitute for checking all applicable binary-delivery, signing and relink conditions.

Primary references: [PyQt licensing](https://www.riverbankcomputing.com/software/pyqt/intro),
[Qt licensing and GPL-only modules](https://doc.qt.io/qt-6/licensing.html),
[OpenCV licensing](https://opencv.org/license/),
[OpenCV wheel third-party notices](https://github.com/opencv/opencv-python/blob/master/LICENSE-3RD-PARTY.txt),
[PyInstaller bundling exception](https://pyinstaller.org/en/stable/license.html),
[libusb v1.0.29 license](https://github.com/libusb/libusb/blob/v1.0.29/COPYING), and
[libuvc upstream notice](https://github.com/libuvc/libuvc/blob/master/LICENSE.txt).
For the delivered build, its exact vendored texts/wheel files take precedence over a
moving upstream branch. The owner should obtain appropriate review before distribution.

## Product routes to review

1. **Free-software Desktop:** GPLv3-compatible project terms are the straightforward
   candidate with the existing GPL PyQt wheels, subject to all component obligations.
   Distribution may be paid; commercial activity and proprietary licensing are different.
   Prepare corresponding source/build/install instructions and notices for recipients.
2. **Proprietary Desktop:** investigate commercial PyQt terms before distributing that
   product. Qt has its own licensing requirements; a PyQt purchase does not license
   every Qt module/codec. Do not treat this private GPL-wheel prototype as a ready
   proprietary deliverable. Verify all remaining components and delivery restrictions.
3. **Android:** consider permissive or copyleft project terms independently after checking
   contributor rights and native LGPL/BSD obligations. Keep exact libusb source/build
   correspondence and the ability to rebuild/replace/re-sign as applicable. There is no
   PyQt dependency in Android and no automatic need to embed Python.

No option is selected here. A format implementation in both languages does not force
identical product licensing, nor does that observation establish ownership of every file.

## Release ledger and remaining gates

The Desktop package's installed-distribution inventory is **not** a complete runtime SBOM.
It also lists build tools and does not alone identify every copied shared library. The
phase 5 byte/ELF audit enumerates actual binaries, their hashes and glibc requirements.
Python's runtime license is now explicitly required/copied. Still map all native codec,
Qt/plugin, numerical and platform libraries to matching notices/source obligations.

Before any public delivery: select product licenses, preserve all third-party notices,
produce complete corresponding sources/build inputs where required, review library
replacement/signing/installation constraints, generate final source/binary correspondence,
and check the actual signed installer/AAB. Don't add a guessed LICENSE or replace upstream
notices. Private prototypes, AppImage/Flatpak encapsulation and store acceptance do not
remove these obligations. Google Play privacy/signing declarations remain separate gates.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
