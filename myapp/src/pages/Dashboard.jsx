import { useEffect, useState, useContext } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import axiosInstance from '../utils/axiosInstance'
import { API_PATHS, BASE_URL } from '../utils/apiPaths'
import { AuthContext } from '../context/AuthContext'
import { fetchCFAvatar } from '../utils/cfApi'
import Navbar from '../components/Navbar'
import axios from 'axios'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');

  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  .db { min-height: 100vh; background: #0a0a0a; color: #e8e8e0; font-family: 'Space Mono', monospace; -webkit-font-smoothing: antialiased; display: flex; flex-direction: column; }

  .db-main { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px 24px; }
  .db-title { font-family: 'Syne', sans-serif; font-size: clamp(32px, 5vw, 56px); font-weight: 800; letter-spacing: -0.02em; text-align: center; line-height: 1; margin-bottom: 8px; }
  .db-subtitle { font-size: 17px; color: #777; letter-spacing: 0.1em; text-align: center; margin-bottom: 36px; }

  /* ── Codeforces Identity Badge ── */
  .db-cf-badge {
    background: #0d0d0d;
    border: 1px solid #1e1e1e;
    border-radius: 4px;
    padding: 20px 28px;
    display: flex;
    align-items: center;
    gap: 20px;
    margin-bottom: 40px;
    width: 100%;
    max-width: 900px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.4);
  }
  .db-cf-avatar {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    border: 2px solid #c8ff00;
    box-shadow: 0 0 16px rgba(200,255,0,0.25);
    object-fit: cover;
  }
  .db-cf-info { flex: 1; }
  .db-cf-tag { font-size: 10px; text-transform: uppercase; letter-spacing: 0.12em; color: #888; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
  .db-cf-tag.verified { color: #c8ff00; }
  .db-cf-tag.unverified { color: #ffaa44; }
  .db-cf-handle { font-family: 'Syne', sans-serif; font-size: 20px; font-weight: 800; color: #f0f0e8; letter-spacing: 0.02em; }
  .db-cf-meta { display: flex; gap: 16px; margin-top: 4px; font-size: 12px; color: #888; }
  .db-cf-meta span em { font-style: normal; color: #c8ff00; font-weight: 700; }
  .db-cf-btn {
    font-family: 'Space Mono', monospace;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #c8ff00;
    background: none;
    border: 1px solid #c8ff0044;
    padding: 10px 18px;
    border-radius: 2px;
    cursor: crosshair;
    transition: all .15s ease;
    white-space: nowrap;
  }
  .db-cf-btn:hover { background: #c8ff00; color: #0a0a0a; }
  .db-cf-btn.primary { background: #c8ff00; color: #0a0a0a; border: none; }
  .db-cf-btn.primary:hover { opacity: 0.88; }

  .db-actions { display: flex; gap: 16px; width: 100%; max-width: 900px; margin-bottom: 36px; }

  .db-action-card { flex: 1.5; border: 1px solid #1e1e1e; background: #0d0d0d; padding: 60px 32px 48px; display: flex; flex-direction: column; align-items: center; cursor: crosshair; transition: border-color .2s, background .2s; position: relative; overflow: hidden; }
  .db-action-card::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, #c8ff0008 0%, transparent 60%);
    opacity: 0;
    transition: opacity .2s;
    pointer-events: none;
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
  .db-modal { background: #0f0f0f; border: 1px solid #2a2a2a; padding: 36px; width: 100%; max-width: 440px; border-radius: 2px; animation: slideUp .2s ease; }
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

  .db-error-box { background: #1a0808; border-left: 2px solid #ff4444; padding: 10px 14px; margin-bottom: 16px; border-radius: 2px; color: #ff8080; font-size: 11px; line-height: 1.5; font-family: 'Space Mono', monospace; }

  .db-code-box { background: #141414; border: 1px dashed #c8ff0088; padding: 12px 16px; border-radius: 2px; margin: 12px 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .db-code-text { font-family: 'Space Mono', monospace; font-size: 15px; font-weight: 700; color: #c8ff00; letter-spacing: 0.12em; user-select: all; }
  .db-code-snippet { background: #080808; border: 1px solid #282828; padding: 12px 14px; border-radius: 2px; font-family: 'Space Mono', monospace; font-size: 11px; color: #c8ff00; overflow-x: auto; margin: 10px 0 16px 0; line-height: 1.5; white-space: pre; }
  .db-copy-btn { font-family: 'Space Mono', monospace; font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: #0a0a0a; background: #c8ff00; border: none; padding: 8px 14px; border-radius: 2px; cursor: crosshair; transition: opacity .15s; white-space: nowrap; }
  .db-copy-btn:hover { opacity: 0.88; }
  .db-steps-list { font-size: 11px; color: #bbb; line-height: 1.8; margin-bottom: 8px; font-family: 'Space Mono', monospace; list-style-type: none; padding-left: 0; }
  .db-steps-list li { margin-bottom: 6px; }
  .db-link-btn { font-family: 'Space Mono', monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #c8ff00; background: none; border: 1px solid #c8ff0088; padding: 11px; border-radius: 2px; cursor: crosshair; transition: all .15s ease; text-decoration: none; display: flex; align-items: center; justify-content: center; flex: 1; text-align: center; }
  .db-link-btn:hover { background: #c8ff0015; border-color: #c8ff00; }

  /* ── Toast ── */
  .db-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: toastIn .2s ease; }
  .db-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes toastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @media (max-width: 580px) {
    .db-actions { flex-direction: column; }
    .db-cf-badge { flex-direction: column; text-align: center; }
    .db-cf-meta { justify-content: center; }
    .db-modal-actions { flex-direction: column; }
  }
`

export default function Dashboard() {
  const navigate = useNavigate()
  const location = useLocation()
  const { userToken } = useContext(AuthContext)

  const [modal, setModal] = useState(null)
  const [joinCode, setJoinCode] = useState('')
  const [isJoining, setIsJoining] = useState(false)
  const [toast, setToast] = useState(null)
  const [avatar, setAvatar] = useState(null)
  const [avatarFailed, setAvatarFailed] = useState(false)
  const [cfRating, setCfRating] = useState(null)
  const [cfRank, setCfRank] = useState(null)

  const [cfInput, setCfInput] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [isChecking, setIsChecking] = useState(false)
  const [challenge, setChallenge] = useState(null)
  const [copied, setCopied] = useState(false)
  const [cfError, setCfError] = useState(null)

  const [user, setUser] = useState(null)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3500)
  }

  const loadUserData = async () => {
    try {
      const res = await axiosInstance.get(API_PATHS.USER.ME)
      if (res.data) {
        setUser(res.data)
        const handle = res.data.codeforcesHandle || res.data.cfHandle
        if (handle) {
          setCfInput(handle)
          fetchCfPublicInfo(handle)
        }
        return res.data
      }
    } catch { /* ignore */ }
    return null
  }

  const loadActiveChallenge = async () => {
    try {
      const res = await axiosInstance.get(API_PATHS.USER.ACTIVE_CHALLENGE)
      if (res.data?.active) {
        setChallenge(res.data)
      } else {
        setChallenge(null)
      }
    } catch {
      setChallenge(null)
    }
  }

  const fetchCfPublicInfo = async (handle) => {
    try {
      const av = await fetchCFAvatar(handle)
      if (av) setAvatar(av)

      const res = await axios.get(`https://codeforces.com/api/user.info?handles=${handle}`)
      if (res.data?.status === 'OK' && res.data.result?.length > 0) {
        const info = res.data.result[0]
        if (info.rating) setCfRating(info.rating)
        if (info.rank) setCfRank(info.rank)
        if (info.avatar || info.titlePhoto) setAvatar(info.avatar || info.titlePhoto)
      }
    } catch { /* ignore */ }
  }

  useEffect(() => {
    loadUserData()
  }, [location])

  const openCfModal = async () => {
    setCfError(null)
    setCopied(false)
    const data = await loadUserData()
    if (!data?.codeforcesVerified && !data?.cfVerified) {
      await loadActiveChallenge()
    }
    setModal('cf')
  }

  const handleGenerateChallenge = async (e) => {
    if (e) e.preventDefault()
    if (!cfInput.trim()) {
      setCfError('Please enter a valid Codeforces handle.')
      return
    }

    setIsGenerating(true)
    setCfError(null)

    try {
      const res = await axiosInstance.post(API_PATHS.USER.GENERATE_CHALLENGE, {
        cfHandle: cfInput.trim()
      })

      if (res.data?.verificationCode) {
        setChallenge(res.data)
        showToast('Verification challenge generated! Submit the code snippet on Codeforces.')
      } else if (res.data?.verified) {
        showToast(res.data.handle ? `Codeforces account '${res.data.handle}' verified!` : 'Codeforces account verified!')
        await loadUserData()
        setChallenge(null)
        setModal(null)
      } else {
        setCfError(res.data?.message || 'Verification challenge failed. Please check the handle.')
      }
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.data?.error || 'Verification failed. Please enter a valid Codeforces handle.'
      setCfError(typeof msg === 'string' ? msg : 'Verification failed. Please enter a valid Codeforces handle.')
    } finally {
      setIsGenerating(false)
    }
  }

  const copyToClipboard = (text, successMsg) => {
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text)
        .then(() => {
          setCopied(true)
          showToast(successMsg)
          setTimeout(() => setCopied(false), 2500)
        })
        .catch(() => fallbackCopy(text, successMsg))
    } else {
      fallbackCopy(text, successMsg)
    }
  }

  const fallbackCopy = (text, successMsg) => {
    try {
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.style.position = 'fixed'
      textArea.style.opacity = '0'
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
      setCopied(true)
      showToast(successMsg)
      setTimeout(() => setCopied(false), 2500)
    } catch {
      showToast('Failed to copy. Please select and copy manually.')
    }
  }

  const handleCopyCode = () => {
    if (challenge?.verificationCode) {
      copyToClipboard(challenge.verificationCode, 'Verification code copied to clipboard!')
    }
  }

  const handleCopySnippet = () => {
    const snippet = challenge?.codeSnippet || `// ${challenge?.verificationCode}\n#include <iostream>\nint main() {\n    cf_arena_verification_error;\n    return 0;\n}`
    copyToClipboard(snippet, 'Code snippet copied to clipboard!')
  }

  const handleCheckVerification = async () => {
    setIsChecking(true)
    setCfError(null)

    try {
      const res = await axiosInstance.post(API_PATHS.USER.VERIFY_CHALLENGE)
      if (res.data?.verified) {
        showToast(res.data.handle ? `Codeforces account '${res.data.handle}' verified & permanently linked!` : 'Codeforces account verified!')
        await loadUserData()
        setChallenge(null)
        setModal(null)
      } else {
        setCfError(res.data?.message || 'Verification submission not found. Please submit the code snippet on Codeforces and try again.')
      }
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.data?.error || 'Verification check failed. Please submit the CE snippet on Codeforces and try again.'
      setCfError(typeof msg === 'string' ? msg : 'Verification check failed. Please submit the CE snippet on Codeforces and try again.')
    } finally {
      setIsChecking(false)
    }
  }

  const onJoinMatch = async () => {
    if (!joinCode.trim()) { showToast('Enter an invite code.', 'error'); return }
    if (!user?.codeforcesVerified && !user?.cfVerified) {
      showToast('Please connect and verify your Codeforces account before joining matches.', 'error')
      return
    }
    setIsJoining(true)
    try {
      const code = joinCode.trim().toUpperCase()
      const res = await axiosInstance.post(`${API_PATHS.MATCH.JOIN}?inviteCode=${encodeURIComponent(code)}`)
      showToast('Joined! Opening room…')
      setModal(null)
      navigate(`/match/${code}`, { state: { role: 'guest', joinedMatch: res?.data || null } })
    } catch (err) {
      const m = err?.response?.data?.message || err?.response?.data?.error || err?.response?.data || 'Could not join.'
      showToast(typeof m === 'string' ? m : 'Failed.', 'error')
    } finally { setIsJoining(false) }
  }

  const isVerified = Boolean(user?.codeforcesVerified || user?.cfVerified)
  const currentHandle = user?.codeforcesHandle || user?.cfHandle || null

  return (
    <>
      <style>{css}</style>
      <div className="db">

        <Navbar onCfSaved={loadUserData} />

        <main className="db-main">
          
          {/* ── Codeforces Account Status Card ── */}
          <div className="db-cf-badge">
            {avatar && !avatarFailed ? (
              <img
                src={avatar}
                alt={currentHandle || "Codeforces Avatar"}
                className="db-cf-avatar"
                onError={() => setAvatarFailed(true)}
              />
            ) : (
              <div
                className="db-cf-avatar"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: 'linear-gradient(135deg, #161616 0%, #222 100%)',
                  fontSize: '22px',
                  fontWeight: '800',
                  color: '#c8ff00',
                  textTransform: 'uppercase',
                  userSelect: 'none'
                }}
              >
                {currentHandle ? currentHandle.charAt(0) : (isVerified ? '✓' : '⚡')}
              </div>
            )}

            <div className="db-cf-info">
              <div className={`db-cf-tag ${isVerified ? 'verified' : 'unverified'}`}>
                {isVerified ? '✓ CODEFORCES ACCOUNT VERIFIED' : '⚠ Codeforces Account Not Connected'}
              </div>
              <div className="db-cf-handle">
                {isVerified && currentHandle ? currentHandle : 'No account linked'}
              </div>
              {isVerified ? (
                <div className="db-cf-meta">
                  {cfRating && <span>Rating: <em>{cfRating}</em></span>}
                  {cfRank && <span>Rank: <em>{cfRank}</em></span>}
                </div>
              ) : (
                <div className="db-cf-meta" style={{ color: '#aaa' }}>
                  Verify your Codeforces account to participate in duels &amp; tournaments.
                </div>
              )}
            </div>

            {!isVerified && (
              <button
                className="db-cf-btn primary"
                onClick={openCfModal}
              >
                Connect Codeforces
              </button>
            )}
          </div>

          <h1 className="db-title">Start a duel.</h1>
          <p className="db-subtitle">// pick your move</p>

          <div className="db-actions">
            <div className="db-action-card">
              <div className="db-action-icon">⚔</div>
              <div className="db-action-label">Create</div>
              <p className="db-action-desc">Host a new match. Get an invite code and wait for your rival.</p>
              <button
                className="db-cta"
                onClick={() => {
                  if (!isVerified) {
                    showToast('Connect your Codeforces account first.', 'error')
                    openCfModal()
                    return
                  }
                  navigate('/match/create')
                }}
              >
                Create Match
              </button>
            </div>

            <div className="db-action-card">
              <div className="db-action-icon">⊕</div>
              <div className="db-action-label">Join</div>
              <p className="db-action-desc">Have a code? Enter it and jump straight into the fight.</p>
              <button
                className="db-cta-ghost"
                onClick={() => {
                  if (!isVerified) {
                    showToast('Connect your Codeforces account first.', 'error')
                    openCfModal()
                    return
                  }
                  setModal('join')
                }}
              >
                Join Match
              </button>
            </div>

            <div className="db-action-card">
              <div className="db-action-icon">🏆</div>
              <div className="db-action-label">Tournaments</div>
              <p className="db-action-desc">Compete in bracket-style arenas. Create or join tournaments.</p>
              <button className="db-cta-ghost" onClick={() => navigate('/tournaments')}>
                View Tournaments (Or Create)
              </button>
            </div>
          </div>
        </main>

        {/* ── CF Connect / Verify Modal ── */}
        {modal === 'cf' && (
          <div className="db-overlay" onClick={() => setModal(null)}>
            <div className="db-modal" onClick={(e) => e.stopPropagation()}>
              <div className="db-modal-title">
                {challenge ? 'Verification Required' : 'Codeforces ID'}
              </div>
              <div className="db-modal-sub">
                {challenge
                  ? 'To verify that you own this Codeforces account:'
                  : 'Enter your Codeforces handle to link and verify your account.'}
              </div>

              {cfError && (
                <div className="db-error-box">
                  {cfError}
                </div>
              )}

              {challenge ? (
                <div>
                  <ul className="db-steps-list">
                    <li><strong>1.</strong> Open problem <strong style={{ color: '#c8ff00' }}>{challenge.contestId}{challenge.problemIndex} ({challenge.problemName || 'Watermelon'})</strong> on Codeforces.</li>
                    <li><strong>2.</strong> Submit the provided intentionally invalid C++ code below with your account <strong>{challenge.cfHandle}</strong>.</li>
                    <li><strong>3.</strong> Ensure the submission result is <em>COMPILATION_ERROR</em>.</li>
                    <li><strong>4.</strong> Return here and click &quot;Check Verification&quot;.</li>
                  </ul>

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '14px', marginBottom: '4px' }}>
                    <span style={{ fontSize: '10px', color: '#888', letterSpacing: '0.1em', textTransform: 'uppercase' }}>
                      Code Snippet to Submit:
                    </span>
                    <button
                      type="button"
                      className="db-copy-btn"
                      onClick={handleCopySnippet}
                    >
                      {copied ? '✓ Copied Snippet!' : 'Copy Snippet'}
                    </button>
                  </div>

                  <pre className="db-code-snippet"><code>{challenge.codeSnippet || `// ${challenge.verificationCode}\n#include <iostream>\nint main() {\n    cf_arena_verification_error;\n    return 0;\n}`}</code></pre>

                  <div className="db-modal-actions" style={{ marginBottom: '8px' }}>
                    <a
                      href={challenge.problemUrl || `https://codeforces.com/problemset/problem/${challenge.contestId || 4}/${challenge.problemIndex || 'A'}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="db-link-btn"
                    >
                      Submit on Codeforces ↗
                    </a>
                    <button
                      type="button"
                      className="db-cta"
                      style={{ flex: 1 }}
                      onClick={handleCheckVerification}
                      disabled={isChecking}
                    >
                      {isChecking ? 'Checking...' : 'Check Verification'}
                    </button>
                  </div>

                  <div className="db-modal-actions" style={{ marginTop: '8px' }}>
                    <button
                      type="button"
                      className="db-btn-cancel"
                      style={{ flex: 1 }}
                      onClick={() => { setChallenge(null); setCfError(null) }}
                    >
                      Back
                    </button>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleGenerateChallenge}>
                  <div className="db-modal-field">
                    <label htmlFor="dashboard-cf-input">Codeforces Handle</label>
                    <input
                      id="dashboard-cf-input"
                      type="text"
                      placeholder="e.g. Mahesh_45"
                      value={cfInput}
                      onChange={(e) => { setCfInput(e.target.value); setCfError(null) }}
                      autoFocus
                    />
                  </div>

                  <div className="db-modal-actions">
                    <button type="button" className="db-btn-cancel" onClick={() => setModal(null)}>Cancel</button>
                    <button
                      type="submit"
                      className="db-cta"
                      style={{ flex: 2 }}
                      disabled={isGenerating || !cfInput.trim()}
                    >
                      {isGenerating ? 'Checking…' : 'Verify Now'}
                    </button>
                  </div>
                </form>
              )}
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
                <label htmlFor="dashboard-join-code-input">Invite Code</label>
                <input
                  id="dashboard-join-code-input"
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