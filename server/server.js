const http = require('http');
const WebSocket = require('ws');
const express = require('express');
const Database = require('better-sqlite3');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');

const PORT = process.env.PORT || 8080;
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(64).toString('hex');
const SALT_ROUNDS = 10;

// ─── Role config ────────────────────────────────────────────────
const DESKTOP_LIMITS = { king: Infinity, queen: Infinity, rook: 5, pawn: 1 };
const ROLE_RANK      = { king: 4, queen: 3, rook: 2, pawn: 1 };
const ALL_ROLES      = ['king', 'queen', 'rook', 'pawn'];

// ─── SQLite setup ────────────────────────────────────────────────
const db = new Database('./gorilla.db');
db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    username     TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role         TEXT NOT NULL CHECK(role IN ('king','queen','rook','pawn')),
    account_type TEXT NOT NULL CHECK(account_type IN ('controller','desktop')),
    created_by   TEXT,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
  );
  CREATE TABLE IF NOT EXISTS room_passwords (
    controller_username TEXT PRIMARY KEY,
    password_hash       TEXT NOT NULL
  );
`);

function log(msg) { console.log(`[${new Date().toISOString()}] ${msg}`); }

// ─── First-run: auto-create King ─────────────────────────────────
(function firstRun() {
  const count = db.prepare('SELECT COUNT(*) as c FROM users').get().c;
  if (count === 0) {
    const pass = crypto.randomBytes(5).toString('hex');
    db.prepare(
      `INSERT INTO users (username,password_hash,role,account_type)
       VALUES ('king',?,'king','controller')`
    ).run(bcrypt.hashSync(pass, SALT_ROUNDS));
    const line = '═'.repeat(52);
    log(line);
    log('  KING ACCOUNT CREATED  (first run)');
    log(`  Username : king`);
    log(`  Password : ${pass}`);
    log('  ⚠  Change this password immediately after login!');
    log(line);
  }
})();

// ─── Helpers ────────────────────────────────────────────────────
const getUser  = db.prepare('SELECT * FROM users WHERE username=?');
const allUsers = db.prepare('SELECT id,username,role,account_type,created_by,created_at FROM users ORDER BY role DESC,username');

function verifyToken(token) {
  try { return jwt.verify(token, JWT_SECRET); }
  catch { return null; }
}

function canManage(actorRole, targetRole) {
  if (actorRole === 'king') return targetRole !== 'king';
  if (actorRole === 'queen') return ROLE_RANK[targetRole] < ROLE_RANK['queen'];
  return false;
}

// ─── In-memory rooms ─────────────────────────────────────────────
// rooms: Map<controllerUsername, { ws, role, desktops: Map<id, {ws, deviceName, selected}> }>
const rooms = new Map();

// ws → { username, role, accountType, roomOwner? (for desktop) }
const clients = new Map();

function getRoom(username) { return rooms.get(username); }

function broadcast(room, msg) {
  const raw = JSON.stringify(msg);
  room.desktops.forEach(d => {
    if (d.ws.readyState === WebSocket.OPEN) d.ws.send(raw);
  });
}

function sendTo(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}

function desktopList(room) {
  const list = [];
  room.desktops.forEach((d, id) => list.push({ id, deviceName: d.deviceName, selected: d.selected }));
  return list;
}

// ─── Express REST API ────────────────────────────────────────────
const app = express();
app.use(express.json());

// ─── Download page ───────────────────────────────────────────────
const GITHUB_REPO = 'toolsrdx-bot/click-relay';
const ACTIONS_URL = `https://github.com/${GITHUB_REPO}/actions`;

app.get('/', (req, res) => {
  res.send(`<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Gorilla — Downloads</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #1e1e2e; color: #cdd6f4; font-family: Arial, sans-serif; padding: 40px 24px; }
    h1 { font-size: 28px; margin-bottom: 6px; }
    p.sub { color: #6c7086; margin-bottom: 36px; font-size: 14px; }
    .grid { display: flex; gap: 20px; flex-wrap: wrap; }
    .card { background: #313244; border-radius: 16px; padding: 28px; flex: 1; min-width: 240px; }
    .card h2 { font-size: 18px; margin-bottom: 8px; }
    .card p { color: #a6adc8; font-size: 13px; margin-bottom: 20px; line-height: 1.5; }
    a.btn { display: inline-block; background: #89b4fa; color: #1e1e2e; text-decoration: none;
            font-weight: bold; padding: 10px 22px; border-radius: 10px; font-size: 14px; }
    a.btn:hover { background: #74a8f7; }
    .tag { display: inline-block; font-size: 11px; background: #45475a; color: #a6adc8;
           padding: 2px 8px; border-radius: 6px; margin-bottom: 12px; }
  </style>
</head>
<body>
  <h1>🦍 Gorilla</h1>
  <p class="sub">Click relay system — downloads</p>
  <div class="grid">
    <div class="card">
      <span class="tag">Android</span>
      <h2>Gorilla Controller</h2>
      <p>Android app — control desktops with your volume buttons.</p>
      <a class="btn" href="${ACTIONS_URL}">Download APK</a>
    </div>
    <div class="card">
      <span class="tag">Windows</span>
      <h2>Gorilla Click</h2>
      <p>Windows desktop client — receives clicks from the controller.</p>
      <a class="btn" href="${ACTIONS_URL}">Download EXE</a>
    </div>
    <div class="card">
      <span class="tag">Linux</span>
      <h2>Gorilla Click</h2>
      <p>Linux desktop client — run with Python 3 + websockets.</p>
      <a class="btn" href="/download/client-linux.py">Download client.py</a>
    </div>
  </div>
  <p style="margin-top:32px;color:#45475a;font-size:12px;">
    For latest builds visit: <a href="${ACTIONS_URL}" style="color:#6c7086;">${ACTIONS_URL}</a>
  </p>
</body>
</html>`);
});

// ─── Direct file downloads ────────────────────────────────────────
const path = require('path');
app.get('/download/client-linux.py', (req, res) => {
  res.download(path.join(__dirname, '../desktop-client-linux/client.py'), 'gorilla-client-linux.py');
});

function authMiddleware(req, res, next) {
  const auth = req.headers.authorization || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  const payload = token ? verifyToken(token) : null;
  if (!payload) return res.status(401).json({ error: 'Unauthorized' });
  req.user = payload;
  next();
}

// Login
app.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) return res.status(400).json({ error: 'Missing fields' });
  const user = getUser.get(username);
  if (!user || !bcrypt.compareSync(password, user.password_hash))
    return res.status(401).json({ error: 'Invalid credentials' });
  const token = jwt.sign(
    { username: user.username, role: user.role, accountType: user.account_type },
    JWT_SECRET, { expiresIn: '30d' }
  );
  log(`Login: ${username} (${user.role}/${user.account_type})`);
  res.json({ token, username: user.username, role: user.role, accountType: user.account_type });
});

// Change own password
app.post('/change-password', authMiddleware, (req, res) => {
  const { oldPassword, newPassword } = req.body || {};
  const user = getUser.get(req.user.username);
  if (!bcrypt.compareSync(oldPassword, user.password_hash))
    return res.status(401).json({ error: 'Wrong current password' });
  db.prepare('UPDATE users SET password_hash=? WHERE username=?')
    .run(bcrypt.hashSync(newPassword, SALT_ROUNDS), req.user.username);
  res.json({ ok: true });
});

// List users (King + Queen)
app.get('/admin/users', authMiddleware, (req, res) => {
  if (!['king','queen'].includes(req.user.role))
    return res.status(403).json({ error: 'Forbidden' });
  res.json(allUsers.all());
});

// Create user (King + Queen)
app.post('/admin/users', authMiddleware, (req, res) => {
  const { role: actorRole } = req.user;
  if (!['king','queen'].includes(actorRole))
    return res.status(403).json({ error: 'Forbidden' });
  const { username, password, role, accountType } = req.body || {};
  if (!username || !password || !ALL_ROLES.includes(role) || !['controller','desktop'].includes(accountType))
    return res.status(400).json({ error: 'Missing or invalid fields' });
  if (!canManage(actorRole, role))
    return res.status(403).json({ error: `${actorRole} cannot create ${role} accounts` });
  try {
    db.prepare(
      `INSERT INTO users (username,password_hash,role,account_type,created_by) VALUES (?,?,?,?,?)`
    ).run(username, bcrypt.hashSync(password, SALT_ROUNDS), role, accountType, req.user.username);
    log(`User created: ${username} (${role}/${accountType}) by ${req.user.username}`);
    res.json({ ok: true, username, role, accountType });
  } catch (e) {
    res.status(409).json({ error: 'Username already exists' });
  }
});

// Delete user (King + Queen)
app.delete('/admin/users/:username', authMiddleware, (req, res) => {
  const { role: actorRole, username: actorUsername } = req.user;
  if (!['king','queen'].includes(actorRole))
    return res.status(403).json({ error: 'Forbidden' });
  const target = getUser.get(req.params.username);
  if (!target) return res.status(404).json({ error: 'User not found' });
  if (!canManage(actorRole, target.role))
    return res.status(403).json({ error: 'Insufficient privileges' });
  if (target.username === 'king') return res.status(403).json({ error: 'Cannot delete King' });
  db.prepare('DELETE FROM users WHERE username=?').run(target.username);
  db.prepare('DELETE FROM room_passwords WHERE controller_username=?').run(target.username);
  log(`User deleted: ${target.username} by ${actorUsername}`);
  res.json({ ok: true });
});

// Change role
app.patch('/admin/users/:username/role', authMiddleware, (req, res) => {
  const { role: actorRole } = req.user;
  if (!['king','queen'].includes(actorRole))
    return res.status(403).json({ error: 'Forbidden' });
  const target = getUser.get(req.params.username);
  if (!target) return res.status(404).json({ error: 'User not found' });
  const { newRole } = req.body || {};
  if (!ALL_ROLES.includes(newRole)) return res.status(400).json({ error: 'Invalid role' });
  if (!canManage(actorRole, target.role) || !canManage(actorRole, newRole))
    return res.status(403).json({ error: 'Insufficient privileges' });
  if (target.role === 'king') return res.status(403).json({ error: 'Cannot demote King' });
  db.prepare('UPDATE users SET role=? WHERE username=?').run(newRole, target.username);
  res.json({ ok: true });
});

// ─── WebSocket server ─────────────────────────────────────────────
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress;
  log(`WS connect from ${ip}`);

  ws.on('message', raw => {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }
    handleMessage(ws, msg);
  });

  ws.on('close', () => handleDisconnect(ws));
  ws.on('error', err => log(`WS error: ${err.message}`));
});

function handleDisconnect(ws) {
  const client = clients.get(ws);
  if (!client) return;
  clients.delete(ws);

  if (client.accountType === 'controller') {
    const room = rooms.get(client.username);
    if (room) {
      broadcast(room, { type: 'controller_disconnected' });
      rooms.delete(client.username);
      log(`Room closed: ${client.username}`);
    }
  } else if (client.accountType === 'desktop' && client.roomOwner) {
    const room = rooms.get(client.roomOwner);
    if (room) {
      room.desktops.delete(client.socketId);
      if (room.ws && room.ws.readyState === WebSocket.OPEN) {
        sendTo(room.ws, { type: 'desktop_left', id: client.socketId, deviceName: client.deviceName });
        sendTo(room.ws, { type: 'desktop_list', desktops: desktopList(room) });
      }
      log(`Desktop left room ${client.roomOwner}: ${client.deviceName}`);
    }
  }
}

function handleMessage(ws, msg) {
  const { type } = msg;

  // ── Auth ──────────────────────────────────────────────────────
  if (type === 'auth') {
    const payload = verifyToken(msg.token);
    if (!payload) return sendTo(ws, { type: 'error', message: 'Invalid token' });
    const user = getUser.get(payload.username);
    if (!user) return sendTo(ws, { type: 'error', message: 'User not found' });
    clients.set(ws, {
      username: user.username,
      role: user.role,
      accountType: user.account_type,
    });
    sendTo(ws, { type: 'auth_ok', username: user.username, role: user.role, accountType: user.account_type });
    log(`Auth ok: ${user.username} (${user.role}/${user.account_type})`);
    return;
  }

  // ── Desktop: join room (no account needed, room password is auth) ─
  if (type === 'join_room') {
    const { controllerUsername, roomPassword, deviceName } = msg;
    if (!controllerUsername || !roomPassword || !deviceName)
      return sendTo(ws, { type: 'error', message: 'Missing fields' });

    const room = rooms.get(controllerUsername);
    if (!room) return sendTo(ws, { type: 'error', message: 'Room not found or controller offline' });

    const storedPass = db.prepare('SELECT password_hash FROM room_passwords WHERE controller_username=?')
                         .get(controllerUsername);
    if (!storedPass || !bcrypt.compareSync(roomPassword, storedPass.password_hash))
      return sendTo(ws, { type: 'error', message: 'Wrong room password' });

    const limit = DESKTOP_LIMITS[room.role];
    if (room.desktops.size >= limit)
      return sendTo(ws, { type: 'error', message: `Desktop limit reached (${limit}) for this role` });

    const socketId = crypto.randomUUID();
    room.desktops.set(socketId, { ws, deviceName, selected: true });
    // track for disconnect cleanup
    clients.set(ws, { accountType: 'desktop', roomOwner: controllerUsername, socketId, deviceName });

    sendTo(ws, { type: 'room_joined', controllerUsername, socketId });
    sendTo(room.ws, { type: 'desktop_joined', id: socketId, deviceName });
    sendTo(room.ws, { type: 'desktop_list', desktops: desktopList(room) });
    log(`Desktop joined room ${controllerUsername}: ${deviceName}`);
    return;
  }

  const client = clients.get(ws);
  if (!client) return sendTo(ws, { type: 'error', message: 'Not authenticated' });

  // ── Controller: open room ─────────────────────────────────────
  if (type === 'open_room') {
    if (client.accountType !== 'controller')
      return sendTo(ws, { type: 'error', message: 'Only controllers can open rooms' });
    const { roomPassword } = msg;
    if (!roomPassword) return sendTo(ws, { type: 'error', message: 'Room password required' });
    const roomPassHash = bcrypt.hashSync(roomPassword, SALT_ROUNDS);
    db.prepare('INSERT OR REPLACE INTO room_passwords (controller_username,password_hash) VALUES (?,?)')
      .run(client.username, roomPassHash);
    rooms.set(client.username, { ws, role: client.role, desktops: new Map() });
    sendTo(ws, { type: 'room_opened', desktops: [] });
    log(`Room opened: ${client.username}`);
    return;
  }

  // ── Controller: click ─────────────────────────────────────────
  if (type === 'click') {
    if (client.accountType !== 'controller')
      return sendTo(ws, { type: 'error', message: 'Only controllers can click' });
    const room = rooms.get(client.username);
    if (!room) return sendTo(ws, { type: 'error', message: 'Room not open' });

    const { cursor, mode, targets } = msg; // mode: 'all' | 'selected'
    if (cursor !== 'A' && cursor !== 'B') return;

    room.desktops.forEach((desktop, id) => {
      const shouldSend = mode === 'all' || (mode === 'selected' && desktop.selected);
      if (shouldSend && desktop.ws.readyState === WebSocket.OPEN) {
        desktop.ws.send(JSON.stringify({ type: 'click', cursor, timestamp: Date.now() }));
      }
    });

    log(`Click ${cursor} [${mode}] → room ${client.username} (${room.desktops.size} desktops)`);
    return;
  }

  // ── Controller: toggle desktop selection ──────────────────────
  if (type === 'select_desktop') {
    const room = rooms.get(client.username);
    if (!room) return;
    const desktop = room.desktops.get(msg.id);
    if (desktop) {
      desktop.selected = !!msg.selected;
      sendTo(ws, { type: 'desktop_list', desktops: desktopList(room) });
    }
    return;
  }

  // ── Controller: change room password ─────────────────────────
  if (type === 'change_room_password') {
    if (client.accountType !== 'controller') return;
    const { newPassword } = msg;
    if (!newPassword) return sendTo(ws, { type: 'error', message: 'Password required' });
    db.prepare('INSERT OR REPLACE INTO room_passwords (controller_username,password_hash) VALUES (?,?)')
      .run(client.username, bcrypt.hashSync(newPassword, SALT_ROUNDS));
    sendTo(ws, { type: 'room_password_changed' });
    return;
  }

  // ── Ping ──────────────────────────────────────────────────────
  if (type === 'ping') {
    sendTo(ws, { type: 'pong' });
    return;
  }

  sendTo(ws, { type: 'error', message: `Unknown type: ${type}` });
}

server.listen(PORT, '0.0.0.0', () => {
  log(`Gorilla relay running on port ${PORT}`);
  log('REST API + WebSocket ready');
});
