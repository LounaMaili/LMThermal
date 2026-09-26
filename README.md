# LMThermal

> **Measurement status (2026-09-26):** Real HT-301 frames confirm 288 thermal
> image rows followed by four non-image trailer rows. The last 514 bytes are
> only part of that trailer. Native disassembly identifies field 356 as a
> duplicated calibration coefficient and locates the app's live center index
> in row 288. Saved Linux image words exceed the app's 14-bit lookup range,
> so per-pixel Celsius remains unvalidated. See
> [the native call chain](docs/NATIVE_CALL_CHAIN.md) and
> [research inventory](docs/RESEARCH_INVENTORY.md). The desktop repository
> holds capture evidence in `docs/MEASUREMENT_AUDIT.md` and `tests/fixtures/`.

Application thermique pour la caméra **Infiray HT-301 (T3-317-13)**.

Remplacement libre et open-source de l'application ThermViewer (abandonnée) et de l'app constructeur HTI (limitée).

## Objectif

Fournir un outil permettant de :
- Capturer le flux vidéo thermique en temps réel
- Accéder aux **données de température brutes** (données embarquées dans chaque frame)
- **Verrouiller une plage de température** (min/max fixe) pour des mesures cohérentes et comparables
- Sélectionner des points de mesure
- Exporter des images avec overlay de température
- Comparer des captures entre elles

## Matériel supporté

| Info | Valeur |
|------|--------|
| Caméra | Infiray HT-301 (T3-317-13) |
| USB Vendor ID | 0x1514 (Infiray) |
| USB Product ID | 0x0001 |
| Interface vidéo | UVC standard (driver `uvcvideo`) |
| Résolution | 384×292 |
| Image thermique exploitable | 384×288 (lignes 0–287) |
| Format vidéo | YUYV 4:2:2 @ 25 fps |
| Plage de mesure annoncée | -20°C à +400°C (non vérifiée ici) |
| Précision annoncée | ±3°C (non vérifiée ici) |

## Découvertes clés

### Architecture du flux de données

```
USB Camera (UVC/YUYV 384×292 @ 25fps)
  │
  ├─ Bytes 0-221,183 : Image thermique YUYV (288 lignes)
  │   └─ Y channel (0-255) = display brightness in saved Linux frames
  │      └─ Native lookup requires 14-bit-compatible words, not these Y bytes
  │
  ├─ Bytes 221,184-223,741 : données non-image
  └─ Bytes 223,742-224,255 : bloc de paramètres (514 bytes)
      ├─ Correction, reflected and ambient temperatures, humidity, emissivity, distance
      ├─ Copies of five calibration coefficients from the earlier trailer
      └─ Field 356: copied calibration coefficient, not live center temperature
```

### Formule de température (GetTempEvn)

The native caller provides a lookup-derived value, a radiation term, and an
inverse correction factor. This formula does not accept an 8-bit Y pixel:

```python
def get_temp_evn(a, env_term, b):
    """Decoded arithmetic; a is lookup-derived, not a display Y byte."""
    val = (a + 273.15) ** 4.0 - env_term
    val = b * val
    return val ** 0.25 - 273.15
```

### Paramètres embarqués (514 bytes, fin de chaque frame)

| Offset | Exemple | Description |
|--------|---------|-------------|
| 4 | 25.0 | Reflected temperature |
| 8 | 25.0 | Ambient temperature |
| 12 | 0.45 | Humidity |
| 16 | 0.98 | Emissivity |
| 20 | 1 | Distance (uint16) |
| 352 | ~0.27 | Copy of calibration coefficient at byte 223494 |
| **356** | **~36.0** | **Copy of calibration coefficient at byte 223498** |
| 364 | ~0.006 | Copy of calibration coefficient at byte 223506 |
| 368 | ~0.82 | Copy of calibration coefficient at byte 223510 |

## Plan du projet

### Phase 1 — Communication & température (mesure à valider)
- [x] Caméra détectée nativement sur Linux (uvcvideo)
- [x] Flux YUYV capturable via OpenCV + V4L2
- [x] APK constructeur décompilé et analysé
- [x] Java/JNI/native lookup path and source of its parameters traced
- [ ] Camera mode transition to 14-bit radiometric image words validated
- [x] Paramètres de température extraits des frames (514 bytes, fin de frame)
- [x] `GetTempEvn()` arithmetic and native caller arguments traced
- [x] `InitTempParam()` décodé — calcul des paramètres de calibration
- [x] 27 constantes `.rodata` extraites (float32/float64)
- [x] Prototype Python expérimental (`prototype/thermal_capture.py`), sans mesure par pixel validée
- [x] `CalcFixRaw()` normal-path arithmetic and five caller inputs traced
- [x] Field 356 identified as a copied calibration coefficient in saved frames
- [ ] Native lookup outputs compared with controlled camera/app readings

### Phase 2 — Application desktop (MVP)
- [ ] Interface temps réel avec flux thermique
- [ ] Affichage de la température pointée (souris)
- [ ] Verrouillage de plage min/max (fixer la palette de couleur)
- [ ] Capture d'image avec overlay température
- [ ] Choix de la palette de couleurs

### Phase 3 — Outils de mesure
- [ ] Points de mesure multiples
- [ ] Mesures min/max/moyenne par pixel
- [ ] Export des données numériques (CSV)

### Phase 4 — Enregistrement vidéo
- [ ] Capture de séquences vidéo thermiques
- [ ] Embedding des données radiométriques dans chaque frame
- [ ] Export vidéo (radiométrique + MP4 visuel)

### Phase 5 — Comparaison & analyse
- [ ] Galerie de captures (photos + vidéos)
- [ ] Comparaison côte-à-côte (même plage forcée)
- [ ] Différence de température entre deux captures
- [ ] Annotations (texte, flèches, zones d'intérêt)

### Phase 6 — Portage Android
- [ ] Prototype Android avec USB Host API
- [ ] Interface tactile adaptée
- [ ] Build & distribution (APK)

## Stack technique

- **Langage** : Python 3 (prototypage Phase 1-2)
- **Bibliothèques** : `opencv-python`, `numpy`, `pyusb`
- **OS cible initial** : Linux (Archlinux confirmé fonctionnel)
- **Cible finale** : Android (Flutter ou Kotlin natif)

## Documentation

- [`docs/HARDWARE.md`](docs/HARDWARE.md) — Documentation technique du matériel
- [`docs/SPECIFICATION.md`](docs/SPECIFICATION.md) — Spécification des fonctionnalités
- [`docs/APK_ANALYSIS.md`](docs/APK_ANALYSIS.md) — Analyse du reverse engineering de l'APK
- [`docs/THERMOMETRY_LIB.md`](docs/THERMOMETRY_LIB.md) — Documentation de `libthermometry.so` (formules décodées)

## Références

- [Protocole InfiRay P2 Pro (reverse-engineered)](https://github.com/nicholasgasior/gopher-p2pro-ir) — commandes vendor USB documentées (ne s'applique pas directement au HT-301)
- [ftobler/infiray_p2_pro_python](https://github.com/ftobler/infiray_p2_pro_python) — approche P2 Pro (non compatible HT-301)
- [ThermViewer](https://thermviewer.com/) — ancienne application (abandonnée)
- [HTI HT-301](https://hti-instrument.com/collections/infrared-thermal-imager/products/ht-301-mobile-phone-thermal-imager) — matériel

## Licence

À déterminer.
