# ClipCutter

Pick a long video, mark multiple clip ranges on the timeline, and batch-export them all
at once (max 720p, real H.264, ready to manually upload to Instagram/YouTube).

## How to use this repo

1. Create a new GitHub repository and push everything in this folder to it.
2. Go to the **Actions** tab — the workflow (`Build ClipCutter APK`) runs automatically
   on push, or you can trigger it manually via "Run workflow".
3. **This first run will likely take 1-3 hours** and may fail. It's compiling FFmpeg
   from source (not just building the app) because that's the only free way to get
   real H.264 hardware encoding — the free prebuilt library only supports VP9/AV1.
4. When it finishes (success or fail), open the run and download the artifact:
   - `ClipCutter-debug-apk` — the installable APK, if the build succeeded.
   - `ffmpeg-kit-build-logs` — only appears if it failed; paste the relevant error
     back to me and I'll help fix the workflow.
5. Install the APK on your phone (you'll need to allow "install from unknown sources"
   since it's unsigned).

## What the app does

- **Pick Video** — opens your file picker, works with mp4/mov/mkv/3gp and more.
- Scrub the timeline, use **Mark Start** / **Mark End** to select a range, then
  **Add Clip to List** — repeat for as many clips as you want from the same video.
- **Export All Clips** — asks for a name (e.g. your podcast name), then exports every
  marked clip as a separate 720p H.264 .mp4 file named `{name} clip 1.mp4`, `{name} clip 2.mp4`, etc.
- Files are saved to the app's folder under `Android/data/com.xyz.clipcutter/files/Movies/`
  on your device (visible in any file manager). A **Share** option pops up after export
  so you can hand a clip straight to Instagram/YouTube's share sheet.
- No captions, no auto-upload — those are deliberately left out of this version, per
  the current scope.

## Known risk points for this first build (read before reporting a failure)

Compiling FFmpeg's native libraries from source is genuinely fragile. If the workflow
fails, it's almost certainly one of these:

1. **Disk space** — full native builds are large; the cleanup step frees some space but
   GitHub's free runners only have ~14GB usable. If this is the failure, we shrink the
   build further (we already limit to arm64-v8a only and skip `--full`).
2. **NDK/SDK path mismatch** — the workflow assumes `ANDROID_SDK_ROOT` is already set
   on the runner (it usually is). If `sdkmanager` isn't found at the expected path,
   that step needs adjusting.
3. **The AAR isn't where we expect** — the "Locate the built AAR" step searches the
   whole `ffmpeg-kit-src` folder for any `.aar` file, but if `android.sh` names or
   places it differently than expected, we'll need to check the actual build log.
4. **Missing apt package** — native build scripts sometimes need one more obscure
   tool than documented; the error message in the log will name it directly.

None of these are dead ends — they're normal for a first pass at a from-source native
build. Send me the failing step's log output and we'll patch the workflow.

## If you'd rather skip the native build entirely

If the from-source build turns out to be too flaky to get working, the fallback is:
- Switch `app/build.gradle` and the workflow to use the free prebuilt library
  (`dev.ffmpegkit-maintained:ffmpeg:8.1.7` from Maven Central) with VP9 encoding
  instead of H.264 — no CI build step needed, but exports as `.webm`, which YouTube
  accepts fine and Instagram may not.
- Or buy the $24 one-time Basic tier AAR from the library maintainer, which includes
  real H.264 hardware encoding as a direct drop-in dependency, no source compile needed.
