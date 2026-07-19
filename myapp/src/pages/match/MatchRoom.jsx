import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useLocation } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import { fetchCFAvatar } from '../../utils/cfApi'
import Navbar from '../../components/Navbar'
import { celebrateSolve } from './fireworks'

// Strip any accidental /ws suffix — the SockJS URL below appends /ws itself.
// VITE_WS_URL should be just the base (e.g. https://cfarena.in), NOT https://cfarena.in/ws
const _RAW_WS_BASE = import.meta.env.VITE_WS_URL || 'http://localhost:8080'
const WS_BASE = _RAW_WS_BASE.replace(/\/ws\/?$/, '')

function toResultsArray(val) {
  if (!val) return []
  if (Array.isArray(val)) return val
  const keys = Object.keys(val)
  if (keys.length === 0) return []
  const len = Math.max(...keys.map(Number)) + 1
  return Array.from({ length: len }, (_, i) => val[i] ?? '-')
}

const css = `
  @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@300;400;500;600;700&display=swap');

  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  :root {
    --bg:          #06060a;
    --surface:     #0c0c10;
    --panel:       #101014;
    --panel2:      #141419;
    --panel3:      #191920;
    --border:      #22222c;
    --border2:     #2e2e3a;
    --border3:     #3a3a48;
    --accent:      #c8ff00;
    --accent-5:    rgba(200,255,0,0.04);
    --accent-10:   rgba(200,255,0,0.09);
    --accent-20:   rgba(200,255,0,0.2);
    --accent-40:   rgba(200,255,0,0.4);
    --accent-dim:  rgba(200,255,0,0.6);
    --text:        #f0f0f6;
    --text2:       #c0c0cc;
    --muted:       #50505e;
    --muted2:      #808090;
    --danger:      #ff6b6b;
    --danger-bg:   rgba(255,107,107,0.08);
    --warn:        #ffaa44;
    --warn-bg:     rgba(255,170,68,0.07);
    --warn-border: rgba(255,170,68,0.24);
    --success:     #44ffaa;
    --mono:        'IBM Plex Mono', monospace;
    --radius:      6px;
    --radius-lg:   10px;
    --glow-sm:     0 0 12px rgba(200,255,0,0.18);
    --glow-md:     0 0 24px rgba(200,255,0,0.25);
    --glow-lg:     0 0 48px rgba(200,255,0,0.18), 0 0 96px rgba(200,255,0,0.08);
  }

  .mr { min-height:100vh; background:var(--bg); color:var(--text); font-family:var(--mono); display:flex; flex-direction:column; -webkit-font-smoothing:antialiased; }
  .mr-main { flex:1; padding:36px 40px 80px; max-width:1200px; margin:0 auto; width:100%; }
  .mr::before { content:''; position:fixed; inset:0; z-index:0; pointer-events:none; background:repeating-linear-gradient(0deg,transparent,transparent 3px,rgba(200,255,0,0.008) 3px,rgba(200,255,0,0.008) 4px); }
  .mr-main { position:relative; z-index:1; }

  /* ── Loading screen ── */
  .mr-loading { position:fixed; inset:0; z-index:200; background:var(--bg); display:flex; flex-direction:column; align-items:center; justify-content:center; }
  .mr-loading::after { content:''; position:absolute; inset:0; pointer-events:none; background:radial-gradient(ellipse 60% 50% at 50% 50%, rgba(200,255,0,0.04) 0%, transparent 70%); }
  .mr-loading-inner { position:relative; z-index:1; display:flex; flex-direction:column; align-items:center; width:100%; max-width:460px; padding:0 24px; }
  .mr-loading-eyebrow { font-size:10px; letter-spacing:0.3em; text-transform:uppercase; color:var(--muted2); margin-bottom:32px; display:flex; align-items:center; gap:12px; }
  .mr-loading-eyebrow::before,.mr-loading-eyebrow::after { content:''; flex:1; height:1px; background:linear-gradient(90deg, transparent, var(--border2)); width:50px; }
  .mr-loading-title { font-size:clamp(48px,9vw,72px); font-weight:700; letter-spacing:-0.03em; line-height:0.95; color:var(--text); text-align:center; margin-bottom:8px; }
  .mr-loading-title span { color:var(--accent); display:inline-block; animation:mrFlicker 3.5s ease-in-out infinite; text-shadow:var(--glow-md); }
  @keyframes mrFlicker { 0%,100%{opacity:1} 46%{opacity:1} 50%{opacity:0.3} 54%{opacity:1} 91%{opacity:1} 93%{opacity:0.5} 95%{opacity:1} }
  .mr-loading-sub { font-size:11px; color:var(--muted2); letter-spacing:0.12em; text-align:center; margin-bottom:52px; }
  .mr-loading-bar-wrap { width:100%; height:1px; background:var(--border2); margin-bottom:24px; position:relative; overflow:hidden; }
  .mr-loading-bar { position:absolute; top:0; left:-60%; width:60%; height:100%; background:linear-gradient(90deg,transparent,var(--accent),var(--accent-dim),transparent); animation:mrSlide 1.5s ease-in-out infinite; }
  @keyframes mrSlide { 0%{left:-60%} 100%{left:110%} }
  .mr-loading-steps { width:100%; display:flex; flex-direction:column; gap:12px; }
  .mr-loading-step { display:flex; align-items:center; gap:14px; font-size:11px; letter-spacing:0.1em; color:var(--muted); transition:color 0.3s; }
  .mr-loading-step.active { color:var(--text); }
  .mr-loading-step.done   { color:var(--muted2); }
  .mr-ls-icon { width:22px; height:22px; border-radius:50%; border:1px solid var(--border2); display:flex; align-items:center; justify-content:center; flex-shrink:0; font-size:9px; transition:all 0.3s; }
  .mr-loading-step.active .mr-ls-icon { border-color:var(--accent); box-shadow:var(--glow-sm); }
  .mr-loading-step.active .mr-ls-icon::before { content:''; width:7px; height:7px; border-radius:50%; background:var(--accent); animation:mrPulse 0.9s infinite; display:block; }
  .mr-loading-step.done .mr-ls-icon { border-color:var(--muted); color:var(--muted2); }
  .mr-loading-step.done .mr-ls-icon::before { content:'✓'; font-size:10px; }

  /* ── Top bar ── */
  .mr-topbar { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:28px; gap:16px; }
  .mr-tag { font-size:9px; color:var(--muted2); letter-spacing:0.28em; text-transform:uppercase; margin-bottom:8px; display:flex; align-items:center; gap:8px; }
  .mr-tag::before { content:''; width:16px; height:1px; background:var(--accent); opacity:0.5; }
  .mr-title { font-size:26px; font-weight:700; letter-spacing:-0.02em; }
  .mr-title em { color:var(--accent); font-style:normal; text-shadow:var(--glow-sm); }

  /* ── Timer ── */
  .mr-timer-block { text-align:right; }
  .mr-timer { font-size:56px; font-weight:300; color:var(--accent); letter-spacing:-0.02em; line-height:1; text-shadow:var(--glow-md); font-variant-numeric:tabular-nums; transition:color 0.5s,text-shadow 0.5s; }
  .mr-timer.urgent { color:var(--danger); text-shadow:0 0 24px rgba(255,107,107,0.4); animation:mrTimerUrgent 1s ease-in-out infinite; }
  @keyframes mrTimerUrgent { 0%,100%{opacity:1} 50%{opacity:0.45} }
  .mr-timer-lbl { font-size:9px; color:var(--muted2); letter-spacing:0.22em; text-transform:uppercase; margin-top:6px; }

  /* ── Status bar ── */
  .mr-status { display:flex; align-items:center; gap:12px; padding:12px 18px; background:var(--panel); border:1px solid var(--border); border-radius:var(--radius); margin-bottom:24px; position:relative; overflow:hidden; }
  .mr-status::before { content:''; position:absolute; left:0; top:0; bottom:0; width:2px; background:var(--accent); opacity:0; transition:opacity 0.3s; }
  .mr-status.live::before { opacity:1; box-shadow:var(--glow-sm); }
  .mr-dot { width:7px; height:7px; border-radius:50%; flex-shrink:0; }
  .mr-dot.live    { background:var(--accent); box-shadow:0 0 8px rgba(200,255,0,0.6); animation:mrPulse 2s infinite; }
  .mr-dot.waiting { background:var(--warn); animation:mrPulse 2.5s infinite; }
  .mr-dot.done    { background:var(--muted); }
  @keyframes mrPulse { 0%,100%{opacity:1} 50%{opacity:0.2} }
  .mr-status-txt { font-size:12px; color:var(--muted2); letter-spacing:0.06em; }

  .mr-err { background:var(--danger-bg); border:1px solid rgba(255,107,107,0.2); border-left:2px solid var(--danger); padding:14px 18px; border-radius:var(--radius); margin-bottom:18px; font-size:12px; color:#ffaaaa; }

  /* ── Lobby ── */
  .mr-lobby { background:var(--panel); border:1px solid var(--border); border-radius:var(--radius-lg); overflow:hidden; margin-bottom:20px; }
  .mr-lobby-head { padding:32px 32px 26px; border-bottom:1px solid var(--border); position:relative; overflow:hidden; }
  .mr-lobby-head::after { content:''; position:absolute; top:-40px; right:-40px; width:200px; height:200px; border-radius:50%; background:radial-gradient(circle, var(--accent-5) 0%, transparent 70%); pointer-events:none; }
  .mr-lobby-title { font-size:30px; font-weight:700; letter-spacing:-0.02em; margin-bottom:6px; }
  .mr-lobby-sub { font-size:11px; color:var(--muted2); letter-spacing:0.1em; }
  .mr-players { display:grid; grid-template-columns:1fr 1fr; }
  .mr-player { padding:26px 32px; border-right:1px solid var(--border); position:relative; }
  .mr-player:last-child { border-right:none; }
  .mr-player-lbl { font-size:9px; color:var(--muted); letter-spacing:0.24em; text-transform:uppercase; margin-bottom:14px; }
  .mr-player-name { font-size:20px; font-weight:600; margin-bottom:12px; letter-spacing:-0.01em; }
  .mr-player-name.empty { color:var(--border3); }
  .mr-badges { display:flex; gap:6px; flex-wrap:wrap; }
  .mr-badge { font-size:9px; letter-spacing:0.16em; text-transform:uppercase; padding:4px 10px; border-radius:2px; border:1px solid; }
  .mr-badge.host   { color:var(--accent); border-color:var(--accent-20); background:var(--accent-10); }
  .mr-badge.you    { color:var(--muted2); border-color:var(--border2); }
  .mr-badge.ready  { color:var(--accent); border-color:var(--accent-20); background:var(--accent-10); }
  .mr-badge.joined { color:var(--warn); border-color:var(--warn-border); background:var(--warn-bg); }
  .mr-code-row { display:flex; align-items:stretch; border-top:1px solid var(--border); }
  .mr-code-lbl-block { padding:16px 26px; border-right:1px solid var(--border); display:flex; align-items:center; background:var(--surface); }
  .mr-code-lbl { font-size:9px; color:var(--muted); letter-spacing:0.24em; text-transform:uppercase; }
  .mr-code-val { flex:1; padding:16px 26px; font-size:24px; font-weight:700; letter-spacing:0.22em; color:var(--accent); display:flex; align-items:center; text-shadow:var(--glow-sm); }
  .mr-copy-btn { padding:0 24px; background:none; border:none; border-left:1px solid var(--border); cursor:pointer; font-family:var(--mono); font-size:10px; letter-spacing:0.16em; text-transform:uppercase; color:var(--muted2); transition:color .15s,background .15s; white-space:nowrap; }
  .mr-copy-btn:hover { color:var(--text); background:var(--panel2); }
  .mr-copy-btn.copied { color:var(--accent); }
  .mr-lobby-foot { padding:22px 32px; border-top:1px solid var(--border); display:flex; align-items:center; gap:16px; background:var(--surface); }
  .mr-hint { font-size:12px; color:var(--muted2); letter-spacing:0.04em; flex:1; line-height:1.9; }
  .mr-hint em { color:var(--accent-dim); font-style:normal; }

  .mr-btn-primary { font-family:var(--mono); font-size:11px; font-weight:600; letter-spacing:0.14em; text-transform:uppercase; color:#06060a; background:var(--accent); border:none; padding:14px 28px; border-radius:var(--radius); cursor:pointer; transition:opacity .15s,transform .1s,box-shadow .15s; white-space:nowrap; flex-shrink:0; box-shadow:0 0 20px rgba(200,255,0,0.2); }
  .mr-btn-primary:hover { opacity:0.9; transform:translateY(-2px); box-shadow:var(--glow-md); }
  .mr-btn-primary:active { transform:translateY(0); box-shadow:none; }
  .mr-btn-primary:disabled { opacity:0.18; cursor:not-allowed; transform:none; box-shadow:none; }

  /* ── Score section ── */
  .mr-score-section { display:grid; grid-template-columns:1fr auto 1fr; gap:0; margin-bottom:16px; background:var(--panel); border:1px solid var(--border); border-radius:var(--radius-lg); overflow:hidden; }
  .mr-score-me  { padding:32px 36px; position:relative; overflow:hidden; border-right:1px solid var(--border); }
  .mr-score-opp { padding:32px 36px; text-align:right; border-left:1px solid var(--border); }
  .mr-score-me::after  { content:''; position:absolute; top:0; left:0; right:0; height:2px; background:linear-gradient(90deg,var(--accent),transparent); }
  .mr-score-me::before { content:''; position:absolute; inset:0; background:radial-gradient(ellipse at 0% 0%,rgba(200,255,0,0.04) 0%,transparent 60%); pointer-events:none; }
  .mr-score-handle { font-size:10px; color:var(--muted2); letter-spacing:0.18em; text-transform:uppercase; margin-bottom:14px; display:flex; align-items:center; gap:8px; }
  .mr-score-opp .mr-score-handle { justify-content:flex-end; }
  .mr-score-handle-pip { width:6px; height:6px; border-radius:50%; background:var(--accent); flex-shrink:0; box-shadow:0 0 6px rgba(200,255,0,0.5); }
  .mr-score-ring-wrap { display:flex; align-items:center; gap:20px; }
  .mr-score-opp .mr-score-ring-wrap { flex-direction:row-reverse; }
  .mr-ring-svg { flex-shrink:0; }
  .mr-ring-track { fill:none; stroke:var(--border2); stroke-width:3; }
  .mr-ring-fill  { fill:none; stroke-width:3; stroke-linecap:round; transition:stroke-dashoffset 0.9s cubic-bezier(0.4,0,0.2,1); }
  .mr-ring-fill.me  { stroke:var(--accent); filter:drop-shadow(0 0 6px rgba(200,255,0,0.5)); }
  .mr-ring-fill.opp { stroke:var(--danger);  filter:drop-shadow(0 0 6px rgba(255,107,107,0.4)); }
  .mr-ring-num { fill:var(--text); font-family:var(--mono); font-size:26px; font-weight:300; text-anchor:middle; dominant-baseline:central; }
  .mr-ring-num.me { fill:var(--accent); }
  @keyframes scorePop { 0%{transform:scale(1)} 35%{transform:scale(1.18)} 65%{transform:scale(0.94)} 100%{transform:scale(1)} }
  .score-pop .mr-ring-num  { animation:scorePop 0.5s cubic-bezier(0.34,1.56,0.64,1); }
  .score-pop .mr-ring-fill { filter:drop-shadow(0 0 12px rgba(200,255,0,0.8)); }
  .mr-score-vs { display:flex; align-items:center; justify-content:center; padding:0 20px; }
  .mr-vs-inner { font-size:11px; font-weight:600; letter-spacing:0.2em; color:var(--muted); }

  /* ── Progress ── */
  .mr-progress-section { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:16px; }
  .mr-prog-card { background:var(--panel); border:1px solid var(--border); border-radius:var(--radius); padding:18px 22px; }
  .mr-prog-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; }
  .mr-prog-label { font-size:10px; color:var(--muted2); letter-spacing:0.16em; text-transform:uppercase; }
  .mr-prog-pct { font-size:13px; font-weight:600; }
  .mr-prog-pct.me  { color:var(--accent); text-shadow:var(--glow-sm); }
  .mr-prog-pct.opp { color:var(--danger); }
  .mr-prog-track { height:6px; background:var(--border2); border-radius:99px; overflow:hidden; position:relative; }
  .mr-prog-fill { height:100%; border-radius:99px; transition:width 1s cubic-bezier(0.4,0,0.2,1); position:relative; }
  .mr-prog-fill.me  { background:var(--accent); box-shadow:0 0 10px rgba(200,255,0,0.5); }
  .mr-prog-fill.opp { background:linear-gradient(90deg,#ff6b6b,#ff3333); box-shadow:0 0 10px rgba(255,107,107,0.4); }
  .mr-prog-fill::after { content:''; position:absolute; inset:0; background:linear-gradient(90deg,transparent 0%,rgba(255,255,255,0.22) 50%,transparent 100%); background-size:200% 100%; animation:progShimmer 2.4s ease-in-out infinite; }
  @keyframes progShimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }

  /* ── Problem table ── */
  .mr-prob-table { background:var(--panel); border:1px solid var(--border); border-radius:var(--radius-lg); overflow:hidden; }
  .mr-prob-table-head { display:grid; grid-template-columns:60px 1fr 170px 170px; padding:12px 28px; border-bottom:1px solid var(--border); background:var(--surface); }
  .mr-th { font-size:9px; color:var(--muted); letter-spacing:0.24em; text-transform:uppercase; }
  .mr-th.center { text-align:center; }
  .mr-prob-row { display:grid; grid-template-columns:60px 1fr 170px 170px; align-items:center; padding:20px 28px; border-bottom:1px solid var(--border); border-left:2px solid transparent; transition:border-color .3s,background .3s; position:relative; overflow:hidden; }
  .mr-prob-row:last-child { border-bottom:none; }
  .mr-prob-row.current-row { border-left-color:var(--accent); background:var(--accent-5); }
  .mr-prob-row.solved-row  { opacity:0.5; }

  /* ── Inline solve flash (no modal) ── */
  @keyframes solveFlash {
    0%   { background:rgba(200,255,0,0);    border-left-color:transparent; }
    8%   { background:rgba(200,255,0,0.22); border-left-color:var(--accent); box-shadow:inset 0 0 40px rgba(200,255,0,0.08); }
    30%  { background:rgba(200,255,0,0.12); border-left-color:var(--accent); }
    100% { background:rgba(200,255,0,0);    border-left-color:transparent; }
  }
  .mr-prob-row.just-solved { animation:solveFlash 2s ease-out forwards; }

  /* Inline solve scan line */
  .mr-prob-row.just-solved::after {
    content:'';
    position:absolute;
    inset:0;
    pointer-events:none;
    background:linear-gradient(90deg, transparent 0%, rgba(200,255,0,0.18) 50%, transparent 100%);
    background-size:200% 100%;
    background-position:200% 0;
    animation:solveScanLine 0.7s ease-out 0.05s forwards;
  }
  @keyframes solveScanLine {
    0%   { background-position:200% 0; opacity:1; }
    100% { background-position:-200% 0; opacity:0; }
  }

  /* Number circle — solved state pop */
  @keyframes solveCheckPop {
    0%   { transform:scale(0.2) rotate(-40deg); opacity:0; }
    55%  { transform:scale(1.4)  rotate(10deg);  opacity:1; }
    75%  { transform:scale(0.88) rotate(-4deg); }
    100% { transform:scale(1)    rotate(0deg);  opacity:1; }
  }
  .mr-prob-n.just-pop { animation:solveCheckPop 0.65s cubic-bezier(0.34,1.56,0.64,1) forwards !important; }

  /* Solved row fade-dim after flash */
  @keyframes solvedDim {
    0%   { opacity:1; }
    100% { opacity:0.5; }
  }
  .mr-prob-row.solved-row { animation:solvedDim 0.5s ease-out 1.8s both; }

  /* ── Unlock animation ── */
  @keyframes unlockSlideIn {
    0%   { opacity:0; transform:translateX(-28px) scaleY(0.85); }
    50%  { opacity:1; transform:translateX(4px)   scaleY(1.02); }
    100% { opacity:1; transform:translateX(0)     scaleY(1); }
  }
  .mr-prob-row.just-unlocked { animation:unlockSlideIn 0.65s cubic-bezier(0.22,1,0.36,1) forwards; }
  .mr-prob-row.just-unlocked::before {
    content:''; position:absolute; inset:0; pointer-events:none; z-index:0;
    background:linear-gradient(90deg, rgba(200,255,0,0.12) 0%, rgba(200,255,0,0.04) 60%, transparent 100%);
    animation:unlockGlowFade 1.6s ease-out 0.1s forwards;
  }
  .mr-prob-row.just-unlocked::after {
    content:''; position:absolute; inset:0; pointer-events:none; z-index:1;
    background:linear-gradient(90deg, transparent 0%, rgba(200,255,0,0.28) 50%, transparent 100%);
    background-size:200% 100%; background-position:200% 0;
    animation:unlockScan 0.8s ease-in-out 0.05s forwards;
  }
  @keyframes unlockGlowFade { 0%{opacity:1} 100%{opacity:0} }
  @keyframes unlockScan { 0%{background-position:200% 0;opacity:1} 100%{background-position:-200% 0;opacity:0} }

  /* ── Problem number circle ── */
  .mr-prob-n { font-size:13px; font-weight:600; color:var(--muted); width:32px; height:32px; display:flex; align-items:center; justify-content:center; border:1px solid var(--border); border-radius:50%; transition:all 0.5s; }
  .mr-prob-n.solved  { color:var(--bg); background:var(--accent); border-color:var(--accent); box-shadow:0 0 12px rgba(200,255,0,0.4); }
  .mr-prob-n.current { color:var(--text2); border-color:var(--border3); }

  /* ── Problem name ── */
  .mr-prob-name { font-size:13px; color:var(--muted2); }
  .mr-prob-name.current { color:var(--text); font-weight:500; }
  .mr-prob-name a { color:inherit; text-decoration:none; display:inline-flex; align-items:center; gap:7px; transition:color .15s; }
  .mr-prob-name a:hover { color:var(--accent); }
  .mr-prob-link-icon { font-size:10px; opacity:0.5; }
  .mr-prob-cell { text-align:center; }

  /* ── State chips ── */
  .mr-state { display:inline-flex; align-items:center; gap:6px; font-size:10px; letter-spacing:0.1em; text-transform:uppercase; padding:5px 13px; border-radius:99px; font-weight:600; }
  .mr-state.ac   { color:var(--bg); background:var(--accent); box-shadow:0 0 14px rgba(200,255,0,0.35); }
  .mr-state.live { color:var(--warn); background:rgba(255,170,68,0.1); border:1px solid rgba(255,170,68,0.28); }
  .mr-state.lock { color:var(--muted); background:transparent; }
  .mr-state-dot { width:5px; height:5px; border-radius:50%; flex-shrink:0; }
  .mr-state.live .mr-state-dot { background:var(--warn); animation:mrPulse 1.4s infinite; }

  /* AC chip pop-in when it first appears */
  @keyframes acChipIn {
    0%   { transform:scale(0.3) translateY(6px); opacity:0; box-shadow:0 0 0 rgba(200,255,0,0); }
    60%  { transform:scale(1.15) translateY(-2px); box-shadow:0 0 24px rgba(200,255,0,0.6); }
    100% { transform:scale(1) translateY(0); opacity:1; box-shadow:0 0 14px rgba(200,255,0,0.35); }
  }
  .mr-state.ac.just-ac { animation:acChipIn 0.5s cubic-bezier(0.34,1.56,0.64,1) forwards; }

  /* ── Unlock banner ── */
  .unlock-banner { position:fixed; bottom:72px; left:50%; transform:translateX(-50%); z-index:400; pointer-events:none; border-radius:var(--radius); animation:bannerIn 0.4s cubic-bezier(0.22,1,0.36,1); box-shadow:0 8px 32px rgba(0,0,0,0.7),0 0 32px rgba(200,255,0,0.1); }
  .unlock-banner-inner { display:flex; align-items:center; gap:12px; padding:12px 22px 12px 14px; background:var(--panel3); border:1px solid var(--border2); border-left:3px solid var(--accent); border-radius:var(--radius); position:relative; overflow:hidden; white-space:nowrap; }
  .unlock-banner-inner::after { content:''; position:absolute; inset:0; pointer-events:none; background:linear-gradient(90deg,transparent 0%,rgba(200,255,0,0.1) 50%,transparent 100%); background-size:200% 100%; background-position:200% 0; animation:unlockBannerScan 0.9s ease-out 0.15s forwards; }
  @keyframes unlockBannerScan { 0%{background-position:200% 0;opacity:1} 100%{background-position:-200% 0;opacity:0} }
  .unlock-banner-icon { width:28px; height:28px; border-radius:50%; background:var(--accent-10); border:1px solid var(--accent-20); display:flex; align-items:center; justify-content:center; flex-shrink:0; animation:unlockIconPop 0.5s cubic-bezier(0.34,1.56,0.64,1) 0.1s both; }
  @keyframes unlockIconPop { from{transform:scale(0) rotate(-90deg);opacity:0} to{transform:scale(1) rotate(0deg);opacity:1} }
  .unlock-banner-icon-dot { width:8px; height:8px; border-radius:50%; background:var(--accent); box-shadow:0 0 8px rgba(200,255,0,0.8); animation:mrPulse 0.8s infinite; }
  .unlock-banner-text { display:flex; flex-direction:column; gap:2px; }
  .unlock-banner-label { font-size:9px; letter-spacing:0.28em; text-transform:uppercase; color:var(--muted2); }
  .unlock-banner-name  { font-size:13px; font-weight:600; letter-spacing:0.04em; color:var(--accent); }
  @keyframes bannerIn { from{opacity:0;transform:translateX(-50%) translateY(16px) scale(0.94)} to{opacity:1;transform:translateX(-50%) translateY(0) scale(1)} }

  /* ── Toast ── */
  .mr-toast { position:fixed; bottom:28px; left:50%; transform:translateX(-50%); font-size:12px; letter-spacing:0.08em; padding:11px 24px; border-radius:var(--radius); border-left:2px solid var(--accent); background:var(--panel3); color:var(--accent-dim); white-space:nowrap; z-index:300; animation:mrToastIn .22s ease; box-shadow:0 4px 24px rgba(0,0,0,0.6); }
  .mr-toast.error { border-color:var(--danger); background:var(--danger-bg); color:#ffaaaa; }
  @keyframes mrToastIn { from{opacity:0;transform:translateX(-50%) translateY(10px)} to{opacity:1;transform:translateX(-50%) translateY(0)} }

  /* ── Chat Box ── */
  .mr-chat { position:fixed; bottom:24px; right:24px; width:320px; z-index:500; display:flex; flex-direction:column; gap:0; }
  .mr-chat-toggle { display:flex; align-items:center; gap:10px; background:var(--panel3); border:1px solid var(--border2); border-radius:var(--radius); padding:10px 16px; cursor:pointer; font-family:var(--mono); font-size:11px; letter-spacing:0.12em; text-transform:uppercase; color:var(--muted2); transition:color .15s,border-color .15s; }
  .mr-chat-toggle:hover { color:var(--text); border-color:var(--accent-20); }
  .mr-chat-unread { background:var(--accent); color:#06060a; font-size:9px; font-weight:700; border-radius:99px; padding:2px 7px; margin-left:auto; }
  .mr-chat-panel { background:var(--panel); border:1px solid var(--border2); border-radius:var(--radius) var(--radius) 0 0; overflow:hidden; display:flex; flex-direction:column; animation:chatSlideUp .25s cubic-bezier(0.22,1,0.36,1); }
  @keyframes chatSlideUp { from{opacity:0;transform:translateY(20px)} to{opacity:1;transform:translateY(0)} }
  .mr-chat-head { display:flex; align-items:center; justify-content:space-between; padding:10px 14px; border-bottom:1px solid var(--border); background:var(--surface); }
  .mr-chat-head-lbl { font-size:9px; letter-spacing:0.24em; text-transform:uppercase; color:var(--muted2); display:flex; align-items:center; gap:8px; }
  .mr-chat-live-dot { width:6px; height:6px; border-radius:50%; background:var(--accent); animation:mrPulse 1.6s infinite; }
  .mr-chat-close { background:none; border:none; cursor:pointer; color:var(--muted); font-size:16px; line-height:1; padding:0 2px; transition:color .15s; }
  .mr-chat-close:hover { color:var(--text); }
  .mr-chat-msgs { height:220px; overflow-y:auto; padding:12px; display:flex; flex-direction:column; gap:8px; scrollbar-width:thin; scrollbar-color:var(--border2) transparent; }
  .mr-chat-msgs::-webkit-scrollbar { width:4px; }
  .mr-chat-msgs::-webkit-scrollbar-thumb { background:var(--border2); border-radius:99px; }
  .mr-chat-msg { display:flex; flex-direction:column; gap:2px; animation:msgIn .18s ease; }
  @keyframes msgIn { from{opacity:0;transform:translateY(6px)} to{opacity:1;transform:translateY(0)} }
  .mr-chat-msg.mine { align-items:flex-end; }
  .mr-chat-sender { font-size:9px; letter-spacing:0.1em; color:var(--muted); text-transform:uppercase; }
  .mr-chat-bubble { font-size:12px; padding:7px 12px; border-radius:var(--radius); max-width:88%; word-break:break-word; line-height:1.5; }
  .mr-chat-msg:not(.mine) .mr-chat-bubble { background:var(--panel2); border:1px solid var(--border); color:var(--text2); border-radius:4px 12px 12px 12px; }
  .mr-chat-msg.mine .mr-chat-bubble { background:rgba(200,255,0,0.1); border:1px solid var(--accent-20); color:var(--accent-dim); border-radius:12px 4px 12px 12px; }
  .mr-chat-empty { font-size:11px; color:var(--muted); text-align:center; padding:20px 0; letter-spacing:0.06em; }
  .mr-chat-input-row { display:flex; border-top:1px solid var(--border); }
  .mr-chat-input { flex:1; background:none; border:none; outline:none; font-family:var(--mono); font-size:12px; color:var(--text); padding:11px 14px; placeholder-color:var(--muted); }
  .mr-chat-input::placeholder { color:var(--muted); }
  .mr-chat-send { background:none; border:none; border-left:1px solid var(--border); padding:0 14px; cursor:pointer; font-size:16px; transition:background .15s; }
  .mr-chat-send:hover { background:var(--accent-5); }

  /* ── Emote Board ── */
  .mr-emote-board { display:flex; align-items:center; gap:6px; padding:10px 14px; border-top:1px solid var(--border); background:var(--surface); overflow-x:auto; scrollbar-width:none; }
  .mr-emote-board::-webkit-scrollbar { display:none; }
  .mr-emote-btn { background:none; border:1px solid var(--border); border-radius:var(--radius); padding:5px 9px; font-size:18px; cursor:pointer; transition:transform .1s, border-color .15s, background .15s; flex-shrink:0; }
  .mr-emote-btn:hover { transform:scale(1.3); border-color:var(--border3); background:var(--panel2); }
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

  /* ── Skip Vote ── */
  .mr-skip-bar { display:flex; align-items:center; justify-content:center; gap:16px; padding:16px 24px; margin-top:16px; background:var(--panel); border:1px solid var(--border); border-radius:var(--radius); }
  .mr-skip-status { font-size:11px; color:var(--muted2); letter-spacing:0.08em; display:flex; align-items:center; gap:8px; }
  .mr-skip-status .check { color:var(--accent); }
  .mr-btn-skip { font-family:var(--mono); font-size:10px; font-weight:600; letter-spacing:0.12em; text-transform:uppercase; color:var(--warn); background:transparent; border:1px solid var(--warn-border); padding:10px 22px; border-radius:var(--radius); cursor:pointer; transition:all .15s; white-space:nowrap; }
  .mr-btn-skip:hover { background:var(--warn-bg); border-color:var(--warn); }
  .mr-btn-skip:disabled { opacity:0.3; cursor:not-allowed; }
  .mr-btn-skip.voted { color:var(--muted2); border-color:var(--border2); background:var(--panel2); cursor:default; }
  .mr-state.skipped { color:var(--muted2); background:rgba(128,128,144,0.1); border:1px solid var(--border2); }

  /* ── Responsive ── */
  @media (max-width:640px) {
    .mr-players { grid-template-columns:1fr; }
    .mr-player  { border-right:none; border-bottom:1px solid var(--border); }
    .mr-score-section { grid-template-columns:1fr; }
    .mr-score-vs { display:none; }
    .mr-score-opp { border-left:none; border-top:1px solid var(--border); text-align:left; }
    .mr-score-opp .mr-score-ring-wrap { flex-direction:row; }
    .mr-score-opp .mr-score-handle { justify-content:flex-start; }
    .mr-progress-section { grid-template-columns:1fr; }
    .mr-prob-table-head { grid-template-columns:44px 1fr 96px 96px; }
    .mr-prob-row        { grid-template-columns:44px 1fr 96px 96px; }
    .mr-timer { font-size:40px; }
    .mr-main { padding:20px 16px 60px; }
    .mr-lobby-foot { flex-direction:column; align-items:stretch; }
    .mr-score-me, .mr-score-opp { padding:24px 24px; }
  }
`

function parseProblem(raw, index) {
  const label = `P${index + 1}`
  const fullLabel = `Problem ${index + 1}`
  if (!raw) return { label, fullLabel, url: '#' }
  if (raw.startsWith('http')) {
    let m = raw.match(/\/contest\/(\d+)\/problem\/([A-Z]\d*)/i)
    if (m) return { label, fullLabel, url: `https://codeforces.com/contest/${m[1]}/problem/${m[2].toUpperCase()}` }
    m = raw.match(/\/problemset\/problem\/(\d+)\/([A-Z]\d*)/i)
    if (m) return { label, fullLabel, url: `https://codeforces.com/contest/${m[1]}/problem/${m[2].toUpperCase()}` }
    return { label, fullLabel, url: raw }
  }
  const m = raw.match(/^(\d+)([A-Z]\d*)$/i)
  if (m) return { label, fullLabel, url: `https://codeforces.com/contest/${m[1]}/problem/${m[2].toUpperCase()}` }
  return { label, fullLabel, url: `https://codeforces.com/problemset?search=${encodeURIComponent(raw)}` }
}

function formatTime(ms) {
  if (ms <= 0) return '00:00'
  const s = Math.floor(ms / 1000)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function ScoreRing({ score, total, isMe }) {
  const r = 42, cx = 52, cy = 52
  const circ = 2 * Math.PI * r
  const offset = circ * (1 - (total > 0 ? score / total : 0))
  return (
    <svg className="mr-ring-svg" width="104" height="104" viewBox="0 0 104 104">
      <circle className="mr-ring-track" cx={cx} cy={cy} r={r} />
      <circle className={`mr-ring-fill ${isMe ? 'me' : 'opp'}`} cx={cx} cy={cy} r={r}
        strokeDasharray={circ} strokeDashoffset={offset}
        style={{ transform: 'rotate(-90deg)', transformOrigin: '52px 52px' }} />
      <text className={`mr-ring-num ${isMe ? 'me' : ''}`} x={cx} y={cy}>{score}</text>
    </svg>
  )
}

function LoadingScreen() {
  const [step, setStep] = useState(0)
  const steps = ['Verifying players', 'Fetching problems from Codeforces', 'Setting up match room']
  useEffect(() => {
    const t1 = setTimeout(() => setStep(1), 600)
    const t2 = setTimeout(() => setStep(2), 1400)
    return () => { clearTimeout(t1); clearTimeout(t2) }
  }, [])
  return (
    <div className="mr-loading">
      <div className="mr-loading-inner">
        <div className="mr-loading-eyebrow">CF Arena</div>
        <div className="mr-loading-title">Match<br /><span>Starting.</span></div>
        <div className="mr-loading-sub">// fetching problems &amp; initializing room</div>
        <div className="mr-loading-bar-wrap"><div className="mr-loading-bar" /></div>
        <div className="mr-loading-steps">
          {steps.map((s, i) => (
            <div key={i} className={`mr-loading-step ${i === step ? 'active' : i < step ? 'done' : ''}`}>
              <div className="mr-ls-icon" /><span>{s}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function UnlockBanner({ problemName }) {
  return (
    <div className="unlock-banner">
      <div className="unlock-banner-inner">
        <div className="unlock-banner-icon"><div className="unlock-banner-icon-dot" /></div>
        <div className="unlock-banner-text">
          <span className="unlock-banner-label">unlocked</span>
          <span className="unlock-banner-name">{problemName}</span>
        </div>
      </div>
    </div>
  )
}

export default function MatchRoom() {
  const { inviteCode } = useParams()
  const { state }      = useLocation()
  const navigate       = useNavigate()

  const [match, setMatch]                 = useState(null)
  const [myHandle, setMyHandle]           = useState(null)
  const [myHandleReady, setMyHandleReady] = useState(false)
  const [timeLeft, setTimeLeft]           = useState(null)
  const [loadError, setLoadError]         = useState(null)
  const [toast, setToast]                 = useState(null)
  const [isReadying, setIsReadying]       = useState(false)
  const [isStarting, setIsStarting]       = useState(false)
  const [codeCopied, setCodeCopied]       = useState(false)
  const [showLoading, setShowLoading]     = useState(false)

  const [meAvatar, setMeAvatar]           = useState(null)
  const [oppAvatar, setOppAvatar]         = useState(null)

  // ── Chat & Emote state ──────────────────────────────────
  const [chatOpen,    setChatOpen]    = useState(false)
  const [chatMsgs,    setChatMsgs]    = useState([])
  const [chatInput,   setChatInput]   = useState('')
  const [unreadCount, setUnreadCount] = useState(0)
  const [floatEmotes, setFloatEmotes] = useState([])   // [{id, emote, x}]
  const chatEndRef = useRef(null)

  const EMOTES = ['🔥', '💀', '🚀', '🤡', '💩', '😤', '🧠', '⚡']

  const [justSolvedRows,   setJustSolvedRows]   = useState({})
  const [justUnlockedRows, setJustUnlockedRows] = useState({})
  const [justPopNums,      setJustPopNums]      = useState({})
  // Track which rows just got AC chip so we can key them for re-mount animation
  const [justAcRows,       setJustAcRows]       = useState({})
  const [scorePopMe,       setScorePopMe]       = useState(false)
  const [scorePopOpp,      setScorePopOpp]      = useState(false)
  const [unlockBanner,     setUnlockBanner]     = useState(null)

  // ── Skip vote state ──────────────────────────────────────
  const [skipVoteMe,  setSkipVoteMe]  = useState(false)
  const [skipVoteOpp, setSkipVoteOpp] = useState(false)
  const prevCurIdxForSkipRef = useRef(null)

  const wsRef          = useRef(null)
  const timerRef       = useRef(null)
  const prevStatusRef  = useRef(null)
  const prevResultsRef = useRef(null)
  const prevCurIdxRef  = useRef(null)
  const amIUser1Ref    = useRef(false)

  const navRole = state?.role || 'guest'

  // ── Browser Notifications ──────────────────────────────────
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission()
    }
  }, [])

  const notify = useCallback((title, body) => {
    if (
      'Notification' in window &&
      Notification.permission === 'granted' &&
      document.hidden
    ) {
      try {
        new Notification(title, {
          body,
          icon: '/favicon.svg',
          tag: 'cfarena-match',
        })
      } catch {}
    }
  }, [])

  const showToast = (text, type = 'ok') => {
    setToast({ text, type })
    setTimeout(() => setToast(null), 3500)
  }

  const detectChanges = useCallback((data) => {
    const newP1 = toResultsArray(data?.player1Results)
    const newP2 = toResultsArray(data?.player2Results)

    // First call — seed refs, fire nothing
    if (prevResultsRef.current === null) {
      prevResultsRef.current = { p1: newP1, p2: newP2 }
      prevCurIdxRef.current  = data?.curIdx ?? 0
      return
    }

    const oldP1 = prevResultsRef.current.p1
    const oldP2 = prevResultsRef.current.p2

    const newlySolvedP1 = []
    const newlySolvedP2 = []
    newP1.forEach((r, i) => { if (r === 'SOLVED' && oldP1[i] !== 'SOLVED') newlySolvedP1.push(i) })
    newP2.forEach((r, i) => { if (r === 'SOLVED' && oldP2[i] !== 'SOLVED') newlySolvedP2.push(i) })

    // Row flash + number pop + AC chip re-mount for any player's solve
    ;[...new Set([...newlySolvedP1, ...newlySolvedP2])].forEach(idx => {
      setJustSolvedRows(p => ({ ...p, [idx]: true }))
      setJustPopNums(p => ({ ...p, [idx]: true }))
      setJustAcRows(p => ({ ...p, [idx]: Date.now() })) // unique key forces chip re-mount
      setTimeout(() => {
        setJustSolvedRows(p => { const n = { ...p }; delete n[idx]; return n })
        setJustPopNums(p => { const n = { ...p }; delete n[idx]; return n })
      }, 2000)
    })

    // Score ring pop
    if (newlySolvedP1.length > 0) { setScorePopMe(true);  setTimeout(() => setScorePopMe(false),  600) }
    if (newlySolvedP2.length > 0) { setScorePopOpp(true); setTimeout(() => setScorePopOpp(false), 600) }

    // Fireworks only for MY solve — no modal, just celebration + inline row animation
    const iAmUser1    = amIUser1Ref.current
    const myNewSolves = iAmUser1 ? newlySolvedP1 : newlySolvedP2
    const oppNewSolves = iAmUser1 ? newlySolvedP2 : newlySolvedP1
    if (myNewSolves.length > 0) {
      celebrateSolve()
    }

    // 🔔 Desktop notification when opponent solves
    if (oppNewSolves.length > 0) {
      const probs = Array.isArray(data?.problems) ? data.problems : []
      oppNewSolves.forEach(idx => {
        const pLabel = `Problem ${idx + 1}`
        const s1 = iAmUser1 ? (data?.score1 ?? 0) : (data?.score2 ?? 0)
        const s2 = iAmUser1 ? (data?.score2 ?? 0) : (data?.score1 ?? 0)
        notify('⚔ CF Arena', `Opponent solved ${pLabel}\nScore: ${s1} - ${s2}`)
      })
    }

    // Unlock banner when curIdx advances
    const newCurIdx = data?.curIdx ?? 0
    const oldCurIdx = prevCurIdxRef.current
    if (oldCurIdx !== null && newCurIdx > oldCurIdx) {
      const probs = Array.isArray(data?.problems) ? data.problems : []
      setJustUnlockedRows(p => ({ ...p, [newCurIdx]: true }))
      setUnlockBanner(parseProblem(probs[newCurIdx], newCurIdx).fullLabel)
      setTimeout(() => {
        setJustUnlockedRows(p => { const n = { ...p }; delete n[newCurIdx]; return n })
        setUnlockBanner(null)
      }, 2800)
    }

    prevCurIdxRef.current  = newCurIdx
    prevResultsRef.current = { p1: newP1, p2: newP2 }
  }, [])

  useEffect(() => {
    axiosInstance.get(API_PATHS.USER.ME)
      .then(r => { setMyHandle(r?.data?.cfHandle || null); setMyHandleReady(true) })
      .catch(() => { setMyHandleReady(true) })
  }, [])

  const fetchStatus = useCallback(async () => {
    try {
      const res  = await axiosInstance.get(`${API_PATHS.MATCH.STATUS}?inviteCode=${encodeURIComponent(inviteCode)}`)
      const data = res?.data
      if (data) {
        const prev = prevStatusRef.current
        if ((prev === 'WAITING' || prev === 'READY') && data.status === 'ONGOING') {
          setShowLoading(true)
          setTimeout(() => setShowLoading(false), data.problems?.length > 0 ? 800 : 2000)
        }
        prevStatusRef.current = data.status
        detectChanges(data)
        setMatch(data)
        // ✅ Use functional updater so this doesn't need showLoading in the dep array
        setShowLoading(prev => {
          if (prev && data.problems?.length > 0) setTimeout(() => setShowLoading(false), 600)
          return prev
        })
        if (data.status === 'FINISHED') navigate(`/results/${inviteCode}`)
      }
    } catch (err) {
      setLoadError(err?.response?.data?.message || err.message || 'Could not load match.')
    }
  // ✅ showLoading intentionally removed from deps — using functional updater above avoids stale closure
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inviteCode, navigate, detectChanges])

  useEffect(() => {
    fetchStatus()
    const t = setInterval(fetchStatus, 3000)
    return () => clearInterval(t)
  }, [fetchStatus])

  // ── Auto-scroll chat to bottom ──────────────────────────
  useEffect(() => {
    if (chatOpen) chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMsgs, chatOpen])

  // ── Reset unread when chat opened ────────────────────────
  useEffect(() => { if (chatOpen) setUnreadCount(0) }, [chatOpen])

  // ── Send chat message ─────────────────────────────────────
  const sendChat = () => {
    const text = chatInput.trim()
    if (!text || !wsRef.current) return
    wsRef.current.send('/app/match/chat', {}, JSON.stringify({
      inviteCode,
      sender: myHandle || 'you',
      text,
    }))
    setChatInput('')
  }

  // ── Send emote ────────────────────────────────────────────
  const sendEmote = (emote) => {
    if (!wsRef.current) return
    wsRef.current.send('/app/match/emote', {}, JSON.stringify({
      inviteCode,
      sender: myHandle || 'you',
      emote,
    }))
  }

  // ── Send skip vote ────────────────────────────────────────
  const sendSkipVote = () => {
    if (!wsRef.current || skipVoteMe) return
    setSkipVoteMe(true)
    wsRef.current.send('/app/match/skip', {}, JSON.stringify({
      inviteCode,
      sender: myHandle || 'you',
    }))
    showToast('You voted to skip. Waiting for opponent…')
  }

  // ── Trigger floating emote animation ─────────────────────
  const triggerFloatEmote = (emote) => {
    const id  = Date.now() + Math.random()
    const x   = 10 + Math.random() * 80   // random horizontal position %
    setFloatEmotes(prev => [...prev, { id, emote, x }])
    setTimeout(() => setFloatEmotes(prev => prev.filter(e => e.id !== id)), 2400)
  }

  useEffect(() => {
    const s1 = document.createElement('script')
    s1.src = 'https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js'
    s1.onload = () => {
      const s2 = document.createElement('script')
      s2.src = 'https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js'
      s2.onload = () => {
        try {
          // WS_BASE is an https:// URL; SockJS accepts http/https and upgrades to wss automatically.
          const sock   = new window.SockJS(`${WS_BASE}/ws`)
          const client = window.Stomp.over(sock)
          client.debug = null
          client.connect({}, () => {
            // ── Existing: match score updates ──────────────
            client.subscribe(`/topic/match/${inviteCode}`, msg => {
              try {
                const data = JSON.parse(msg.body)
                const prev = prevStatusRef.current
                if ((prev === 'WAITING' || prev === 'READY') && data.status === 'ONGOING') {
                  setShowLoading(true)
                  setTimeout(() => setShowLoading(false), data.problems?.length > 0 ? 800 : 2000)
                }
                prevStatusRef.current = data.status
                detectChanges(data)
                setMatch(data)
                if (data?.status === 'FINISHED') navigate(`/results/${inviteCode}`)
              } catch {}
            })

            // ── NEW: real-time chat ────────────────────────
            client.subscribe(`/topic/match/${inviteCode}/chat`, msg => {
              try {
                const data = JSON.parse(msg.body)
                setChatMsgs(prev => [...prev, data])
                setChatOpen(prev => {
                  if (!prev) setUnreadCount(c => c + 1)
                  return prev
                })
                // 🔔 Notify for opponent's chat messages
                if (data.sender !== (myHandle || 'you')) {
                  notify('💬 CF Arena', `${data.sender}: ${data.text}`)
                }
              } catch {}
            })

            // ── NEW: emote reactions ───────────────────────
            client.subscribe(`/topic/match/${inviteCode}/emotes`, msg => {
              try {
                const data = JSON.parse(msg.body)
                triggerFloatEmote(data.emote)
              } catch {}
            })

            // ── NEW: skip vote notifications ───────────────
            client.subscribe(`/topic/match/${inviteCode}/skip`, msg => {
              try {
                const data = JSON.parse(msg.body)
                // Backend sends skipVoteUser1 + skipVoteUser2 booleans.
                // Map them to "me" vs "opponent" using amIUser1Ref.
                const iAmUser1 = amIUser1Ref.current
                const myVoteFlag   = iAmUser1 ? data.skipVoteUser1 : data.skipVoteUser2
                const oppVoteFlag  = iAmUser1 ? data.skipVoteUser2 : data.skipVoteUser1
                if (myVoteFlag)  setSkipVoteMe(true)
                if (oppVoteFlag) {
                  setSkipVoteOpp(true)
                  showToast('Opponent wants to skip!')
                  notify('⊘ CF Arena', 'Opponent wants to skip the current problem')
                }
              } catch {}
            })
          })
          wsRef.current = client
        } catch {}
      }
      document.head.appendChild(s2)
    }
    document.head.appendChild(s1)
    return () => { try { wsRef.current?.disconnect?.() } catch {} }
  }, [inviteCode, navigate, detectChanges])

  useEffect(() => {
    if (!match?.endTime || match.status !== 'ONGOING') { clearInterval(timerRef.current); return }
    const tick = () => {
      const left = new Date(match.endTime).getTime() - Date.now()
      setTimeLeft(left)
      if (left <= 0) { clearInterval(timerRef.current); fetchStatus() }
    }
    tick(); clearInterval(timerRef.current); timerRef.current = setInterval(tick, 1000)
    return () => clearInterval(timerRef.current)
  }, [match?.endTime, match?.status, fetchStatus])

  useEffect(() => {
    if (!match) return
    if ((match?.curIdx ?? 0) > 5) navigate(`/results/${inviteCode}`)
  }, [match, navigate, inviteCode])

  const amIUser1 = myHandleReady
    ? (myHandle ? match?.user1?.toLowerCase() === myHandle.toLowerCase() : navRole === 'host')
    : navRole === 'host'
  amIUser1Ref.current = amIUser1

  const isHost  = amIUser1
  const isGuest = myHandleReady && myHandle
    ? match?.user2?.toLowerCase() === myHandle.toLowerCase()
    : navRole === 'guest'

  const meHandle  = amIUser1 ? match?.user1 : match?.user2
  const oppHandle = amIUser1 ? match?.user2 : match?.user1

  // Fetch avatars whenever handles are confirmed
  useEffect(() => {
    if (meHandle && meHandle !== 'waiting…') {
      fetchCFAvatar(meHandle).then(url => { if(url) setMeAvatar(url) })
    }
  }, [meHandle])

  useEffect(() => {
    if (oppHandle && oppHandle !== 'waiting…') {
      fetchCFAvatar(oppHandle).then(url => { if(url) setOppAvatar(url) })
    }
  }, [oppHandle])
  const meScore   = amIUser1 ? (match?.score1 ?? 0) : (match?.score2 ?? 0)
  const oppScore  = amIUser1 ? (match?.score2 ?? 0) : (match?.score1 ?? 0)

  const problems      = Array.isArray(match?.problems) ? match.problems : []
  const totalProblems = problems.length || 1
  const curIdx        = match?.curIdx ?? 0
  const mePct         = Math.round((meScore / totalProblems) * 100)
  const oppPct        = Math.round((oppScore / totalProblems) * 100)

  const matchStatus   = match?.status || 'WAITING'
  const isLobby       = matchStatus === 'WAITING' || matchStatus === 'READY'
  const isOngoing     = matchStatus === 'ONGOING'
  const timerUrgent   = timeLeft !== null && timeLeft < 5 * 60 * 1000
  const guestJoined   = match?.user2 != null
  const guestReady    = guestJoined && matchStatus === 'READY'
  const canClickReady = !isHost && !guestJoined && navRole === 'guest'

  const dotClass  = isOngoing ? 'live' : matchStatus === 'FINISHED' ? 'done' : 'waiting'
  const statusTxt = !match ? 'Loading…'
    : isOngoing ? 'Live — match in progress'
    : matchStatus === 'FINISHED' ? 'Match finished'
    : matchStatus === 'READY' ? 'Both players ready — host can start'
    : 'Waiting for player 2 to join…'

  function myProblemState(i) {
    const results = toResultsArray(match?.player1Results)
    const oppResults = toResultsArray(match?.player2Results)
    const myR  = amIUser1 ? results[i]    : oppResults[i]
    if (myR === 'SOLVED') return 'solved'
    if (myR === 'SKIPPED') return 'skipped'
    if (i === curIdx) return 'current'
    return 'locked'
  }
  function oppProblemState(i) {
    const results = toResultsArray(match?.player1Results)
    const oppResults = toResultsArray(match?.player2Results)
    const oppR = amIUser1 ? oppResults[i] : results[i]
    if (oppR === 'SOLVED') return 'solved'
    if (oppR === 'SKIPPED') return 'skipped'
    if (i === curIdx) return 'working'
    return 'locked'
  }

  // Reset skip votes when curIdx changes (problem advanced)
  useEffect(() => {
    if (prevCurIdxForSkipRef.current !== null && curIdx !== prevCurIdxForSkipRef.current) {
      setSkipVoteMe(false)
      setSkipVoteOpp(false)
    }
    prevCurIdxForSkipRef.current = curIdx
  }, [curIdx])

  const renderAvatar = (handle, size = '48px') => {
    const av = handle === meHandle ? meAvatar : handle === oppHandle ? oppAvatar : null
    const glowCol = handle === meHandle ? '#c8ff00' : '#ff6b6b'
    if (!av || !handle || handle === 'waiting…') return null
    return <img src={av} alt={handle} style={{ width: size, height: size, borderRadius: '50%', border: `1px solid ${glowCol}`, objectFit: 'cover', flexShrink: 0, boxShadow: `0 0 12px ${glowCol}33` }} />
  }

  const onReady = async () => {
    setIsReadying(true)
    try {
      await axiosInstance.post(`${API_PATHS.MATCH.JOIN}?inviteCode=${encodeURIComponent(inviteCode)}`)
      showToast('Ready! Waiting for host…'); fetchStatus()
    } catch (err) { showToast(err?.response?.data?.message || 'Failed to ready up.', 'error') }
    finally { setIsReadying(false) }
  }

  const onStart = async () => {
    setIsStarting(true); setShowLoading(true)
    try {
      await axiosInstance.post(`${API_PATHS.MATCH.START}?inviteCode=${encodeURIComponent(inviteCode)}`)
      fetchStatus()
    } catch (err) { setShowLoading(false); showToast(err?.response?.data?.message || 'Cannot start yet.', 'error') }
    finally { setIsStarting(false) }
  }

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(inviteCode)
      setCodeCopied(true); showToast('Invite code copied!')
      setTimeout(() => setCodeCopied(false), 2000)
    } catch { showToast('Copy failed.', 'error') }
  }

  return (
    <>
      <style>{css}</style>
      {showLoading && <LoadingScreen />}
      {unlockBanner && <UnlockBanner problemName={unlockBanner} />}

      {/* ── Floating Emote Overlay ── */}
      <div className="mr-emote-overlay">
        {floatEmotes.map(({ id, emote, x }) => (
          <div key={id} className="mr-float-emote" style={{ left: `${x}%` }}>{emote}</div>
        ))}
      </div>

      {/* ── Chat Box (fixed bottom-right) ── */}
      {isOngoing && (
        <div className="mr-chat">
          {chatOpen && (
            <div className="mr-chat-panel">
              <div className="mr-chat-head">
                <span className="mr-chat-head-lbl">
                  <span className="mr-chat-live-dot" />
                  Match Chat
                </span>
                <button className="mr-chat-close" onClick={() => setChatOpen(false)}>✕</button>
              </div>
              <div className="mr-chat-msgs">
                {chatMsgs.length === 0 && <div className="mr-chat-empty">No messages yet. Say something!</div>}
                {chatMsgs.map((m, i) => (
                  <div key={i} className={`mr-chat-msg ${m.sender === (myHandle || 'you') ? 'mine' : ''}`}>
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
                  placeholder="Type a message…"
                  value={chatInput}
                  onChange={ev => setChatInput(ev.target.value)}
                  onKeyDown={ev => ev.key === 'Enter' && sendChat()}
                />
                <button className="mr-chat-send" onClick={sendChat}>↑</button>
              </div>
            </div>
          )}
          <button className="mr-chat-toggle" onClick={() => setChatOpen(o => !o)}>
            💬 <span>Chat</span>
            {!chatOpen && unreadCount > 0 && (
              <span className="mr-chat-unread">{unreadCount}</span>
            )}
          </button>
        </div>
      )}

      <div className="mr">
        <Navbar onCfSaved={() => showToast('CF handle updated!')} />
        <main className="mr-main">
          <div className="mr-topbar">
            <div>
              <div className="mr-tag">CF Arena · Match Room</div>
              <div className="mr-title">code_<em>{inviteCode}</em></div>
            </div>
            {isOngoing && (
              <div className="mr-timer-block">
                <div className={`mr-timer ${timerUrgent ? 'urgent' : ''}`}>
                  {timeLeft !== null ? formatTime(timeLeft) : '--:--'}
                </div>
                <div className="mr-timer-lbl">time remaining</div>
              </div>
            )}
          </div>

          <div className={`mr-status ${isOngoing ? 'live' : ''}`}>
            <span className={`mr-dot ${dotClass}`} />
            <span className="mr-status-txt">{statusTxt}</span>
          </div>

          {loadError && <div className="mr-err">{loadError}</div>}

          {isLobby && match && (
            <div className="mr-lobby">
              <div className="mr-lobby-head">
                <div className="mr-lobby-title">{matchStatus === 'READY' ? 'Ready to fight.' : 'Waiting for players.'}</div>
                <div className="mr-lobby-sub">// {match.duration || '?'}-minute match · {problems.length || '?'} problems</div>
              </div>
              <div className="mr-players">
                <div className="mr-player">
                  <div className="mr-player-lbl">Player 1 · Host</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '12px' }}>
                    {renderAvatar(match.user1, '60px')}
                    <div>
                      <div className="mr-player-name" style={{ marginBottom: 0 }}>{match.user1 || '—'}</div>
                      <div className="mr-badges" style={{ marginTop: '8px' }}>
                        {match.user1 && <span className="mr-badge host">host</span>}
                        {isHost && <span className="mr-badge you">you</span>}
                      </div>
                    </div>
                  </div>
                </div>
                <div className="mr-player">
                  <div className="mr-player-lbl">Player 2 · Guest</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '12px' }}>
                    {renderAvatar(match.user2, '60px')}
                    <div>
                      <div className={`mr-player-name ${!guestJoined ? 'empty' : ''}`} style={{ marginBottom: 0 }}>{match.user2 || 'waiting…'}</div>
                      <div className="mr-badges" style={{ marginTop: '8px' }}>
                        {guestJoined && <span className={`mr-badge ${guestReady ? 'ready' : 'joined'}`}>{guestReady ? 'ready' : 'joined'}</span>}
                        {isGuest && <span className="mr-badge you">you</span>}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div className="mr-code-row">
                <div className="mr-code-lbl-block"><span className="mr-code-lbl">Invite Code</span></div>
                <div className="mr-code-val">{inviteCode}</div>
                <button className={`mr-copy-btn ${codeCopied ? 'copied' : ''}`} onClick={copyCode}>
                  {codeCopied ? '✓ Copied' : '⎘ Copy'}
                </button>
              </div>
              <div className="mr-lobby-foot">
                <div className="mr-hint">
                  {isHost
                    ? guestReady ? <>Opponent is ready. <em>Start when you are.</em></> : 'Share the code. Start unlocks once opponent clicks Ready.'
                    : guestJoined ? "You're in. Waiting for host to start." : <>Click <em>Ready Up</em> to signal you're set.</>}
                </div>
                {isHost && (
                  <button className="mr-btn-primary" onClick={onStart} disabled={!guestReady || isStarting}>
                    {isStarting ? 'Starting…' : guestReady ? '⚔ Start Match' : 'Awaiting…'}
                  </button>
                )}
                {canClickReady && (
                  <button className="mr-btn-primary" onClick={onReady} disabled={isReadying}>
                    {isReadying ? 'Joining…' : '✓ Ready Up'}
                  </button>
                )}
                {!isHost && navRole !== 'host' && guestJoined && (
                  <button className="mr-btn-primary" disabled>Waiting for host…</button>
                )}
              </div>
            </div>
          )}

          {isOngoing && match && (
            <>
              <div className="mr-score-section">
                <div className={`mr-score-me ${scorePopMe ? 'score-pop' : ''}`}>
                  <div className="mr-score-handle" style={{ display: 'flex', alignItems: 'center' }}>
                    {renderAvatar(meHandle, '24px')}
                    <span className="mr-score-handle-pip" />
                    {meHandle || 'you'}&nbsp;·&nbsp;you
                  </div>
                  <div className="mr-score-ring-wrap">
                    <ScoreRing score={meScore} total={totalProblems} isMe={true} />
                    <div><div className="mr-score-lbl">problems solved</div></div>
                  </div>
                </div>
                <div className="mr-score-vs"><div className="mr-vs-inner">VS</div></div>
                <div className={`mr-score-opp ${scorePopOpp ? 'score-pop' : ''}`}>
                  <div className="mr-score-handle" style={{ display: 'flex', alignItems: 'center' }}>
                    {oppHandle || 'opponent'}
                    <span className="mr-score-handle-pip" style={{ background: '#ff6b6b', boxShadow: 'none' }} />
                    {renderAvatar(oppHandle, '24px')}
                  </div>
                  <div className="mr-score-ring-wrap">
                    <ScoreRing score={oppScore} total={totalProblems} isMe={false} />
                    <div><div className="mr-score-lbl">problems solved</div></div>
                  </div>
                </div>
              </div>

              <div className="mr-progress-section">
                <div className="mr-prog-card">
                  <div className="mr-prog-head">
                    <span className="mr-prog-label">Your progress</span>
                    <span className="mr-prog-pct me">{mePct}%</span>
                  </div>
                  <div className="mr-prog-track"><div className="mr-prog-fill me" style={{ width: `${mePct}%` }} /></div>
                </div>
                <div className="mr-prog-card">
                  <div className="mr-prog-head">
                    <span className="mr-prog-label">{oppHandle || 'Opponent'}</span>
                    <span className="mr-prog-pct opp">{oppPct}%</span>
                  </div>
                  <div className="mr-prog-track"><div className="mr-prog-fill opp" style={{ width: `${oppPct}%` }} /></div>
                </div>
              </div>

              <div className="mr-prob-table">
                <div className="mr-prob-table-head">
                  <span className="mr-th">#</span>
                  <span className="mr-th">Problem</span>
                  <span className="mr-th center">You</span>
                  <span className="mr-th center">{oppHandle || 'Opponent'}</span>
                </div>
                {problems.map((raw, i) => {
                  const { fullLabel, url } = parseProblem(raw, i)
                  const myState  = myProblemState(i)
                  const oppState = oppProblemState(i)
                  // Use justAcRows[i] as key suffix so chip remounts and re-animates on solve
                  const acKey = justAcRows[i] || 0
                  return (
                    <div key={i} className={[
                      'mr-prob-row',
                      i === curIdx         ? 'current-row'   : '',
                      myState === 'solved' ? 'solved-row'    : '',
                      justSolvedRows[i]    ? 'just-solved'   : '',
                      justUnlockedRows[i]  ? 'just-unlocked' : '',
                    ].filter(Boolean).join(' ')}>
                      <div className={[
                        'mr-prob-n',
                        myState === 'solved'  ? 'solved'   : '',
                        myState === 'current' ? 'current'  : '',
                        justPopNums[i]        ? 'just-pop' : '',
                      ].filter(Boolean).join(' ')}>
                        {myState === 'solved' ? '✓' : i + 1}
                      </div>
                      <div className={`mr-prob-name ${myState === 'current' ? 'current' : ''}`}>
                        {myState === 'current'
                          ? <a href={url} target="_blank" rel="noreferrer">{fullLabel}<span className="mr-prob-link-icon">↗</span></a>
                          : fullLabel}
                      </div>
                      <div className="mr-prob-cell">
                        {myState === 'solved'  && (
                          <span key={`ac-me-${i}-${acKey}`} className={`mr-state ac ${justAcRows[i] ? 'just-ac' : ''}`}>
                            ✓ Solved
                          </span>
                        )}
                        {myState === 'current' && <span className="mr-state live"><span className="mr-state-dot" />Live</span>}
                        {myState === 'skipped' && <span className="mr-state skipped">⊘ Skipped</span>}
                        {myState === 'locked'  && <span className="mr-state lock">—</span>}
                      </div>
                      <div className="mr-prob-cell">
                        {oppState === 'solved'  && (
                          <span key={`ac-opp-${i}-${acKey}`} className={`mr-state ac ${justAcRows[i] ? 'just-ac' : ''}`}>
                            ✓ Solved
                          </span>
                        )}
                        {oppState === 'working' && <span className="mr-state live"><span className="mr-state-dot" />Live</span>}
                        {oppState === 'skipped' && <span className="mr-state skipped">⊘ Skipped</span>}
                        {oppState === 'locked'  && <span className="mr-state lock">—</span>}
                      </div>
                    </div>
                  )
                })}
              </div>

              {/* ── Skip Vote Bar ── */}
              {curIdx < problems.length && (
                <div className="mr-skip-bar">
                  <div className="mr-skip-status">
                    {skipVoteMe  && <span>You: <span className="check">✓ voted</span></span>}
                    {skipVoteOpp && <span>Opponent: <span className="check">✓ voted</span></span>}
                    {!skipVoteMe && !skipVoteOpp && <span>Both players must agree to skip</span>}
                    {skipVoteMe && !skipVoteOpp && <span style={{color:'var(--muted)'}}>· waiting for opponent…</span>}
                    {!skipVoteMe && skipVoteOpp && <span style={{color:'var(--warn)'}}>· opponent wants to skip!</span>}
                  </div>
                  <button
                    className={`mr-btn-skip ${skipVoteMe ? 'voted' : ''}`}
                    onClick={sendSkipVote}
                    disabled={skipVoteMe}
                  >
                    {skipVoteMe ? '⊘ Vote Sent' : '⊘ Vote to Skip'}
                  </button>
                </div>
              )}
            </>
          )}
        </main>
        {toast && <div className={`mr-toast ${toast.type === 'error' ? 'error' : ''}`}>{toast.text}</div>}
      </div>
    </>
  )
}