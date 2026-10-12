import './style.css'

// Player controller entry point.
//
// Players join by entering a name and the room code shown on the host's
// screen. In a real deploy this bundle is served by the Element itself
// (/app/static/collection/), so API calls are same-origin relative paths;
// `vite dev` proxies /collection to VITE_BACKEND_URL for local development.
//
// The lobby join flow, per-game views (Get Lit prompts, Sticker Doodle canvas,
// ...), and the realtime WebSocket connection are built on top of this shell.

type HealthStatus = {
  status: string
  element: string
}

const app = document.querySelector<HTMLDivElement>('#app')!

app.innerHTML = `
  <main class="join">
    <h1>The Public Records Collection</h1>
    <form id="join" autocomplete="off">
      <label>
        Your name
        <input id="name" name="name" maxlength="20" required />
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
    <p id="status" class="status" role="status">Checking backend…</p>
  </main>
`

async function checkBackend(): Promise<void> {
  const status = document.querySelector<HTMLParagraphElement>('#status')!
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

document.querySelector<HTMLFormElement>('#join')!.addEventListener('submit', (event) => {
  event.preventDefault()
  const data = new FormData(event.currentTarget as HTMLFormElement)
  const name = String(data.get('name') ?? '').trim()
  const code = String(data.get('code') ?? '').trim().toUpperCase()
  // TODO: open the lobby WebSocket at /collection/ws and join room `code` as `name`.
  console.log('join requested', { name, code })
})

void checkBackend()
