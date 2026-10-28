import { useEffect, useState, useContext, useRef, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
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

  :root {
    --bg: #060606; --surface: #0f0f0f; --border: #252525; --border2: #363636;
    --accent: #c8ff00; --accent-dim: rgba(200,255,0,0.09);
    --text: #f0f0e8; --muted: #606060; --muted2: #909090;
    --err: #ff4d4d; --radius: 2px;
  }

  .tb { min-height: 100vh; background: var(--bg); color: var(--text); font-family: 'IBM Plex Mono', monospace; display: flex; flex-direction: column; overflow-x: hidden; }
  .tb-main { flex: 1; display: flex; flex-direction: column; padding: 40px 40px 64px; }
  
  .tb-top { margin-bottom: 32px; }
  .tb-back { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; letter-spacing: 0.12em; text-transform: uppercase; color: var(--muted2); background: none; border: none; cursor: pointer; padding: 0; margin-bottom: 24px; transition: color 0.15s; }
  .tb-back:hover { color: var(--text); }
  .tb-back svg { width: 12px; height: 12px; }

  .tb-header { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 24px; }
  .tb-title { font-family: 'Bebas Neue', sans-serif; font-size: clamp(48px, 6vw, 64px); letter-spacing: 0.04em; line-height: 0.9; margin-bottom: 8px; }
  .tb-meta { display: flex; gap: 16px; font-size: 11px; color: var(--muted2); letter-spacing: 0.1em; text-transform: uppercase; }
  
  .tb-status-badge { font-size: 10px; font-weight: 600; letter-spacing: 0.14em; padding: 6px 14px; border-radius: var(--radius); border: 1px solid; }
  .tb-status-badge.reg { color: var(--accent); border-color: rgba(200,255,0,0.2); background: var(--accent-dim); }
  .tb-status-badge.ongoing { color: #ff8800; border-color: rgba(255,136,0,0.2); background: rgba(255,136,0,0.08); }
  .tb-status-badge.finished { color: var(--muted2); border-color: var(--border2); background: var(--surface); }

  /* Referee Panel Styles */
  .tb-referee-panel { background: #0f120a; border: 1px solid rgba(200,255,0,0.25); border-radius: var(--radius); padding: 24px; margin-bottom: 36px; box-shadow: 0 0 24px rgba(200,255,0,0.05); }
  .tb-ref-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(200,255,0,0.15); padding-bottom: 14px; margin-bottom: 18px; flex-wrap: wrap; gap: 12px; }
  .tb-ref-title { font-size: 13px; font-weight: 600; letter-spacing: 0.16em; text-transform: uppercase; color: var(--accent); display: flex; align-items: center; gap: 8px; }
  .tb-ref-badge { font-size: 9px; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; padding: 4px 8px; background: rgba(200,255,0,0.15); color: var(--accent); border-radius: 2px; }
  .tb-ref-roster { font-size: 11px; color: var(--muted2); letter-spacing: 0.1em; }
  .tb-ref-roster span { color: var(--text); font-weight: 600; }

  .tb-req-grid { display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px; }
  .tb-req-item { display: flex; justify-content: space-between; align-items: center; background: #161811; border: 1px solid #282e1d; padding: 10px 16px; border-radius: 2px; }
  .tb-req-player { font-size: 12px; font-weight: 500; color: #f0f0e8; letter-spacing: 0.06em; }
  .tb-req-actions { display: flex; gap: 8px; }
  .tb-btn-accept { font-family: 'IBM Plex Mono', monospace; font-size: 10px; font-weight: 600; letter-spacing: 0.12em; text-transform: uppercase; color: #060606; background: var(--accent); border: none; padding: 6px 12px; border-radius: 2px; cursor: pointer; transition: opacity 0.15s; }
  .tb-btn-accept:hover { opacity: 0.85; }
  .tb-btn-decline { font-family: 'IBM Plex Mono', monospace; font-size: 10px; font-weight: 600; letter-spacing: 0.12em; text-transform: uppercase; color: #ff8888; background: rgba(255,80,80,0.1); border: 1px solid rgba(255,80,80,0.25); padding: 6px 12px; border-radius: 2px; cursor: pointer; transition: all 0.15s; }
  .tb-btn-decline:hover { background: rgba(255,80,80,0.2); }

  .tb-ref-controls { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
  .tb-btn-start-tourney { font-family: 'IBM Plex Mono', monospace; font-size: 12px; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; color: #060606; background: var(--accent); border: none; padding: 12px 24px; border-radius: 2px; cursor: pointer; transition: opacity 0.15s; box-shadow: 0 0 16px rgba(200,255,0,0.3); }
  .tb-btn-start-tourney:hover { opacity: 0.85; }
  .tb-btn-start-tourney:disabled { opacity: 0.35; cursor: not-allowed; box-shadow: none; }

  .tb-bracket-container { flex: 1; display: flex; gap: 64px; overflow-x: auto; padding-bottom: 40px; scrollbar-width: thin; scrollbar-color: var(--border2) var(--bg); }
  .tb-bracket-container::-webkit-scrollbar { height: 8px; }
  .tb-bracket-container::-webkit-scrollbar-track { background: var(--bg); }
  .tb-bracket-container::-webkit-scrollbar-thumb { background: var(--border2); border-radius: 4px; }

  .tb-round { display: flex; flex-direction: column; gap: 24px; min-width: 240px; }
  .tb-round-title { font-size: 11px; font-weight: 600; letter-spacing: 0.18em; text-transform: uppercase; color: var(--muted2); margin-bottom: 16px; border-bottom: 1px solid var(--border); padding-bottom: 8px; text-align: center; }

  .tb-match { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; display: flex; flex-direction: column; position: relative; }
  .tb-match.is-mine { border-color: var(--accent); box-shadow: 0 0 12px rgba(200,255,0,0.1); }
  .tb-match.finished { opacity: 0.7; }
  
  .tb-player { padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); font-size: 13px; font-weight: 500; }
  .tb-player:last-child { border-bottom: none; }
  .tb-player.winner { color: var(--accent); }
  .tb-player.loser { color: var(--muted); text-decoration: line-through; }
  .tb-player.bye { color: var(--muted2); font-style: italic; }
  .tb-score { font-family: 'Bebas Neue', sans-serif; font-size: 18px; letter-spacing: 0.05em; }

  .tb-match-footer { padding: 10px 16px; background: #0a0a0a; border-top: 1px solid var(--border); font-size: 10px; letter-spacing: 0.1em; text-transform: uppercase; display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
  .tb-match-status.live { color: #ff8800; display: flex; align-items: center; gap: 6px; }
  .tb-match-status.live::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: #ff8800; animation: pulseLive 1.5s infinite; }
  @keyframes pulseLive { 0%,100%{opacity:1;} 50%{opacity:0.3;} }

  .tb-match-status.sched { color: var(--muted2); }
  .tb-match-status.done { color: var(--muted2); }

  .tb-play-btn { font-family: 'IBM Plex Mono', monospace; font-size: 10px; font-weight: 600; letter-spacing: 0.14em; color: #000; background: var(--accent); border: none; padding: 6px 12px; border-radius: 2px; cursor: pointer; text-transform: uppercase; transition: all 0.15s; animation: pulseBtn 2s infinite; }
  .tb-play-btn:hover { opacity: 0.8; }
  @keyframes pulseBtn { 0%,100%{box-shadow: 0 0 8px rgba(200,255,0,0.3);} 50%{box-shadow: 0 0 16px rgba(200,255,0,0.6);} }

  .tb-monitor-btn { font-family: 'IBM Plex Mono', monospace; font-size: 10px; font-weight: 600; letter-spacing: 0.12em; color: var(--muted2); background: transparent; border: 1px solid var(--border2); padding: 5px 10px; border-radius: 2px; cursor: pointer; text-transform: uppercase; transition: all 0.15s; }
  .tb-monitor-btn:hover { color: var(--text); border-color: var(--accent); }

  .tb-empty { text-align: center; color: var(--muted2); padding: 40px; font-size: 13px; letter-spacing: 0.1em; }

  .tb-btn-start-round { font-family: 'IBM Plex Mono', monospace; font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; color: #000; background: var(--accent); border: none; padding: 6px 14px; border-radius: 2px; cursor: pointer; transition: all 0.15s; margin-left: 12px; font-weight: 600; }
  .tb-btn-start-round:hover { opacity: 0.8; }
  .tb-btn-start-round:disabled { opacity: 0.5; cursor: not-allowed; }

  .tb-toast { position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: 0.06em; padding: 10px 20px; border-radius: 2px; border-left: 2px solid #c8ff00; background: #0f120a; color: #c8ff00; white-space: nowrap; z-index: 200; animation: toastIn .2s ease; }
  .tb-toast.error { border-color: #ff4444; background: #120a0a; color: #ff6666; }
  @keyframes toastIn { from{opacity:0;transform:translateX(-50%) translateY(8px)}to{opacity:1;transform:translateX(-50%) translateY(0)} }

  @media (max-width: 768px) {
    .tb-main { padding: 24px 20px; }
    .tb-header { flex-direction: column; align-items: flex-start; gap: 16px; }
  }

  /* ── Arena Chat Box ── */
  .mr-chat { position:fixed; bottom:24px; right:24px; width:320px; z-index:500; display:flex; flex-direction:column; gap:0; font-family:'IBM Plex Mono', monospace; }
  .mr-chat-toggle { display:flex; align-items:center; gap:10px; background:#191920; border:1px solid #2e2e3a; border-radius:6px; padding:10px 16px; cursor:pointer; font-family:'IBM Plex Mono', monospace; font-size:11px; letter-spacing:0.12em; text-transform:uppercase; color:#808090; transition:color .15s,border-color .15s; }
  .mr-chat-toggle:hover { color:#f0f0f6; border-color:rgba(200,255,0,0.3); }
  .mr-chat-unread { background:#c8ff00; color:#06060a; font-size:9px; font-weight:700; border-radius:99px; padding:2px 7px; margin-left:auto; }
  .mr-chat-panel { background:#101014; border:1px solid #2e2e3a; border-radius:6px 6px 0 0; overflow:hidden; display:flex; flex-direction:column; animation:chatSlideUp .25s cubic-bezier(0.22,1,0.36,1); }
  @keyframes chatSlideUp { from{opacity:0;transform:translateY(20px)} to{opacity:1;transform:translateY(0)} }
  .mr-chat-head { display:flex; align-items:center; justify-content:space-between; padding:10px 14px; border-bottom:1px solid #22222c; background:#0c0c10; }
  .mr-chat-head-lbl { font-size:9px; letter-spacing:0.24em; text-transform:uppercase; color:#808090; display:flex; align-items:center; gap:8px; }
  .mr-chat-live-dot { width:6px; height:6px; border-radius:50%; background:#c8ff00; animation:pulseLive 1.6s infinite; }
  .mr-chat-close { background:none; border:none; cursor:pointer; color:#50505e; font-size:16px; line-height:1; padding:0 2px; transition:color .15s; }
  .mr-chat-close:hover { color:#f0f0f6; }
  .mr-chat-msgs { height:220px; overflow-y:auto; padding:12px; display:flex; flex-direction:column; gap:8px; scrollbar-width:thin; scrollbar-color:#2e2e3a transparent; }
  .mr-chat-msgs::-webkit-scrollbar { width:4px; }
  .mr-chat-msgs::-webkit-scrollbar-thumb { background:#2e2e3a; border-radius:99px; }
  .mr-chat-msg { display:flex; flex-direction:column; gap:2px; animation:msgIn .18s ease; }
  @keyframes msgIn { from{opacity:0;transform:translateY(6px)} to{opacity:1;transform:translateY(0)} }
  .mr-chat-msg.mine { align-items:flex-end; }
  .mr-chat-sender { font-size:9px; letter-spacing:0.1em; color:#50505e; text-transform:uppercase; }
  .mr-chat-bubble { font-size:12px; padding:7px 12px; border-radius:6px; max-width:88%; word-break:break-word; line-height:1.5; }
  .mr-chat-msg:not(.mine) .mr-chat-bubble { background:#141419; border:1px solid #22222c; color:#c0c0cc; border-radius:4px 12px 12px 12px; }
  .mr-chat-msg.mine .mr-chat-bubble { background:rgba(200,255,0,0.1); border:1px solid rgba(200,255,0,0.2); color:rgba(200,255,0,0.85); border-radius:12px 4px 12px 12px; }
  .mr-chat-empty { font-size:11px; color:#50505e; text-align:center; padding:20px 0; letter-spacing:0.06em; }
  .mr-chat-input-row { display:flex; border-top:1px solid #22222c; }
  .mr-chat-input { flex:1; background:none; border:none; outline:none; font-family:'IBM Plex Mono', monospace; font-size:12px; color:#f0f0f6; padding:11px 14px; }
  .mr-chat-input::placeholder { color:#50505e; }
  .mr-chat-send { background:none; border:none; border-left:1px solid #22222c; padding:0 14px; cursor:pointer; font-size:16px; color:#c8ff00; transition:background .15s; }
  .mr-chat-send:hover { background:rgba(200,255,0,0.06); }

  /* ── Emote Board ── */
  .mr-emote-board { display:flex; align-items:center; gap:6px; padding:10px 14px; border-top:1px solid #22222c; background:#0c0c10; overflow-x:auto; scrollbar-width:none; }
  .mr-emote-board::-webkit-scrollbar { display:none; }
  .mr-emote-btn { background:none; border:1px solid #22222c; border-radius:6px; padding:5px 9px; font-size:18px; cursor:pointer; transition:transform .1s, border-color .15s, background .15s; flex-shrink:0; }
  .mr-emote-btn:hover { transform:scale(1.3); border-color:#3a3a48; background:#141419; }
  .mr-emote-btn:active { transform:scale(0.9); }

  /* ── Floating Emote Animation ── */
  .mr-emote-overlay { position:fixed; inset:0; z-index:600; pointer-events:none; overflow:hidden; }
  .mr-float-emote { position:absolute; bottom:10%; font-size:80px; animation:floatEmote 2.2s cubic-bezier(0.22,1,0.36,1) forwards; pointer-events:none; filter:drop-shadow(0 0 24px rgba(200,255,0,0.4)); }
  @keyframes floatEmote {
    0%   { transform:translateY(0)   scale(0.3) rotate(-15deg); opacity:0; }
    12%  { transform:translateY(-40px) scale(1.4) rotate(8deg);  opacity:1; }
    40%  { transform:translateY(-180px) scale(1.1) rotate(-4deg); opacity:1; }
    80%  { transform:translateY(-380px) scale(0.9) rotate(6deg);  opacity:0.6; }
    100% { transform:translateY(-520px) scale(0.7) rotate(-2deg); opacity:0; }
  }
`

export default function TournamentBracket() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { userToken } = useContext(AuthContext)
  const token = localStorage.getItem('token') || userToken
  const claims = useMemo(() => decodeToken(token), [token])
  const userEmail = claims?.sub || ''

  const [tData, setTData] = useState(null)
  const [rounds, setRounds] = useState({})
  const [loading, setLoading] = useState(true)
  const [myHandle, setMyHandle] = useState(() => localStorage.getItem('cfHandle') || '')
  const [startingRound, setStartingRound] = useState(false)
  const [startingTournament, setStartingTournament] = useState(false)
  const [toast, setToast] = useState(null)

  // ── Arena Chat & Emotes state ──
  const [chatOpen, setChatOpen] = useState(false)
  const [chatMsgs, setChatMsgs] = useState([])
  const [chatInput, setChatInput] = useState('')
  const [unreadCount, setUnreadCount] = useState(0)
  const [floatEmotes, setFloatEmotes] = useState([])
  const chatEndRef = useRef(null)
  const lastAutoNavMatchRef = useRef(null)

  const EMOTES = ['🔥', '💀', '🚀', '🤡', '💩', '😤', '🧠', '⚡']

  const isMine = (sender) => {
    if (!sender) return false
    if (sender === 'you') return true
    if (myHandle && sender.trim().toLowerCase() === myHandle.trim().toLowerCase()) return true
    return false
  }

  const triggerFloatEmote = (emote) => {
    const eid = Date.now() + Math.random()
    const x = 10 + Math.random() * 80
    setFloatEmotes(prev => [...prev, { id: eid, emote, x }])
    setTimeout(() => setFloatEmotes(prev => prev.filter(e => e.id !== eid)), 2400)
  }

  const wsRef = useRef(null)

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3000)
  }

  const getWsUrl = () => {
    if (import.meta.env.VITE_WS_URL) return `${import.meta.env.VITE_WS_URL}/ws`
    const proto = window.location.protocol === 'https:' ? 'https:' : 'http:'
    return `${proto}//${window.location.host}/ws`
  }

  const startRound = async (round) => {
    setStartingRound(true)
    try {
      await axiosInstance.post(`/api/tournament/${id}/start-round/${round}`)
      showToast(`Round ${round} started!`)
      fetchBracket()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to start round', 'error')
    } finally {
      setStartingRound(false)
    }
  }

  const [advancing, setAdvancing] = useState(false)
  
  const advanceRound = async () => {
    setAdvancing(true)
    try {
      await axiosInstance.post(`/api/tournament/${id}/advance-round`)
      showToast('Advanced to next round!')
      fetchBracket()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to advance round', 'error')
    } finally {
      setAdvancing(false)
    }
  }

  const startTournament = async () => {
    setStartingTournament(true)
    try {
      await axiosInstance.post(`${API_PATHS.TOURNAMENT.START}/${id}/start`)
      showToast('Tournament started!')
      fetchBracket()
    } catch (err) {
      showToast(err?.response?.data?.message || err?.response?.data || 'Failed to start tournament', 'error')
    } finally {
      setStartingTournament(false)
    }
  }

  const handleAcceptRequest = async (playerHandle) => {
    try {
      await axiosInstance.post(API_PATHS.TOURNAMENT.ACCEPT_REQUEST(id, playerHandle))
      showToast(`Accepted ${playerHandle}!`)
      fetchBracket()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to accept request', 'error')
    }
  }

  const handleDeclineRequest = async (playerHandle) => {
    try {
      await axiosInstance.post(API_PATHS.TOURNAMENT.DECLINE_REQUEST(id, playerHandle))
      showToast(`Declined ${playerHandle}.`)
      fetchBracket()
    } catch (err) {
      showToast(err?.response?.data?.message || 'Failed to decline request', 'error')
    }
  }

  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then(res => {
        const handle = res.data?.codeforcesHandle || res.data?.cfHandle || ''
        if (handle) {
          setMyHandle(handle)
          localStorage.setItem('cfHandle', handle)
        }
      })
      .catch(() => {})
  }, [])

  const fetchBracket = () => {
    if (id) {
      localStorage.setItem('activeTournamentId', id)
    }
    axiosInstance.get(`${API_PATHS.TOURNAMENT.BRACKET}/${id}/bracket`)
      .then(res => {
        setTData(res.data?.tournament || null)
        setRounds(res.data?.rounds || {})
        setLoading(false)
      })
      .catch(() => setLoading(false))
  }

  // ── Auto-scroll chat ──
  useEffect(() => {
    if (chatOpen) chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMsgs, chatOpen])

  useEffect(() => {
    if (chatOpen) setUnreadCount(0)
  }, [chatOpen])

  // ── Auto-navigate player into match room when round starts ──
  useEffect(() => {
    if (!myHandle || !rounds) return
    for (const rNum of Object.keys(rounds)) {
      const list = rounds[rNum] || []
      const active = list.find(m => isMyMatch(m) && m.status === 'ONGOING' && m.inviteCode)
      if (active && (!lastAutoNavMatchRef.current || lastAutoNavMatchRef.current !== active.inviteCode)) {
        lastAutoNavMatchRef.current = active.inviteCode
        showToast('Your match is live! Entering match room…')
        navigate(`/match/${active.inviteCode}`)
        break
      }
    }
  }, [rounds, myHandle, navigate])

  const sendChat = async () => {
    const text = chatInput.trim()
    if (!text) return
    const curToken = localStorage.getItem('token') || ''
    const headers = curToken ? { Authorization: `Bearer ${curToken}`, authorization: `Bearer ${curToken}` } : {}

    setChatInput('')

    let sentViaWs = false
    if (wsRef.current && wsRef.current.connected) {
      try {
        wsRef.current.send('/app/match/chat', headers, JSON.stringify({
          inviteCode: id,
          sender: myHandle || 'you',
          text,
        }))
        sentViaWs = true
      } catch (err) {
        console.warn('WS sendChat failed, falling back to REST:', err)
      }
    }

    if (!sentViaWs) {
      try {
        await axiosInstance.post(API_PATHS.MATCH.CHAT, {
          inviteCode: id,
          sender: myHandle || 'you',
          text,
        })
      } catch (err) {
        console.error('Failed to send tournament chat:', err)
        showToast('Failed to send chat message', 'error')
      }
    }
  }

  const sendEmote = async (emote) => {
    if (!emote) return
    const curToken = localStorage.getItem('token') || ''
    const headers = curToken ? { Authorization: `Bearer ${curToken}`, authorization: `Bearer ${curToken}` } : {}

    if (wsRef.current && wsRef.current.connected) {
      try {
        wsRef.current.send('/app/match/emote', headers, JSON.stringify({
          inviteCode: id,
          sender: myHandle || 'you',
          emote,
        }))
      } catch (err) {}
    } else {
      try {
        await axiosInstance.post(API_PATHS.MATCH.EMOTE, {
          inviteCode: id,
          sender: myHandle || 'you',
          emote,
        })
      } catch (err) {}
    }
  }

  useEffect(() => {
    fetchBracket()
    
    // Set up Stomp websocket connection to get live updates
    if (window.SockJS && window.Stomp) {
      try {
        const wsUrl = getWsUrl()
        const sock = new window.SockJS(wsUrl)
        const client = window.Stomp.over(sock)
        client.debug = () => {}
        const curToken = localStorage.getItem('token')
        const headers = curToken ? { Authorization: `Bearer ${curToken}`, authorization: `Bearer ${curToken}` } : {}
        client.connect(headers, () => {
          client.subscribe(`/topic/tournament/${id}`, () => {
            fetchBracket()
          })
          client.subscribe(`/topic/tournament/${id}/chat`, (msg) => {
            try {
              const data = JSON.parse(msg.body)
              setChatMsgs(prev => [...prev, data])
              setChatOpen(prev => {
                if (!prev) setUnreadCount(c => c + 1)
                return prev
              })
            } catch {}
          })
          client.subscribe(`/topic/match/${id}/chat`, (msg) => {
            try {
              const data = JSON.parse(msg.body)
              setChatMsgs(prev => [...prev, data])
              setChatOpen(prev => {
                if (!prev) setUnreadCount(c => c + 1)
                return prev
              })
            } catch {}
          })
          client.subscribe(`/topic/tournament/${id}/emotes`, (msg) => {
            try {
              const data = JSON.parse(msg.body)
              triggerFloatEmote(data.emote)
            } catch {}
          })
          client.subscribe(`/topic/match/${id}/emotes`, (msg) => {
            try {
              const data = JSON.parse(msg.body)
              triggerFloatEmote(data.emote)
            } catch {}
          })
        }, () => {})
        wsRef.current = client
      } catch (e) {
        // Fallback to polling
      }
    }
    
    // Periodic poll so join requests and match results update automatically even if WS drops
    const interval = setInterval(() => {
      fetchBracket()
    }, 3500)

    return () => {
      clearInterval(interval)
      if (wsRef.current) {
        try { wsRef.current.disconnect() } catch (e) {}
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (loading) {
    return (
      <>
        <style>{css}</style>
        <div className="tb">
          <Navbar />
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#888', fontSize: '13px' }}>
            Loading tournament arena...
          </div>
        </div>
      </>
    )
  }

  if (!tData) {
    return (
      <>
        <style>{css}</style>
        <div className="tb">
          <Navbar />
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '16px' }}>
            <div style={{ color: '#ff6666', fontSize: '14px' }}>Tournament not found.</div>
            <button className="tb-play-btn" onClick={() => navigate('/tournaments')}>
              Back to Tournaments
            </button>
          </div>
        </div>
      </>
    )
  }

  const isReferee = Boolean(
    (tData.creatorEmail && userEmail && tData.creatorEmail.trim().toLowerCase() === userEmail.trim().toLowerCase()) ||
    (tData.creatorId && myHandle && tData.creatorId.trim().toLowerCase() === myHandle.trim().toLowerCase())
  )
  const isMyMatch = (m) => myHandle && (m.player1 === myHandle || m.player2 === myHandle)

  const getRoundName = (roundNum) => {
    const total = tData.totalRounds || 1
    const remain = total - roundNum
    if (remain === 0) return 'Final'
    if (remain === 1) return 'Semifinals'
    if (remain === 2) return 'Quarterfinals'
    return `Round of ${Math.pow(2, total - roundNum + 1)}`
  }

  return (
    <>
      <style>{css}</style>
      <div className="tb">
        <Navbar />

        <main className="tb-main">
          
          <div className="tb-top">
            <button className="tb-back" onClick={() => navigate('/tournaments')}>
              <svg viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M8 2L4 6l4 4"/>
              </svg>
              Back to Tournaments
            </button>
            <div className="tb-header">
              <div>
                <h1 className="tb-title">{tData.name}</h1>
                <div className="tb-meta">
                  <span>Referee: {tData.creatorId || 'Host'}</span>
                  <span>{tData.registeredPlayers?.length || 0}/{tData.maxPlayers} Players</span>
                  <span>{tData.difficulty}</span>
                  {tData.winnerId && <span>🏆 {tData.winnerId} won!</span>}
                </div>
              </div>
              
              <div className={`tb-status-badge ${tData.status === 'REGISTRATION' ? 'reg' : tData.status === 'ONGOING' ? 'ongoing' : 'finished'}`}>
                {tData.status}
              </div>
            </div>
          </div>

          {/* DEDICATED REFEREE MANAGEMENT PANEL */}
          {isReferee && (
            <div className="tb-referee-panel">
              <div className="tb-ref-head">
                <div className="tb-ref-title">
                  ⚖ Dedicated Referee Panel
                  <span className="tb-ref-badge">Host Only</span>
                </div>
                <div className="tb-ref-roster">
                  Accepted Players: <span>{tData.registeredPlayers?.length || 0} / {tData.maxPlayers}</span>
                  {tData.registeredPlayers?.length > 0 && (
                    <div style={{ marginTop: '4px', fontSize: '11px', color: '#888' }}>
                      Roster: {tData.registeredPlayers.join(', ')}
                    </div>
                  )}
                </div>
              </div>

              {/* Pending Join Requests */}
              {tData.status === 'REGISTRATION' && (
                <div>
                  <div style={{ fontSize: '11px', letterSpacing: '0.14em', textTransform: 'uppercase', color: '#ffaa44', marginBottom: '10px', fontWeight: 600 }}>
                    Player Join Requests ({tData.joinRequests?.length || 0})
                  </div>
                  {(!tData.joinRequests || tData.joinRequests.length === 0) ? (
                    <div style={{ fontSize: '12px', color: '#707070', marginBottom: '16px' }}>
                      No pending requests right now. Players can send join requests from the tournaments list.
                    </div>
                  ) : (
                    <div className="tb-req-grid">
                      {tData.joinRequests.map((player) => (
                        <div className="tb-req-item" key={player}>
                          <div className="tb-req-player">
                            <span>🎮 {player}</span>
                          </div>
                          <div className="tb-req-actions">
                            <button
                              className="tb-btn-accept"
                              onClick={() => handleAcceptRequest(player)}
                              disabled={(tData.registeredPlayers?.length || 0) >= tData.maxPlayers}
                            >
                              ✓ Accept
                            </button>
                            <button
                              className="tb-btn-decline"
                              onClick={() => handleDeclineRequest(player)}
                            >
                              ✕ Decline
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Tournament Management Controls */}
              <div className="tb-ref-controls">
                {tData.status === 'REGISTRATION' && (
                  <button
                    className="tb-btn-start-tourney"
                    onClick={startTournament}
                    disabled={startingTournament || (tData.registeredPlayers?.length || 0) < 2}
                  >
                    {startingTournament ? 'Starting…' : (tData.registeredPlayers?.length || 0) < 2 ? 'Need ≥ 2 Players to Start' : '⚔ Start Tournament'}
                  </button>
                )}

                {tData.status === 'ONGOING' && (
                  <button 
                    className="tb-btn-start-tourney"
                    onClick={advanceRound}
                    disabled={advancing}
                  >
                    {advancing ? 'Advancing…' : '⏩ Advance to Next Round'}
                  </button>
                )}
              </div>
            </div>
          )}

          <div className="tb-bracket-container">
            {Object.keys(rounds).length === 0 ? (
              <div className="tb-empty">Bracket will be generated when tournament starts.</div>
            ) : (
              Object.keys(rounds).sort((a,b) => Number(a) - Number(b)).map((roundNum) => {
                const matches = rounds[roundNum]
                const canStartRound = isReferee && tData.currentRound === Number(roundNum) && tData.status === 'ONGOING' && matches.some(m => m.status === 'READY' && m.status !== 'BYE')

                return (
                  <div className="tb-round" key={roundNum}>
                    <div className="tb-round-title">
                      {getRoundName(Number(roundNum))}
                      {canStartRound && (
                        <button 
                          className="tb-btn-start-round" 
                          onClick={() => startRound(roundNum)} 
                          disabled={startingRound}
                        >
                          {startingRound ? 'Starting...' : `Start`}
                        </button>
                      )}
                    </div>
                    
                    {matches.map((m, idx) => {
                      const mine = isMyMatch(m)
                      const p1Winner = m.winnerId && m.winnerId === m.player1
                      const p2Winner = m.winnerId && m.winnerId === m.player2
                      const isBye = m.status === 'BYE'

                      return (
                        <div className={`tb-match ${mine ? 'is-mine' : ''} ${m.status === 'FINISHED' ? 'finished' : ''}`} key={idx}>
                          <div className={`tb-player ${p1Winner ? 'winner' : ''} ${m.winnerId && p2Winner ? 'loser' : ''} ${m.player1 === 'BYE' ? 'bye' : ''}`}>
                            <span>{m.player1}</span>
                            {!isBye && m.status !== 'SCHEDULED' && m.status !== 'READY' && <span className="tb-score">{m.score1 || 0}</span>}
                          </div>
                          
                          <div className={`tb-player ${p2Winner ? 'winner' : ''} ${m.winnerId && p1Winner ? 'loser' : ''} ${m.player2 === 'BYE' ? 'bye' : ''}`}>
                            <span>{m.player2}</span>
                            {!isBye && m.status !== 'SCHEDULED' && m.status !== 'READY' && <span className="tb-score">{m.score2 || 0}</span>}
                          </div>

                          <div className="tb-match-footer">
                            {isBye ? (
                              <span className="tb-match-status done">BYE (Auto-advance)</span>
                            ) : m.status === 'FINISHED' ? (
                              <span className="tb-match-status done">
                                {m.winnerId && m.winnerId !== 'DRAW' ? `Winner: ${m.winnerId}` : 'Finished'}
                              </span>
                            ) : m.status === 'ONGOING' ? (
                              <span className="tb-match-status live">Live</span>
                            ) : m.status === 'READY' ? (
                              <span className="tb-match-status sched">Waiting for Admin to Start</span>
                            ) : (
                              <span className="tb-match-status sched">Scheduled</span>
                            )}

                            {/* PLAY BUTTON FOR PARTICIPANT */}
                            {mine && !isBye && (m.status === 'READY' || m.status === 'ONGOING') && (
                              <button 
                                className="tb-play-btn"
                                onClick={() => {
                                  localStorage.setItem('activeTournamentId', id)
                                  localStorage.setItem(`tournament_match_${m.inviteCode}`, id)
                                  navigate(`/match/${m.inviteCode}`, { state: { tournamentId: id } })
                                }}
                              >
                                {m.status === 'ONGOING' ? '▶ Play Match' : '🎮 Enter Match Room'}
                              </button>
                            )}

                            {/* FINISHED MATCH RESULTS BUTTON */}
                            {!isBye && m.inviteCode && m.status === 'FINISHED' && (
                              <button
                                className="tb-monitor-btn"
                                onClick={() => {
                                  localStorage.setItem('activeTournamentId', id)
                                  localStorage.setItem(`tournament_match_${m.inviteCode}`, id)
                                  navigate(`/results/${m.inviteCode}`, { state: { tournamentId: id } })
                                }}
                                title="View match scorecard and problem breakdown"
                              >
                                📊 Results
                              </button>
                            )}

                            {/* REFEREE / SPECTATOR MONITOR BUTTON */}
                            {!isBye && m.inviteCode && (m.status === 'READY' || m.status === 'ONGOING') && (!mine || isReferee) && (
                              <button 
                                className="tb-monitor-btn"
                                onClick={() => {
                                  localStorage.setItem('activeTournamentId', id)
                                  localStorage.setItem(`tournament_match_${m.inviteCode}`, id)
                                  navigate(`/match/${m.inviteCode}`, { state: { role: isReferee ? 'host' : 'spectator', tournamentId: id } })
                                }}
                                title="Monitor live scores and results"
                              >
                                {isReferee ? '👁 Referee Monitor' : '👁 Spectate'}
                              </button>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )
              })
            )}
          </div>
        </main>

        {/* ── Floating Emote Overlay ── */}
        <div className="mr-emote-overlay">
          {floatEmotes.map(({ id: eid, emote, x }) => (
            <div key={eid} className="mr-float-emote" style={{ left: `${x}%` }}>{emote}</div>
          ))}
        </div>

        {/* ── Arena Chat Box ── */}
        <div className="mr-chat">
          {chatOpen && (
            <div className="mr-chat-panel">
              <div className="mr-chat-head">
                <span className="mr-chat-head-lbl">
                  <span className="mr-chat-live-dot" />
                  Tournament Arena Chat
                </span>
                <button className="mr-chat-close" onClick={() => setChatOpen(false)}>✕</button>
              </div>
              <div className="mr-chat-msgs">
                {chatMsgs.length === 0 && <div className="mr-chat-empty">No messages yet. Chat with participants!</div>}
                {chatMsgs.map((m, i) => (
                  <div key={i} className={`mr-chat-msg ${isMine(m.sender) ? 'mine' : ''}`}>
                    <span className="mr-chat-sender">{m.sender}</span>
                    <div className="mr-chat-bubble">{m.text}</div>
                  </div>
                ))}
                <div ref={chatEndRef} />
              </div>
              {/* Emote board */}
              <div className="mr-emote-board">
                {EMOTES.map(e => (
                  <button key={e} className="mr-emote-btn" onClick={() => sendEmote(e)} title="Send emote">{e}</button>
                ))}
              </div>
              <div className="mr-chat-input-row">
                <input
                  className="mr-chat-input"
                  placeholder="Message participants…"
                  value={chatInput}
                  onChange={ev => setChatInput(ev.target.value)}
                  onKeyDown={ev => ev.key === 'Enter' && sendChat()}
                />
                <button className="mr-chat-send" onClick={sendChat}>↑</button>
              </div>
            </div>
          )}
          <button className="mr-chat-toggle" onClick={() => setChatOpen(o => !o)}>
            💬 <span>Arena Chat</span>
            {!chatOpen && unreadCount > 0 && (
              <span className="mr-chat-unread">{unreadCount}</span>
            )}
          </button>
        </div>

        {toast && <div className={`tb-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>}
      </div>
    </>
  )
}
