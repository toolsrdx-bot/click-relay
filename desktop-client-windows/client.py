"""Gorilla Click — Windows desktop client with login and room join.
Uses Win32 SendInput via ctypes. No external deps beyond `websockets`.
"""
import asyncio, ctypes, json, threading, tkinter as tk
import urllib.request, urllib.error

SERVER_HOST = "103.164.3.212"
HTTP_URL    = f"http://{SERVER_HOST}:8080"
WS_URL      = f"ws://{SERVER_HOST}:8080"

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
        self.win.attributes('-alpha', 0.85)
        self.win.geometry(f'{size}x{size}+{x}+{y}')
        self.win.configure(bg=color)
        c = tk.Canvas(self.win, width=size, height=size, bg=color,
                      highlightthickness=2, highlightbackground='white')
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

def http_post(path, payload, token=None):
    req = urllib.request.Request(f"{HTTP_URL}{path}", data=json.dumps(payload).encode(),
                                  headers={'Content-Type':'application/json'})
    if token: req.add_header('Authorization', f'Bearer {token}')
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.loads(r.read()), None
    except urllib.error.HTTPError as e:
        try:    err = json.loads(e.read()).get('error', str(e))
        except: err = str(e)
        return None, err
    except Exception as e:
        return None, str(e)

class LoginWindow:
    def __init__(self):
        self.root   = tk.Tk()
        self.root.title("Gorilla Click — Login")
        self.root.geometry("340x390"); self.root.configure(bg='#1e1e2e'); self.root.resizable(False,False)
        self.result = None
        self._build()

    def _field(self, label, show=''):
        tk.Label(self.root, text=label, bg='#1e1e2e', fg='#cdd6f4',
                 font=('Arial',9), anchor='w').pack(fill='x', padx=32)
        e = tk.Entry(self.root, show=show, bg='#313244', fg='#cdd6f4',
                     insertbackground='white', relief='flat', font=('Arial',11))
        e.pack(fill='x', padx=32, pady=(2,10), ipady=6)
        return e

    def _build(self):
        tk.Label(self.root, text="Gorilla Click", font=('Arial',18,'bold'),
                 bg='#1e1e2e', fg='#cdd6f4').pack(pady=(24,2))
        tk.Label(self.root, text="Desktop Login", font=('Arial',10),
                 bg='#1e1e2e', fg='#6c7086').pack(pady=(0,16))
        self.f_u  = self._field("Your Username")
        self.f_p  = self._field("Your Password", show='•')
        self.f_cu = self._field("Controller Username")
        self.f_rp = self._field("Room Password", show='•')
        self.f_dn = self._field("Device Name  (e.g. Office PC)")
        self.err  = tk.Label(self.root, text='', bg='#1e1e2e', fg='#f38ba8', font=('Arial',9))
        self.err.pack()
        tk.Button(self.root, text="Connect", command=self._submit,
                  bg='#89b4fa', fg='#1e1e2e', font=('Arial',10,'bold'),
                  relief='flat', pady=8).pack(fill='x', padx=32, pady=(4,0))
        self.root.bind('<Return>', lambda _: self._submit())

    def _submit(self):
        u,p,cu,rp,dn = (self.f_u.get().strip(), self.f_p.get(),
                         self.f_cu.get().strip(), self.f_rp.get(), self.f_dn.get().strip())
        if not all([u,p,cu,rp,dn]):
            self.err.config(text='All fields are required.'); return
        self.err.config(text='Logging in...'); self.root.update()
        res, err = http_post('/login', {'username':u,'password':p})
        if err:    self.err.config(text=f'Login failed: {err}'); return
        if res.get('accountType') != 'desktop':
            self.err.config(text='Not a desktop account.'); return
        self.result = (res['token'], cu, rp, dn)
        self.root.destroy()

    def run(self):
        self.root.mainloop(); return self.result

class DesktopClient:
    def __init__(self, token, ctrl_user, room_pass, device_name):
        self.token=token; self.ctrl_user=ctrl_user
        self.room_pass=room_pass; self.device_name=device_name
        self.ws=None; self.loop=None
        self.root = tk.Tk()
        self.root.title("Gorilla Click")
        self.root.geometry("360x160+40+40"); self.root.configure(bg='#1e1e2e')
        self.root.attributes('-topmost', True)
        self._build_ui(); self._spawn_cursors(); self._start_loop()

    def _build_ui(self):
        top = tk.Frame(self.root, bg='#1e1e2e')
        top.pack(fill='x', padx=12, pady=10)
        tk.Label(top, text="Gorilla Click", font=('Arial',14,'bold'),
                 bg='#1e1e2e', fg='#cdd6f4').pack(side='left')
        self.status_lbl = tk.Label(top, text="● Connecting...",
                                   font=('Arial',10), bg='#1e1e2e', fg='#fab387')
        self.status_lbl.pack(side='right')
        info = tk.Frame(self.root, bg='#313244', pady=6)
        info.pack(fill='x', padx=12)
        tk.Label(info, text=f"Room: {self.ctrl_user}   Device: {self.device_name}",
                 bg='#313244', fg='#cdd6f4', font=('Arial',9)).pack(padx=8)
        self.log_var = tk.StringVar(value="Drag A and B markers anywhere on screen.")
        tk.Label(self.root, textvariable=self.log_var, font=('Arial',8),
                 bg='#1e1e2e', fg='#a6adc8', wraplength=340).pack(pady=6)

    def _spawn_cursors(self):
        self.cursor_a = FloatingCursor(self.root,'A','#1e66f5',300,300)
        self.cursor_b = FloatingCursor(self.root,'B','#e64553',500,300)

    def _start_loop(self):
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_forever, daemon=True).start()
        asyncio.run_coroutine_threadsafe(self._connect(), self.loop)

    async def _connect(self):
        try:
            self.ws = await websockets.connect(WS_URL, ping_interval=20, ping_timeout=10)
            await self.ws.send(json.dumps({'type':'auth','token':self.token}))
            async for raw in self.ws:
                self._handle(json.loads(raw))
        except Exception as e:
            self._log(f'Disconnected: {e}')
            self.root.after(0, lambda: self.status_lbl.config(text='● Disconnected',fg='#f38ba8'))

    def _handle(self, msg):
        t = msg.get('type')
        if   t == 'auth_ok':   asyncio.run_coroutine_threadsafe(self._join_room(), self.loop)
        elif t == 'room_joined':
            self.root.after(0, lambda: self.status_lbl.config(text='● Connected',fg='#a6e3a1'))
            self._log(f"In {self.ctrl_user}'s room. Ready.")
        elif t == 'click':
            c = msg.get('cursor')
            x,y = (self.cursor_a if c=='A' else self.cursor_b).click()
            self._log(f'Click {c} at ({x},{y})')
        elif t == 'controller_disconnected':
            self.root.after(0, lambda: self.status_lbl.config(text='● Controller offline',fg='#fab387'))
        elif t == 'error':
            self._log(f"Error: {msg.get('message')}")

    async def _join_room(self):
        await self.ws.send(json.dumps({'type':'join_room',
            'controllerUsername':self.ctrl_user, 'roomPassword':self.room_pass,
            'deviceName':self.device_name}))

    def _log(self, text):
        self.root.after(0, lambda: self.log_var.set(text))

    def run(self):
        self.root.mainloop()

if __name__ == '__main__':
    result = LoginWindow().run()
    if result:
        DesktopClient(*result).run()
