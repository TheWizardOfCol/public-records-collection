import './style.css'

// Player controller for The Public Records Collection.
//
// Players join by entering a name and the room code shown on the host's screen.
// In a real deploy this bundle is served by the Element itself
// (/app/static/collection/), so the REST and WebSocket URLs are same-origin
// relative paths; `vite dev` proxies both to VITE_BACKEND_URL for local work.

type Player = { id: string; name: string; host: boolean; connected: boolean }
type RoomSettings = { familyFriendly: boolean; answerTimerSeconds: number }
type Room = {
  code: string
  phase: string
  gameId: string | null
  settings: RoomSettings
  players: Player[]
}
type ServerMessage = {
  type: string
  room?: Room
  playerId?: string
  reason?: string
  message?: string
}
type HealthStatus = { status: string; element: string }

const app = document.querySelector<HTMLDivElement>('#app')!

let socket: WebSocket | null = null
let selfId: string | null = null
let lastName = ''

const escapeHtml = (value: string): string =>
  value.replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  )

function wsUrl(): string {
  const base = location.origin.replace(/^http/, 'ws')
  return `${base}/collection-ws/lobby`
}

function renderJoin(error?: string): void {
  app.innerHTML = `
    <main class="join">
      <h1>The Public Records Collection</h1>
      <form id="join" autocomplete="off">
        <label>
          Your name
          <input id="name" name="name" maxlength="20" value="${escapeHtml(lastName)}" required />
        </label>
        <label>
          Room code
          <input
            id="code"
            name="code"
            inputmode="latin"
            autocomplete="off"
            maxlength="6"
            placeholder="ABCD"
            required
          />
        </label>
        <button type="submit">Join</button>
      </form>
      <p id="status" class="status" role="status" ${error ? 'data-state="error"' : ''}>
        ${error ? escapeHtml(error) : 'Checking backend…'}
      </p>
    </main>
  `

  document.querySelector<HTMLFormElement>('#join')!.addEventListener('submit', (event) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget as HTMLFormElement)
    const name = String(data.get('name') ?? '').trim()
    const code = String(data.get('code') ?? '').trim().toUpperCase()
    if (!name || !code) return
    lastName = name
    join(name, code)
  })

  if (!error) void checkBackend()
}

function renderLobby(room: Room): void {
  const isLobby = room.phase === 'lobby'
  const roster = room.players
    .map((p) => {
      const self = p.id === selfId ? ' (you)' : ''
      const host = p.host ? ' — host' : ''
      const away = p.connected ? '' : ' · away'
      return `<li>${escapeHtml(p.name)}${self}${host}${away}</li>`
    })
    .join('')

  app.innerHTML = `
    <main class="lobby">
      <header class="lobby__head">
        <span class="lobby__code-label">Room code</span>
        <span class="lobby__code">${room.code}</span>
      </header>
      <section class="lobby__body">
        <h2>${isLobby ? 'Waiting room' : 'Starting…'}</h2>
        <ul class="roster">${roster}</ul>
      </section>
      <p id="status" class="status" role="status" data-state="ok">
        ${isLobby ? 'Waiting for the host to start the game…' : `Starting ${room.gameId ?? 'the game'}…`}
      </p>
      <button id="leave" class="link">Leave</button>
    </main>
  `

  document.querySelector<HTMLButtonElement>('#leave')!.addEventListener('click', () => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'leave' }))
    }
    socket?.close()
    selfId = null
    renderJoin()
  })
}

function renderClosed(reason: string): void {
  selfId = null
  socket = null
  renderJoin(reason || 'The host closed the room.')
}

function handle(message: ServerMessage): void {
  switch (message.type) {
    case 'welcome':
      selfId = message.playerId ?? null
      if (message.room) renderLobby(message.room)
      break
    case 'state':
      if (message.room) renderLobby(message.room)
      break
    case 'closed':
      renderClosed(message.reason ?? 'The host closed the room.')
      break
    case 'error':
      socket?.close()
      socket = null
      renderJoin(message.message ?? 'Something went wrong.')
      break
    default:
      break
  }
}

function join(name: string, code: string): void {
  const status = document.querySelector<HTMLParagraphElement>('#status')
  if (status) status.textContent = 'Connecting…'

  socket = new WebSocket(wsUrl())
  socket.addEventListener('open', () => {
    socket!.send(JSON.stringify({ type: 'join', code, name }))
  })
  socket.addEventListener('message', (event) => {
    handle(JSON.parse(String(event.data)) as ServerMessage)
  })
  socket.addEventListener('close', () => {
    if (selfId) renderClosed('Connection closed.')
  })
  socket.addEventListener('error', () => {
    socket = null
    renderJoin('Could not reach the room. Check the code and try again.')
  })
}

async function checkBackend(): Promise<void> {
  const status = document.querySelector<HTMLParagraphElement>('#status')
  if (!status) return
  try {
    const res = await fetch('/collection/health', { headers: { Accept: 'application/json' } })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const health = (await res.json()) as HealthStatus
    status.textContent = `Connected — ${health.element} (${health.status})`
    status.dataset.state = 'ok'
  } catch (err) {
    status.textContent = `Backend unreachable: ${(err as Error).message}`
    status.dataset.state = 'error'
  }
}

renderJoin()
