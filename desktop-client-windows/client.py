"""Gorilla Click — Windows desktop client. No account needed, just room credentials."""
import asyncio, ctypes, json, ssl, threading, tkinter as tk
from tkinter import messagebox
import urllib.request

SERVER_HOST    = "103.164.3.212"
SERVER_PORT    = 8080
WS_URL         = f"wss://{SERVER_HOST}:{SERVER_PORT}"
HTTP_BASE      = f"https://{SERVER_HOST}:{SERVER_PORT}"
CLIENT_VERSION = "1.1.0"

# Palette
BG   = "#0D1117"
SURF = "#161B22"
CARD = "#1C2333"
CYAN = "#4FC3F7"
RED  = "#FF7B72"
GRN  = "#56D364"
TEXT = "#E6EDF3"
SUB  = "#6E7681"
AMB  = "#FFA657"

try:
    import websockets
except ImportError:
    raise SystemExit("pip install websockets")

try:
    import pystray
    from PIL import Image, ImageDraw
    TRAY_OK = True
except ImportError:
    TRAY_OK = False

def make_ssl():
    import pathlib
    cert_path = pathlib.Path(__file__).parent / "server_cert.crt"
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    if cert_path.exists():
        ctx.load_verify_locations(str(cert_path))
    else:
        ctx.verify_mode = ssl.CERT_NONE
    return ctx

SSL_CTX = make_ssl()

def check_version():
    try:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
        with urllib.request.urlopen(f"{HTTP_BASE}/version", context=ctx, timeout=4) as r:
            data = json.loads(r.read())
        sv = data.get("version", "")
        if sv and sv != CLIENT_VERSION:
            return f"Update available: server v{sv}  (you have v{CLIENT_VERSION})"
    except Exception:
        pass
    return ""

MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP   = 0x0004

def inject_click(x: int, y: int) -> None:
    user32 = ctypes.windll.user32
    user32.SetCursorPos(int(x), int(y))
    user32.mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)
    user32.mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)

class FloatingCursor:
    def __init__(self, parent, name, color, x=200, y=200, size=36):
        self.size = size
        self.win  = tk.Toplevel(parent)
        self.win.overrideredirect(True)
        self.win.attributes('-topmost', True)
        self.win.attributes('-alpha', 0.88)
        self.win.geometry(f'{size}x{size}+{x}+{y}')
        self.win.configure(bg=color)
        c = tk.Canvas(self.win, width=size, height=size, bg=color,
                      highlightthickness=2, highlightbackground='#E6EDF3')
        c.pack()
        c.create_text(size//2, size//2, text=name, fill='white', font=('Arial',14,'bold'))
        c.bind('<ButtonPress-1>', lambda e: setattr(self,'_dx',e.x_root-self.win.winfo_x()) or setattr(self,'_dy',e.y_root-self.win.winfo_y()))
        c.bind('<B1-Motion>',     lambda e: self.win.geometry(f'+{e.x_root-self._dx}+{e.y_root-self._dy}'))
        self._dx = self._dy = 0

    def click(self):
        x = self.win.winfo_x() + self.size//2
        y = self.win.winfo_y() + self.size//2
        self.win.withdraw(); self.win.update()
        inject_click(x, y)
        self.win.after(80, self.win.deiconify)
        return x, y

class JoinWindow:
    def __init__(self):
        self.root   = tk.Tk()
        self.root.title("Gorilla Click — Join Room")
        self.root.geometry("360x320")
        self.root.configure(bg=BG)
        self.root.resizable(True, True)
        self.result = None
        self._build()

    def _field(self, label, show=''):
        tk.Label(self.root, text=label, bg=BG, fg=SUB,
                 font=('Arial',9), anchor='w').pack(fill='x', padx=32)
        e = tk.Entry(self.root, show=show, bg=SURF, fg=TEXT,
                     insertbackground=CYAN, relief='flat', font=('Arial',11),
                     highlightthickness=1, highlightcolor=CYAN, highlightbackground=CARD)
        e.pack(fill='x', padx=32, pady=(2,12), ipady=7)
        return e

    def _build(self):
        tk.Label(self.root, text="Gorilla Click", font=('Arial',20,'bold'),
                 bg=BG, fg=TEXT).pack(pady=(28,2))
        tk.Label(self.root, text=f"Join a Room  (v{CLIENT_VERSION})", font=('Arial',10),
                 bg=BG, fg=SUB).pack(pady=(0,18))
        threading.Thread(target=self._check_ver, daemon=True).start()
        self.f_cu = self._field("Controller Username")
        self.f_rp = self._field("Room Password", show='•')
        self.f_dn = self._field("Device Name  (e.g. Office PC)")
        self.err  = tk.Label(self.root, text='', bg=BG, fg=RED, font=('Arial',9))
        self.err.pack()
        btn = tk.Button(self.root, text="Connect", command=self._submit,
                        bg=CYAN, fg=BG, font=('Arial',11,'bold'),
                        relief='flat', pady=10, cursor='hand2')
        btn.pack(fill='x', padx=32, pady=(6,0))
        self.root.bind('<Return>', lambda _: self._submit())

    def _check_ver(self):
        msg = check_version()
        if msg:
            self.root.after(0, lambda: self.err.config(text=msg, fg=AMB))

    def _submit(self):
        cu, rp, dn = self.f_cu.get().strip(), self.f_rp.get(), self.f_dn.get().strip()
        if not all([cu, rp, dn]):
            self.err.config(text='All fields are required.'); return
        self.result = (cu, rp, dn)
        self.root.destroy()

    def run(self):
        self.root.mainloop(); return self.result

MAX_RETRIES   = 20   # ~1 minute of retries (3s apart)
RETRY_DELAY_S = 3

class DesktopClient:
    def __init__(self, ctrl_user, room_pass, device_name):
        self.ctrl_user        = ctrl_user
        self.room_pass        = room_pass
        self.device_name      = device_name
        self.ws               = None
        self.loop             = None
        self._connected       = False
        self._user_disconnect = False
        self._retry_count     = 0

        self.root = tk.Tk()
        self.root.title("Gorilla Click")
        self.root.geometry("380x210+40+40")
        self.root.configure(bg=BG)
        self.root.attributes('-topmost', True)
        self._build_ui()
        self._spawn_cursors()
        self._start_loop()
        self._setup_tray()
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        threading.Thread(target=self._version_check_bg, daemon=True).start()

    def _build_ui(self):
        # Header row
        top = tk.Frame(self.root, bg=BG)
        top.pack(fill='x', padx=14, pady=(12,0))
        tk.Label(top, text="Gorilla Click", font=('Arial',14,'bold'),
                 bg=BG, fg=TEXT).pack(side='left')

        # Switch Room button — always visible at top right
        tk.Button(top, text="⇄ Switch Room", command=self._switch_room,
                  bg=AMB, fg=BG, font=('Arial',9,'bold'),
                  relief='flat', padx=10, pady=4, cursor='hand2').pack(side='right')

        self.status_lbl = tk.Label(top, text="● Connecting…",
                                   font=('Arial',10), bg=BG, fg=AMB)
        self.status_lbl.pack(side='right', padx=(0, 10))

        # Info bar
        info = tk.Frame(self.root, bg=SURF, pady=6)
        info.pack(fill='x', padx=14, pady=6)
        tk.Label(info, text=f"Room: {self.ctrl_user}   Device: {self.device_name}",
                 bg=SURF, fg=TEXT, font=('Arial',9)).pack(padx=10)

        # Log line
        self.log_var = tk.StringVar(value="Drag A and B markers anywhere on screen.")
        tk.Label(self.root, textvariable=self.log_var, font=('Arial',8),
                 bg=BG, fg=SUB, wraplength=360).pack(pady=(0,4))

        # Bottom action row (Disconnect or Reconnect)
        self.btn_row = tk.Frame(self.root, bg=BG)
        self.btn_row.pack(fill='x', padx=14, pady=4)

        self.btn_disconnect = tk.Button(
            self.btn_row, text="Disconnect", command=self._disconnect,
            bg=RED, fg=BG, font=('Arial',9,'bold'), relief='flat', pady=6, cursor='hand2')
        self.btn_reconnect = tk.Button(
            self.btn_row, text="Reconnect", command=self._reconnect,
            bg=CYAN, fg=BG, font=('Arial',9,'bold'), relief='flat', pady=6, cursor='hand2')
        self._set_buttons_connected()

    def _spawn_cursors(self):
        self.cursor_a = FloatingCursor(self.root, 'A', '#1A6FD4', 300, 300)
        self.cursor_b = FloatingCursor(self.root, 'B', '#C0392B', 500, 300)

    def _start_loop(self):
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_forever, daemon=True).start()
        asyncio.run_coroutine_threadsafe(self._connect(), self.loop)

    async def _connect(self):
        self.root.after(0, lambda: (
            self.status_lbl.config(text='● Connecting…', fg=AMB),
            self._set_buttons_connected(),
        ))
        try:
            self.ws = await websockets.connect(
                WS_URL, ssl=SSL_CTX, ping_interval=15, ping_timeout=8)
            await self.ws.send(json.dumps({
                'type': 'join_room',
                'controllerUsername': self.ctrl_user,
                'roomPassword': self.room_pass,
                'deviceName': self.device_name,
            }))
            async for raw in self.ws:
                self._handle(json.loads(raw))
        except Exception as e:
            self._log(f'Connection lost: {e}')
        finally:
            self.ws = None
            self._connected = False
            if self._user_disconnect:
                self.root.after(0, self._show_reconnect_state)
            else:
                if self._retry_count < MAX_RETRIES:
                    self._retry_count += 1
                    self.root.after(0, lambda: self.status_lbl.config(
                        text=f'● Retrying {self._retry_count}/{MAX_RETRIES}…', fg=AMB))
                    await asyncio.sleep(RETRY_DELAY_S)
                    if not self._user_disconnect:
                        asyncio.run_coroutine_threadsafe(self._connect(), self.loop)
                else:
                    self.root.after(0, self._show_reconnect_state)

    def _handle(self, msg):
        t = msg.get('type')
        if t == 'room_joined':
            self._connected = True
            self._retry_count = 0
            self.root.after(0, lambda: (
                self.status_lbl.config(text='● Connected', fg=GRN),
                self._set_buttons_connected(),
            ))
            self._log(f"In {self.ctrl_user}'s room. Ready.")
        elif t == 'click':
            c = msg.get('cursor')
            x, y = (self.cursor_a if c == 'A' else self.cursor_b).click()
            self._log(f'Click {c} at ({x},{y})')
            if self.ws and msg.get('timestamp'):
                asyncio.run_coroutine_threadsafe(
                    self.ws.send(json.dumps({'type': 'click_ack', 'timestamp': msg['timestamp']})),
                    self.loop)
        elif t == 'controller_disconnected':
            # Room is gone — close WS and switch UI to disconnected state
            self._log('Controller closed the room.')
            if self.ws:
                asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
        elif t == 'error':
            self._log(f"Error: {msg.get('message')}")
            # If we never joined (e.g. room not open yet), close WS so the
            # auto-retry loop kicks in and tries again until the room exists.
            if not self._connected and self.ws:
                asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)

    def _log(self, text):
        self.root.after(0, lambda: self.log_var.set(text))

    def _setup_tray(self):
        if not TRAY_OK:
            return
        img = Image.new('RGB', (64, 64), color='#0D1117')
        d = ImageDraw.Draw(img)
        d.ellipse([12,12,52,52], fill='#4FC3F7')
        menu = pystray.Menu(
            pystray.MenuItem('Show / Hide', self._toggle_window, default=True),
            pystray.MenuItem('Disconnect', lambda: self.root.after(0, self._disconnect)),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem('Quit', lambda: self.root.after(0, self._quit)),
        )
        self._tray = pystray.Icon('Gorilla Click', img, 'Gorilla Click', menu)
        threading.Thread(target=self._tray.run, daemon=True).start()

    def _toggle_window(self):
        if self.root.state() == 'withdrawn':
            self.root.after(0, self.root.deiconify)
        else:
            self.root.after(0, self.root.withdraw)

    def _on_close(self):
        if TRAY_OK:
            self.root.withdraw()
        else:
            self._quit()

    def _quit(self):
        self._user_disconnect = True
        if self.ws:
            asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
        if TRAY_OK and hasattr(self, '_tray'):
            self._tray.stop()
        self.root.destroy()

    def _version_check_bg(self):
        msg = check_version()
        if msg:
            self.root.after(0, lambda: messagebox.showinfo('Gorilla Update', msg))

    def _set_buttons_connected(self):
        self.btn_reconnect.pack_forget()
        self.btn_disconnect.pack(in_=self.btn_row, fill='x', expand=True)

    def _show_reconnect_state(self):
        self.status_lbl.config(text='● Disconnected', fg=RED)
        self.btn_disconnect.pack_forget()
        self.btn_reconnect.pack(in_=self.btn_row, fill='x', expand=True)

    def _disconnect(self):
        self._user_disconnect = True
        if self.ws:
            asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
        self.root.after(0, self._show_reconnect_state)

    def _reconnect(self):
        self._user_disconnect = False
        self._retry_count = 0
        asyncio.run_coroutine_threadsafe(self._connect(), self.loop)

    def _switch_room(self):
        self._user_disconnect = True
        self.switch_requested = True
        if self.ws:
            asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
        self.root.after(50, self.root.destroy)

    def run(self):
        self.switch_requested = False
        self.root.mainloop()
        return self.switch_requested

if __name__ == '__main__':
    while True:
        result = JoinWindow().run()
        if not result:
            break
        client = DesktopClient(*result)
        if not client.run():
            break
