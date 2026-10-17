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

type Choice = { id: string; label: string; audioUrl?: string | null }

type CryptidSubmissionView = {
  id: string
  playerId: string
  playerName: string | null
  kind: string
  label: string | null
  choiceId: string | null
  recordingId: string | null
  audioUrl: string | null
  points: number | null
}

type CryptidPublicView = {
  phase: string
  episodeId: string
  episodeTitle: string
  episodeBlurb: string
  video: string
  videoNoMusic: string
  round: number
  totalRounds: number
  roundType: string
  seeStart: number
  seeEnd: number
  recordStart: number
  recordEnd: number
  directorNotes: string
  prompts: string[]
  choices: Choice[]
  submitted: number
  players: number
  submissions: CryptidSubmissionView[]
  roundScores: ScoreEntry[]
  scores: ScoreEntry[]
}

type CryptidPrivateView = {
  phase: string
  round: number
  totalRounds: number
  roundType: string
  directorNotes: string
  prompt: string | null
  recordStart: number
  recordEnd: number
  recordSeconds: number
  choices: Choice[]
  submissionId: string | null
  recordingId: string | null
  choiceLabel: string | null
  canSubmit: boolean
  roundScore: number
  totalScore: number
}

type AnyGame = GetLitPublicView | CryptidPublicView
type AnyYou = GetLitPrivateView | CryptidPrivateView

type ServerMessage = {
  type: string
  room?: Room
  game?: AnyGame
  you?: AnyYou
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
let lastGame: AnyGame | null = null
let lastYou: AnyYou | null = null
let lastSig = ''

// Connection resilience: keep the socket alive with a heartbeat, and reclaim the
// same seat after a drop (mobile browsers suspend the page — and the socket —
// when the tab/app is backgrounded, so the connection often dies on tab switch).
let pingTimer = 0
let reconnectTimer = 0
let reconnectArmed = false
let reconnectAttempts = 0
let lastJoinName = ''
let lastJoinCode = ''

// Answer-phase scratch state, kept out of the DOM so a re-render can't wipe it.
let passage: PassageView | null = null
let passageLoading = false
let passageError = ''
let passageWorkId: string | null = null
let answerDraft = ''
let draftRound = -1

// Cryptid Cinema record-phase scratch state (also survives re-renders).
type RecState = 'idle' | 'recording' | 'ready' | 'uploading'
let recState: RecState = 'idle'
let recRound = -1
let recBlob: Blob | null = null
let recUrl = ''
let recError = ''
let recStartedAt = 0
let recTimer = 0
let recCancelled = false
let recChunks: BlobPart[] = []
let mediaRecorder: MediaRecorder | null = null
let recStream: MediaStream | null = null

// Which submissions this player has already voted on / propped this round.
let cryptidVoteRound = -1
let cryptidVoted = new Set<string>()
let cryptidPropped = new Set<string>()
let cryptidVoteError = ''

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
        <div class="lobby__code-wrap">
          <span class="lobby__code-label">Room code</span>
          <span class="lobby__code">${room.code}</span>
          <div class="lobby__copy">
            <button type="button" id="copy-code" class="ghost">Copy room code</button>
            <button type="button" id="copy-url" class="ghost">Copy join URL</button>
          </div>
        </div>
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

  bindLobby(room)
}

function bindLobby(room: Room): void {
  document.querySelector<HTMLButtonElement>('#copy-code')?.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(room.code); } catch (e) {}
  })
  document.querySelector<HTMLButtonElement>('#copy-url')?.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(location.href); } catch (e) {}
  })
  bindLeave()
}

function bindLeave(): void {
  document.querySelector<HTMLButtonElement>('#leave')?.addEventListener('click', () => {
    reconnectArmed = false
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
  const g = lastGame
  const y = lastYou
  if (!g || !y) return
  if ('episodeId' in g) {
    renderCryptid(g, y as CryptidPrivateView)
    return
  }
  const game = g
  const you = y as GetLitPrivateView

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

// --- Cryptid Cinema -----------------------------------------------------------

const ROUND_TYPE_LABELS: Record<string, string> = {
  UNDYINGALOG: 'Undyingalog',
  FOLEY: 'Foley',
  UNKNOWABLE_SFX: 'Unknowable SFX',
  UFSFX: 'Unknowable SFX',
  FOUNDTRACK: 'Foundtrack',
}

function roundTypeLabel(type: string): string {
  return ROUND_TYPE_LABELS[type] ?? type
}

function renderCryptid(game: CryptidPublicView, you: CryptidPrivateView): void {
  // Reset per-round scratch when the round changes.
  if (you.round !== recRound) {
    recRound = you.round
    resetRecording()
  }
  if (you.round !== cryptidVoteRound) {
    cryptidVoteRound = you.round
    cryptidVoted = new Set()
    cryptidPropped = new Set()
    cryptidVoteError = ''
  }

  const head =
    you.round > 0
      ? `<span>Round ${you.round} of ${you.totalRounds}</span><span>${escapeHtml(roundTypeLabel(you.roundType))}</span>`
      : `<span>${escapeHtml(game.episodeTitle)}</span>`
  const board = `<p class="board">${you.totalScore} pt${you.totalScore === 1 ? '' : 's'}</p>`

  let body = ''
  switch (you.phase) {
    case 'brief':
      body = cryptidBrief(game, you)
      break
    case 'record':
      body = cryptidRecord(game, you)
      break
    case 'vote':
      body = cryptidVote(game, you)
      break
    case 'results':
      body = cryptidResults(game, you)
      break
    case 'finished':
      body = cryptidFinished(game, you)
      break
    default:
      body = '<p class="status">Loading…</p>'
  }

  app.innerHTML = `
    <main class="game">
      <header class="game__head">${head}${board}</header>
      <section class="game__body" id="game-body">${body}</section>
      <button id="leave" class="link">Leave</button>
    </main>
  `

  bindLeave()
  bindCryptid(game, you)
}

function cryptidBrief(game: CryptidPublicView, you: CryptidPrivateView): string {
  const line = you.prompt
    ? `<h2>Your line</h2><p class="prompt">${escapeHtml(you.prompt)}</p>`
    : you.choices.length
      ? '<p class="hint">This round you\'ll pick an option rather than record.</p>'
      : '<p class="hint">No line this round — get ready to perform.</p>'
  return `
    <h2>${escapeHtml(game.episodeTitle)}</h2>
    <p class="hint">${escapeHtml(you.directorNotes)}</p>
    ${line}
    <p class="status">The host is about to roll the clip. Get ready.</p>
  `
}

function cryptidRecord(game: CryptidPublicView, you: CryptidPrivateView): string {
  if (you.submissionId) {
    const chosen = you.choiceLabel
      ? `<p class="chosen">${escapeHtml(you.choiceLabel)}</p>`
      : '<p class="chosen">Your take is in the can.</p>'
    return `
      <h2>Submitted</h2>
      ${chosen}
      <p class="status" data-state="ok">Waiting for the other players… (${game.submitted} of ${game.players})</p>
    `
  }

  if (you.choices.length) {
    const items = you.choices
      .map(
        (c) => `
      <li class="work-choice">
        <button class="work" data-choice="${escapeHtml(c.id)}">
          <span class="work__title">${escapeHtml(c.label)}</span>
        </button>
        ${c.audioUrl ? `<audio class="preview" controls preload="none" src="${escapeHtml(c.audioUrl)}"></audio>` : ''}
      </li>`,
      )
      .join('')
    const hint = you.choices.some((c) => c.audioUrl)
      ? '<p class="hint">Give each song a listen, then pick your favourite.</p>'
      : ''
    return `
      <h2>Pick your option</h2>
      ${you.prompt ? `<p class="prompt">${escapeHtml(you.prompt)}</p>` : ''}
      ${hint}
      <ul class="works">${items}</ul>
      <p class="status">${game.submitted} of ${game.players} submitted.</p>
    `
  }

  const prompt = you.prompt ? `<p class="prompt">${escapeHtml(you.prompt)}</p>` : ''
  return `
    <h2>Record your take</h2>
    ${prompt}
    ${recordControl(you)}
    <p class="status">${game.submitted} of ${game.players} submitted.</p>
  `
}

function recordControl(you: CryptidPrivateView): string {
  if (recState === 'recording') {
    return `
      <p class="hint" id="rec-count">● Recording…</p>
      <div class="answer-actions"><button id="rec-stop">Stop</button></div>
    `
  }
  if (recState === 'uploading') {
    return '<p class="status">Uploading your take…</p>'
  }
  if (recState === 'ready' && recUrl) {
    return `
      <audio id="rec-playback" controls src="${recUrl}"></audio>
      <div class="answer-actions">
        <button id="rec-again" class="ghost">Re-record</button>
        <button id="rec-submit">Submit take</button>
      </div>
      ${recError ? `<p class="status" data-state="error">${escapeHtml(recError)}</p>` : ''}
    `
  }
  const hint =
    recError
      ? `<p class="status" data-state="error">${escapeHtml(recError)}</p>`
      : `<p class="hint">Deliver your line — about ${Math.round(you.recordSeconds)}s.</p>`
  return `
    <div class="answer-actions"><button id="rec-start">Record</button></div>
    ${hint}
  `
}

// A submission's audio: the uploaded take, or — for a foundtrack song — its preview clip.
// Both come straight off the submission (the songs are dealt as private hands, so there is
// no shared option list to look the audio up in).
function submissionContent(s: CryptidSubmissionView): string {
  if (s.kind === 'recording' && s.audioUrl) {
    return `<audio controls preload="none" src="${escapeHtml(s.audioUrl)}"></audio>`
  }
  if (s.kind === 'choice' && s.audioUrl) {
    return `<audio class="preview" controls preload="none" src="${escapeHtml(s.audioUrl)}"></audio><p class="answer-card__text">${escapeHtml(s.label ?? '—')}</p>`
  }
  return `<p class="answer-card__text">${escapeHtml(s.label ?? '—')}</p>`
}

function cryptidVote(game: CryptidPublicView, you: CryptidPrivateView): string {
  const voted = cryptidVoted.size > 0
  const propped = cryptidPropped.size > 0
  const cards = game.submissions
    .filter((s) => s.id !== you.submissionId)
    .map((s) => {
      const content = submissionContent(s)
      const heart = `<button class="vote" data-sub="${escapeHtml(s.id)}" data-prop="false" ${voted ? 'disabled' : ''} title="Vote (100 points)">♥</button>`
      const star = `<button class="vote vote--golden" data-sub="${escapeHtml(s.id)}" data-prop="true" ${propped ? 'disabled' : ''} title="Prop (10 points)">★</button>`
      return `
        <li class="answer-card">
          ${content}
          <div class="answer-card__votes">${heart}${star}</div>
        </li>`
    })
    .join('')

  const note = projectEmpty(game, you)
  if (note) return `<h2>Vote on the takes</h2>${note}`
  return `
    <h2>Vote on the takes</h2>
    <p class="hint">${game.submitted} of ${game.players} submitted. Play each take, then vote ♥ (100) once and prop ★ (10) once.</p>
    ${cryptidVoteError ? `<p class="status" data-state="error">${escapeHtml(cryptidVoteError)}</p>` : ''}
    <ul class="answers">${cards}</ul>
  `
}

function projectEmpty(game: CryptidPublicView, you: CryptidPrivateView): string {
  if (game.submissions.filter((s) => s.id !== you.submissionId).length > 0) return ''
  return '<p class="hint">No other takes to vote on this round.</p>'
}

function cryptidResults(game: CryptidPublicView, you: CryptidPrivateView): string {
  const ranked = game.submissions
    .slice()
    .sort((a, b) => (b.points ?? 0) - (a.points ?? 0))
    .map((s) => {
      const mine = s.id === you.submissionId ? ' answer-card--mine' : ''
      const content = submissionContent(s)
      return `
        <li class="answer-card${mine}">
          ${content}
          <p class="answer-card__author">${escapeHtml(s.playerName ?? '?')} <span class="points">+${s.points ?? 0}</span></p>
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

function cryptidFinished(game: CryptidPublicView, you: CryptidPrivateView): string {
  const winner = game.scores[0]
  return `
    <h2>${winner ? `${escapeHtml(winner.name)} wins!` : "That's a wrap"}</h2>
    <ol class="scores">${scoreRows(game.scores)}</ol>
    <p class="hint">You finished with ${you.totalScore} point${you.totalScore === 1 ? '' : 's'}.</p>
  `
}

function bindCryptid(game: CryptidPublicView, you: CryptidPrivateView): void {
  document.querySelectorAll<HTMLButtonElement>('button.work').forEach((btn) => {
    btn.addEventListener('click', () => {
      const choiceId = btn.dataset.choice
      if (choiceId) send({ type: 'submit-answer', text: choiceId })
    })
  })

  document.querySelector<HTMLButtonElement>('#rec-start')?.addEventListener('click', () => {
    void startRecording(you)
  })
  document.querySelector<HTMLButtonElement>('#rec-stop')?.addEventListener('click', () => stopRecording())
  document.querySelector<HTMLButtonElement>('#rec-again')?.addEventListener('click', () => {
    resetRecording()
    renderCryptid(game, you)
  })
  document.querySelector<HTMLButtonElement>('#rec-submit')?.addEventListener('click', () => {
    void submitRecording(you)
  })

  document.querySelectorAll<HTMLButtonElement>('button.vote[data-sub]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const subId = btn.dataset.sub
      if (!subId || btn.disabled) return
      const prop = btn.dataset.prop === 'true'
      send({ type: 'cast-vote', answerId: subId, golden: prop })
      ;(prop ? cryptidPropped : cryptidVoted).add(subId)
      document
        .querySelectorAll<HTMLButtonElement>(`button.vote[data-sub="${subId}"]`)
        .forEach((b) => (b.disabled = true))
    })
  })

  // Only one song preview at a time — pause the others when one starts.
  const previews = Array.from(document.querySelectorAll<HTMLAudioElement>('audio.preview'))
  previews.forEach((a) => {
    a.addEventListener('play', () => {
      previews.forEach((other) => {
        if (other !== a) other.pause()
      })
    })
  })
}

async function startRecording(you: CryptidPrivateView): Promise<void> {
  recError = ''
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    recStream = stream
    recChunks = []
    recCancelled = false
    const mr = new MediaRecorder(stream)
    mediaRecorder = mr
    mr.addEventListener('dataavailable', (e) => {
      if (e.data.size) recChunks.push(e.data)
    })
    mr.addEventListener('stop', () => {
      void finalizeRecording(you)
    })
    mr.start()
    recState = 'recording'
    recStartedAt = Date.now()
    renderCryptid(lastGame as CryptidPublicView, you)
    recTimer = window.setInterval(() => {
      const left = Math.max(0, you.recordSeconds - (Date.now() - recStartedAt) / 1000)
      const el = document.getElementById('rec-count')
      if (el) el.textContent = left > 0 ? `● Recording… ${left.toFixed(0)}s` : '● Finishing…'
      if (left <= 0) stopRecording()
    }, 250)
  } catch (err) {
    recError = `Microphone unavailable: ${(err as Error).message}`
    recState = 'idle'
    renderCryptid(lastGame as CryptidPublicView, you)
  }
}

function stopRecording(): void {
  if (recTimer) {
    window.clearInterval(recTimer)
    recTimer = 0
  }
  if (mediaRecorder && mediaRecorder.state !== 'inactive') mediaRecorder.stop()
}

async function finalizeRecording(you: CryptidPrivateView): Promise<void> {
  const cancelled = recCancelled
  const raw = new Blob(recChunks, { type: mediaRecorder?.mimeType || 'audio/webm' })
  recStream?.getTracks().forEach((t) => t.stop())
  recStream = null
  mediaRecorder = null
  recCancelled = false
  if (cancelled) return
  const elapsed = Date.now() - recStartedAt
  if (elapsed < 400) {
    resetRecording()
    recError = 'That was too short — hold the note a moment.'
    renderCryptid(lastGame as CryptidPublicView, you)
    return
  }
  try {
    recBlob = await blobToWav(raw)
    if (recUrl) URL.revokeObjectURL(recUrl)
    recUrl = URL.createObjectURL(recBlob)
    recState = 'ready'
  } catch (err) {
    resetRecording()
    recError = `Couldn't process that recording: ${(err as Error).message}`
  }
  renderCryptid(lastGame as CryptidPublicView, you)
}

async function submitRecording(you: CryptidPrivateView): Promise<void> {
  if (!recBlob) return
  recState = 'uploading'
  recError = ''
  renderCryptid(lastGame as CryptidPublicView, you)
  try {
    const buf = await recBlob.arrayBuffer()
    const bytes = new Uint8Array(buf)
    const CHUNK = 8000
    if (bytes.length <= CHUNK) {
      const res = await fetch('/collection/recordings', {
        method: 'POST',
        headers: { 'Content-Type': 'audio/wav' },
        body: recBlob,
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = (await res.json()) as { id: string }
      send({ type: 'submit-answer', text: data.id })
    } else {
      const chunks = Math.ceil(bytes.length / CHUNK)
      const start = await fetch('/collection/recordings/chunked/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ totalChunks: chunks, expectedSize: bytes.length, contentType: 'audio/wav' }),
      })
      if (!start.ok) throw new Error(`HTTP ${start.status}`)
      const { uploadId } = (await start.json()) as { uploadId: string }
      for (let i = 0; i < chunks; i++) {
        const slice = bytes.slice(i * CHUNK, Math.min((i + 1) * CHUNK, bytes.length))
        const r = await fetch(`/collection/recordings/chunked/${uploadId}/${i}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/octet-stream' },
          body: slice,
        })
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
      }
      const fin = await fetch(`/collection/recordings/chunked/${uploadId}/finish`, {
        method: 'POST',
        headers: { 'Content-Type': 'audio/wav' },
      })
      if (!fin.ok) throw new Error(`HTTP ${fin.status}`)
      const data = (await fin.json()) as { id: string }
      send({ type: 'submit-answer', text: data.id })
    }
  } catch (err) {
    recError = `Upload failed: ${(err as Error).message}`
    recState = 'ready'
    renderCryptid(lastGame as CryptidPublicView, you)
  }
}

function resetRecording(): void {
  if (recTimer) {
    window.clearInterval(recTimer)
    recTimer = 0
  }
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    recCancelled = true
    try {
      mediaRecorder.stop()
    } catch {
      /* ignore */
    }
  }
  mediaRecorder = null
  recStream?.getTracks().forEach((t) => t.stop())
  recStream = null
  recChunks = []
  recBlob = null
  recState = 'idle'
  recError = ''
  if (recUrl) {
    URL.revokeObjectURL(recUrl)
    recUrl = ''
  }
}

// Decode whatever MediaRecorder produced (webm/opus in Chrome/Firefox) and re-encode
// as 16-bit mono WAV, which the Godot host can play back without extra codecs.
async function blobToWav(blob: Blob): Promise<Blob> {
  const arr = await blob.arrayBuffer()
  const ctx = new AudioContext()
  try {
    const decoded = await ctx.decodeAudioData(arr.slice(0))
    return encodeWav(decoded)
  } finally {
    void ctx.close()
  }
}

function encodeWav(buffer: AudioBuffer): Blob {
  const channels = buffer.numberOfChannels
  const frames = buffer.length
  const mono = new Float32Array(frames)
  for (let c = 0; c < channels; c++) {
    const data = buffer.getChannelData(c)
    for (let i = 0; i < frames; i++) mono[i] += data[i] / channels
  }
  const bytes = frames * 2
  const out = new ArrayBuffer(44 + bytes)
  const view = new DataView(out)
  const writeStr = (offset: number, s: string) => {
    for (let i = 0; i < s.length; i++) view.setUint8(offset + i, s.charCodeAt(i))
  }
  writeStr(0, 'RIFF')
  view.setUint32(4, 36 + bytes, true)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, buffer.sampleRate, true)
  view.setUint32(28, buffer.sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeStr(36, 'data')
  view.setUint32(40, bytes, true)
  let offset = 44
  for (let i = 0; i < frames; i++) {
    const s = Math.max(-1, Math.min(1, mono[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
    offset += 2
  }
  return new Blob([out], { type: 'audio/wav' })
}

async function loadPassage(offset: number): Promise<void> {
  const you = lastYou as GetLitPrivateView | null
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
      reconnectAttempts = 0
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
      reconnectArmed = false
      renderClosed(message.reason ?? 'The host closed the room.')
      break
    case 'error':
      reconnectArmed = false
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

  lastJoinName = name
  lastJoinCode = code
  reconnectArmed = true

  lastGame = null
  lastYou = null
  lastSig = ''

  const ws = new WebSocket(wsUrl())
  socket = ws
  ws.addEventListener('open', () => {
    // Reclaim our seat if we already have one (reconnect), else take a new one.
    ws.send(JSON.stringify({ type: 'join', code, name, playerId: selfId ?? undefined }))
    startHeartbeat()
  })
  ws.addEventListener('message', (event) => {
    handle(JSON.parse(String(event.data)) as ServerMessage)
  })
  ws.addEventListener('close', () => {
    if (socket !== ws) return
    stopHeartbeat()
    socket = null
    attemptReconnect()
  })
  ws.addEventListener('error', () => {
    // A failed connect also fires `close`; reconnect is handled there.
  })
}

function startHeartbeat(): void {
  stopHeartbeat()
  pingTimer = window.setInterval(() => send({ type: 'ping' }), 20000)
}

function stopHeartbeat(): void {
  if (pingTimer) {
    window.clearInterval(pingTimer)
    pingTimer = 0
  }
}

function attemptReconnect(): void {
  if (!reconnectArmed || !lastJoinCode) return
  if (reconnectTimer) return
  reconnectAttempts += 1
  if (reconnectAttempts > 6) {
    reconnectArmed = false
    renderJoin('Connection lost. Check the room code and try again.')
    return
  }
  const status = document.querySelector<HTMLParagraphElement>('#status')
  if (status && document.getElementById('app')) status.textContent = 'Reconnecting…'
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = 0
    if (reconnectArmed && lastJoinCode) join(lastJoinName, lastJoinCode)
  }, 1500)
}

// Mobile browsers suspend the page (and its socket) when backgrounded; on return
// the socket is usually dead, so reconnect rather than dumping to the join screen.
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') return
  if (!reconnectArmed || !lastJoinCode) return
  if (socket && socket.readyState === WebSocket.OPEN) return
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = 0
  }
  join(lastJoinName, lastJoinCode)
})

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
