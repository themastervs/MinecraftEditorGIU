# Themastervs Wallpaper

A client-side **Fabric** mod for **Minecraft 1.21.11** that lets you set custom wallpapers (images or **mp4 video**) on the main menu, the multiplayer screen and the connecting screen, restyles the buttons to match the wallpaper, and gives you a menu to turn individual GUI elements on or off (logo, splash text, other mods' buttons, and more).

<!-- Add screenshots here: docs/menu.png, docs/elements.png -->
<img width="1920" height="1080" alt="Minecraft Screenshot 2026 11 08 - 15 46 36 12" src="https://github.com/user-attachments/assets/88f5ea5b-2d8a-489e-a7af-0fbaacb4e4a2" />
<img width="1920" height="1080" alt="Minecraft Screenshot 2026 11 08 - 15 46 40 19" src="https://github.com/user-attachments/assets/dde9b6d8-2d09-46f6-8cfb-d082c3783ea7" />
<img width="1920" height="1080" alt="Minecraft Screenshot 2026 11 08 - 15 46 21 36" src="https://github.com/user-attachments/assets/5e171ea1-974b-4d85-a6b3-f5deecc0ff30" />


## Features

- **Separate wallpaper per screen**: main menu, multiplayer and connecting screen each get their own.
- **Images and video**: `png`, `jpg`, `bmp`, `gif`, `mp4`, `webm`, `mkv`, `mov`, `avi`.
- **Adaptive button style**: the dominant color of the wallpaper is analyzed (and updated live for videos) and used to tint the buttons, so they always match.
- **Elements menu**: list everything the mod detects on a screen and toggle each element with one click. Hide the Minecraft logo, the yellow splash text, buttons added by other mods, extra textures or text, then bring them back whenever you want.
- **In-game picker**: no need to edit files by hand. Press **Fondos** on the main menu or multiplayer screen.
- **Fast video playback**: frames are sent from ffmpeg over a local socket straight into the texture memory, with triple buffering so the render thread only uploads the texture.
- A small `themastervs` credit is drawn in the bottom-right corner of the supported screens.

## Requirements

| Requirement | Notes |
|---|---|
| Minecraft | 1.21.11 |
| [Fabric Loader](https://fabricmc.net/use/) | 0.18.0 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required |
| Java | 21 |
| [ffmpeg](https://ffmpeg.org/download.html) + `ffprobe` | **only for video wallpapers**. Must be in your `PATH`, or set its path in `config.json`. Images work without it. |

## Installation

1. Install Fabric Loader and Fabric API for 1.21.11.
2. Put `themastervs-wallpaper-<version>.jar` in your `.minecraft/mods` folder.
3. Start the game once. The folder `.minecraft/config/themastervs/` is created.

## Usage

### Choosing wallpapers

1. Copy your images or videos into `.minecraft/config/themastervs/`.
2. In the main menu or the multiplayer screen, click **Fondos** (top-left).
3. You will see three rows: *Menu principal*, *Multijugador* and *Conectando*. Click a row to cycle through the files in the folder (the last option is "ninguno" = none). The change is saved and applied immediately.
4. Click **Hecho** to go back.

### Elements menu

1. Open **Fondos**, then **Elementos de esta pantalla**.
2. The list shows what the mod has detected on that screen: the Minecraft logo, the splash text, buttons, textures and text.
3. Click an entry to switch it `[ON]` / `[OFF]`.
4. **Solo vanilla** turns off every button that does not come from vanilla Minecraft (for example buttons added by other mods). **Activar todo** restores everything.
5. Changes are applied when you return to the screen. They are stored in `config/themastervs/elements.json`.

## Configuration

`config/themastervs/config.json`:

| Key | Default | Description |
|---|---|---|
| `ffmpeg` | `"ffmpeg"` | Command or full path to ffmpeg. `ffprobe` is looked up next to it. |
| `title` / `multiplayer` / `connecting` | `""` | File name (inside the config folder) or absolute path of each wallpaper. Normally set through the in-game picker. |
| `video_width` / `video_height` | `1920` / `1080` | **Maximum** video texture size. A video is never upscaled past its original size. |
| `video_fps` | `0` | `0` = use the video's own frame rate. Set a number to cap it (e.g. `30`). |
| `scale_flags` | `"bicubic"` | ffmpeg scaler used when a video is larger than the maximum. `bilinear` / `fast_bilinear` are faster. |
| `hwaccel` | `"auto"` (if the key is missing) | ffmpeg hardware decoding. Use `"none"` if it performs worse on your machine. |
| `title_keep` | `["menu.", "narrator.button.", "title."]` | Translation keys / texts treated as vanilla by **Solo vanilla**. Add entries here to keep a mod button (for example `"Mods"`). |

New keys are not added to an existing file automatically. Add them by hand or delete the file to regenerate it (you will lose your selections).

## Performance tips

- A video cannot play faster than its own frame rate. A 30 fps file will look like 30 fps.
- Every 5 seconds the log prints the real decode rate: `themastervs video: N fps` (search `logs/latest.log` for `themastervs`).
- If playback stutters: lower `video_width`/`video_height`, set `video_fps` to `30`, try `"scale_flags": "fast_bilinear"`, or try `"hwaccel": "none"`.
- 1080p at 60 fps moves roughly 500 MB/s of raw RGBA data, so it needs a reasonably fast CPU.
- Videos play without audio and loop. Decoding stops automatically when the screen is not visible.

## Building from source

Requirements: JDK 21.

Loom 1.14 needs **Gradle 9.2 or newer**. If your global Gradle is older, generate a wrapper from an empty folder and copy it into the project:

```powershell
mkdir wrapgen; cd wrapgen
gradle wrapper --gradle-version 9.2.1
Copy-Item -Recurse -Force gradlew, gradlew.bat, gradle ..\themastervs-wallpaper\
cd ..\themastervs-wallpaper
.\gradlew.bat build
```

On Linux/macOS use `./gradlew build`. The jar is written to `build/libs/` (use the one **without** the `-sources` suffix).

## Project layout

```
src/main/java/com/themastervs/wallpaper/
  WallpaperMod.java      entrypoint, wallpaper loading/rendering, button styling, picker screens
  Elements.java          detected-element registry and persistence
  mixin/
    GuiGraphicsMixin.java   intercepts textures/text so elements can be hidden
    LogoRendererMixin.java  Minecraft logo
    SplashRendererMixin.java splash text
src/main/resources/
  fabric.mod.json
  themastervs.mixins.json
```

## How it works

- **Wallpapers** are drawn with Fabric API's screen events right after the screen background, so they replace the panorama but stay under the buttons. Images are decoded once; videos are decoded by an external ffmpeg process.
- **Video transport**: ffmpeg writes raw RGBA frames to a loopback TCP socket. A reader thread reads each frame straight into the native memory of one of three textures, and the render thread only uploads the newest finished frame.
- **Button colors** are computed from a saturation-weighted average of the wallpaper pixels and smoothed over time.
- **Elements**: widgets are detected from the screen's button list. Textures and text are detected by mixins on `GuiGraphics` while a supported screen is being rendered. A hidden element simply has its draw call cancelled.

## Known limitations

- Targets **Minecraft 1.21.11 only**. Internal rendering APIs change often between versions.
- The elements menu can only hide things that are drawn through the methods the mod intercepts. Anything drawn another way will not show up in the list. The mixins are non-fatal: if a target is missing the game still starts, but that element cannot be hidden.
- The **Fondos** button is only available on the main menu and multiplayer screens (the connecting screen is configured from there).
- Videos have no audio.
- Other mods that heavily rewrite the same screens may conflict.

## Troubleshooting

| Problem | What to check |
|---|---|
| Video does not show | Run `ffmpeg -version` and `ffprobe -version` in a terminal. Look for `themastervs` in `logs/latest.log`. |
| "No se pudo lanzar ffmpeg" in the log | ffmpeg is not in the `PATH`. Set the full path in `config.json`. |
| Image does not load | Use a supported format; check the log for the file name. |
| Gradle error about Loom / plugin variants | Your Gradle is older than 9.2. Use a wrapper as described above. |

## License

MIT

## Credits

Made by **themastervs**. Built with [Fabric](https://fabricmc.net/) and [ffmpeg](https://ffmpeg.org/).
