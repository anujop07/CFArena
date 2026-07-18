import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import Navbar from '../../components/Navbar'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=Bebas+Neue&display=swap');

  :root {
    --bg: #060606; --surface: #0f0f0f; --border: #252525; --border2: #363636;
    --accent: #c8ff00; --accent-dim: rgba(200,255,0,0.09);
    --text: #f0f0e8; --muted: #606060; --muted2: #909090;
    --err: #ff4d4d; --radius: 2px;
  }

  .tc { min-height: 100vh; background: var(--bg); color: var(--text); font-family: 'IBM Plex Mono', monospace; display: flex; flex-direction: column; }
  .tc-main { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 40px 20px 64px; }
  .tc-inner { width: 100%; max-width: 560px; }

  .tc-back { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-family: 'IBM Plex Mono', monospace; letter-spacing: 0.12em; text-transform: uppercase; color: var(--muted2); background: none; border: none; cursor: pointer; padding: 0; margin-bottom: 40px; transition: color 0.15s; }
  .tc-back:hover { color: var(--text); }
  .tc-back svg { width: 12px; height: 12px; }

  .tc-header { margin-bottom: 32px; }
  .tc-step { font-size: 15px; letter-spacing: 0.22em; text-transform: uppercase; color: var(--muted2); margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
  .tc-step::after { content: ''; flex: 1; height: 1px; background: var(--border); }
  .tc-title { font-family: 'Bebas Neue', sans-serif; font-size: clamp(48px, 10vw, 64px); letter-spacing: 0.04em; line-height: 0.92; margin-bottom: 8px; }
  .tc-subtitle { font-size: 12px; color: var(--muted); letter-spacing: 0.08em; }

  .tc-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; }

  .tc-section { padding: 24px; border-bottom: 1px solid var(--border); }
  .tc-section:last-child { border-bottom: none; }

  .tc-label { font-size: 10px; font-weight: 600; letter-spacing: 0.18em; text-transform: uppercase; color: var(--muted2); margin-bottom: 12px; }

  .tc-input { width: 100%; background: var(--bg); border: 1px solid var(--border); color: var(--text); font-family: 'IBM Plex Mono', monospace; font-size: 14px; padding: 12px 16px; border-radius: var(--radius); outline: none; transition: border-color 0.15s; }
  .tc-input:focus { border-color: var(--accent); }
  .tc-input::placeholder { color: #444; }

  .tc-select-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .tc-select-btn { background: var(--bg); border: 1px solid var(--border); color: var(--muted2); font-family: 'IBM Plex Mono', monospace; font-size: 13px; font-weight: 500; padding: 14px 8px; border-radius: var(--radius); cursor: pointer; text-align: center; transition: all 0.15s; }
  .tc-select-btn:hover { border-color: var(--border2); color: var(--text); }
  .tc-select-btn.active { border-color: var(--accent); color: var(--accent); background: var(--accent-dim); }

  .tc-diff-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .tc-diff-btn { background: var(--bg); border: 1px solid var(--border); color: var(--muted2); font-family: 'IBM Plex Mono', monospace; font-size: 13px; font-weight: 500; padding: 14px 8px; border-radius: var(--radius); cursor: pointer; text-align: center; transition: all 0.15s; text-transform: uppercase; letter-spacing: 0.08em; }
  .tc-diff-btn:hover { border-color: var(--border2); color: var(--text); }
  .tc-diff-btn.active { border-color: var(--accent); color: var(--accent); background: var(--accent-dim); }

  .tc-dur-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
  .tc-dur-btn { background: var(--bg); border: 1px solid var(--border); color: var(--muted2); font-family: 'IBM Plex Mono', monospace; font-size: 13px; font-weight: 500; padding: 14px 8px; border-radius: var(--radius); cursor: pointer; text-align: center; transition: all 0.15s; }
  .tc-dur-btn:hover { border-color: var(--border2); color: var(--text); }
  .tc-dur-btn.active { border-color: var(--accent); color: var(--accent); background: var(--accent-dim); }

  .tc-error { background: #0f0606; border-left: 2px solid var(--err); padding: 12px 14px; border-radius: var(--radius); margin-bottom: 16px; }
  .tc-error-label { font-size: 10px; letter-spacing: 0.18em; text-transform: uppercase; color: var(--err); margin-bottom: 4px; }
  .tc-error-msg { font-size: 12px; color: #ff8080; line-height: 1.55; }

  .tc-cta { width: 100%; font-family: 'IBM Plex Mono', monospace; font-size: 13px; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; color: #060606; background: var(--accent); border: none; padding: 18px; border-radius: var(--radius); cursor: pointer; transition: opacity 0.15s, transform 0.1s; }
  .tc-cta:hover { opacity: 0.88; }
  .tc-cta:active { transform: scale(0.985); }
  .tc-cta:disabled { opacity: 0.2; cursor: not-allowed; transform: none; }

  .tc-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: toastIn .2s ease; }
  .tc-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes toastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @media (max-width: 480px) {
    .tc-select-grid, .tc-diff-grid { grid-template-columns: repeat(2, 1fr); }
    .tc-dur-grid { grid-template-columns: repeat(2, 1fr); }
  }
`

const PLAYER_SIZES = [8, 16, 32]
const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD']
const DURATIONS = [15, 30, 45, 60]

export default function TournamentCreate() {
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [maxPlayers, setMaxPlayers] = useState(8)
  const [difficulty, setDifficulty] = useState('EASY')
  const [duration, setDuration] = useState(30)
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const totalRounds = Math.log2(maxPlayers)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3200)
  }

  const onSubmit = async () => {
    if (!name.trim()) { setError('Tournament name is required.'); return }

    setIsCreating(true)
    setError(null)

    try {
      const payload = {
        name: name.trim(),
        maxPlayers,
        difficulty,
        matchDurationMinutes: duration
      }

      const res = await axiosInstance.post(API_PATHS.TOURNAMENT.CREATE, payload)
      showToast('Tournament created!')

      // Navigate to tournament bracket page
      setTimeout(() => navigate(`/tournament/${res.data.id}`), 500)
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.data || 'Failed to create tournament.'
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg))
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <>
      <style>{css}</style>
      <div className="tc">
        <Navbar />

        <main className="tc-main">
          <div className="tc-inner">

            <button className="tc-back" onClick={() => navigate('/tournaments')}>
              <svg viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M8 2L4 6l4 4"/>
              </svg>
              Back to tournaments
            </button>

            <div className="tc-header">
              <div className="tc-step">Create Tournament</div>
              <h1 className="tc-title">New Arena.</h1>
              <p className="tc-subtitle">// configure your bracket tournament</p>
            </div>

            <div className="tc-card">

              {/* Tournament Name */}
              <div className="tc-section">
                <div className="tc-label">Tournament Name</div>
                <input
                  className="tc-input"
                  type="text"
                  placeholder="Weekend Blitz Cup"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={50}
                />
              </div>

              {/* Max Players */}
              <div className="tc-section">
                <div className="tc-label">Max Players</div>
                <div className="tc-select-grid">
                  {PLAYER_SIZES.map((size) => (
                    <button
                      key={size}
                      className={`tc-select-btn ${maxPlayers === size ? 'active' : ''}`}
                      onClick={() => setMaxPlayers(size)}
                    >
                      {size}
                    </button>
                  ))}
                </div>
              </div>

              {/* Difficulty */}
              <div className="tc-section">
                <div className="tc-label">Difficulty</div>
                <div className="tc-diff-grid">
                  {DIFFICULTIES.map((diff) => (
                    <button
                      key={diff}
                      className={`tc-diff-btn ${difficulty === diff ? 'active' : ''}`}
                      onClick={() => setDifficulty(diff)}
                    >
                      {diff}
                    </button>
                  ))}
                </div>
              </div>

              {/* Match Duration */}
              <div className="tc-section">
                <div className="tc-label">Match Duration</div>
                <div className="tc-dur-grid">
                  {DURATIONS.map((d) => (
                    <button
                      key={d}
                      className={`tc-dur-btn ${duration === d ? 'active' : ''}`}
                      onClick={() => setDuration(d)}
                    >
                      {d} min
                    </button>
                  ))}
                </div>
              </div>

              {/* Submit */}
              <div className="tc-section">
                {error && (
                  <div className="tc-error">
                    <div className="tc-error-label">Error</div>
                    <div className="tc-error-msg">{error}</div>
                  </div>
                )}
                <button className="tc-cta" onClick={onSubmit} disabled={isCreating}>
                  {isCreating ? 'Creating…' : '🏆 Create Tournament'}
                </button>
              </div>

            </div>

          </div>
        </main>

        {toast && <div className={`tc-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>}
      </div>
    </>
  )
}
