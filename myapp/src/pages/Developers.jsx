import { useNavigate } from 'react'
import Navbar from '../components/Navbar'
import dev1Img from '../assets/dev1.png'

const css = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');

  .dev-page { min-height: 100vh; background: #0a0a0a; color: #e8e8e0; font-family: 'Space Mono', monospace; display: flex; flex-direction: column; }
  
  .dev-main { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 60px 24px; max-width: 1200px; margin: 0 auto; width: 100%; }

  .dev-header { text-align: center; margin-bottom: 60px; }
  .dev-title { font-family: 'Syne', sans-serif; font-size: clamp(40px, 6vw, 64px); font-weight: 800; letter-spacing: -0.02em; margin-bottom: 12px; }
  .dev-title em { color: #c8ff00; font-style: normal; }
  .dev-subtitle { font-size: 16px; color: #888; letter-spacing: 0.1em; text-transform: uppercase; }

  .dev-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 40px; width: 100%; max-width: 860px; margin: 0 auto; }

  .dev-card { background: #0d0d0d; border: 1px solid #1e1e1e; padding: 48px 32px; display: flex; flex-direction: column; align-items: center; text-align: center; transition: all 0.3s ease; border-radius: 6px; position: relative; overflow: hidden; }
  .dev-card::before { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(200,255,0,0.05) 0%, transparent 100%); opacity: 0; transition: opacity 0.3s; pointer-events: none; }
  .dev-card:hover { border-color: #c8ff00; transform: translateY(-4px); box-shadow: 0 12px 32px rgba(0,0,0,0.5); }
  .dev-card:hover::before { opacity: 1; }

  .dev-img-wrap { width: 160px; height: 160px; border-radius: 50%; padding: 6px; border: 2px solid #2a2a2a; margin-bottom: 28px; transition: border-color 0.3s; position: relative; }
  .dev-card:hover .dev-img-wrap { border-color: #c8ff00; box-shadow: 0 0 20px rgba(200,255,0,0.2); }
  .dev-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; background: #1a1a1a; }

  .dev-name { font-family: 'Syne', sans-serif; font-size: 28px; font-weight: 700; letter-spacing: 0.02em; margin-bottom: 8px; color: #fff; }
  .dev-role { font-size: 13px; color: #c8ff00; letter-spacing: 0.15em; text-transform: uppercase; margin-bottom: 16px; font-weight: 700; }
  .dev-desc { font-size: 14px; color: #888; line-height: 1.7; }

  @media (max-width: 768px) {
    .dev-grid { grid-template-columns: 1fr; }
  }
`

const DEVELOPERS = [
  {
    name: "Developer 1",
    role: "Fullstack Engineer",
    img: dev1Img,
    desc: "Passionate about building scalable backend systems and competitive programming."
  },
  {
    name: "Developer Queen Manasvi don",
    role: "allover Architect",
    img: "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka&backgroundColor=ffdfbf",
    desc: "Loves creating smooth, pixel-perfect user interfaces and intense animations."
  },
  {
    name: "Developer 3",
    role: "Backend Ninja",
    img: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jack&backgroundColor=b6e3f4",
    desc: "Ensures the database and websockets are always blazing fast."
  },
  {
    name: "Developer 4",
    role: "UI/UX Designer",
    img: "https://api.dicebear.com/7.x/avataaars/svg?seed=Jocelyn&backgroundColor=ffd5dc",
    desc: "Designs the ultimate dark-mode arena experience for coders."
  }
]

export default function Developers() {
  return (
    <>
      <style>{css}</style>
      <div className="dev-page">
        <Navbar />

        <main className="dev-main">
          <div className="dev-header">
            <h1 className="dev-title">Meet the <em>Creators</em></h1>
            <p className="dev-subtitle">// The team behind CF_ARENA</p>
          </div>

          <div className="dev-grid">
            {DEVELOPERS.map((dev, i) => (
              <div className="dev-card" key={i}>
                <div className="dev-img-wrap">
                  <img src={dev.img} alt={dev.name} className="dev-img" />
                </div>
                <h3 className="dev-name">{dev.name}</h3>
                <div className="dev-role">{dev.role}</div>
                <p className="dev-desc">{dev.desc}</p>
              </div>
            ))}
          </div>
        </main>
      </div>
    </>
  )
}
