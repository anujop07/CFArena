import { useEffect, useState } from 'react'

const STEPS = [
  {
    num: '01',
    title: 'Create a match',
    desc: 'Log in with your Codeforces handle, choose a duration (15–120 min), and get a shareable 6-character invite code.',
  },
  {
    num: '02',
    title: 'Invite your rival',
    desc: 'Share the code. Your opponent joins. Problems are auto-curated — no solved problems, difficulty-scaled for a fair fight.',
  },
  {
    num: '03',
    title: 'Race & win',
    desc: 'The clock starts. Solve problems on Codeforces — CF Duel tracks submissions in real-time. First to more solves wins.',
  },
]

const FEATURES = [
  { tag: 'Real-time',           title: 'WebSocket live updates',  desc: 'Score updates pushed instantly. No refreshing — watch the score change the moment your opponent ACs.' },
  { tag: 'Fair matchmaking',    title: 'Smart problem selection', desc: 'Problems filtered from Codeforces — neither player has solved them. Difficulty calibrated to both.' },
  { tag: 'Race condition safe', title: 'Atomic scoring engine',   desc: 'MongoDB atomic updates with curIdx guard. No double-awarding — handled at the database layer.' },
  { tag: 'Flexible',            title: 'Custom match duration',   desc: 'Set the clock from 15 to 120 minutes. Quick blitz or full competitive grinding.' },
]







export default function Landing() {
  const [navSolid, setNavSolid] = useState(false)

  useEffect(() => {
    const onScroll = () => setNavSolid(window.scrollY > 20)
    window.addEventListener('scroll', onScroll)
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <div style={s.root}>
      <div style={s.grid} />

      {/* NAV */}
      <nav style={{ ...s.nav, background: navSolid ? 'rgba(10,10,10,0.97)' : 'rgba(10,10,10,0.85)' }}>
        <a href="/" style={s.logo}>CF<span style={{ color: '#c8ff00' }}>_</span>DUEL</a>
        <div style={s.navLinks}>
          <a href="#how-it-works" style={s.navLink}>How it works</a>
          <a href="#features"     style={s.navLink}>Features</a>
          <a href="/developers"   style={s.navLink}>Developers</a>
          <a href="/login"        style={s.btnGhost}>Log in</a>
          <a href="/signup"       style={s.btnPrimary}>Sign up free</a>
        </div>
      </nav>

      {/* HERO */}
      <section style={s.hero}>
      <div style={s.heroInner}>
        <p style={s.eyebrow}>
          <span style={s.eyebrowDot} /> Real-time 1v1 Codeforces Duels
        </p>
        <h1 style={s.h1}>
          <span style={{ display: 'block', color: '#e8e8e0' }}>PROVE</span>
          <span style={{ display: 'block', color: '#c8ff00' }}>YOU&apos;RE</span>
          <span style={{ display: 'block', WebkitTextStroke: '1px #707768', color: 'rgba(176,184,168,0.26)', textShadow: '0 0 10px rgba(120,130,112,0.2)' }}>BETTER.</span>
        </h1>
        <p style={s.heroSub}>
          Challenge any Codeforces user to a live 1v1 duel.
          Race through curated problems and settle the ranking debate with real match pressure.
        </p>
        <div style={s.heroCta}>
          <a href="/signup" style={s.btnPrimary}>Start dueling →</a>
          <a href="/login" style={s.btnGhost}>I have an account</a>
        </div>
      </div>
    </section>

      {/* HOW IT WORKS */}
      <section style={s.section} id="how-it-works">
        <div style={s.sectionInner}>
          <p style={s.sectionLabel}>// how it works</p>
          <h2 style={s.sectionTitle}>
            Three steps to{' '}
            <span style={{ WebkitTextStroke: '1px #6f7568', color: 'rgba(176,184,168,0.24)', textShadow: '0 0 8px rgba(120,130,112,0.14)' }}>domination</span>
          </h2>
          <div style={s.stepsGrid}>
            {STEPS.map(step => <StepCard key={step.num} {...step} />)}
          </div>
        </div>
      </section>

      {/* FEATURES */}
      <section style={s.section} id="features">
        <div style={s.sectionInner}>
          <p style={s.sectionLabel}>// features</p>
          <h2 style={s.sectionTitle}>
            Built for{' '}
            <span style={{ WebkitTextStroke: '1px #6f7568', color: 'rgba(176,184,168,0.24)', textShadow: '0 0 8px rgba(120,130,112,0.14)' }}>serious</span>{' '}
            competitors
          </h2>
          <div style={s.featuresGrid}>
            {FEATURES.map(f => <FeatureCard key={f.title} {...f} />)}
          </div>
        </div>
      </section>

      {/* CTA */}
      <div style={s.ctaSection}>
        <div style={s.ctaInner}>
          <h2 style={s.ctaH2}>Ready to <span style={{ color: '#c8ff00' }}>prove it?</span></h2>
          <p style={{ color: '#9a9d92', marginBottom: '2rem', fontSize: 15, lineHeight: 1.8 }}>
            Create a free account. Link your handle. Issue a challenge.
          </p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
            <a href="/signup" style={s.btnPrimary}>Create free account →</a>
            <a href="/login"  style={s.btnGhost}>Log in</a>
          </div>
        </div>
      </div>

      {/* FOOTER */}
      <footer style={s.footer}>
        <span style={{ fontSize: 12, color: '#9a9d92' }}>
          © 2026 CF_DUEL ·{' '}
          <a href="#" style={{ color: '#9a9d92', textDecoration: 'none' }}>GitHub</a>
          {' · '}
          <a href="#" style={{ color: '#9a9d92', textDecoration: 'none' }}>API</a>
        </span>
        <span style={{ fontSize: 12, color: '#6f7268' }}>Spring Boot · MongoDB · WebSocket</span>
      </footer>
    </div>
  )
}

function StepCard({ num, title, desc }) {
  const [hov, setHov] = useState(false)
  return (
    <div
      style={{ ...s.step, background: hov ? '#161616' : '#0a0a0a' }}
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => setHov(false)}
    >
      <div style={{ ...s.stepNum, color: hov ? '#3d3d3d' : '#222' }}>{num}</div>
      <h3 style={s.stepTitle}>{title}</h3>
      <p style={s.stepDesc}>{desc}</p>
      <span style={{ ...s.stepArrow, color: hov ? '#c8ff00' : '#2a2a2a', transform: hov ? 'translate(3px,-3px)' : 'none' }}>↗</span>
    </div>
  )
}

function FeatureCard({ tag, title, desc }) {
  const [hov, setHov] = useState(false)
  return (
    <div
      style={{ ...s.feature, background: hov ? '#161616' : '#0a0a0a', borderLeft: `2px solid ${hov ? '#c8ff00' : 'transparent'}` }}
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => setHov(false)}
    >
      <p style={s.featureTag}>{tag}</p>
      <h3 style={s.featureTitle}>{title}</h3>
      <p style={s.featureDesc}>{desc}</p>
    </div>
  )
}

/* ─── STYLES ─────────────────────────────────────────────────────────────── */
const s = {
  root: {
    background: '#0a0a0a',
    color: '#e8e8e0',
    fontFamily: "'Space Mono', monospace",
    fontSize: 14,
    lineHeight: 1.6,
    overflowX: 'hidden',
    minHeight: '100vh',
    position: 'relative',
  },
  grid: {
    position: 'fixed', inset: 0, pointerEvents: 'none', zIndex: 0,
    backgroundImage: 'linear-gradient(rgba(200,255,0,0.018) 1px,transparent 1px),linear-gradient(90deg,rgba(200,255,0,0.018) 1px,transparent 1px)',
    backgroundSize: '60px 60px',
  },
  nav: {
    position: 'fixed', top: 0, left: 0, right: 0,
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '0 2rem', height: 52,
    borderBottom: '1px solid #1e1e1e',
    backdropFilter: 'blur(12px)',
    zIndex: 100, transition: 'background 0.3s',
  },
  logo: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: 17,
    letterSpacing: '-0.5px', color: '#e8e8e0', textDecoration: 'none',
  },
  navLinks: { display: 'flex', alignItems: 'center', gap: '1.75rem' },
  navLink: {
    color: '#a8ab9f', textDecoration: 'none', fontSize: 12,
    letterSpacing: '0.08em', textTransform: 'uppercase',
  },
  btnPrimary: {
    display: 'inline-flex', alignItems: 'center',
    padding: '9px 20px',
    fontFamily: "'Space Mono', monospace", fontSize: 11, fontWeight: 700,
    letterSpacing: '0.06em', textTransform: 'uppercase', textDecoration: 'none',
    border: '1px solid #c8ff00', background: '#c8ff00', color: '#0a0a0a',
  },
  btnGhost: {
    display: 'inline-flex', alignItems: 'center',
    padding: '9px 20px',
    fontFamily: "'Space Mono', monospace", fontSize: 11, fontWeight: 700,
    letterSpacing: '0.06em', textTransform: 'uppercase', textDecoration: 'none',
    border: '1px solid #2a2a2a', background: 'transparent', color: '#e8e8e0',
  },
  hero: {
    position: 'relative', minHeight: '100vh',
    display: 'flex', alignItems: 'center',
    padding: '80px 2rem 4rem', zIndex: 1,
  },
  heroInner: {
  maxWidth: 800, margin: '0 auto', width: '100%',
  display: 'flex', flexDirection: 'column', alignItems: 'center',
  textAlign: 'center',
  },
  heroSub: {
    fontSize: 16, color: '#b1b4aa', lineHeight: 1.85,
    maxWidth: 560, margin: '0 auto 2rem', textAlign: 'center',
  },
  heroCta: {
    display: 'flex', alignItems: 'center',
    justifyContent: 'center', gap: '1rem', flexWrap: 'wrap',
  },
  eyebrow: {
    display: 'inline-flex', alignItems: 'center', gap: 8,
    fontSize: 11, letterSpacing: '0.12em', textTransform: 'uppercase',
    color: '#c8ff00', marginBottom: '1.75rem',
  },
  eyebrowDot: {
    width: 6, height: 6, background: '#c8ff00', borderRadius: '50%',
    display: 'inline-block', animation: 'pulse 1.5s ease-in-out infinite', flexShrink: 0,
  },
  h1: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800,
    fontSize: 'clamp(48px, 6.5vw, 88px)', lineHeight: 0.92,
    letterSpacing: '-2px', marginBottom: '1.5rem',
  },
  heroStats: {
    display: 'flex', gap: '2rem', marginTop: '2.5rem',
    paddingTop: '2rem', borderTop: '1px solid #1e1e1e',
  },
  statNum: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800,
    fontSize: 26, color: '#e8e8e0', letterSpacing: '-1px',
  },
  statLabel: { fontSize: 10, color: '#6b6b60', textTransform: 'uppercase', letterSpacing: '0.1em' },
  terminal: {
    background: '#111', border: '1px solid #1e1e1e', position: 'relative', overflow: 'hidden',
  },
  termAccent: { position: 'absolute', top: 0, left: 0, right: 0, height: 2, background: '#c8ff00' },
  termBar: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '10px 16px', borderBottom: '1px solid #1e1e1e', background: '#161616',
  },
  termDots: { display: 'flex', gap: 6 },
  dot: { width: 9, height: 9, borderRadius: '50%', display: 'inline-block' },
  termTitle: { fontSize: 11, color: '#9a9d92', letterSpacing: '0.06em' },
  termBody: { padding: 18, fontSize: 13 },
  cursor: {
    display: 'inline-block', width: 8, height: 13,
    background: '#c8ff00', verticalAlign: 'middle',
    animation: 'blink 1s step-end infinite',
  },
  matchHeader: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    marginBottom: 10, fontSize: 10, color: '#9a9d92',
    textTransform: 'uppercase', letterSpacing: '0.08em',
  },
  matchStatus: { color: '#c8ff00', border: '1px solid rgba(200,255,0,0.25)', padding: '2px 7px', fontSize: 10 },
  matchPlayers: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem' },
  playerName: { fontFamily: "'Syne', sans-serif", fontWeight: 700, fontSize: 15, color: '#e8e8e0' },
  playerRating: { fontSize: 10, color: '#9a9d92' },
  scoreRow: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: 26,
    display: 'flex', alignItems: 'center', gap: 8,
  },
  matchFooter: {
    marginTop: 10, paddingTop: 10, borderTop: '1px solid #1e1e1e',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    fontSize: 10, color: '#9a9d92',
  },
  section: {
    position: 'relative', zIndex: 1, padding: '5rem 2rem',
    borderTop: '1px solid #1e1e1e',
  },
  sectionInner: { maxWidth: 1140, margin: '0 auto' },
  sectionLabel: {
    fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase',
    color: '#c8ff00', marginBottom: '0.75rem',
  },
  sectionTitle: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800,
    fontSize: 'clamp(28px, 3.5vw, 48px)', lineHeight: 1.05,
    letterSpacing: '-1px', color: '#e8e8e0', marginBottom: '2.5rem',
  },
  stepsGrid: {
    display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
    gap: 1, background: '#1e1e1e', border: '1px solid #1e1e1e',
  },
  step: {
    padding: '2rem', position: 'relative', overflow: 'hidden',
    transition: 'background 0.2s',
  },
  stepNum: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800,
    fontSize: 52, lineHeight: 1, marginBottom: '1.25rem',
    letterSpacing: '-2px', transition: 'color 0.2s',
  },
  stepTitle: {
    fontFamily: "'Syne', sans-serif", fontWeight: 700,
    fontSize: 16, color: '#e8e8e0', marginBottom: '0.5rem',
  },
  stepDesc: { fontSize: 14, color: '#a4a89d', lineHeight: 1.8, maxWidth: 360 },
  stepArrow: {
    position: 'absolute', bottom: '1.25rem', right: '1.25rem',
    fontSize: 18, transition: 'color 0.2s, transform 0.2s',
  },
  featuresGrid: {
    display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)',
    gap: 1, background: '#1e1e1e', border: '1px solid #1e1e1e',
  },
  feature: {
    padding: '1.75rem 2rem',
    borderLeft: '2px solid transparent',
    transition: 'all 0.2s',
  },
  featureTag: {
    fontSize: 10, letterSpacing: '0.12em', textTransform: 'uppercase',
    color: '#00c8ff', marginBottom: '0.5rem',
  },
  featureTitle: {
    fontFamily: "'Syne', sans-serif", fontWeight: 700,
    fontSize: 17, color: '#e8e8e0', marginBottom: '0.5rem',
  },
  featureDesc: { fontSize: 14, color: '#a4a89d', lineHeight: 1.8, maxWidth: 520 },
  ctaSection: {
    position: 'relative', zIndex: 1, padding: '5rem 2rem',
    borderTop: '1px solid #1e1e1e', background: '#111',
  },
  ctaInner: { maxWidth: 700, margin: '0 auto', textAlign: 'center' },
  ctaH2: {
    fontFamily: "'Syne', sans-serif", fontWeight: 800,
    fontSize: 'clamp(32px, 4vw, 60px)', lineHeight: 0.95,
    letterSpacing: '-2px', marginBottom: '1.25rem', color: '#e8e8e0',
  },
  footer: {
    position: 'relative', zIndex: 1, borderTop: '1px solid #1e1e1e',
    padding: '1.5rem 2rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    gap: '1rem', flexWrap: 'wrap',
  },
}