# Depth Clock

A no-root Android **live wallpaper** that recreates Apple's lock-screen depth effect — the
clock is drawn *behind* the subject of your photo, so the subject pops out in front of the
digits. Adds subtle gyroscope **parallax** so the layers shift as you tilt the phone.

Built for the Pixel 10 Pro (works on any device with Android 8.0 / API 26+).

## Why home screen, not lock screen

On stock Android the system lock-screen clock always renders on top of the wallpaper layer,
and there's no non-root way to hide it. So a wallpaper physically **cannot** put your photo in
front of the real lock clock. This app draws its *own* clock inside the wallpaper, which means
it gives the true effect on the **home screen** (where there's no system clock). If you also
set it on the lock screen you'll see Android's clock on top of it — so set it on **Home screen**
in the wallpaper picker.

## How it works

1. You pick a photo in the setup screen.
2. On-device **ML Kit Subject Segmentation** cuts the subject out (nothing leaves the phone).
3. The wallpaper engine composites three layers every frame:
   `background photo → live clock → subject cut-out`.
   Because the cut-out is the same subject at the same position, the clock is occluded by the
   subject's silhouette — exactly like iOS.
4. The gyroscope offsets each layer by a different amount (background least, subject most) for
   a 3D tilt effect.

## Build (from your phone, via GitHub Actions)

No local toolchain or wrapper jar needed — CI builds it in the cloud and attaches the APK to a
`latest` release, same flow as Sideline.

1. Create a new GitHub repo (e.g. `PebbleYYC/DepthClock`).
2. Push these files to the `main` branch.
3. The **Build APK** workflow (`.github/workflows/build.yml`) runs on every push: it sets up
   JDK 17 + Android SDK + Gradle 8.9 and runs `gradle assembleDebug`.
4. When it finishes, grab **DepthClock-debug.apk** from the repo's **Releases → Latest build**,
   install it on your Pixel, and open **Depth Clock**.

You can also trigger it manually from the Actions tab (**workflow_dispatch**).

> The workflow invokes Gradle directly, so no `gradle-wrapper.jar` is committed. If you ever open
> this in a desktop IDE instead, run `gradle wrapper` once to generate it.

## Use

1. Install and open **Depth Clock**.
2. Tap **Choose photo** → pick a shot with a clear subject. Wait for the cut-out.
3. Tune clock colour, position, size; toggle seconds / date / parallax. The preview updates live.
4. Tap **Set as wallpaper** → choose **Home screen**.

## Tuning notes

- Segmentation quality depends on the photo — a clear foreground subject against a distinct
  background works best, same as iOS.
- Parallax amplitude, layer depth factors, and the easing constant are near the top of
  `DepthWallpaperService.kt` (`amp`, the `0.25 / 0.55 / 1.00` factors, and `0.12f` ease) if you
  want to push the effect harder or calmer.
- Clock typeface/shadow live in `ClockRenderer.kt`.

## Project layout

```
app/src/main/java/com/pebble/depthclock/
  ConfigActivity.kt        Compose setup UI + live preview
  DepthWallpaperService.kt WallpaperService engine: layering, parallax, frame loop
  ClockRenderer.kt         Shared clock drawing (preview + wallpaper use the same code)
  Segmenter.kt             ML Kit subject segmentation wrapper
  ImageUtils.kt            Decode / downscale / EXIF-orient / save
  Prefs.kt                 Config + file paths
```
