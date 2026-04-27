# Gorilla — Windows Desktop Client

For Windows 10 / 11.

You have **three ways** to run/distribute this client:

| Mode | What user does | Build step needed |
|---|---|---|
| **A. Run from source** | `python client.py` | None |
| **B. Single .exe** | Double-click `Gorilla.exe` | `build.bat` (one time) |
| **C. Installer** | Run `GorillaSetup.exe` → Next, Next, Finish | `build.bat` + Inno Setup |

---

## A. Run From Source (Dev Mode)

1. Install **Python 3.10+** from https://www.python.org/downloads/ — check **"Add Python to PATH"**
2. Double-click `setup.bat` to install the websockets package
3. Run: `python client.py`

---

## B. Build a Single `.exe`

End users won't need Python — they just get one file.

1. Install Python (one-time, on the build machine)
2. Double-click `build.bat`
3. Wait 1-2 minutes
4. Result: `dist\Gorilla.exe` — give this file to anyone

The `.exe` is ~15-25 MB, includes the Python interpreter and all dependencies.

---

## C. Build a Full Installer (.exe Setup Wizard)

For the most polished distribution — Start Menu shortcut, uninstaller, optional auto-startup.

1. Run `build.bat` first to create `dist\Gorilla.exe`
2. Install **Inno Setup** (free): https://jrsoftware.org/isinfo.php
3. Open `installer.iss` in Inno Setup Compiler
4. Click **Build → Compile**
5. Result: `Output\GorillaSetup.exe` — single installer file

Users run the installer once → app appears in Start Menu, can be uninstalled normally.

---

## Configuration

Before building, edit `client.py` and set your relay server:

```python
SERVER_URL = "ws://YOUR_VM_IP:8080"
```

Then rebuild.

---

## How It Works

- Small control window opens at top-left.
- Click **Create Session** → 6-digit code.
- Drag the **A** and **B** floating markers anywhere on screen.
- On Android: enter code, press Volume Up/Down → real mouse clicks at A/B in any Windows app.

Uses native Win32 `SendInput` via `ctypes` — no external runtime DLLs needed.
