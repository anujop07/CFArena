import { useContext, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import axiosInstance from '../utils/axiosInstance'
import { API_PATHS } from '../utils/apiPaths'
import { AuthContext } from '../context/AuthContext'

function decodeToken(token) {
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    return JSON.parse(atob(padded))
  } catch { return null }
}

const navCss = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');

  .nav { display: flex; align-items: center; justify-content: space-between; padding: 18px 36px; border-bottom: 1px solid #222; background: #0a0a0a; position: sticky; top: 0; z-index: 50; }
  .nav-brand { font-family: 'Syne', sans-serif; font-weight: 800; font-size: 17px; letter-spacing: 0.1em; color: #e8e8e0; text-decoration: none; }
  .nav-brand em { color: #c8ff00; font-style: normal; }

  .nav-right { display: flex; align-items: center; gap: 8px; }
  .nav-user-pill { font-family: 'Space Mono', monospace; font-size: 10px; letter-spacing: 0.08em; color: #999; border: 1px solid #2a2a2a; padding: 5px 12px; border-radius: 2px; }

  .nav-btn { font-family: 'Space Mono', monospace; font-size: 10px; letter-spacing: 0.08em; color: #999; background: none; border: 1px solid #2a2a2a; padding: 5px 12px; border-radius: 2px; cursor: crosshair; transition: all .2s ease; }
  .nav-btn:hover { color: #e8e8e0; border-color: #666; }
  .nav-btn.accent { color: #c8ff00; border-color: #c8ff0044; }
  .nav-btn.accent:hover { background: #c8ff0011; border-color: #c8ff00; }
  
  @keyframes historyPulse { 0% { transform: scale(1); box-shadow: 0 0 0 0 rgba(200, 255, 0, 0.4); } 50% { transform: scale(1.03); box-shadow: 0 0 8px 1px rgba(200, 255, 0, 0); } 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(200, 255, 0, 0); } }
  .nav-btn.history { color: #c8ff00; border-color: #c8ff00; background: #0a0a0a; animation: historyPulse 2.5s infinite; transition: all 0.3s; }
  .nav-btn.history:hover { background: #c8ff00; color: #0a0a0a; animation: none; transform: translateY(-1px); box-shadow: 0 4px 10px rgba(200, 255, 0, 0.2); }

  /* ── Edit CF Handle modal ── */
  .nav-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.88); display: flex; align-items: center; justify-content: center; z-index: 100; backdrop-filter: blur(4px); animation: navFadeIn .15s ease; }
  @keyframes navFadeIn { from{opacity:0}to{opacity:1} }
  .nav-modal { background: #0f0f0f; border: 1px solid #2a2a2a; padding: 36px; width: 100%; max-width: 400px; border-radius: 2px; animation: navSlideUp .2s ease; }
  @keyframes navSlideUp { from{transform:translateY(12px);opacity:0}to{transform:translateY(0);opacity:1} }
  .nav-modal-title { font-family: 'Syne', sans-serif; font-size: 22px; font-weight: 800; letter-spacing: 0.04em; margin-bottom: 4px; text-transform: uppercase; color: #e8e8e0; }
  .nav-modal-sub { font-size: 11px; color: #888; letter-spacing: 0.06em; margin-bottom: 28px; line-height: 1.7; font-family: 'Space Mono', monospace; }
  .nav-modal-field { margin-bottom: 16px; }
  .nav-modal-field label { display: block; font-family: 'Space Mono', monospace; font-size: 10px; color: #888; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 8px; }
  .nav-modal-field input { width: 100%; background: #0a0a0a; border: 1px solid #2a2a2a; color: #e8e8e0; font-family: 'Space Mono', monospace; font-size: 13px; padding: 10px 14px; border-radius: 2px; outline: none; transition: border-color .15s; }
  .nav-modal-field input:focus { border-color: #c8ff00; }
  .nav-modal-field input::placeholder { color: #444; }
  .nav-modal-actions { display: flex; gap: 8px; margin-top: 24px; }
  .nav-btn-cancel { font-family: 'Space Mono', monospace; font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: #888; background: none; border: 1px solid #2a2a2a; padding: 11px 20px; border-radius: 2px; cursor: crosshair; transition: color .15s, border-color .15s; flex: 1; }
  .nav-btn-cancel:hover { color: #e8e8e0; border-color: #666; }
  .nav-btn-save { font-family: 'Space Mono', monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: #0a0a0a; background: #c8ff00; border: none; padding: 12px; border-radius: 2px; cursor: crosshair; transition: opacity .15s; flex: 2; }
  .nav-btn-save:hover { opacity: 0.88; }
  .nav-btn-save:disabled { opacity: 0.3; cursor: not-allowed; }

  /* ── Toast ── */
  .nav-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-family: 'Space Mono', monospace; font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: navToastIn .2s ease; }
  .nav-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes navToastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @media (max-width: 580px) {
    .nav { padding: 14px 18px; }
    .nav-user-pill { display: none; }
  }
`

export default function Navbar({ onCfSaved }) {
  const navigate = useNavigate()
  const { logout } = useContext(AuthContext)

  const token = localStorage.getItem('token')
  const claims = useMemo(() => decodeToken(token), [token])
  const userEmail = claims?.sub || 'player'

  const [showCfModal, setShowCfModal] = useState(false)
  const [cfHandle, setCfHandle] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [toast, setToast] = useState(null)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3000)
  }

  const onLogout = () => { logout(); navigate('/login', { replace: true }) }

  const openCfModal = async () => {
    // Pre-fill current handle if available
    try {
      const res = await axiosInstance.get(API_PATHS.USER.ME)
      if (res.data?.cfHandle) setCfHandle(res.data.cfHandle)
    } catch { /* ignore */ }
    setShowCfModal(true)
  }

  const onSaveCfHandle = async () => {
    if (!cfHandle.trim()) { showToast('Enter your Codeforces handle.', 'error'); return }
    setIsSaving(true)
    try {
      await axiosInstance.post(API_PATHS.USER.ADD_CF_HANDLE, { cfHandle: cfHandle.trim() })
      showToast('Handle updated!')
      setShowCfModal(false)
      onCfSaved?.()
    } catch (err) {
      const m = err?.response?.data?.message || err?.response?.data || 'Failed to save handle.'
      showToast(typeof m === 'string' ? m : 'Failed.', 'error')
    } finally { setIsSaving(false) }
  }

  return (
    <>
      <style>{navCss}</style>

      <header className="nav">
        <Link to="/dashboard" className="nav-brand">CF<em>_</em>ARENA</Link>
        <div className="nav-right">
          <span className="nav-user-pill">{userEmail}</span>
          <button className="nav-btn history" onClick={() => navigate('/history')} title="View match history">
            History
          </button>
          <button className="nav-btn" onClick={() => navigate('/developers')} title="Meet the developers">
            Developers
          </button>
          <button className="nav-btn accent" onClick={openCfModal} title="Edit Codeforces handle">
            CF Handle
          </button>
          <button className="nav-btn" onClick={onLogout}>Logout</button>
        </div>
      </header>

      {/* Edit CF Handle modal */}
      {showCfModal && (
        <div className="nav-overlay" onClick={() => setShowCfModal(false)}>
          <div className="nav-modal" onClick={(e) => e.stopPropagation()}>
            <div className="nav-modal-title">Edit CF Handle</div>
            <div className="nav-modal-sub">
              Update your Codeforces handle. This is used to fetch your problem history.
            </div>
            <div className="nav-modal-field">
              <label>Codeforces Handle</label>
              <input
                type="text"
                placeholder="tourist"
                value={cfHandle}
                autoFocus
                onChange={(e) => setCfHandle(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && onSaveCfHandle()}
              />
            </div>
            <div className="nav-modal-actions">
              <button className="nav-btn-cancel" onClick={() => setShowCfModal(false)}>Cancel</button>
              <button className="nav-btn-save" onClick={onSaveCfHandle} disabled={isSaving}>
                {isSaving ? 'Saving…' : 'Save Handle'}
              </button>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div className={`nav-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>
      )}
    </>
  )
}
