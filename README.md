# LMThermal

Application thermique pour la caméra **Infiray HT-301 (T3-317-13)**.

Remplacement libre et open-source de l'application ThermViewer (abandonnée) et de l'app constructeur HTI (limitée).

## Objectif

Fournir un outil permettant de :
- Capturer le flux vidéo thermique en temps réel
- Accéder aux **données de température brutes** (mode Y16 via vendor commands USB)
- **Verrouiller une plage de température** (min/max fixe) pour des mesures cohérentes et comparables
- Sélectionner des zones / points de mesure
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
| Format brut | YUYV 4:2:2 @ 25 fps |
| Format température | Y16 (16-bit raw, via vendor commands) |

## Plan du projet

### Phase 1 — Communication USB & extraction température
- [ ] Installer `libusb` + bindings Python (`pyusb`)
- [ ] Tester les vendor commands identifiées (protocole InfiRay P2 Pro) :
  - `ctrl_transfer(0x41, 0x45, 0x78, ...)` = write
  - `ctrl_transfer(0xC1, 0x44, 0x78, ...)` = read
- [ ] Activer le mode `y16_preview` (commande `0x010a`) pour obtenir les données brutes 16-bit
- [ ] Convertir les valeurs Y16 en températures réelles (°C)
- [ ] Capturer et afficher une frame de température validée

### Phase 2 — Application desktop (MVP)
- [ ] Interface temps réel avec flux thermique
- [ ] Affichage de la température pointée (souris)
- [ ] Verrouillage de plage min/max (fixer la palette de couleur)
- [ ] Capture d'image avec overlay température
- [ ] Choix de la palette de couleurs (pseudo_color `0x8409`)

### Phase 3 — Outils de mesure
- [ ] Sélection de zones (rectangle, cercle, polygone)
- [ ] Mesures min/max/moyenne par zone
- [ ] Points de mesure multiples
- [ ] Export des données numériques (CSV)

### Phase 4 — Comparaison & analyse
- [ ] Galerie de captures sauvegardées
- [ ] Comparaison côte-à-côte (même plage forcée)
- [ ] Différence de température entre deux captures
- [ ] Annotations sur les images

### Phase 5 — Portage Android (futur)
- [ ] Évaluation de la faisabilité (libusb sur Android via USB host API)
- [ ] Prototype Android minimal

## Stack technique (proposée)

- **Langage** : Python 3 (prototypage rapide, Erwan connaît un peu)
- **Bibliothèques clés** :
  - `pyusb` — communication USB vendor commands
  - `opencv-python` — capture vidéo V4L2 + traitement d'image
  - `numpy` — manipulation des données Y16
  - `tkinter` ou `PyQt6` — interface graphique (à décider)
- **OS cible initial** : Linux (Archlinux confirmé fonctionnel)

## Références

- [Protocole InfiRay P2 Pro (reverse-engineered)](https://github.com/nicholasgasior/gopher-p2pro-ir) — commandes vendor USB documentées
- [ThermViewer](https://thermviewer.com/) — ancienne application (abandonnée)
- [HTI HT-301](https://hti-instrument.com/collections/infrared-thermal-imager/products/ht-301-mobile-phone-thermal-imager) — matériel

## Licence

À déterminer.
