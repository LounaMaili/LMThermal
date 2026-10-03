# Desktop presentation references

Generated without camera access by `tools/generate_presentation_goldens.py`, using
Desktop `celsius_palette.py`, OpenCV/NumPy and existing sanitized matrix goldens.
`manifest.json` pins source/library versions and every numerical reference SHA-256.

- `palettes.argb`: five 256-entry little-endian uint32 ARGB tables in Desktop palette
  order (White hot, Black hot, Inferno, Iron-like/OpenCV HOT, Turbo).
- `*.levels`: all 110592 round-to-even uint8 indices from settled-room/hand matrices,
  Auto (2nd/98th percentile, minimum 1 °C span) or exact Locked 25–45 °C bounds.
- `ranges.properties`: Double percentile/reference bounds for both real matrices
  and synthetic linear/constant/near-constant/outlier cases.

The generated Kotlin tables embed only the three color maps. These are display-only
numeric maps; OpenCV/Python and reference files are not runtime app dependencies.
Full-pixel color comparisons combine these independently exported levels/tables.
Git binary attributes preserve palette/level bytes. Original matrix provenance and
hashes remain in `../thermometry/manifest.json`; no new scene captures were added.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
