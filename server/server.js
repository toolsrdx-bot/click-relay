const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const crypto = require('crypto');

const PORT = process.env.PORT || 8080;

// sessionCode -> { android: ws, desktop: ws, token: string }
const sessions = new Map();

// ws -> sessionCode
const clientSession = new Map();

const wss = new WebSocket.Server({ port: PORT });

function log(msg) {
  console.log(`[${new Date().toISOString()}] ${msg}`);
}

function send(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function generateSessionCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function generateToken() {
  return crypto.randomBytes(32).toString('hex');
}

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress;
  log(`New connection from ${ip}`);

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      send(ws, { type: 'error', message: 'Invalid JSON' });
      return;
    }

    handleMessage(ws, msg);
  });

  ws.on('close', () => {
    const code = clientSession.get(ws);
    if (!code) return;

    const session = sessions.get(code);
    if (!session) return;

    if (session.android === ws) {
      log(`Android disconnected from session ${code}`);
      session.android = null;
      if (session.desktop) {
        send(session.desktop, { type: 'android_disconnected' });
      }
    } else if (session.desktop === ws) {
      log(`Desktop disconnected from session ${code}`);
      session.desktop = null;
      if (session.android) {
        send(session.android, { type: 'desktop_disconnected' });
      }
    }

    clientSession.delete(ws);

    // Clean up session if both disconnected
    if (!session.android && !session.desktop) {
      sessions.delete(code);
      log(`Session ${code} cleaned up`);
    }
  });

  ws.on('error', (err) => {
    log(`WebSocket error: ${err.message}`);
  });
});

function handleMessage(ws, msg) {
  const { type } = msg;

  switch (type) {

    // Desktop creates a session, gets a 6-digit code to share with Android
    case 'create_session': {
      let code = generateSessionCode();
      while (sessions.has(code)) code = generateSessionCode();

      const token = generateToken();
      sessions.set(code, { android: null, desktop: ws, token });
      clientSession.set(ws, code);

      log(`Session created: ${code}`);
      send(ws, { type: 'session_created', code, token });
      break;
    }

    // Android joins using the 6-digit code
    case 'join_session': {
      const { code } = msg;
      const session = sessions.get(code);

      if (!session) {
        send(ws, { type: 'error', message: 'Session not found' });
        return;
      }

      if (session.android) {
        send(ws, { type: 'error', message: 'Session already has an Android client' });
        return;
      }

      session.android = ws;
      clientSession.set(ws, code);

      log(`Android joined session ${code}`);
      send(ws, { type: 'session_joined', code });
      // Tell Android the desktop is already there so it activates volume buttons
      if (session.desktop) {
        send(ws, { type: 'desktop_connected' });
      }
      send(session.desktop, { type: 'android_connected' });
      break;
    }

    // Android sends click event: CLICK_A or CLICK_B
    case 'click': {
      const code = clientSession.get(ws);
      if (!code) {
        send(ws, { type: 'error', message: 'Not in a session' });
        return;
      }

      const session = sessions.get(code);
      if (!session || session.android !== ws) {
        send(ws, { type: 'error', message: 'Unauthorized' });
        return;
      }

      const { cursor } = msg; // 'A' or 'B'
      if (cursor !== 'A' && cursor !== 'B') {
        send(ws, { type: 'error', message: 'Invalid cursor' });
        return;
      }

      if (!session.desktop) {
        send(ws, { type: 'error', message: 'Desktop not connected' });
        return;
      }

      log(`Click ${cursor} → session ${code}`);
      send(session.desktop, { type: 'click', cursor, timestamp: Date.now() });
      break;
    }

    // Heartbeat / ping
    case 'ping': {
      send(ws, { type: 'pong' });
      break;
    }

    default: {
      send(ws, { type: 'error', message: `Unknown message type: ${type}` });
    }
  }
}

log(`Relay server running on ws://0.0.0.0:${PORT}`);
log('Waiting for connections...');
