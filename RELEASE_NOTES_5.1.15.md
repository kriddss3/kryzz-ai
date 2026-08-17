# Kryzz AI 5.1.15 — new monochrome K-monogram across the app

## Branding refresh

- The supplied white K-monogram is now the **Kryzz AI mark** across every branded surface:
  - Launcher icon (adaptive, both `ic_launcher` and `ic_launcher_round`)
  - Android 13+ themed icon (monochrome slot included)
  - Pre-Android 8 fallback bitmap (JPG)
  - Splash-screen animated icon
  - In-app `KryzzMark` composable
  - In-app `KryzzWordmark` lockup
- Silhouettes are stored as transparent RGBA so the in-app tint layer can recolour them.

## Light-mode inversion

- **Night mode** (existing behaviour): white K on Obsidian near-black `#050506` background.
- **Light mode** (new): **black K** on paper-tone `#F4F4F1` background, mirrored in:
  - `kryzz_launcher_foreground_dark.png` referenced from the default (non-night) `mipmap-anydpi-v26` / `mipmap-anydpi-v33` launcher XMLs.
  - `kryzz_mark_foreground_dark.png` selected by `KryzzMark` when `MaterialTheme.colorScheme.background.luminance() < 0.45f` is false.
  - `values/icon_colors.xml` overriding `kryzz_icon_background` to the paper tone; `values-night/icon_colors.xml` keeps the Obsidian tone.
  - `values-v31/themes.xml` (light) using the paper-tone splash background + dark mark; `values-night-v31/themes.xml` unchanged.
  - `mipmap-night-anydpi-v26` / `mipmap-night-anydpi-v33` provide the night-mode launcher XMLs so the system resolves the right variant on theme change.

## Test contract

- `UiReviewContractTest.launcherUsesAdaptiveRoundAndThemedKryzzArtwork` updated to assert the day-mode adaptive icon references `@drawable/kryzz_launcher_foreground_dark` (the black silhouette). PNG dimensions and RGBA colour type are unchanged.

## Version

- Version name: 5.1.15
- Version code: 59