import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import Navbar from '../../components/Navbar'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  .mj { min-height: 100vh; background: #0a0a0a; color: #e8e8e0; font-family: 'Space Mono', monospace; display: flex; flex-direction: column; }
  .mj-main { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; }
  .mj-step-label { font-size: 10px; color: #555; letter-spacing: 0.18em; text-transform: uppercase; margin-bottom: 14px; }
  .mj-title { font-family: 'Syne', sans-serif; font-size: clamp(28px, 4vw, 48px); font-weight: 800; letter-spacing: -0.02em; text-align: center; line-height: 1; margin-bottom: 6px; }
  .mj-subtitle { font-size: 11px; color: #777; letter-spacing: 0.08em; text-align: center; margin-bottom: 48px; }
  .mj-card { width: 100%; max-width: 480px; border: 1px solid #1e1e1e; background: #0d0d0d; padding: 44px; border-radius: 2px; position: relative; overflow: hidden; }
  .mj-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 1px; background: linear-gradient(90deg, transparent, #c8ff0040, transparent); }
  .mj-field-label { font-size: 10px; color: #888; letter-spacing: 0.12em; text-transform: uppercase; margin-bottom: 14px; }
  .mj-input-wrap { position: relative; margin-bottom: 32px; }
  .mj-input { width: 100%; font-family: 'Syne', sans-serif; font-size: 36px; font-weight: 800; letter-spacing: 0.3em; text-align: center; text-transform: uppercase; background: #0a0a0a; border: 1px solid #2a2a2a; color: #e8e8e0; padding: 24px 16px; border-radius: 2px; outline: none; transition: border-color .15s; }
  .mj-input::placeholder { color: #2a2a2a; }
  .mj-input:focus { border-color: #c8ff00; }
  .mj-divider { height: 1px; background: #1a1a1a; margin-bottom: 32px; }
  .mj-cta { width: 100%; font-family: 'Space Mono', monospace; font-size: 12px; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase; color: #0a0a0a; background: #c8ff00; border: none; padding: 16px; border-radius: 2px; cursor: crosshair; transition: opacity .15s, transform .1s; }
  .mj-cta:hover { opacity: 0.88; }
  .mj-cta:active { transform: scale(0.98); }
  .mj-cta:disabled { opacity: 0.3; cursor: not-allowed; }
  .mj-error-box { background: #120a0a; border: 1px solid #ff444433; border-left: 2px solid #ff4444; padding: 14px 16px; border-radius: 2px; margin-bottom: 20px; font-size: 11px; color: #ff6666; letter-spacing: 0.04em; line-height: 1.6; word-break: break-word; }
  .mj-error-label { font-size: 9px; letter-spacing: 0.14em; text-transform: uppercase; color: #ff4444; margin-bottom: 6px; }
  .mj-back { display: inline-flex; align-items: center; gap: 8px; font-size: 10px; letter-spacing: 0.1em; color: #555; margin-bottom: 32px; transition: color .15s; cursor: crosshair; background: none; border: none; font-family: 'Space Mono', monospace; }
  .mj-back:hover { color: #e8e8e0; }
  .mj-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: mjToastIn .2s ease; }
  .mj-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes mjToastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }
`

function extractError(err) {
  const d = err?.response?.data
  if (!d) return err?.message || 'Unknown error.'
  if (typeof d === 'string') return d
  if (typeof d === 'object') return d.message || d.error || d.detail || JSON.stringify(d)
  return String(d)
}

export default function MatchJoin() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [isJoining, setIsJoining] = useState(false)
  const [joinError, setJoinError] = useState(null)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then((res) => {
        if (!res.data?.codeforcesVerified) {
          setJoinError('Your Codeforces account is not verified. Please connect your Codeforces account via OIDC before joining a match.')
        }
      })
      .catch(() => {})
  }, [])

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3500)
  }

  const onJoin = async () => {
    const trimmed = code.trim().toUpperCase()
    if (trimmed.length < 4) {
      setJoinError('Enter a valid invite code.')
      return
    }
    setIsJoining(true)
    setJoinError(null)
    try {
      await axiosInstance.post(`${API_PATHS.MATCH.JOIN}?inviteCode=${encodeURIComponent(trimmed)}`)
      showToast('Joined! Waiting for host to start…')
      // Navigate to room as guest
      setTimeout(() => navigate(`/match/${trimmed}`, { state: { role: 'guest' } }), 800)
    } catch (err) {
      const status = err?.response?.status
      const msg = extractError(err)
      setJoinError(status ? `Error ${status}: ${msg}` : `Network error — ${msg}`)
    } finally {
      setIsJoining(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') onJoin()
  }

  return (
    <>
      <style>{css}</style>
      <div className="mj">
        <Navbar onCfSaved={() => showToast('CF handle updated!')} />
        <main className="mj-main">
          <button className="mj-back" onClick={() => navigate('/dashboard')}>
            ← Back to dashboard
          </button>

          <div className="mj-step-label">Join a match</div>
          <h1 className="mj-title">Enter code.</h1>
          <p className="mj-subtitle">// paste the invite code your opponent shared</p>

          <div className="mj-card">
            <div className="mj-field-label">Invite Code</div>
            <div className="mj-input-wrap">
              <input
                className="mj-input"
                maxLength={6}
                placeholder="XXXXXX"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                onKeyDown={handleKeyDown}
                autoFocus
              />
            </div>

            <div className="mj-divider" />

            {joinError && (
              <div className="mj-error-box">
                <div className="mj-error-label">Failed to join</div>
                {joinError}
              </div>
            )}

            <button
              className="mj-cta"
              onClick={onJoin}
              disabled={isJoining || code.trim().length < 4}
            >
              {isJoining ? 'Joining…' : '⚔ Join Match'}
            </button>
          </div>
        </main>

        {toast && (
          <div className={`mj-toast ${toast.type === 'error' ? 'error' : ''}`}>
            {toast.text}
          </div>
        )}
      </div>
    </>
  )
}