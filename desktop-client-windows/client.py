"""Gorilla Click — Windows desktop client. No account needed, just room credentials."""
import asyncio, ctypes, json, threading, tkinter as tk

SERVER_HOST = "103.164.3.212"
WS_URL      = f"ws://{SERVER_HOST}:8080"

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
        tk.Label(self.root, text="Join a Room", font=('Arial',10),
                 bg=BG, fg=SUB).pack(pady=(0,18))
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

    def _submit(self):
        cu, rp, dn = self.f_cu.get().strip(), self.f_rp.get(), self.f_dn.get().strip()
        if not all([cu, rp, dn]):
            self.err.config(text='All fields are required.'); return
        self.result = (cu, rp, dn)
        self.root.destroy()

    def run(self):
        self.root.mainloop(); return self.result

class DesktopClient:
    def __init__(self, ctrl_user, room_pass, device_name):
        self.ctrl_user   = ctrl_user
        self.room_pass   = room_pass
        self.device_name = device_name
        self.ws          = None
        self.loop        = None
        self._connected  = False

        self.root = tk.Tk()
        self.root.title("Gorilla Click")
        self.root.geometry("380x200+40+40")
        self.root.configure(bg=BG)
        self.root.attributes('-topmost', True)
        self._build_ui()
        self._spawn_cursors()
        self._start_loop()

    def _build_ui(self):
        # Header row
        top = tk.Frame(self.root, bg=BG)
        top.pack(fill='x', padx=14, pady=(12,0))
        tk.Label(top, text="Gorilla Click", font=('Arial',14,'bold'),
                 bg=BG, fg=TEXT).pack(side='left')
        self.status_lbl = tk.Label(top, text="● Connecting…",
                                   font=('Arial',10), bg=BG, fg=AMB)
        self.status_lbl.pack(side='right')

        # Info bar
        info = tk.Frame(self.root, bg=SURF, pady=6)
        info.pack(fill='x', padx=14, pady=6)
        tk.Label(info, text=f"Room: {self.ctrl_user}   Device: {self.device_name}",
                 bg=SURF, fg=TEXT, font=('Arial',9)).pack(padx=10)

        # Log line
        self.log_var = tk.StringVar(value="Drag A and B markers anywhere on screen.")
        tk.Label(self.root, textvariable=self.log_var, font=('Arial',8),
                 bg=BG, fg=SUB, wraplength=360).pack(pady=(0,4))

        # Disconnect / Reconnect buttons
        btn_row = tk.Frame(self.root, bg=BG)
        btn_row.pack(fill='x', padx=14, pady=4)

        self.btn_disconnect = tk.Button(
            btn_row, text="Disconnect", command=self._disconnect,
            bg=RED, fg=BG, font=('Arial',9,'bold'), relief='flat', pady=6, cursor='hand2')
        self.btn_disconnect.pack(side='left', fill='x', expand=True, padx=(0,4))

        self.btn_reconnect = tk.Button(
            btn_row, text="Reconnect", command=self._reconnect,
            bg=CYAN, fg=BG, font=('Arial',9,'bold'), relief='flat', pady=6, cursor='hand2')
        self.btn_reconnect.pack(side='left', fill='x', expand=True, padx=(4,0))
        self.btn_reconnect.pack_forget()  # hidden until disconnected

    def _spawn_cursors(self):
        self.cursor_a = FloatingCursor(self.root, 'A', '#1A6FD4', 300, 300)
        self.cursor_b = FloatingCursor(self.root, 'B', '#C0392B', 500, 300)

    def _start_loop(self):
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_forever, daemon=True).start()
        asyncio.run_coroutine_threadsafe(self._connect(), self.loop)

    async def _connect(self):
        self.root.after(0, lambda: self.status_lbl.config(text='● Connecting…', fg=AMB))
        try:
            self.ws = await websockets.connect(WS_URL, ping_interval=20, ping_timeout=10)
            await self.ws.send(json.dumps({
                'type': 'join_room',
                'controllerUsername': self.ctrl_user,
                'roomPassword': self.room_pass,
                'deviceName': self.device_name,
            }))
            async for raw in self.ws:
                self._handle(json.loads(raw))
        except Exception as e:
            self._log(f'Disconnected: {e}')
        finally:
            self._connected = False
            self.root.after(0, self._show_reconnect)

    def _handle(self, msg):
        t = msg.get('type')
        if t == 'room_joined':
            self._connected = True
            self.root.after(0, lambda: (
                self.status_lbl.config(text='● Connected', fg=GRN),
                self.btn_reconnect.pack_forget(),
                self.btn_disconnect.pack(side='left', fill='x', expand=True, padx=(0,4)),
            ))
            self._log(f"In {self.ctrl_user}'s room. Ready.")
        elif t == 'click':
            c = msg.get('cursor')
            x, y = (self.cursor_a if c == 'A' else self.cursor_b).click()
            self._log(f'Click {c} at ({x},{y})')
        elif t == 'controller_disconnected':
            self.root.after(0, lambda: self.status_lbl.config(text='● Controller offline', fg=AMB))
        elif t == 'error':
            self._log(f"Error: {msg.get('message')}")

    def _log(self, text):
        self.root.after(0, lambda: self.log_var.set(text))

    def _disconnect(self):
        if self.ws:
            asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
        self.root.after(0, self._show_reconnect)

    def _show_reconnect(self):
        self.status_lbl.config(text='● Disconnected', fg=RED)
        self.btn_disconnect.pack_forget()
        self.btn_reconnect.pack(side='left', fill='x', expand=True, padx=(4,0))

    def _reconnect(self):
        self.btn_reconnect.pack_forget()
        self.btn_disconnect.pack(side='left', fill='x', expand=True, padx=(0,4))
        asyncio.run_coroutine_threadsafe(self._connect(), self.loop)

    def run(self):
        self.root.mainloop()

if __name__ == '__main__':
    result = JoinWindow().run()
    if result:
        DesktopClient(*result).run()
