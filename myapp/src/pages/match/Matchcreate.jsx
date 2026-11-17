import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import Navbar from '../../components/Navbar'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=Bebas+Neue&display=swap');

  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  :root {
    --bg:       #060606;
    --surface:  #0f0f0f;
    --border:   #252525;
    --border2:  #363636;
    --accent:   #c8ff00;
    --accent-dim: rgba(200,255,0,0.09);
    --text:     #f0f0e8;
    --muted:    #606060;
    --muted2:   #909090;
    --err:      #ff4d4d;
    --err-bg:   #0f0606;
    --warn:     #ff8800;
    --radius:   2px;
  }

  .mc {
    min-height: 100vh;
    background: var(--bg);
    color: var(--text);
    font-family: 'IBM Plex Mono', monospace;
    -webkit-font-smoothing: antialiased;
    display: flex;
    flex-direction: column;
  }

  /* ── Layout ── */
  .mc-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 40px 20px 64px;
  }

  .mc-inner {
    width: 100%;
    max-width: 520px;
    display: flex;
    flex-direction: column;
  }

  /* ── Back nav ── */
  .mc-back {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-family: 'IBM Plex Mono', monospace;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--muted2);
    background: none;
    border: none;
    cursor: pointer;
    padding: 0;
    margin-bottom: 40px;
    transition: color 0.15s;
  }
  .mc-back:hover { color: var(--text); }
  .mc-back svg { width: 12px; height: 12px; }

  /* ── Header ── */
  .mc-header {
    margin-bottom: 32px;
  }
  .mc-step {
    font-size: 15px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--muted2);
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .mc-step::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--border);
  }
  .mc-title {
    font-family: 'Bebas Neue', sans-serif;
    font-size: clamp(60px, 12vw, 75px);
    letter-spacing: 0.04em;
    line-height: 0.92;
    color: var(--text);
    margin-bottom: 10px;
  }
  .mc-subtitle {
    font-size: 15px;
    color: var(--muted2);
    letter-spacing: 0.1em;
  }

  /* ── Card ── */
  .mc-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
  }

  /* Card section = padded block with optional divider */
  .mc-section {
    padding: 24px;
    border-bottom: 1px solid var(--border);
  }
  .mc-section:last-child { border-bottom: none; }

  /* ── Field label ── */
  .mc-label {
    font-size: 11px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--muted2);
    margin-bottom: 14px;
  }

  /* ── Duration grid ── */
  .mc-duration-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
  }
  .mc-dur-btn {
    font-family: 'IBM Plex Mono', monospace;
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 16px 7px;
    cursor: pointer;
    transition: border-color 0.15s, background 0.15s, transform 0.1s;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }
  .mc-dur-btn .val {
    font-family: 'Bebas Neue', sans-serif;
    font-size: 28px;
    letter-spacing: 0.04em;
    color: var(--muted2);
    line-height: 1;
  }
  .mc-dur-btn .unit {
    font-size: 9px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--muted2);
  }
  .mc-dur-btn:hover { border-color: var(--border2); transform: scale(1.02); }
  .mc-dur-btn:hover .val { color: var(--text); }
  .mc-dur-btn.active {
    border-color: var(--accent);
    background: var(--accent-dim);
  }
  .mc-dur-btn.active .val { color: var(--accent); }
  .mc-dur-btn.active .unit { color: var(--accent); opacity: 0.6; }

  /* ── Difficulty grid ── */
  .mc-diff-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
  }
  .mc-diff-btn {
    font-family: 'IBM Plex Mono', monospace;
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 14px 6px;
    cursor: pointer;
    transition: border-color 0.15s, background 0.15s, transform 0.1s;
    text-align: center;
  }
  .mc-diff-btn .val {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.1em;
    color: var(--muted2);
    text-transform: uppercase;
  }
  .mc-diff-btn:hover { border-color: var(--border2); transform: scale(1.02); }
  .mc-diff-btn:hover .val { color: var(--text); }
  .mc-diff-btn.active {
    border-color: var(--accent);
    background: var(--accent-dim);
  }
  .mc-diff-btn.active .val { color: var(--accent); }

  /* ── Error ── */
  .mc-error {
    background: var(--err-bg);
    border-left: 2px solid var(--err);
    padding: 12px 14px;
    border-radius: var(--radius);
    margin-bottom: 16px;
  }
  .mc-error-label {
    font-size: 10px;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    color: var(--err);
    margin-bottom: 4px;
  }
  .mc-error-msg {
    font-size: 12px;
    color: #ff8080;
    line-height: 1.55;
    word-break: break-word;
  }

  /* ── CTA ── */
  .mc-cta {
    width: 100%;
    font-family: 'IBM Plex Mono', monospace;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: #060606;
    background: var(--accent);
    border: none;
    padding: 18px;
    border-radius: var(--radius);
    cursor: pointer;
    transition: opacity 0.15s, transform 0.1s;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }
  .mc-cta:hover { opacity: 0.88; }
  .mc-cta:active { transform: scale(0.985); }
  .mc-cta:disabled { opacity: 0.2; cursor: not-allowed; transform: none; }

  /* ── Status bar ── */
  .mc-status {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .mc-dot {
    width: 6px; height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
    background: var(--warn);
    animation: blink 1.6s ease-in-out infinite;
  }
  .mc-dot.ready { background: var(--accent); animation: none; }
  @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0.2} }
  .mc-status-text {
    font-size: 12px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--muted2);
  }
  .mc-status-text.ready { color: var(--accent); }

  /* ── Invite code ── */
  .mc-code-wrap {
    cursor: pointer;
    user-select: none;
    transition: opacity 0.15s;
  }
  .mc-code-wrap:hover { opacity: 0.75; }
  .mc-code-display {
    display: flex;
    align-items: baseline;
    gap: 0;
    margin-bottom: 8px;
  }
  .mc-code-char {
    font-family: 'Bebas Neue', sans-serif;
    font-size: clamp(60px, 15vw, 88px);
    letter-spacing: 0.06em;
    color: var(--accent);
    line-height: 1;
    flex: 1;
    text-align: center;
  }
  .mc-copy-hint {
    font-size: 11px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--muted2);
  }

  /* ── Meta grid ── */
  .mc-meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1px;
    background: var(--border);
    border-top: 1px solid var(--border);
  }
  .mc-meta-item {
    background: var(--surface);
    padding: 16px 20px;
  }
  .mc-meta-key {
    font-size: 10px;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    color: var(--muted2);
    margin-bottom: 6px;
  }
  .mc-meta-val {
    font-family: 'Bebas Neue', sans-serif;
    font-size: 24px;
    letter-spacing: 0.06em;
    color: var(--text);
  }

  /* ── Action row ── */
  .mc-actions {
    display: grid;
    grid-template-columns: 1fr 1fr 2fr;
    gap: 8px;
  }
  .mc-btn-ghost {
    font-family: 'IBM Plex Mono', monospace;
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--muted2);
    background: none;
    border: 1px solid var(--border);
    padding: 14px 8px;
    border-radius: var(--radius);
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s;
    white-space: nowrap;
  }
  .mc-btn-ghost:hover { color: var(--text); border-color: var(--border2); }

  .mc-btn-accent {
    font-family: 'IBM Plex Mono', monospace;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: #060606;
    background: var(--accent);
    border: none;
    padding: 14px;
    border-radius: var(--radius);
    cursor: pointer;
    transition: opacity 0.15s;
    white-space: nowrap;
  }
  .mc-btn-accent:hover { opacity: 0.85; }
  .mc-btn-accent:disabled {
    background: var(--border2);
    color: var(--muted);
    cursor: not-allowed;
    opacity: 1;
  }

  /* ── Toast ── */
  .mc-toast {
    position: fixed;
    top: 80px;
  right: 20px;
  left: auto;
  transform: none;
    font-size: 12px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    padding: 10px 20px;
    border-radius: var(--radius);
    border-left: 2px solid var(--accent);
    background: #0a1000;
    color: var(--accent);
    white-space: nowrap;
    z-index: 200;
    animation: toastIn 0.2s ease;
  }
  .mc-toast.err { border-color: var(--err); background: var(--err-bg); color: #ff8080; }
  @keyframes toastIn {
    from { opacity: 0; transform: translateX(-50%) translateY(8px); }
    to   { opacity: 1; transform: translateX(-50%) translateY(0); }
  }

  @media (max-width: 480px) {
    .mc-duration-grid { grid-template-columns: repeat(3, 1fr); }
    .mc-actions { grid-template-columns: 1fr 1fr; }
    .mc-btn-accent { grid-column: 1 / -1; }
  }
`

const DURATIONS = [15, 30, 45, 60]
const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD']

function extractError(err) {
  const d = err?.response?.data
  if (!d) return err?.message || 'Unknown error.'
  if (typeof d === 'string') return d
  if (typeof d === 'object') return d.message || d.error || d.detail || JSON.stringify(d)
  return String(d)
}

function InviteCodeDisplay({ code }) {
  return (
    <div className="mc-code-display">
      {(code || '').split('').map((ch, i) => (
        <span key={i} className="mc-code-char">{ch}</span>
      ))}
    </div>
  )
}

export default function MatchCreate() {
  const navigate = useNavigate()

  const [duration, setDuration]       = useState(30)
  const [difficulty, setDifficulty]   = useState('EASY')
  const [isCreating, setIsCreating]   = useState(false)
  const [isStarting, setIsStarting]   = useState(false)
  const [match, setMatch]             = useState(null)
  const [matchStatus, setMatchStatus] = useState(null)
  const [createError, setCreateError] = useState(null)
  const [toast, setToast]             = useState(null)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3200)
  }

  const loadMatchStatus = useCallback(async (code) => {
    if (!code) return
    try {
      const res = await axiosInstance.get(
        `${API_PATHS.MATCH.STATUS}?inviteCode=${encodeURIComponent(code)}`
      )
      setMatchStatus(res?.data || null)
    } catch { /* fail silently */ }
  }, [])

  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then((res) => {
        if (!res.data?.codeforcesVerified && !res.data?.cfVerified) {
          setCreateError('Your Codeforces account is not verified. Please connect your Codeforces account via OIDC before creating a match.')
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!match?.inviteCode) { setMatchStatus(null); return }
    loadMatchStatus(match.inviteCode)
    const t = setInterval(() => loadMatchStatus(match.inviteCode), 3000)
    return () => clearInterval(t)
  }, [match?.inviteCode, loadMatchStatus])

  const onCreateMatch = async () => {
    setIsCreating(true)
    setCreateError(null)
    try {
      const payload = { duration: Number(duration), difficulty: difficulty }
      const res = await axiosInstance.post(API_PATHS.MATCH.CREATE, payload)
      const data = res?.data
      if (!data)            { setCreateError('Server returned an empty response.'); return }
      if (!data.inviteCode) { setCreateError(`Missing inviteCode: ${JSON.stringify(data)}`); return }
      setMatch(data)
      showToast('Match created — share the code!')
    } catch (err) {
      const status = err?.response?.status
      const msg = extractError(err)
      setCreateError(status ? `Error ${status}: ${msg}` : `Network error — ${msg}`)
    } finally {
      setIsCreating(false)
    }
  }

  const onStartMatch = async () => {
    setIsStarting(true)
    try {
      await axiosInstance.post(`${API_PATHS.MATCH.START}?inviteCode=${encodeURIComponent(match.inviteCode)}`)
      navigate(`/match/${match.inviteCode}`, { state: { role: 'host' } })
    } catch (err) {
      showToast(extractError(err) || 'Not ready yet.', 'err')
    } finally {
      setIsStarting(false)
    }
  }

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(match.inviteCode)
      showToast('Copied to clipboard')
    } catch {
      showToast('Copy failed.', 'err')
    }
  }

  const isReady       = matchStatus?.status === 'READY'
  const currentStatus = matchStatus?.status || match?.status || 'WAITING'

  return (
    <>
      <style>{css}</style>
      <div className="mc">
        <Navbar onCfSaved={() => showToast('CF handle updated!')} />

        <main className="mc-main">
          <div className="mc-inner">

            <button className="mc-back" onClick={() => navigate('/dashboard')}>
              <svg viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M8 2L4 6l4 4"/>
              </svg>
              Back to dashboard
            </button>

            {!match ? (
              <>
                <div className="mc-header">
                  <div className="mc-step">Step 1 of 2</div>
                  <h1 className="mc-title">Configure<br/>Match.</h1>
                  <p className="mc-subtitle">// set duration · generate invite code</p>
                </div>

                <div className="mc-card">
                  <div className="mc-section">
                    <div className="mc-label">Difficulty</div>
                    <div className="mc-diff-grid">
                      {DIFFICULTIES.map((diff) => (
                        <button
                          key={diff}
                          className={`mc-diff-btn ${difficulty === diff ? 'active' : ''}`}
                          onClick={() => setDifficulty(diff)}
                        >
                          <span className="val">{diff}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="mc-section">
                    <div className="mc-label">Match Duration</div>
                    <div className="mc-duration-grid">
                      {DURATIONS.map((d) => (
                        <button
                          key={d}
                          className={`mc-dur-btn ${duration === d ? 'active' : ''}`}
                          onClick={() => setDuration(d)}
                        >
                          <span className="val">{d}</span>
                          <span className="unit">min</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="mc-section">
                    {createError && (
                      <div className="mc-error">
                        <div className="mc-error-label">Error</div>
                        <div className="mc-error-msg">{createError}</div>
                      </div>
                    )}
                    <button className="mc-cta" onClick={onCreateMatch} disabled={isCreating}>
                      {isCreating ? 'Creating…' : `⚔ Create ${duration}-min Match`}
                    </button>
                  </div>
                </div>
              </>
            ) : (
              <>
                <div className="mc-header">
                  <div className="mc-step">Step 2 of 2</div>
                  <h1 className="mc-title">Share &amp; Wait.</h1>
                  <p className="mc-subtitle">// send the code · start when rival joins</p>
                </div>

                <div className="mc-card">
                  {/* Status */}
                  <div className="mc-section">
                    <div className="mc-status">
                      <span className={`mc-dot ${isReady ? 'ready' : ''}`} />
                      <span className={`mc-status-text ${isReady ? 'ready' : ''}`}>
                        {isReady ? 'Opponent joined — ready to start' : 'Waiting for opponent to join…'}
                      </span>
                    </div>
                  </div>

                  {/* Invite code */}
                  <div className="mc-section" style={{ paddingBottom: 20 }}>
                    <div className="mc-label">Invite Code</div>
                    <div className="mc-code-wrap" onClick={copyCode} title="Click to copy">
                      <InviteCodeDisplay code={match.inviteCode} />
                      <div className="mc-copy-hint">Click to copy</div>
                    </div>
                  </div>

                  {/* Meta */}
                  <div className="mc-meta">
                    <div className="mc-meta-item">
                      <div className="mc-meta-key">Difficulty</div>
                      <div className="mc-meta-val" style={{ fontSize: '18px' }}>{difficulty}</div>
                    </div>
                    <div className="mc-meta-item">
                      <div className="mc-meta-key">Duration</div>
                      <div className="mc-meta-val" style={{ fontSize: '18px' }}>{duration} min</div>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="mc-section" style={{ borderTop: '1px solid var(--border)' }}>
                    <div className="mc-actions">
                      <button className="mc-btn-ghost" onClick={copyCode}>Copy Code</button>
                      <button
                        className="mc-btn-ghost"
                        onClick={() => navigate(`/match/${match.inviteCode}`, { state: { role: 'host' } })}
                      >
                        Open Room
                      </button>
                      <button
                        className="mc-btn-accent"
                        onClick={onStartMatch}
                        disabled={isStarting || !isReady}
                        title={!isReady ? 'Waiting for opponent' : 'Start now'}
                      >
                        {isStarting ? 'Starting…' : '⚔ Start Match'}
                      </button>
                    </div>
                  </div>
                </div>
              </>
            )}

          </div>
        </main>

        {toast && (
          <div className={`mc-toast ${toast.type === 'err' ? 'err' : ''}`}>
            {toast.text}
          </div>
        )}
      </div>
    </>
  )
}