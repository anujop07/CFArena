import { useEffect, useRef, useState } from 'react'
import Navbar from '../components/Navbar'
import dev1Img from '../assets/dev1.png'
import dev2Img from '../assets/dev2.jpg'
import dev3Img from '../assets/dev3.jpg'
import dev4Img from '../assets/dev4.jpg'

/* ─── SVG Icon Components ─── */
const LinkedInIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
    <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z" />
  </svg>
)

const GitHubIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 0C5.374 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z" />
  </svg>
)

const CodeIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" />
  </svg>
)

const css = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');

  .dev-page {
    min-height: 100vh;
    background: #0a0a0a;
    color: #e8e8e0;
    font-family: 'Space Mono', monospace;
    display: flex;
    flex-direction: column;
    overflow-x: hidden;
  }

  /* ─── Animated background grid ─── */
  .dev-bg {
    position: fixed;
    inset: 0;
    z-index: 0;
    pointer-events: none;
    background-image:
      linear-gradient(rgba(200,255,0,0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(200,255,0,0.03) 1px, transparent 1px);
    background-size: 60px 60px;
    mask-image: radial-gradient(ellipse 80% 70% at 50% 30%, black 30%, transparent 100%);
    -webkit-mask-image: radial-gradient(ellipse 80% 70% at 50% 30%, black 30%, transparent 100%);
  }

  /* ─── Floating orbs ─── */
  .dev-orb {
    position: fixed;
    border-radius: 50%;
    filter: blur(120px);
    pointer-events: none;
    z-index: 0;
    animation: orbFloat 12s ease-in-out infinite;
  }
  .dev-orb--1 { width: 400px; height: 400px; background: rgba(200,255,0,0.06); top: 10%; left: -5%; animation-delay: 0s; }
  .dev-orb--2 { width: 350px; height: 350px; background: rgba(0,200,255,0.04); bottom: 10%; right: -5%; animation-delay: -4s; }
  .dev-orb--3 { width: 250px; height: 250px; background: rgba(200,100,255,0.04); top: 50%; left: 50%; animation-delay: -8s; }

  @keyframes orbFloat {
    0%, 100% { transform: translate(0, 0) scale(1); }
    33% { transform: translate(30px, -20px) scale(1.05); }
    66% { transform: translate(-20px, 15px) scale(0.95); }
  }

  /* ─── Main content ─── */
  .dev-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 80px 24px 100px;
    max-width: 1300px;
    margin: 0 auto;
    width: 100%;
    position: relative;
    z-index: 1;
  }

  /* ─── Header ─── */
  .dev-header {
    text-align: center;
    margin-bottom: 24px;
    opacity: 0;
    transform: translateY(30px);
    animation: fadeUp 0.8s ease forwards;
  }
  .dev-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    background: rgba(200,255,0,0.08);
    border: 1px solid rgba(200,255,0,0.15);
    padding: 6px 16px;
    border-radius: 100px;
    font-size: 11px;
    color: #c8ff00;
    letter-spacing: 0.15em;
    text-transform: uppercase;
    margin-bottom: 24px;
    font-weight: 700;
  }
  .dev-badge .pulse {
    width: 6px; height: 6px;
    background: #c8ff00;
    border-radius: 50%;
    animation: pulse 2s ease-in-out infinite;
  }
  @keyframes pulse {
    0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(200,255,0,0.4); }
    50% { opacity: 0.6; box-shadow: 0 0 0 8px rgba(200,255,0,0); }
  }
  .dev-title {
    font-family: 'Syne', sans-serif;
    font-size: clamp(42px, 6vw, 72px);
    font-weight: 800;
    letter-spacing: -0.03em;
    margin-bottom: 16px;
    line-height: 1.05;
    background: linear-gradient(135deg, #fff 0%, #e8e8e0 50%, #c8ff00 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  .dev-subtitle {
    font-size: 15px;
    color: #666;
    letter-spacing: 0.08em;
    max-width: 500px;
    margin: 0 auto;
    line-height: 1.6;
  }

  /* ─── Stats bar ─── */
  .dev-stats {
    display: flex;
    gap: 48px;
    margin: 48px 0 64px;
    padding: 24px 48px;
    background: rgba(255,255,255,0.02);
    border: 1px solid #1a1a1a;
    border-radius: 12px;
    backdrop-filter: blur(10px);
    opacity: 0;
    transform: translateY(20px);
    animation: fadeUp 0.8s ease 0.2s forwards;
  }
  .dev-stat {
    text-align: center;
  }
  .dev-stat-num {
    font-family: 'Syne', sans-serif;
    font-size: 32px;
    font-weight: 800;
    color: #c8ff00;
    margin-bottom: 4px;
    line-height: 1;
  }
  .dev-stat-label {
    font-size: 11px;
    color: #555;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  /* ─── Card Grid ─── */
  .dev-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 32px;
    width: 100%;
    max-width: 960px;
    margin: 0 auto;
  }

  /* ─── Card ─── */
  .dev-card {
    background: rgba(14,14,14,0.8);
    border: 1px solid #1a1a1a;
    padding: 40px 28px 32px;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    transition: all 0.45s cubic-bezier(0.23, 1, 0.32, 1);
    border-radius: 16px;
    position: relative;
    overflow: hidden;
    backdrop-filter: blur(20px);
    opacity: 0;
    transform: translateY(40px);
  }
  .dev-card.visible {
    opacity: 1;
    transform: translateY(0);
  }
  .dev-card::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(ellipse at 50% 0%, rgba(200,255,0,0.06) 0%, transparent 70%);
    opacity: 0;
    transition: opacity 0.45s;
    pointer-events: none;
  }
  .dev-card::after {
    content: '';
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent, #c8ff00, transparent);
    transition: width 0.45s cubic-bezier(0.23, 1, 0.32, 1);
  }
  .dev-card:hover {
    border-color: rgba(200,255,0,0.25);
    transform: translateY(-8px);
    box-shadow:
      0 20px 60px rgba(0,0,0,0.4),
      0 0 40px rgba(200,255,0,0.05);
  }
  .dev-card:hover::before { opacity: 1; }
  .dev-card:hover::after { width: 80%; }

  /* ─── Avatar ─── */
  .dev-img-wrap {
    width: 140px;
    height: 140px;
    border-radius: 50%;
    padding: 3px;
    background: linear-gradient(135deg, #1e1e1e, #2a2a2a);
    margin-bottom: 24px;
    transition: all 0.45s cubic-bezier(0.23, 1, 0.32, 1);
    position: relative;
  }
  .dev-card:hover .dev-img-wrap {
    background: linear-gradient(135deg, #c8ff00, #88cc00);
    box-shadow: 0 0 30px rgba(200,255,0,0.2);
    transform: scale(1.05);
  }
  .dev-img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
    background: #1a1a1a;
    display: block;
  }
  .dev-status {
    position: absolute;
    bottom: 6px;
    right: 6px;
    width: 14px;
    height: 14px;
    background: #22c55e;
    border: 3px solid #0e0e0e;
    border-radius: 50%;
    animation: pulse 2s ease-in-out infinite;
  }

  /* ─── Name & Role ─── */
  .dev-name {
    font-family: 'Syne', sans-serif;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 0.01em;
    margin-bottom: 6px;
    color: #fff;
    transition: color 0.3s;
  }
  .dev-card:hover .dev-name { color: #c8ff00; }

  .dev-role {
    font-size: 11px;
    color: #c8ff00;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    margin-bottom: 14px;
    font-weight: 700;
    opacity: 0.8;
  }
  .dev-desc {
    font-size: 13px;
    color: #777;
    line-height: 1.7;
    margin-bottom: 18px;
  }

  /* ─── Skill Tags ─── */
  .dev-skills {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: center;
    margin-bottom: 20px;
  }
  .dev-skill {
    font-size: 10px;
    padding: 4px 10px;
    border-radius: 100px;
    border: 1px solid #222;
    color: #888;
    letter-spacing: 0.05em;
    transition: all 0.3s;
    background: rgba(255,255,255,0.02);
  }
  .dev-card:hover .dev-skill {
    border-color: rgba(200,255,0,0.2);
    color: #aaa;
  }

  /* ─── Social Links ─── */
  .dev-socials {
    display: flex;
    gap: 10px;
    margin-top: auto;
  }
  .dev-social {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 10px;
    border: 1px solid #222;
    color: #666;
    background: rgba(255,255,255,0.02);
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);
    text-decoration: none;
    position: relative;
    overflow: hidden;
  }
  .dev-social::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 10px;
    opacity: 0;
    transition: opacity 0.3s;
  }
  .dev-social--linkedin::before {
    background: linear-gradient(135deg, #0077B5, #00A0DC);
  }
  .dev-social--github::before {
    background: linear-gradient(135deg, #333, #6e5494);
  }
  .dev-social:hover {
    border-color: transparent;
    color: #fff;
    transform: translateY(-3px) scale(1.1);
    box-shadow: 0 8px 20px rgba(0,0,0,0.3);
  }
  .dev-social:hover::before { opacity: 1; }
  .dev-social svg { position: relative; z-index: 1; }

  /* ─── Quote Section ─── */
  .dev-quote-section {
    margin-top: 80px;
    text-align: center;
    max-width: 700px;
    opacity: 0;
    transform: translateY(30px);
  }
  .dev-quote-section.visible {
    opacity: 1;
    transform: translateY(0);
    transition: all 0.8s ease;
  }
  .dev-quote-line {
    width: 60px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #c8ff00, transparent);
    margin: 0 auto 32px;
  }
  .dev-quote {
    font-family: 'Syne', sans-serif;
    font-size: 24px;
    font-weight: 400;
    color: #555;
    line-height: 1.6;
    font-style: italic;
  }
  .dev-quote em {
    color: #c8ff00;
    font-style: normal;
    font-weight: 700;
  }
  .dev-quote-attr {
    margin-top: 20px;
    font-size: 12px;
    color: #444;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  /* ─── Tech Stack Footer ─── */
  .dev-tech {
    margin-top: 60px;
    display: flex;
    align-items: center;
    gap: 32px;
    padding: 20px 40px;
    border: 1px solid #151515;
    border-radius: 12px;
    background: rgba(255,255,255,0.01);
    opacity: 0;
    transform: translateY(20px);
  }
  .dev-tech.visible {
    opacity: 1;
    transform: translateY(0);
    transition: all 0.8s ease;
  }
  .dev-tech-label {
    font-size: 10px;
    color: #444;
    letter-spacing: 0.15em;
    text-transform: uppercase;
    white-space: nowrap;
  }
  .dev-tech-items {
    display: flex;
    gap: 20px;
    flex-wrap: wrap;
  }
  .dev-tech-item {
    font-size: 12px;
    color: #666;
    display: flex;
    align-items: center;
    gap: 6px;
    transition: color 0.3s;
  }
  .dev-tech-item:hover { color: #c8ff00; }
  .dev-tech-dot {
    width: 4px; height: 4px;
    background: #c8ff00;
    border-radius: 50%;
    opacity: 0.5;
  }

  /* ─── Animation keyframes ─── */
  @keyframes fadeUp {
    to { opacity: 1; transform: translateY(0); }
  }

  /* ─── Responsive ─── */
  @media (max-width: 768px) {
    .dev-grid { grid-template-columns: 1fr; max-width: 420px; }
    .dev-stats { flex-wrap: wrap; gap: 24px; padding: 20px 28px; justify-content: center; }
    .dev-tech { flex-direction: column; gap: 12px; text-align: center; }
    .dev-tech-items { justify-content: center; }
  }
  @media (max-width: 480px) {
    .dev-main { padding: 40px 16px 60px; }
    .dev-card { padding: 32px 20px 24px; }
  }
`

const DEVELOPERS = [
  {
    name: "Anuj",
    role: "Fullstack Engineer",
    img: dev1Img,
    desc: "Passionate about building scalable backend systems and competitive programming.",
    skills: ["Java", "Spring Boot", "React", "WebSockets"],
    linkedin: "https://linkedin.com/in/",
    github: "https://github.com/"
  },
  {
    name: "Manasvi",
    role: "Allover Architect",
    img: dev2Img,
    desc: "Loves creating smooth, pixel-perfect user interfaces and intense animations.",
    skills: ["React", "CSS", "Figma", "UI/UX"],
    linkedin: "https://linkedin.com/in/",
    github: "https://github.com/"
  },
  {
    name: "Developer 3",
    role: "Backend Ninja",
    img: dev3Img,
    desc: "Ensures the database and websockets are always blazing fast.",
    skills: ["Node.js", "PostgreSQL", "Redis", "Docker"],
    linkedin: "https://linkedin.com/in/",
    github: "https://github.com/"
  },
  {
    name: "Developer 4",
    role: "UI/UX Designer",
    img: dev4Img,
    desc: "Designs the ultimate dark-mode arena experience for coders.",
    skills: ["Figma", "Framer", "CSS", "Motion"],
    linkedin: "https://linkedin.com/in/",
    github: "https://github.com/"
  }
]

const TECH_STACK = ["React", "Spring Boot", "PostgreSQL", "WebSockets", "OAuth2", "Vite", "Codeforces API"]

export default function Developers() {
  const cardRefs = useRef([])
  const quoteRef = useRef(null)
  const techRef = useRef(null)
  const [counters, setCounters] = useState({ commits: 0, lines: 0, hours: 0, coffee: 0 })

  useEffect(() => {
    // Intersection observer for scroll animations
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.add('visible')
          }
        })
      },
      { threshold: 0.15, rootMargin: '0px 0px -40px 0px' }
    )

    cardRefs.current.forEach(card => { if (card) observer.observe(card) })
    if (quoteRef.current) observer.observe(quoteRef.current)
    if (techRef.current) observer.observe(techRef.current)

    // Animate counters
    const targets = { commits: 2847, lines: 58, hours: 1200, coffee: 342 }
    const duration = 2000
    const start = Date.now()
    const tick = () => {
      const progress = Math.min((Date.now() - start) / duration, 1)
      const ease = 1 - Math.pow(1 - progress, 3) // easeOutCubic
      setCounters({
        commits: Math.floor(targets.commits * ease),
        lines: Math.floor(targets.lines * ease),
        hours: Math.floor(targets.hours * ease),
        coffee: Math.floor(targets.coffee * ease)
      })
      if (progress < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)

    return () => observer.disconnect()
  }, [])

  return (
    <>
      <style>{css}</style>
      <div className="dev-page">
        <div className="dev-bg" />
        <div className="dev-orb dev-orb--1" />
        <div className="dev-orb dev-orb--2" />
        <div className="dev-orb dev-orb--3" />

        <Navbar />

        <main className="dev-main">
          {/* ── Header ── */}
          <div className="dev-header">
            <div className="dev-badge">
              <span className="pulse" />
              Building CF Arena
            </div>
            <h1 className="dev-title">Meet the Creators</h1>
            <p className="dev-subtitle">
              The engineers crafting the ultimate competitive programming arena
            </p>
          </div>

          {/* ── Stats Bar ── */}
          <div className="dev-stats">
            <div className="dev-stat">
              <div className="dev-stat-num">{counters.commits.toLocaleString()}</div>
              <div className="dev-stat-label">Commits</div>
            </div>
            <div className="dev-stat">
              <div className="dev-stat-num">{counters.lines}K</div>
              <div className="dev-stat-label">Lines of Code</div>
            </div>
            <div className="dev-stat">
              <div className="dev-stat-num">{counters.hours.toLocaleString()}+</div>
              <div className="dev-stat-label">Dev Hours</div>
            </div>
            <div className="dev-stat">
              <div className="dev-stat-num">{counters.coffee}</div>
              <div className="dev-stat-label">Cups of Coffee</div>
            </div>
          </div>

          {/* ── Developer Cards ── */}
          <div className="dev-grid">
            {DEVELOPERS.map((dev, i) => (
              <div
                className="dev-card"
                key={i}
                ref={el => cardRefs.current[i] = el}
                style={{ transitionDelay: `${i * 0.12}s` }}
              >
                <div className="dev-img-wrap">
                  <img src={dev.img} alt={dev.name} className="dev-img" />
                  <div className="dev-status" />
                </div>
                <h3 className="dev-name">{dev.name}</h3>
                <div className="dev-role">{dev.role}</div>
                <p className="dev-desc">{dev.desc}</p>

                {/* Skill Tags */}
                <div className="dev-skills">
                  {dev.skills.map((skill, j) => (
                    <span className="dev-skill" key={j}>{skill}</span>
                  ))}
                </div>

                {/* Social Links */}
                <div className="dev-socials">
                  <a
                    href={dev.linkedin}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="dev-social dev-social--linkedin"
                    title="LinkedIn"
                  >
                    <LinkedInIcon />
                  </a>
                  <a
                    href={dev.github}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="dev-social dev-social--github"
                    title="GitHub"
                  >
                    <GitHubIcon />
                  </a>
                </div>
              </div>
            ))}
          </div>

          {/* ── Quote ── */}
          <div className="dev-quote-section" ref={quoteRef}>
            <div className="dev-quote-line" />
            <p className="dev-quote">
              "We believe competitive programming should be <em>social</em>,
              <em> real-time</em>, and absolutely <em>thrilling</em>."
            </p>
            <p className="dev-quote-attr">— The CF Arena Team</p>
          </div>

          {/* ── Tech Stack ── */}
          <div className="dev-tech" ref={techRef}>
            <span className="dev-tech-label">Built With</span>
            <div className="dev-tech-items">
              {TECH_STACK.map((tech, i) => (
                <span className="dev-tech-item" key={i}>
                  <span className="dev-tech-dot" />
                  {tech}
                </span>
              ))}
            </div>
          </div>
        </main>
      </div>
    </>
  )
}
