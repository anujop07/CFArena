import { useContext, useEffect, useMemo, useState } from 'react'
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
  .nav-btn.verified { color: #c8ff00; border-color: #c8ff0088; background: #c8ff0008; }
  
  @keyframes historyPulse { 0% { transform: scale(1); box-shadow: 0 0 0 0 rgba(200, 255, 0, 0.4); } 50% { transform: scale(1.03); box-shadow: 0 0 8px 1px rgba(200, 255, 0, 0); } 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(200, 255, 0, 0); } }
  .nav-btn.history { color: #c8ff00; border-color: #c8ff00; background: #0a0a0a; animation: historyPulse 2.5s infinite; transition: all 0.3s; }
  .nav-btn.history:hover { background: #c8ff00; color: #0a0a0a; animation: none; transform: translateY(-1px); box-shadow: 0 4px 10px rgba(200, 255, 0, 0.2); }

  /* ── Edit CF Handle modal ── */
  .nav-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.88); display: flex; align-items: center; justify-content: center; z-index: 100; backdrop-filter: blur(4px); animation: navFadeIn .15s ease; }
  @keyframes navFadeIn { from{opacity:0}to{opacity:1} }
  .nav-modal { background: #0f0f0f; border: 1px solid #2a2a2a; padding: 36px; width: 100%; max-width: 480px; border-radius: 2px; animation: navSlideUp .2s ease; }
  @keyframes navSlideUp { from{transform:translateY(12px);opacity:0}to{transform:translateY(0);opacity:1} }
  .nav-modal-title { font-family: 'Syne', sans-serif; font-size: 22px; font-weight: 800; letter-spacing: 0.04em; margin-bottom: 4px; text-transform: uppercase; color: #e8e8e0; }
  .nav-modal-sub { font-size: 11px; color: #888; letter-spacing: 0.06em; margin-bottom: 24px; line-height: 1.7; font-family: 'Space Mono', monospace; }
  
  .nav-status-box { background: #080808; border: 1px solid #222; padding: 18px; margin-bottom: 24px; border-radius: 2px; }
  .nav-status-line { font-size: 12px; margin-bottom: 6px; }
  .nav-status-label { color: #777; font-size: 10px; text-transform: uppercase; letter-spacing: 0.1em; }
  .nav-status-val { color: #e8e8e0; font-weight: 700; }
  .nav-status-val.ok { color: #c8ff00; }
  
  .nav-modal-actions { display: flex; gap: 8px; margin-top: 16px; }
  .nav-btn-cancel { font-family: 'Space Mono', monospace; font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: #888; background: none; border: 1px solid #2a2a2a; padding: 11px 20px; border-radius: 2px; cursor: crosshair; transition: color .15s, border-color .15s; flex: 1; text-align: center; }
  .nav-btn-cancel:hover { color: #e8e8e0; border-color: #666; }
  .nav-btn-save { font-family: 'Space Mono', monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: #0a0a0a; background: #c8ff00; border: none; padding: 12px; border-radius: 2px; cursor: crosshair; transition: opacity .15s; flex: 2; text-align: center; text-decoration: none; display: flex; align-items: center; justify-content: center; }
  .nav-btn-save:hover { opacity: 0.88; }
  .nav-btn-save:disabled { opacity: 0.3; cursor: not-allowed; }

  .nav-code-box { background: #141414; border: 1px dashed #c8ff0088; padding: 12px 16px; border-radius: 2px; margin: 12px 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .nav-code-text { font-family: 'Space Mono', monospace; font-size: 15px; font-weight: 700; color: #c8ff00; letter-spacing: 0.12em; user-select: all; }
  .nav-code-snippet { background: #080808; border: 1px solid #282828; padding: 12px 14px; border-radius: 2px; font-family: 'Space Mono', monospace; font-size: 11px; color: #c8ff00; overflow-x: auto; margin: 10px 0 16px 0; line-height: 1.5; white-space: pre; }
  .nav-copy-btn { font-family: 'Space Mono', monospace; font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: #0a0a0a; background: #c8ff00; border: none; padding: 8px 14px; border-radius: 2px; cursor: crosshair; transition: opacity .15s; white-space: nowrap; }
  .nav-copy-btn:hover { opacity: 0.88; }
  .nav-steps-list { font-size: 11px; color: #bbb; line-height: 1.8; margin-bottom: 8px; font-family: 'Space Mono', monospace; list-style-type: none; padding-left: 0; }
  .nav-steps-list li { margin-bottom: 6px; }
  .nav-link-btn { font-family: 'Space Mono', monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #c8ff00; background: none; border: 1px solid #c8ff0088; padding: 11px; border-radius: 2px; cursor: crosshair; transition: all .15s ease; text-decoration: none; display: flex; align-items: center; justify-content: center; flex: 1; text-align: center; }
  .nav-link-btn:hover { background: #c8ff0015; border-color: #c8ff00; }

  .nav-field { margin-bottom: 20px; }
  .nav-field label { display: block; font-size: 10px; color: #888; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 8px; font-family: 'Space Mono', monospace; }
  .nav-field input { width: 100%; box-sizing: border-box; background: #0a0a0a; border: 1px solid #2a2a2a; color: #e8e8e0; font-family: 'Space Mono', monospace; font-size: 13px; padding: 11px 14px; border-radius: 2px; outline: none; transition: border-color .15s; }
  .nav-field input:focus { border-color: #c8ff00; }
  .nav-field input::placeholder { color: #444; }
  .nav-error-box { background: #1a0808; border-left: 2px solid #ff4444; padding: 10px 14px; margin-bottom: 16px; border-radius: 2px; color: #ff8080; font-size: 11px; line-height: 1.5; font-family: 'Space Mono', monospace; }

  /* ── Toast ── */
  .nav-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-family: 'Space Mono', monospace; font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: navToastIn .2s ease; }
  .nav-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes navToastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @media (max-width: 580px) {
    .nav { padding: 14px 18px; }
    .nav-user-pill { display: none; }
    .nav-modal-actions { flex-direction: column; }
  }
`

export default function Navbar({ onCfSaved, dashboardTo = '/dashboard' }) {
  const navigate = useNavigate()
  const { logout } = useContext(AuthContext)

  const token = localStorage.getItem('token')
  const claims = useMemo(() => decodeToken(token), [token])
  const userEmail = claims?.sub || 'player'

  const [showCfModal, setShowCfModal] = useState(false)
  const [userInfo, setUserInfo] = useState(null)
  const [cfInput, setCfInput] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [isChecking, setIsChecking] = useState(false)
  const [challenge, setChallenge] = useState(null)
  const [copied, setCopied] = useState(false)
  const [cfError, setCfError] = useState(null)
  const [toast, setToast] = useState(null)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3000)
  }

  const onLogout = () => {
    localStorage.removeItem('cfHandle')
    logout()
    navigate('/login', { replace: true })
  }

  const loadUserInfo = async () => {
    try {
      const res = await axiosInstance.get(API_PATHS.USER.ME)
      if (res.data) {
        setUserInfo(res.data)
        const currentHandle = res.data.codeforcesHandle || res.data.cfHandle || ''
        setCfInput(currentHandle)
        if (currentHandle) {
          localStorage.setItem('cfHandle', currentHandle)
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

  useEffect(() => {
    loadUserInfo()
  }, [])

  const openCfModal = async () => {
    setCfError(null)
    setCopied(false)
    const data = await loadUserInfo()
    if (!data?.codeforcesVerified && !data?.cfVerified) {
      await loadActiveChallenge()
    }
    setShowCfModal(true)
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
        await loadUserInfo()
        setChallenge(null)
        if (onCfSaved) onCfSaved()
      } else {
        setCfError(res.data?.message || 'Verification failed. Please check the handle.')
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
        await loadUserInfo()
        setChallenge(null)
        if (onCfSaved) onCfSaved()
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

  const isVerified = Boolean(userInfo?.codeforcesVerified || userInfo?.cfVerified)
  const handle = userInfo?.codeforcesHandle || userInfo?.cfHandle || null

  return (
    <>
      <style>{navCss}</style>

      <header className="nav">
        <Link to={dashboardTo} className="nav-brand">CF<em>_</em>ARENA</Link>
        <div className="nav-right">
          <span className="nav-user-pill">{userEmail}</span>
          <button className="nav-btn history" onClick={() => navigate('/history')} title="View match history">
            History
          </button>
          <button className="nav-btn" onClick={() => navigate('/developers')} title="Meet the developers">
            Developers
          </button>
          <button
            className={`nav-btn ${isVerified ? 'verified' : 'accent'}`}
            onClick={openCfModal}
            title="Codeforces Account"
          >
            {isVerified ? `✓ ${handle}` : 'Connect CF'}
          </button>
          <button className="nav-btn" onClick={onLogout}>Logout</button>
        </div>
      </header>

      {/* CF Account modal */}
      {showCfModal && (
        <div className="nav-overlay" onClick={() => setShowCfModal(false)}>
          <div className="nav-modal" onClick={(e) => e.stopPropagation()}>
            <div className="nav-modal-title">
              {isVerified ? 'Codeforces Account' : challenge ? 'Verification Required' : 'Codeforces ID'}
            </div>
            <div className="nav-modal-sub">
              {isVerified
                ? 'Your permanently linked Codeforces identity.'
                : challenge
                ? 'To verify that you own this Codeforces account:'
                : 'Enter your Codeforces handle to link and verify your account.'}
            </div>

            {cfError && (
              <div className="nav-error-box">
                {cfError}
              </div>
            )}

            {isVerified ? (
              <>
                <div className="nav-status-box">
                  <div className="nav-status-line">
                    <span className="nav-status-label">Status: </span>
                    <span className="nav-status-val ok">
                      ✓ CODEFORCES ACCOUNT VERIFIED
                    </span>
                  </div>
                  {handle && (
                    <div className="nav-status-line">
                      <span className="nav-status-label">Handle: </span>
                      <span className="nav-status-val">{handle}</span>
                    </div>
                  )}
                  {userInfo?.codeforcesVerifiedAt && (
                    <div className="nav-status-line" style={{ fontSize: '10px', color: '#666', marginTop: '8px' }}>
                      Verified on: {new Date(userInfo.codeforcesVerifiedAt).toLocaleString()}
                    </div>
                  )}
                </div>

                <div className="nav-modal-actions">
                  <button className="nav-btn-save" style={{ flex: 1 }} onClick={() => setShowCfModal(false)}>
                    Close
                  </button>
                </div>
              </>
            ) : challenge ? (
              <div>
                <ul className="nav-steps-list">
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
                    className="nav-copy-btn"
                    onClick={handleCopySnippet}
                  >
                    {copied ? '✓ Copied Snippet!' : 'Copy Snippet'}
                  </button>
                </div>

                <pre className="nav-code-snippet"><code>{challenge.codeSnippet || `// ${challenge.verificationCode}\n#include <iostream>\nint main() {\n    cf_arena_verification_error;\n    return 0;\n}`}</code></pre>

                <div className="nav-modal-actions" style={{ marginBottom: '8px' }}>
                  <a
                    href={challenge.problemUrl || `https://codeforces.com/problemset/problem/${challenge.contestId || 4}/${challenge.problemIndex || 'A'}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="nav-link-btn"
                  >
                    Submit on Codeforces ↗
                  </a>
                  <button
                    type="button"
                    className="nav-btn-save"
                    onClick={handleCheckVerification}
                    disabled={isChecking}
                  >
                    {isChecking ? 'Checking...' : 'Check Verification'}
                  </button>
                </div>

                <div className="nav-modal-actions" style={{ marginTop: '8px' }}>
                  <button
                    type="button"
                    className="nav-btn-cancel"
                    style={{ flex: 1 }}
                    onClick={() => { setChallenge(null); setCfError(null) }}
                  >
                    Back
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleGenerateChallenge}>
                <div className="nav-field">
                  <label htmlFor="cf-modal-input">Codeforces Handle</label>
                  <input
                    id="cf-modal-input"
                    type="text"
                    placeholder="e.g. Mahesh_45"
                    value={cfInput}
                    onChange={(e) => { setCfInput(e.target.value); setCfError(null) }}
                    autoFocus
                  />
                </div>

                <div className="nav-modal-actions">
                  <button
                    type="button"
                    className="nav-btn-cancel"
                    onClick={() => setShowCfModal(false)}
                  >
                    Close
                  </button>
                  <button
                    type="submit"
                    className="nav-btn-save"
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

      {toast && (
        <div className={`nav-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>
      )}
    </>
  )
}


