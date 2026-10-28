import { useEffect, useState, useContext, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import { AuthContext } from '../../context/AuthContext'
import Navbar from '../../components/Navbar'

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

const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=Bebas+Neue&display=swap');

  .tl { min-height: 100vh; background: #060606; color: #f0f0e8; font-family: 'IBM Plex Mono', monospace; display: flex; flex-direction: column; }

  .tl-main { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 40px 20px 64px; }
  .tl-inner { width: 100%; max-width: 720px; }

  .tl-back { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-family: 'IBM Plex Mono', monospace; letter-spacing: 0.12em; text-transform: uppercase; color: #909090; background: none; border: none; cursor: pointer; padding: 0; margin-bottom: 40px; transition: color 0.15s; }
  .tl-back:hover { color: #f0f0e8; }
  .tl-back svg { width: 12px; height: 12px; }

  .tl-header { margin-bottom: 36px; display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
  .tl-title { font-family: 'Bebas Neue', sans-serif; font-size: clamp(48px, 10vw, 64px); letter-spacing: 0.04em; line-height: 0.92; }
  .tl-subtitle { font-size: 12px; color: #606060; letter-spacing: 0.14em; text-transform: uppercase; margin-top: 8px; }

  .tl-create-btn { font-family: 'IBM Plex Mono', monospace; font-size: 12px; font-weight: 600; letter-spacing: 0.12em; text-transform: uppercase; color: #060606; background: #c8ff00; border: none; padding: 14px 28px; border-radius: 2px; cursor: pointer; transition: opacity 0.15s; white-space: nowrap; }
  .tl-create-btn:hover { opacity: 0.85; }

  .tl-tabs { display: flex; gap: 0; margin-bottom: 24px; border-bottom: 1px solid #252525; }
  .tl-tab { font-family: 'IBM Plex Mono', monospace; font-size: 11px; font-weight: 500; letter-spacing: 0.12em; text-transform: uppercase; color: #606060; background: none; border: none; border-bottom: 2px solid transparent; padding: 12px 20px; cursor: pointer; transition: color 0.15s, border-color 0.15s; }
  .tl-tab:hover { color: #909090; }
  .tl-tab.active { color: #c8ff00; border-bottom-color: #c8ff00; }

  .tl-empty { text-align: center; padding: 64px 20px; color: #606060; font-size: 13px; letter-spacing: 0.08em; }

  .tl-card { background: #0d0d0d; border: 1px solid #1e1e1e; margin-bottom: 12px; transition: border-color 0.2s; overflow: hidden; }
  .tl-card:hover { border-color: #363636; }

  .tl-card-top { padding: 24px 24px 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
  .tl-card-name { font-family: 'Bebas Neue', sans-serif; font-size: 28px; letter-spacing: 0.04em; line-height: 1; }
  .tl-card-status { font-size: 10px; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; padding: 5px 12px; border-radius: 2px; white-space: nowrap; }
  .tl-card-status.reg { color: #c8ff00; background: rgba(200,255,0,0.08); border: 1px solid rgba(200,255,0,0.2); }
  .tl-card-status.ongoing { color: #ff8800; background: rgba(255,136,0,0.08); border: 1px solid rgba(255,136,0,0.2); }
  .tl-card-status.finished { color: #909090; background: rgba(144,144,144,0.08); border: 1px solid rgba(144,144,144,0.2); }

  .tl-card-meta { padding: 0 24px 16px; display: flex; gap: 24px; flex-wrap: wrap; }
  .tl-card-meta-item { font-size: 11px; color: #606060; letter-spacing: 0.08em; }
  .tl-card-meta-item span { color: #909090; }

  .tl-card-bottom { padding: 16px 24px; border-top: 1px solid #1a1a1a; display: flex; gap: 8px; align-items: center; justify-content: flex-end; }

  .tl-btn { font-family: 'IBM Plex Mono', monospace; font-size: 11px; font-weight: 600; letter-spacing: 0.12em; text-transform: uppercase; padding: 10px 20px; border-radius: 2px; cursor: pointer; transition: all 0.15s; white-space: nowrap; }
  .tl-btn-primary { color: #060606; background: #c8ff00; border: none; }
  .tl-btn-primary:hover { opacity: 0.85; }
  .tl-btn-primary:disabled { opacity: 0.3; cursor: not-allowed; }
  .tl-btn-ghost { color: #909090; background: none; border: 1px solid #252525; }
  .tl-btn-ghost:hover { color: #f0f0e8; border-color: #363636; }

  .tl-badge { font-size: 10px; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; padding: 6px 14px; border-radius: 2px; }
  .tl-badge-registered { color: #c8ff00; background: rgba(200,255,0,0.06); }
  .tl-badge-full { color: #ff4d4d; background: rgba(255,77,77,0.06); }

  .tl-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: toastIn .2s ease; }
  .tl-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes toastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @keyframes pulse-glow { 0%,100%{box-shadow:0 0 8px rgba(200,255,0,0.3)}50%{box-shadow:0 0 20px rgba(200,255,0,0.6)} }

  @media (max-width: 580px) {
    .tl-header { flex-direction: column; align-items: flex-start; }
    .tl-card-meta { gap: 12px; }
  }
`

export default function TournamentList() {
  const navigate = useNavigate()
  const { userToken } = useContext(AuthContext)
  const token = localStorage.getItem('token') || userToken
  const claims = useMemo(() => decodeToken(token), [token])
  const userEmail = claims?.sub || ''

  const [tab, setTab] = useState('active')
  const [tournaments, setTournaments] = useState([])
  const [loading, setLoading] = useState(true)
  const [toast, setToast] = useState(null)
  const [myCfHandle, setMyCfHandle] = useState(localStorage.getItem('cfHandle') || '')

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3000)
  }

  // Fetch user info
  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then(res => {
        const handle = res.data?.codeforcesHandle || res.data?.cfHandle || ''
        if (handle) {
          setMyCfHandle(handle)
          localStorage.setItem('cfHandle', handle)
        }
      })
      .catch(() => {})
  }, [])

  const fetchTournaments = () => {
    const url = tab === 'my'
      ? API_PATHS.TOURNAMENT.MY
      : API_PATHS.TOURNAMENT.ACTIVE

    axiosInstance.get(url)
      .then(res => setTournaments(res.data || []))
      .catch(() => setTournaments([]))
      .finally(() => setLoading(false))
  }

  // Fetch tournaments based on tab and poll
  useEffect(() => {
    setLoading(true)
    fetchTournaments()

    const interval = setInterval(fetchTournaments, 4000)
    return () => clearInterval(interval)
  }, [tab])

  const onRequestJoin = async (id) => {
    try {
      await axiosInstance.post(API_PATHS.TOURNAMENT.REQUEST_JOIN(id))
      showToast('Join request sent!')
      fetchTournaments()
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.data || 'Failed to request join.'
      showToast(typeof msg === 'string' ? msg : 'Failed.', 'error')
    }
  }

  const onCancelRequest = async (id) => {
    try {
      await axiosInstance.post(API_PATHS.TOURNAMENT.CANCEL_REQUEST(id))
      showToast('Join request cancelled.')
      fetchTournaments()
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.data || 'Failed to cancel request.'
      showToast(typeof msg === 'string' ? msg : 'Failed.', 'error')
    }
  }

  const onUnregister = async (id) => {
    try {
      await axiosInstance.post(`${API_PATHS.TOURNAMENT.UNREGISTER}/${id}/unregister`)
      showToast('Left tournament.')
      fetchTournaments()
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.data || 'Failed.'
      showToast(typeof msg === 'string' ? msg : 'Failed.', 'error')
    }
  }

  const statusClass = (s) => {
    if (s === 'REGISTRATION') return 'reg'
    if (s === 'ONGOING') return 'ongoing'
    return 'finished'
  }

  const statusLabel = (s) => {
    if (s === 'REGISTRATION') return '📋 Open'
    if (s === 'ONGOING') return '⚡ Live'
    if (s === 'FINISHED') return '✅ Completed'
    return s
  }

  const isRegistered = (t) => t.registeredPlayers?.includes(myCfHandle)
  const hasRequested = (t) => t.joinRequests?.includes(myCfHandle)
  const isCreator = (t) => {
    if (!t) return false
    const email = userEmail?.trim().toLowerCase()
    const handle = (myCfHandle || localStorage.getItem('cfHandle') || '').trim().toLowerCase()
    if (t.creatorEmail && email && t.creatorEmail.trim().toLowerCase() === email) return true
    if (t.creatorId && handle && t.creatorId.trim().toLowerCase() === handle) return true
    return false
  }
  const isFull = (t) => (t.registeredPlayers?.length || 0) >= t.maxPlayers

  const getRoundLabel = (round, total) => {
    const remaining = total - round
    if (remaining === 0) return 'Final'
    if (remaining === 1) return 'Semifinals'
    if (remaining === 2) return 'Quarterfinals'
    return `Round of ${Math.pow(2, total - round + 1)}`
  }

  return (
    <>
      <style>{css}</style>
      <div className="tl">
        <Navbar />

        <main className="tl-main">
          <div className="tl-inner">

            <button className="tl-back" onClick={() => navigate('/dashboard')}>
              <svg viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M8 2L4 6l4 4"/>
              </svg>
              Back to dashboard
            </button>

            <div className="tl-header">
              <div>
                <h1 className="tl-title">Tournaments</h1>
                <p className="tl-subtitle">// bracket elimination arenas</p>
              </div>
              <button className="tl-create-btn" onClick={() => navigate('/tournament/create')}>
                + Create Tournament
              </button>
            </div>

            <div className="tl-tabs">
              <button className={`tl-tab ${tab === 'active' ? 'active' : ''}`} onClick={() => setTab('active')}>
                Active
              </button>
              <button className={`tl-tab ${tab === 'my' ? 'active' : ''}`} onClick={() => setTab('my')}>
                My Tournaments
              </button>
            </div>

            {loading ? (
              <div className="tl-empty">Loading...</div>
            ) : tournaments.length === 0 ? (
              <div className="tl-empty">
                {tab === 'my' ? 'You haven\'t joined or created any tournaments yet.' : 'No active tournaments right now.'}
              </div>
            ) : (
              tournaments.map((t) => {
                const creator = isCreator(t)
                const registered = isRegistered(t)
                const requested = hasRequested(t)
                const full = isFull(t)

                return (
                  <div className="tl-card" key={t.id}>
                    <div className="tl-card-top">
                      <div>
                        <div className="tl-card-name">{t.name || 'Unnamed Tournament'}</div>
                        <div style={{ fontSize: '10px', color: '#888', marginTop: '4px', letterSpacing: '0.08em' }}>
                          Referee / Host: <span style={{ color: '#f0f0e8' }}>{t.creatorId || 'Host'}</span>
                          {creator && <span style={{ color: '#c8ff00', marginLeft: '6px' }}>(You)</span>}
                        </div>
                      </div>
                      <div className={`tl-card-status ${statusClass(t.status)}`}>
                        {statusLabel(t.status)}
                      </div>
                    </div>

                    <div className="tl-card-meta">
                      <div className="tl-card-meta-item">
                        Players: <span>{t.registeredPlayers?.length || 0}/{t.maxPlayers}</span>
                      </div>
                      {creator && t.joinRequests?.length > 0 && (
                        <div className="tl-card-meta-item">
                          Requests: <span style={{ color: '#ffaa44', fontWeight: 600 }}>{t.joinRequests.length} Pending</span>
                        </div>
                      )}
                      <div className="tl-card-meta-item">
                        Difficulty: <span>{t.difficulty}</span>
                      </div>
                      <div className="tl-card-meta-item">
                        Duration: <span>{t.matchDurationMinutes} min</span>
                      </div>
                      {t.status === 'ONGOING' && (
                        <div className="tl-card-meta-item">
                          Round: <span>{getRoundLabel(t.currentRound, t.totalRounds)}</span>
                        </div>
                      )}
                      {t.status === 'FINISHED' && t.winnerId && (
                        <div className="tl-card-meta-item">
                          🏆 Winner: <span style={{ color: '#c8ff00' }}>{t.winnerId}</span>
                        </div>
                      )}
                    </div>

                    <div className="tl-card-bottom">
                      {/* Host Referee Controls */}
                      {creator ? (
                        <>
                          <span className="tl-badge" style={{ color: '#c8ff00', background: 'rgba(200,255,0,0.1)', border: '1px solid rgba(200,255,0,0.3)' }}>
                            ⚖ Host / Referee
                          </span>
                          <button className="tl-btn tl-btn-primary" onClick={() => navigate(`/tournament/${t.id}`)}>
                            Referee Panel
                          </button>
                        </>
                      ) : (
                        <>
                          {/* Non-creator player states */}
                          {t.status === 'REGISTRATION' && !registered && !requested && !full && (
                            <button className="tl-btn tl-btn-primary" onClick={() => onRequestJoin(t.id)}>
                              Request to Join
                            </button>
                          )}

                          {t.status === 'REGISTRATION' && !registered && requested && (
                            <>
                              <span className="tl-badge" style={{ color: '#ffaa44', background: 'rgba(255,170,68,0.1)', border: '1px solid rgba(255,170,68,0.3)' }}>
                                ⏳ Request Pending
                              </span>
                              <button className="tl-btn tl-btn-ghost" onClick={() => onCancelRequest(t.id)}>
                                Cancel Request
                              </button>
                            </>
                          )}

                          {t.status === 'REGISTRATION' && !registered && !requested && full && (
                            <span className="tl-badge tl-badge-full">Full</span>
                          )}

                          {registered && (
                            <>
                              <span className="tl-badge tl-badge-registered">✓ Accepted / Player</span>
                              {t.status === 'REGISTRATION' && (
                                <button className="tl-btn tl-btn-ghost" onClick={() => onUnregister(t.id)}>
                                  Leave
                                </button>
                              )}
                            </>
                          )}

                          {/* View bracket button for everyone */}
                          <button className="tl-btn tl-btn-ghost" onClick={() => navigate(`/tournament/${t.id}`)}>
                            View Bracket
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                )
              })
            )}

          </div>
        </main>

        {toast && <div className={`tl-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>}
      </div>
    </>
  )
}
