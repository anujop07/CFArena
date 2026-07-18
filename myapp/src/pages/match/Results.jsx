import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import Navbar from '../../components/Navbar'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  :root {
    --bg:        #09090b;
    --surface:   #0f0f12;
    --panel:     #111114;
    --border:    #1e1e24;
    --border2:   #2a2a33;
    --accent:    #c8ff00;
    --accent-10: rgba(200,255,0,0.07);
    --accent-20: rgba(200,255,0,0.18);
    --accent-dim: rgba(200,255,0,0.55);
    --text:      #eeeef2;
    --muted:     #46464f;
    --muted2:    #7a7a88;
    --danger:    #f87171;
    --warn:      #fb923c;
    --mono:      'IBM Plex Mono', monospace;
    --radius:    4px;
  }

  .rs { min-height:100vh; background:var(--bg); color:var(--text); font-family:var(--mono); display:flex; flex-direction:column; -webkit-font-smoothing:antialiased; }
  .rs-main { flex:1; display:flex; flex-direction:column; align-items:center; padding:52px 20px 80px; }
  .rs-inner { width:100%; max-width:860px; display:flex; flex-direction:column; gap:12px; }

  /* ── Loading ── */
  .rs-loading { display:flex; flex-direction:column; align-items:center; gap:16px; padding:100px 0; color:var(--muted2); font-size:13px; letter-spacing:0.1em; }
  .rs-spinner { width:28px; height:28px; border:2px solid var(--border2); border-top-color:var(--accent); border-radius:50%; animation:rsSpin 0.9s linear infinite; }
  @keyframes rsSpin { to { transform:rotate(360deg); } }

  /* ── Verdict ── */
  .rs-verdict { text-align:center; padding-bottom:24px; }
  .rs-eyebrow { font-size:10px; color:var(--muted2); letter-spacing:0.22em; text-transform:uppercase; margin-bottom:14px; display:inline-flex; align-items:center; gap:10px; }
  .rs-eyebrow::before,.rs-eyebrow::after { content:''; display:inline-block; width:32px; height:1px; background:var(--border2); }
  .rs-verdict-main { font-family:var(--mono); font-size:clamp(44px,10vw,72px); font-weight:600; line-height:0.95; letter-spacing:-0.02em; margin-bottom:12px; }
  .rs-verdict-main .winner { color:var(--accent); }
  .rs-verdict-main .draw   { color:var(--warn); }
  .rs-verdict-sub { font-size:11px; color:var(--muted2); letter-spacing:0.18em; text-transform:uppercase; }

  /* ── Battle: two cards + VS spine ── */
  .rs-battle {
    display: grid;
    grid-template-columns: 1fr 64px 1fr;
    align-items: stretch;
  }

  /* Player card */
  .rs-pcard {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
  .rs-pcard.win { border-color: rgba(200,255,0,0.22); }

  .rs-pcard-head {
    padding: 26px 24px 22px;
    border-bottom: 1px solid var(--border);
    display: flex; flex-direction: column; gap: 9px;
    position: relative; overflow: hidden;
  }
  .rs-pcard.win .rs-pcard-head { background: var(--accent-10); }
  .rs-pcard.win .rs-pcard-head::after {
    content: ''; position: absolute;
    top: 0; left: 0; right: 0; height: 2px;
    background: var(--accent);
  }

  .rs-result-pill {
    display: inline-block; align-self: flex-start;
    font-size: 9px; letter-spacing:0.18em; text-transform:uppercase;
    padding: 3px 10px; border-radius: 2px; border: 1px solid;
  }
  .rs-result-pill.win  { color:var(--accent); border-color:var(--accent-20);       background:var(--accent-10); }
  .rs-result-pill.lose { color:var(--muted2); border-color:var(--border2);          background:transparent; }
  .rs-result-pill.draw { color:var(--warn);   border-color:rgba(251,146,60,0.25);   background:rgba(251,146,60,0.06); }

  .rs-pcard-handle { font-family:var(--mono); font-size:20px; font-weight:600; letter-spacing:-0.01em; line-height:1.1; word-break:break-all; }
  .rs-you-tag { font-size:9px; color:var(--accent-dim); letter-spacing:0.16em; text-transform:uppercase; }

  .rs-score-row { display:flex; align-items:baseline; gap:9px; margin-top:2px; }
  .rs-big-score { font-family:var(--mono); font-size:60px; font-weight:400; line-height:1; letter-spacing:-0.02em; }
  .rs-big-score.win  { color:var(--accent); }
  .rs-big-score.lose { color:var(--border2); }
  .rs-big-score.draw { color:var(--warn); }
  .rs-score-lbl { font-size:10px; color:var(--muted2); letter-spacing:0.16em; text-transform:uppercase; }

  /* Problem breakdown */
  .rs-pcard-body { flex:1; }
  .rs-pcard-body-head {
    padding: 10px 20px;
    font-size: 10px; color:var(--muted);
    letter-spacing:0.2em; text-transform:uppercase;
    border-bottom: 1px solid var(--border);
    background: var(--surface);
  }

  .rs-prob-row {
    display: flex; align-items: center; gap: 12px;
    padding: 12px 20px;
    border-bottom: 1px solid var(--border);
    transition: background .12s;
  }
  .rs-prob-row:last-child { border-bottom: none; }
  .rs-prob-row:hover { background: rgba(255,255,255,0.015); }

  .rs-prob-n {
    font-family: var(--mono); font-size: 12px; font-weight: 600;
    width: 26px; height: 26px; border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    flex-shrink: 0; border: 1px solid var(--border);
    color: var(--muted);
  }
  .rs-prob-n.ac   { background: var(--accent); border-color: var(--accent); color: var(--bg); }

  .rs-prob-label { flex:1; font-size:12px; color:var(--muted2); }

  .rs-prob-chip {
    display: inline-flex; align-items: center; gap: 5px;
    font-size: 10px; letter-spacing:0.1em; text-transform:uppercase;
    padding: 3px 10px; border-radius: 99px; flex-shrink: 0;
  }
  .rs-prob-chip.ac   { color:var(--bg);    background:var(--accent); }
  .rs-prob-chip.miss { color:var(--muted); background:transparent; border:1px solid var(--border); }

  .rs-cf-link { font-size:11px; color:var(--muted); text-decoration:none; transition:color .15s; flex-shrink:0; }
  .rs-cf-link:hover { color:var(--accent); }

  /* ── VS spine ── */
  .rs-vs-col {
    display: flex; flex-direction: column;
    align-items: center; justify-content: flex-start;
    padding-top: 48px; position: relative;
  }
  .rs-vs-line {
    position: absolute; top: 0; bottom: 0; left: 50%;
    transform: translateX(-50%);
    width: 1px; background: var(--border); z-index: 0;
  }
  .rs-vs-badge {
    position: relative; z-index: 1;
    font-family: var(--mono); font-size: 11px; font-weight: 600;
    color: var(--muted2); background: var(--bg);
    border: 1px solid var(--border2); border-radius: var(--radius);
    padding: 7px 9px; letter-spacing: 0.1em;
  }

  /* ── Meta strip ── */
  .rs-meta {
    display: grid; grid-template-columns: repeat(3,1fr);
    background: var(--panel); border: 1px solid var(--border);
    border-radius: var(--radius); overflow: hidden;
  }
  .rs-meta-item { padding:15px 20px; border-right:1px solid var(--border); }
  .rs-meta-item:last-child { border-right:none; }
  .rs-meta-key { font-size:10px; color:var(--muted2); letter-spacing:0.18em; text-transform:uppercase; margin-bottom:5px; }
  .rs-meta-val { font-family:var(--mono); font-size:18px; font-weight:500; letter-spacing:-0.01em; }

  /* ── Actions ── */
  .rs-actions { display:grid; grid-template-columns:1fr 2fr; gap:10px; }
  .rs-btn-ghost { font-family:var(--mono); font-size:11px; letter-spacing:0.12em; text-transform:uppercase; color:var(--muted2); background:none; border:1px solid var(--border2); padding:15px; border-radius:var(--radius); cursor:pointer; transition:color .15s,border-color .15s; }
  .rs-btn-ghost:hover { color:var(--text); border-color:var(--muted2); }
  .rs-btn-cta { font-family:var(--mono); font-size:11px; font-weight:600; letter-spacing:0.14em; text-transform:uppercase; color:#09090b; background:var(--accent); border:none; padding:15px; border-radius:var(--radius); cursor:pointer; transition:opacity .15s; }
  .rs-btn-cta:hover { opacity:0.87; }

  @media (max-width:640px) {
    .rs-battle { grid-template-columns:1fr; }
    .rs-vs-col { flex-direction:row; padding:0 16px; height:44px; justify-content:flex-start; }
    .rs-vs-line { top:50%; bottom:auto; left:0; right:0; width:100%; height:1px; transform:none; }
    .rs-meta { grid-template-columns:1fr 1fr; }
    .rs-meta-item:nth-child(2) { border-right:none; }
    .rs-meta-item:nth-child(3) { border-top:1px solid var(--border); border-right:none; grid-column:1/-1; }
    .rs-actions { grid-template-columns:1fr; }
  }
`

function cfUrl(pid) {
  if (!pid) return '#'
  if (pid.startsWith('http')) return pid
  const m = pid.match(/^(\d+)([A-Z].*)$/i)
  if (!m) return `https://codeforces.com/problemset?search=${encodeURIComponent(pid)}`
  return `https://codeforces.com/contest/${m[1]}/problem/${m[2].toUpperCase()}`
}

/**
 * Java's Map<Integer, String> serializes to JSON as { "0": "SOLVED", "1": "—", ... }
 * NOT as an array. We must look up by string key index.
 *
 * Also handles the array format as a fallback (future-proofing).
 * Falls back to score-based check only if results map is absent entirely.
 */
function isSolved(resultsMap, index) {
  if (resultsMap === null || resultsMap === undefined) return null // unknown

  // Array format (just in case backend changes)
  if (Array.isArray(resultsMap)) {
    return resultsMap[index] === 'SOLVED'
  }

  // Object / Map format: keys are integers serialized as strings
  // Java sends: { "0": "SOLVED", "1": "—", "2": "SOLVED" }
  const val = resultsMap[index] ?? resultsMap[String(index)]
  if (val === undefined) return null // problem hasn't been attempted yet
  return val === 'SOLVED'
}

function PlayerCard({ handle, isYou, score, results, problems, isWin, isDraw }) {
  const pillClass  = isWin ? 'win' : isDraw ? 'draw' : 'lose'
  const scoreClass = isWin ? 'win' : isDraw ? 'draw' : 'lose'

  return (
    <div className={`rs-pcard ${isWin ? 'win' : ''}`}>
      <div className="rs-pcard-head">
        <span className={`rs-result-pill ${pillClass}`}>
          {isDraw ? 'draw' : isWin ? 'winner' : 'defeated'}
        </span>
        <div className="rs-pcard-handle">{handle || '—'}</div>
        {isYou && <div className="rs-you-tag">you</div>}
        <div className="rs-score-row">
          <div className={`rs-big-score ${scoreClass}`}>{score}</div>
          <div className="rs-score-lbl">solved</div>
        </div>
      </div>

      <div className="rs-pcard-body">
        <div className="rs-pcard-body-head">Problems</div>
        {problems.map((pid, i) => {
          // isSolved returns: true | false | null (not yet played)
          const solvedState = isSolved(results, i)
          // If results map exists but key is absent → not reached → show miss
          // If results map is null entirely → fall back to score
          const ac = solvedState !== null ? solvedState : i < score

          return (
            <div className="rs-prob-row" key={i}>
              <span className={`rs-prob-n ${ac ? 'ac' : ''}`}>{i + 1}</span>
              <span className="rs-prob-label">Problem {i + 1}</span>
              <span className={`rs-prob-chip ${ac ? 'ac' : 'miss'}`}>
                {ac ? '✓ AC' : '—'}
              </span>
              <a className="rs-cf-link" href={cfUrl(pid)} target="_blank" rel="noreferrer">↗</a>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default function Results() {
  const { inviteCode } = useParams()
  const navigate = useNavigate()

  const [match, setMatch]       = useState(null)
  const [myHandle, setMyHandle] = useState(null)
  const [loading, setLoading]   = useState(true)

  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then((r) => setMyHandle(r?.data?.cfHandle || null))
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!inviteCode) return
    axiosInstance
      .get(`${API_PATHS.MATCH.STATUS}?inviteCode=${encodeURIComponent(inviteCode)}`)
      .then((r) => setMatch(r?.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [inviteCode])

  if (loading) return (
    <>
      <style>{css}</style>
      <div className="rs"><Navbar /><main className="rs-main"><div className="rs-loading"><div className="rs-spinner" />Loading results…</div></main></div>
    </>
  )

  if (!match) return (
    <>
      <style>{css}</style>
      <div className="rs"><Navbar /><main className="rs-main"><div className="rs-loading">Match not found.</div></main></div>
    </>
  )

  const s1 = match.score1 ?? 0
  const s2 = match.score2 ?? 0

  // Java Map<Integer,String> comes as { "0": "SOLVED", "1": "—", ... }
  // player1Results → always user1, player2Results → always user2
  const r1 = match.player1Results ?? null
  const r2 = match.player2Results ?? null

  const isDraw  = s1 === s2
  const p1Wins  = !isDraw && s1 > s2
  const p2Wins  = !isDraw && s2 > s1
  const winner  = isDraw ? null : p1Wins ? match.user1 : match.user2
  const problems = match.problems || []

  const durationMs  = match.endTime && match.startTime
    ? new Date(match.endTime).getTime() - new Date(match.startTime).getTime()
    : null
  const durationMin = durationMs ? Math.round(durationMs / 60000) : null

  const isMe = (handle) =>
    !!myHandle && !!handle && handle.toLowerCase() === myHandle.toLowerCase()



  return (
    <>
      <style>{css}</style>
      <div className="rs">
        <Navbar />
        <main className="rs-main">
          <div className="rs-inner">

            <div className="rs-verdict">
              <div className="rs-eyebrow">match complete</div>
              <div className="rs-verdict-main">
                {isDraw
                  ? <span className="draw">Draw.</span>
                  : <><span className="winner">{winner}</span>{' '}wins.</>
                }
              </div>
              <div className="rs-verdict-sub">// {inviteCode} · final results</div>
            </div>

            <div className="rs-battle">
              {/* user1 always left, player1Results always belongs to user1 */}
              <PlayerCard
                handle={match.user1}
                isYou={isMe(match.user1)}
                score={s1}
                results={r1}
                problems={problems}
                isWin={p1Wins}
                isDraw={isDraw}
              />

              <div className="rs-vs-col">
                <div className="rs-vs-line" />
                <div className="rs-vs-badge">VS</div>
              </div>

              {/* user2 always right, player2Results always belongs to user2 */}
              <PlayerCard
                handle={match.user2}
                isYou={isMe(match.user2)}
                score={s2}
                results={r2}
                problems={problems}
                isWin={p2Wins}
                isDraw={isDraw}
              />
            </div>

            <div className="rs-meta">
              <div className="rs-meta-item">
                <div className="rs-meta-key">Duration</div>
                <div className="rs-meta-val">{durationMin != null ? `${durationMin}m` : '—'}</div>
              </div>
              <div className="rs-meta-item">
                <div className="rs-meta-key">Problems</div>
                <div className="rs-meta-val">{problems.length || '—'}</div>
              </div>
              <div className="rs-meta-item">
                <div className="rs-meta-key">Started</div>
                <div className="rs-meta-val">
                  {match.startTime
                    ? new Date(match.startTime).toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' })
                    : '—'}
                </div>
              </div>
            </div>

            <div className="rs-actions">
              <button className="rs-btn-ghost" onClick={() => navigate('/dashboard')}>Dashboard</button>
              <button className="rs-btn-cta"   onClick={() => navigate('/match/create')}>⚔ New Match</button>
            </div>

          </div>
        </main>
      </div>
    </>
  )
}