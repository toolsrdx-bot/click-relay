# Gorilla — Linux Desktop Client

For Mint, Ubuntu, Debian (X11 desktop sessions).

## Install

```bash
bash setup.sh
```

## Run

```bash
python3 client.py
```

## How It Works

- Small control window opens at top-left of screen.
- Click **Create Session** → relay returns a 6-digit code.
- Two floating draggable markers (**A**, **B**) appear — drag anywhere on screen.
- On Android: enter the code, press Volume Up = Cursor A click, Volume Down = Cursor B click.
- Clicks land in whatever app is under the marker (marker briefly hides during click).

## Configuration

Edit `client.py` — change `SERVER_URL` to point to your relay server.
