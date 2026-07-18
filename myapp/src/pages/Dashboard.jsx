import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosInstance from '../utils/axiosInstance'
import { API_PATHS } from '../utils/apiPaths'
import { fetchCFAvatar } from '../utils/cfApi'
import Navbar from '../components/Navbar'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');

  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  .db { min-height: 100vh; background: #0a0a0a; color: #e8e8e0; font-family: 'Space Mono', monospace; -webkit-font-smoothing: antialiased; display: flex; flex-direction: column; }

  .db-main { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px 24px; }
  .db-title { font-family: 'Syne', sans-serif; font-size: clamp(32px, 5vw, 56px); font-weight: 800; letter-spacing: -0.02em; text-align: center; line-height: 1; margin-bottom: 8px; }
  .db-subtitle { font-size: 17px; color: #777; letter-spacing: 0.1em; text-align: center; margin-bottom: 52px; }

  .db-actions { display: flex; gap: 16px; width: 100%; max-width: 900px; margin-bottom: 36px; }

  .db-action-card { flex: 1.5; border: 1px solid #1e1e1e; background: #0d0d0d; padding: 60px 32px 48px; display: flex; flex-direction: column; align-items: center; cursor: crosshair; transition: border-color .2s, background .2s; position: relative; overflow: hidden; }
  .db-action-card::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, #c8ff0008 0%, transparent 60%);
    opacity: 0;
    transition: opacity .2s;
    pointer-events: none; /* ← FIX: was blocking button clicks */
  }
  .db-action-card:hover { border-color: #c8ff00; background: #0f0f0d; }
  .db-action-card:hover::before { opacity: 1; }

  .db-action-icon { font-size: 34px; margin-bottom: 14px; line-height: 1; }
  .db-action-label { font-family: 'Syne', sans-serif; font-size: clamp(16px, 1.8vw, 22px); font-weight: 800; letter-spacing: 0.04em; margin-bottom: 8px; text-transform: uppercase; word-break: break-word; text-align: center; width: 100%; }
  .db-action-desc { font-size: 15px; color: #888; letter-spacing: 0.04em; text-align: center; line-height: 1.7; margin-bottom: 28px; }

  .db-cta { width: 100%; font-family: 'Space Mono', monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: #0a0a0a; background: #c8ff00; border: none; padding: 13px; border-radius: 2px; cursor: crosshair; transition: opacity .15s, transform .1s; position: relative; z-index: 1; }
  .db-cta:hover { opacity: 0.88; }
  .db-cta:active { transform: scale(0.98); }
  .db-cta:disabled { opacity: 0.3; cursor: not-allowed; }

  .db-cta-ghost { width: 100%; font-family: 'Space Mono', monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: #c8ff00; background: none; border: 1px solid #c8ff00; padding: 12px; border-radius: 2px; cursor: crosshair; transition: background .15s, color .15s; position: relative; z-index: 1; }
  .db-cta-ghost:hover { background: #c8ff00; color: #0a0a0a; }

  /* ── Modal ── */
  .db-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.88); display: flex; align-items: center; justify-content: center; z-index: 100; backdrop-filter: blur(4px); animation: fadeIn .15s ease; }
  @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
  .db-modal { background: #0f0f0f; border: 1px solid #2a2a2a; padding: 36px; width: 100%; max-width: 400px; border-radius: 2px; animation: slideUp .2s ease; }
  @keyframes slideUp { from { transform: translateY(12px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
  .db-modal-title { font-family: 'Syne', sans-serif; font-size: 22px; font-weight: 800; letter-spacing: 0.04em; margin-bottom: 4px; text-transform: uppercase; }
  .db-modal-sub { font-size: 11px; color: #888; letter-spacing: 0.06em; margin-bottom: 28px; line-height: 1.7; }
  .db-modal-field { margin-bottom: 16px; }
  .db-modal-field label { display: block; font-size: 10px; color: #888; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 8px; }
  .db-modal-field input { width: 100%; background: #0a0a0a; border: 1px solid #2a2a2a; color: #e8e8e0; font-family: 'Space Mono', monospace; font-size: 13px; padding: 10px 14px; border-radius: 2px; outline: none; transition: border-color .15s; }
  .db-modal-field input:focus { border-color: #c8ff00; }
  .db-modal-field input::placeholder { color: #444; }
  .db-modal-actions { display: flex; gap: 8px; margin-top: 24px; }
  .db-btn-cancel { font-family: 'Space Mono', monospace; font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: #888; background: none; border: 1px solid #2a2a2a; padding: 11px 20px; border-radius: 2px; cursor: crosshair; transition: color .15s, border-color .15s; flex: 1; }
  .db-btn-cancel:hover { color: #e8e8e0; border-color: #666; }

  /* ── Toast ── */
  .db-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: toastIn .2s ease; }
  .db-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes toastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @media (max-width: 580px) {
    .db-actions { flex-direction: column; }
  }
`

export default function Dashboard() {
  const navigate = useNavigate()

  const [modal, setModal] = useState(null)
  const [joinCode, setJoinCode] = useState('')
  const [isJoining, setIsJoining] = useState(false)
  const [toast, setToast] = useState(null)
  const [avatar, setAvatar] = useState(null)

  const [cfHandle, setCfHandle] = useState('')
  const [isSavingCf, setIsSavingCf] = useState(false)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3000)
  }

  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then(async (res) => { 
        if (!res.data?.cfHandle) {
          setModal('cf') 
        } else {
          const avUrl = await fetchCFAvatar(res.data.cfHandle)
          if (avUrl) setAvatar(avUrl)
        }
      })
      .catch(() => {})
  }, [])

  const onSaveCfHandle = async () => {
    if (!cfHandle.trim()) { showToast('Enter your Codeforces handle.', 'error'); return }
    setIsSavingCf(true)
    try {
      await axiosInstance.post(API_PATHS.USER.ADD_CF_HANDLE, { cfHandle: cfHandle.trim() })
      showToast("Handle saved. You're all set!")
      setModal(null)
    } catch (err) {
      const m = err?.response?.data?.message || err?.response?.data || 'Failed to save handle.'
      showToast(typeof m === 'string' ? m : 'Failed.', 'error')
    } finally { setIsSavingCf(false) }
  }

  const onJoinMatch = async () => {
    if (!joinCode.trim()) { showToast('Enter an invite code.', 'error'); return }
    setIsJoining(true)
    try {
      const code = joinCode.trim().toUpperCase()
      const res = await axiosInstance.post(`${API_PATHS.MATCH.JOIN}?inviteCode=${encodeURIComponent(code)}`)
      showToast('Joined! Opening room…')
      setModal(null)
      navigate(`/match/${code}`, { state: { role: 'guest', joinedMatch: res?.data || null } })
    } catch (err) {
      const m = err?.response?.data?.message || err?.response?.data || 'Could not join.'
      showToast(typeof m === 'string' ? m : 'Failed.', 'error')
    } finally { setIsJoining(false) }
  }

  return (
    <>
      <style>{css}</style>
      <div className="db">

        <Navbar onCfSaved={() => showToast('CF handle updated!')} />

        <main className="db-main">
          {avatar && (
            <div style={{ marginBottom: '24px', animation: 'fadeIn 0.5s ease-out' }}>
              <img src={avatar} alt="Codeforces Avatar" style={{ width: '90px', height: '90px', borderRadius: '50%', border: '2px solid #c8ff00', boxShadow: '0 0 24px rgba(200,255,0,0.3)', objectFit: 'cover' }} />
            </div>
          )}
          <h1 className="db-title">Start a duel.</h1>
          <p className="db-subtitle">// pick your move</p>

          <div className="db-actions">
            <div className="db-action-card">
              <div className="db-action-icon">⚔</div>
              <div className="db-action-label">Create</div>
              <p className="db-action-desc">Host a new match. Get an invite code and wait for your rival.</p>
              <button className="db-cta" onClick={() => navigate('/match/create')}>
                Create Match
              </button>
            </div>

            <div className="db-action-card">
              <div className="db-action-icon">⊕</div>
              <div className="db-action-label">Join</div>
              <p className="db-action-desc">Have a code? Enter it and jump straight into the fight.</p>
              <button className="db-cta-ghost" onClick={() => setModal('join')}>
                Join Match
              </button>
            </div>

            <div className="db-action-card">
              <div className="db-action-icon">🏆</div>
              <div className="db-action-label">Tournaments</div>
              <p className="db-action-desc">Compete in bracket-style arenas. Create or join tournaments.</p>
              <button className="db-cta-ghost" onClick={() => navigate('/tournaments')}>
                View Tournaments
              </button>
            </div>
          </div>
        </main>

        {/* ── CF Handle modal (OAuth first-time) ── */}
        {modal === 'cf' && (
          <div className="db-overlay">
            <div className="db-modal">
              <div className="db-modal-title">One last step</div>
              <div className="db-modal-sub">
                You signed in with Google. Enter your Codeforces handle to start dueling.
              </div>
              <div className="db-modal-field">
                <label>Codeforces Handle</label>
                <input
                  type="text" placeholder="tourist" value={cfHandle} autoFocus
                  onChange={(e) => setCfHandle(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && onSaveCfHandle()}
                />
              </div>
              <div className="db-modal-actions">
                <button className="db-cta" style={{ flex: 1 }} onClick={onSaveCfHandle} disabled={isSavingCf}>
                  {isSavingCf ? 'Saving…' : 'Confirm Handle'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── JOIN modal ── */}
        {modal === 'join' && (
          <div className="db-overlay" onClick={() => setModal(null)}>
            <div className="db-modal" onClick={(e) => e.stopPropagation()}>
              <div className="db-modal-title">Join Match</div>
              <div className="db-modal-sub">Enter the invite code shared by your rival.</div>
              <div className="db-modal-field">
                <label>Invite Code</label>
                <input
                  type="text" placeholder="XK7P2R" value={joinCode} autoFocus
                  onChange={(e) => setJoinCode(e.target.value.toUpperCase())} maxLength={10}
                  style={{ fontSize: '22px', letterSpacing: '0.22em', fontWeight: 700, textAlign: 'center' }}
                />
              </div>
              <div className="db-modal-actions">
                <button className="db-btn-cancel" onClick={() => setModal(null)}>Cancel</button>
                <button className="db-cta" style={{ flex: 2 }} onClick={onJoinMatch} disabled={isJoining}>
                  {isJoining ? 'Joining…' : 'Enter Arena'}
                </button>
              </div>
            </div>
          </div>
        )}

        {toast && <div className={`db-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>}
      </div>
    </>
  )
}