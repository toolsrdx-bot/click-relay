"""Gorilla — Linux desktop client.
Uses xdotool for mouse click injection on X11.
"""
import asyncio
import json
import subprocess
import threading
import tkinter as tk
import websockets

SERVER_URL = "ws://103.164.3.212:8080"  # Your VM relay server


def inject_click(x: int, y: int) -> None:
    """Move mouse to (x, y) and perform a left click via xdotool."""
    try:
        subprocess.run(
            ['xdotool', 'mousemove', str(x), str(y), 'click', '1'],
            check=False,
        )
    except FileNotFoundError:
        print("xdotool not found — install with: sudo apt install xdotool")


class FloatingCursor:
    """Always-on-top draggable marker that lives anywhere on screen."""

    def __init__(self, parent, name, color, initial_x=200, initial_y=200, size=36):
        self.parent = parent
        self.name = name
        self.color = color
        self.size = size

        self.win = tk.Toplevel(parent)
        self.win.overrideredirect(True)
        self.win.attributes('-topmost', True)
        self.win.attributes('-alpha', 0.85)
        self.win.geometry(f'{size}x{size}+{initial_x}+{initial_y}')
        self.win.configure(bg=color)

        canvas = tk.Canvas(self.win, width=size, height=size,
                           bg=color, highlightthickness=2,
                           highlightbackground='white')
        canvas.pack()
        canvas.create_text(size // 2, size // 2, text=name,
                           fill='white', font=('Arial', 14, 'bold'))

        canvas.bind('<ButtonPress-1>', self._start_drag)
        canvas.bind('<B1-Motion>', self._do_drag)

        self._drag_x = 0
        self._drag_y = 0

    def _start_drag(self, event):
        self._drag_x = event.x_root - self.win.winfo_x()
        self._drag_y = event.y_root - self.win.winfo_y()

    def _do_drag(self, event):
        new_x = event.x_root - self._drag_x
        new_y = event.y_root - self._drag_y
        self.win.geometry(f'+{new_x}+{new_y}')

    def click(self):
        x = self.win.winfo_x() + self.size // 2
        y = self.win.winfo_y() + self.size // 2
        self.win.withdraw()
        self.win.update()
        inject_click(x, y)
        self.win.after(80, self.win.deiconify)
        return x, y


class DesktopClient:
    def __init__(self, root):
        self.root = root
        self.root.title("Gorilla")
        self.root.geometry("360x180+40+40")
        self.root.configure(bg='#1e1e2e')
        self.root.attributes('-topmost', True)

        self.ws = None
        self.session_code = None
        self.loop = None
        self.connected = False

        self._build_control_ui()
        self._spawn_cursors()
        self._start_event_loop()

    def _build_control_ui(self):
        top = tk.Frame(self.root, bg='#1e1e2e')
        top.pack(fill='x', padx=12, pady=10)
        tk.Label(top, text="Gorilla", font=('Arial', 14, 'bold'),
                 bg='#1e1e2e', fg='#cdd6f4').pack(side='left')
        self.status_label = tk.Label(top, text="● Disconnected",
                                     font=('Arial', 10),
                                     bg='#1e1e2e', fg='#f38ba8')
        self.status_label.pack(side='right')

        sess = tk.Frame(self.root, bg='#313244', pady=8)
        sess.pack(fill='x', padx=12)
        tk.Label(sess, text="Code:", font=('Arial', 10),
                 bg='#313244', fg='#cdd6f4').pack(side='left', padx=8)
        self.code_label = tk.Label(sess, text="------",
                                   font=('Courier', 18, 'bold'),
                                   bg='#313244', fg='#a6e3a1')
        self.code_label.pack(side='left', padx=8)
        self.connect_btn = tk.Button(sess, text="Create Session",
                                     command=self._create_session,
                                     bg='#89b4fa', fg='#1e1e2e',
                                     font=('Arial', 9, 'bold'),
                                     relief='flat', padx=8)
        self.connect_btn.pack(side='right', padx=8)

        self.android_label = tk.Label(self.root, text="Android: waiting...",
                                      font=('Arial', 9),
                                      bg='#1e1e2e', fg='#fab387')
        self.android_label.pack(pady=2)

        self.log_var = tk.StringVar(value="Drag the A and B markers anywhere on screen.")
        tk.Label(self.root, textvariable=self.log_var,
                 font=('Arial', 8), bg='#1e1e2e', fg='#a6adc8',
                 wraplength=340).pack(pady=2)

    def _spawn_cursors(self):
        self.cursor_a = FloatingCursor(self.root, 'A', '#1e66f5', 300, 300)
        self.cursor_b = FloatingCursor(self.root, 'B', '#e64553', 500, 300)

    def _start_event_loop(self):
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_forever, daemon=True).start()

    def _create_session(self):
        self.connect_btn.config(state='disabled', text='Connecting...')
        asyncio.run_coroutine_threadsafe(self._connect_and_create(), self.loop)

    def _disconnect(self):
        if self.ws:
            asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
        self.connect_btn.config(state='normal', text='Create Session',
                                command=self._create_session, bg='#89b4fa')
        self.code_label.config(text='------')
        self._update_status(False)
        self._update_log("Disconnected.")

    async def _connect_and_create(self):
        try:
            self.ws = await websockets.connect(SERVER_URL,
                                               ping_interval=20, ping_timeout=10)
            await self.ws.send(json.dumps({'type': 'create_session'}))
            self._update_log('Connected to relay server.')
            await self._listen()
        except Exception as e:
            self._update_status(False)
            self._update_log(f'Connection failed: {e}')
            self.root.after(0, lambda: self.connect_btn.config(
                state='normal', text='Create Session'))

    async def _listen(self):
        try:
            async for raw in self.ws:
                self._handle_message(json.loads(raw))
        except websockets.ConnectionClosed:
            self._update_status(False)
            self._update_log('Disconnected from relay server.')
            self.root.after(0, lambda: self.connect_btn.config(
                state='normal', text='Create Session',
                command=self._create_session, bg='#89b4fa'))

    def _handle_message(self, msg):
        t = msg.get('type')
        if t == 'session_created':
            self.session_code = msg['code']
            self._update_status(True)
            self.root.after(0, lambda: self.code_label.config(text=msg['code']))
            self.root.after(0, lambda: self.connect_btn.config(
                state='normal', text='Disconnect',
                command=self._disconnect, bg='#f38ba8'))
            self._update_log(f"Session ready. Share code {msg['code']} with phone.")

        elif t == 'android_connected':
            self.root.after(0, lambda: self.android_label.config(
                text='Android: ● connected', fg='#a6e3a1'))
            self._update_log('Phone connected. Volume buttons are live.')

        elif t == 'android_disconnected':
            self.root.after(0, lambda: self.android_label.config(
                text='Android: waiting...', fg='#fab387'))
            self._update_log('Phone disconnected.')

        elif t == 'click':
            cursor = msg.get('cursor')
            if cursor == 'A':
                x, y = self.cursor_a.click()
            elif cursor == 'B':
                x, y = self.cursor_b.click()
            else:
                return
            self._update_log(f'Click {cursor} at ({x}, {y})')

        elif t == 'error':
            self._update_log(f"Server: {msg.get('message')}")

    def _update_status(self, connected):
        self.connected = connected
        if connected:
            self.root.after(0, lambda: self.status_label.config(
                text='● Connected', fg='#a6e3a1'))
        else:
            self.root.after(0, lambda: self.status_label.config(
                text='● Disconnected', fg='#f38ba8'))

    def _update_log(self, text):
        self.root.after(0, lambda: self.log_var.set(text))


if __name__ == '__main__':
    root = tk.Tk()
    app = DesktopClient(root)
    root.mainloop()
