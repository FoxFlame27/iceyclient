# Icey Client

A premium Minecraft launcher with an icy theme, built with Electron.

## Prerequisites

- **Node.js** 18+
- **Java** 17+ (required for launching Minecraft and installing Fabric)

## Install

```bash
npm install
```

## Run

```bash
npm start
```

## Build

```bash
npm run build
```

Builds are output to the `dist/` folder.

## Custom Assets

Drop your own images into `src/assets/` with these filenames:

| File | Purpose |
|------|---------|
| `homebg.png` | Home page background |
| `icon.png` | Logo displayed on home page |
| `fabric.png` | Fabric logo (no background) |
| `installbg.png` | Default installation card image |

## Launcher Data

Launcher data (installations, settings, logs) is stored separately from the app:

| OS | Path |
|----|------|
| Windows | `%APPDATA%\IceyClient\` |
| macOS | `~/Library/Application Support/IceyClient/` |
| Linux | `~/.iceyclient/` |

## Features

- Custom frameless window with icy theme
- Minecraft installation management (Vanilla + Fabric)
- Mod and resource pack browsing via Modrinth & CurseForge
- Drag-and-drop mod installation
- Configurable RAM, JVM args, themes, accent colors
- Session timer and play state management
- Cross-platform (Windows + macOS)
