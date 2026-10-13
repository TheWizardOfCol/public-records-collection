import './style.css'

// Player controller for The Public Records Collection.
//
// Players join by entering a name and the room code shown on the host's screen.
// In a real deploy this bundle is served by the Element itself
// (/app/static/collection/), so the REST and WebSocket URLs are same-origin
// relative paths; `vite dev` proxies both to VITE_BACKEND_URL for local work.

type Player = { id: string; name: string; host: boolean; connected: boolean }
type RoomSettings = { familyFriendly: boolean; answerTimerSeconds: number; rounds: number }
type Room = {
  code: string
  phase: string
  gameId: string | null
  settings: RoomSettings
  players: Player[]
}

type WorkChoice = { playerId: string; playerName: string; workId: string | null; title: string | null }
type AnswerView = { id: string; text: string; authorName: string | null; points: number | null }
type ScoreEntry = { playerId: string; name: string; score: number }
type WorkView = { id: string; title: string; author: string }

type GetLitPublicView = {
  phase: string
  round: number
  totalRounds: number
  prompt: string | null
  works: WorkChoice[]
  submitted: number
  players: number
  answers: AnswerView[]
  roundScores: ScoreEntry[]
  scores: ScoreEntry[]
}

type GetLitPrivateView = {
  phase: string
  round: number
  totalRounds: number
  prompt: string | null
  library: WorkView[]
  workId: string | null
  workTitle: string | null
  answerId: string | null
  answer: string | null
  canGolden: boolean
  hearted: string[]
  roundScore: number
  totalScore: number
}

type PassageView = {
  bookId: string
  title: string
  author: string
  offset: number
  length: number
  totalLength: number
  last: boolean
  text: string
}

type ServerMessage = {
  type: string
  room?: Room
  game?: GetLitPublicView
  you?: GetLitPrivateView
  playerId?: string
  reason?: string
  message?: string
}
type HealthStatus = { status: string; element: string }

const app = document.querySelector<HTMLDivElement>('#app')!

const PASSAGE_LIMIT = 1200

let socket: WebSocket | null = null
let selfId: string | null = null
let lastName = ''
let lastGame: GetLitPublicView | null = null
let lastYou: GetLitPrivateView | null = null
let lastSig = ''

// Answer-phase scratch state, kept out of the DOM so a re-render can't wipe it.
let passage: PassageView | null = null
let passageLoading = false
let passageError = ''
let passageWorkId: string | null = null
let answerDraft = ''
let draftRound = -1

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

  bindLeave()
}

function bindLeave(): void {
  document.querySelector<HTMLButtonElement>('#leave')?.addEventListener('click', () => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'leave' }))
    }
    socket?.close()
    selfId = null
    lastGame = null
    lastYou = null
    lastSig = ''
    renderJoin()
  })
}

// --- Game screen --------------------------------------------------------------

function gameHeader(you: GetLitPrivateView): string {
  if (you.round <= 0) return 'Get Lit'
  return `Round ${you.round} of ${you.totalRounds}`
}

function renderGame(): void {
  const game = lastGame
  const you = lastYou
  if (!game || !you) return

  // Reset per-round scratch when the round changes.
  if (you.round !== draftRound) {
    draftRound = you.round
    answerDraft = ''
  }
  // Drop the cached passage if the player is answering from a different work.
  if (you.workId !== passageWorkId) {
    passageWorkId = you.workId
    passage = null
    passageError = ''
  }

  const round = you.round > 0 ? `<p class="round">${gameHeader(you)}</p>` : ''
  const prompt = you.prompt ? `<p class="prompt">${escapeHtml(you.prompt)}</p>` : ''
  const board = `<p class="board">${you.totalScore} pt${you.totalScore === 1 ? '' : 's'}</p>`

  let body = ''
  switch (you.phase) {
    case 'select':
      body = promptSelect(you)
      break
    case 'answer':
      body = promptAnswer(you)
      break
    case 'vote':
      body = promptVote(game, you)
      break
    case 'results':
      body = promptResults(game, you)
      break
    case 'finished':
      body = promptFinished(game, you)
      break
    default:
      body = '<p class="status">Loading…</p>'
  }

  const prevActive = document.activeElement as HTMLElement | null
  const restoreId = prevActive?.id ?? ''
  const restoreSel =
    prevActive instanceof HTMLTextAreaElement
      ? [prevActive.selectionStart, prevActive.selectionEnd]
      : null

  app.innerHTML = `
    <main class="game">
      <header class="game__head">${round}${board}</header>
      ${prompt}
      <section class="game__body" id="game-body">${body}</section>
      <button id="leave" class="link">Leave</button>
    </main>
  `

  bindLeave()
  bindGame()

  if (restoreId) {
    const el = document.getElementById(restoreId)
    if (el instanceof HTMLElement) {
      el.focus()
      if (el instanceof HTMLTextAreaElement && restoreSel) {
        el.setSelectionRange(restoreSel[0], restoreSel[1])
      }
    }
  }

  if (you.phase === 'answer' && !you.answerId && !passage && !passageLoading) {
    void loadPassage(0)
  }
}

function promptSelect(you: GetLitPrivateView): string {
  if (you.workId) {
    return `
      <h2>Your work</h2>
      <p class="chosen">${escapeHtml(you.workTitle ?? you.workId)}</p>
      <p class="status" data-state="ok">Chosen — waiting for the other players…</p>
    `
  }
  const items = you.library
    .map(
      (w) => `
      <li>
        <button class="work" data-work="${escapeHtml(w.id)}">
          <span class="work__title">${escapeHtml(w.title)}</span>
          <span class="work__author">${escapeHtml(w.author)}</span>
        </button>
      </li>`,
    )
    .join('')
  return `
    <h2>Choose the work you'll answer from</h2>
    <ul class="works">${items}</ul>
    <p class="status">You'll pull every answer from this one work.</p>
  `
}

function promptAnswer(you: GetLitPrivateView): string {
  if (you.answerId) {
    return `
      <h2>Answer submitted</h2>
      <blockquote class="answer">“${escapeHtml(you.answer ?? '')}”</blockquote>
      <p class="status" data-state="ok">Waiting for the other players…</p>
    `
  }

  let reader = ''
  if (passageLoading && !passage) {
    reader = '<p class="status">Loading your work…</p>'
  } else if (passageError) {
    reader = `<p class="status" data-state="error">${escapeHtml(passageError)}</p>`
  } else if (passage) {
    const from = passage.offset + 1
    const to = passage.offset + passage.length
    reader = `
      <pre class="passage" id="passage">${escapeHtml(passage.text)}</pre>
      <div class="pager">
        <button id="prev" class="ghost" ${passage.offset === 0 ? 'disabled' : ''}>‹ Earlier</button>
        <span class="pager__count">${from}–${to} of ${passage.totalLength}</span>
        <button id="next" class="ghost" ${passage.last ? 'disabled' : ''}>Later ›</button>
      </div>
    `
  }

  return `
    <h2>Find a passage for the prompt</h2>
    <p class="hint">Select text above (or type your own), then submit. It must come from your work.</p>
    ${reader}
    <textarea id="answer-input" class="answer-input" maxlength="600" placeholder="Paste or type your passage…">${escapeHtml(answerDraft)}</textarea>
    <div class="answer-actions">
      <button id="use-selection" class="ghost">Use selection</button>
      <button id="submit-answer">Submit</button>
    </div>
    <p id="answer-status" class="status"></p>
  `
}

function promptVote(game: GetLitPublicView, you: GetLitPrivateView): string {
  const hearted = new Set(you.hearted)
  const cards = game.answers
    .filter((a) => a.id !== you.answerId)
    .map((a) => {
      const done = hearted.has(a.id)
      const normal = `
        <button class="vote" data-id="${escapeHtml(a.id)}" data-golden="false" ${done ? 'disabled' : ''} title="Heart (1 point)">♥</button>`
      const golden = `
        <button class="vote vote--golden" data-id="${escapeHtml(a.id)}" data-golden="true" ${done || !you.canGolden ? 'disabled' : ''} title="Golden heart (3 points)">★</button>`
      return `
        <li class="answer-card">
          <p class="answer-card__text">“${escapeHtml(a.text)}”</p>
          <div class="answer-card__votes">${normal}${golden}</div>
        </li>`
    })
    .join('')

  const goldenNote = you.canGolden
    ? '<p class="hint">One golden heart (★) is still yours to give — 3 points.</p>'
    : '<p class="hint">You have spent your golden heart. Keep hearting with ♥.</p>'

  return `
    <h2>Vote for the best answer</h2>
    <p class="hint">${game.submitted} of ${game.players} answered. Hearts are anonymous.</p>
    ${goldenNote}
    <ul class="answers">${cards}</ul>
  `
}

function promptResults(game: GetLitPublicView, you: GetLitPrivateView): string {
  const ranked = game.answers
    .slice()
    .sort((a, b) => (b.points ?? 0) - (a.points ?? 0))
    .map((a) => {
      const mine = a.id === you.answerId ? ' answer-card--mine' : ''
      return `
        <li class="answer-card${mine}">
          <p class="answer-card__text">“${escapeHtml(a.text)}”</p>
          <p class="answer-card__author">${escapeHtml(a.authorName ?? '?')} <span class="points">+${a.points ?? 0}</span></p>
        </li>`
    })
    .join('')

  return `
    <h2>Results</h2>
    <p class="hint">You earned ${you.roundScore} this round · ${you.totalScore} total.</p>
    <ul class="answers">${ranked}</ul>
    <h2>Standings</h2>
    <ol class="scores">${scoreRows(game.scores)}</ol>
    <p class="status">Waiting for the host to continue…</p>
  `
}

function promptFinished(game: GetLitPublicView, you: GetLitPrivateView): string {
  const winner = game.scores[0]
  return `
    <h2>${winner ? `${escapeHtml(winner.name)} wins!` : 'Game over'}</h2>
    <ol class="scores">${scoreRows(game.scores)}</ol>
    <p class="hint">You finished with ${you.totalScore} point${you.totalScore === 1 ? '' : 's'}.</p>
  `
}

function scoreRows(entries: ScoreEntry[]): string {
  return entries
    .map((e) => {
      const mine = e.playerId === selfId ? ' class="scores__mine"' : ''
      const self = e.playerId === selfId ? ' (you)' : ''
      return `<li${mine}>${escapeHtml(e.name)}${self} <span class="points">${e.score}</span></li>`
    })
    .join('')
}

function bindGame(): void {
  // select — choose a work
  document.querySelectorAll<HTMLButtonElement>('button.work').forEach((btn) => {
    btn.addEventListener('click', () => {
      const workId = btn.dataset.work
      if (workId) send({ type: 'choose-work', workId })
    })
  })

  // answer — pager, selection helper, submit
  document.querySelector<HTMLButtonElement>('#prev')?.addEventListener('click', () => {
    if (passage) void loadPassage(Math.max(0, passage.offset - PASSAGE_LIMIT))
  })
  document.querySelector<HTMLButtonElement>('#next')?.addEventListener('click', () => {
    if (passage && !passage.last) void loadPassage(passage.offset + PASSAGE_LIMIT)
  })

  const input = document.querySelector<HTMLTextAreaElement>('#answer-input')
  if (input) {
    const wasFocused = document.activeElement === input
    if (wasFocused) {
      input.focus()
      input.selectionStart = input.selectionEnd = input.value.length
    }
    input.addEventListener('input', () => {
      answerDraft = input.value
    })
  }
  document.querySelector<HTMLButtonElement>('#use-selection')?.addEventListener('click', () => {
    const selected = window.getSelection()?.toString().trim()
    if (!selected) return
    answerDraft = selected
    const el = document.querySelector<HTMLTextAreaElement>('#answer-input')
    if (el) el.value = selected
  })
  document.querySelector<HTMLButtonElement>('#submit-answer')?.addEventListener('click', () => {
    const text = answerDraft.trim()
    if (!text) return
    send({ type: 'submit-answer', text })
  })

  // vote — hearts and golden hearts
  document.querySelectorAll<HTMLButtonElement>('button.vote').forEach((btn) => {
    btn.addEventListener('click', () => {
      const answerId = btn.dataset.id
      if (!answerId || btn.disabled) return
      send({ type: 'cast-vote', answerId, golden: btn.dataset.golden === 'true' })
    })
  })
}

async function loadPassage(offset: number): Promise<void> {
  const you = lastYou
  if (!you?.workId) return
  passageLoading = true
  passageError = ''
  renderGame()
  try {
    const url = `/collection/books/${encodeURIComponent(you.workId)}/text?offset=${offset}&limit=${PASSAGE_LIMIT}`
    const res = await fetch(url, { headers: { Accept: 'application/json' } })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    passage = (await res.json()) as PassageView
  } catch (err) {
    passage = null
    passageError = `Couldn't load your work: ${(err as Error).message}`
  } finally {
    passageLoading = false
    renderGame()
  }
}

function send(payload: Record<string, unknown>): void {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload))
  }
}

// --- Connection ---------------------------------------------------------------

function renderClosed(reason: string): void {
  selfId = null
  socket = null
  lastGame = null
  lastYou = null
  lastSig = ''
  renderJoin(reason || 'The host closed the room.')
}

function handle(message: ServerMessage): void {
  switch (message.type) {
    case 'welcome':
      selfId = message.playerId ?? null
      if (message.room) renderLobby(message.room)
      break
    case 'state':
      if (message.room && message.room.phase === 'lobby') renderLobby(message.room)
      break
    case 'game': {
      if (message.game) lastGame = message.game
      if (message.you) lastYou = message.you
      const sig = JSON.stringify([message.game, message.you])
      if (sig === lastSig) break
      lastSig = sig
      renderGame()
      break
    }
    case 'closed':
      renderClosed(message.reason ?? 'The host closed the room.')
      break
    case 'error':
      socket?.close()
      socket = null
      lastGame = null
      lastYou = null
      lastSig = ''
      renderJoin(message.message ?? 'Something went wrong.')
      break
    default:
      break
  }
}

function join(name: string, code: string): void {
  const status = document.querySelector<HTMLParagraphElement>('#status')
  if (status) status.textContent = 'Connecting…'

  lastGame = null
  lastYou = null
  lastSig = ''

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
