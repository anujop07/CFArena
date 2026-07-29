import { useState, useEffect } from 'react'

/**
 * CutePig – a tiny animated pink pig mascot.
 * It peeks normally, but covers its eyes with its hooves when
 * the password input is focused (like Telegram's monkey).
 *
 * Usage: <CutePig passwordFocused={bool} />
 */
export default function CutePig({ passwordFocused = false }) {
  const [blink, setBlink] = useState(false)

  // Blink every few seconds when NOT covering eyes
  useEffect(() => {
    if (passwordFocused) return
    const id = setInterval(() => {
      setBlink(true)
      setTimeout(() => setBlink(false), 180)
    }, 3200)
    return () => clearInterval(id)
  }, [passwordFocused])

  return (
    <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '-8px', pointerEvents: 'none' }}>
      <svg
        width="90"
        height="90"
        viewBox="0 0 200 200"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        style={{ overflow: 'visible' }}
      >
        {/* ---- body glow ---- */}
        <defs>
          <radialGradient id="pigGlow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#ffb6c1" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#ffb6c1" stopOpacity="0" />
          </radialGradient>
        </defs>
        <circle cx="100" cy="110" r="88" fill="url(#pigGlow)" />

        {/* ---- ears ---- */}
        <ellipse cx="58" cy="52" rx="24" ry="28" fill="#f8a4b8"
          style={{ transform: 'rotate(-18deg)', transformOrigin: '58px 52px' }} />
        <ellipse cx="58" cy="52" rx="14" ry="17" fill="#f06292"
          style={{ transform: 'rotate(-18deg)', transformOrigin: '58px 52px' }} />

        <ellipse cx="142" cy="52" rx="24" ry="28" fill="#f8a4b8"
          style={{ transform: 'rotate(18deg)', transformOrigin: '142px 52px' }} />
        <ellipse cx="142" cy="52" rx="14" ry="17" fill="#f06292"
          style={{ transform: 'rotate(18deg)', transformOrigin: '142px 52px' }} />

        {/* ---- head ---- */}
        <ellipse cx="100" cy="105" rx="62" ry="58" fill="#f8a4b8" />

        {/* ---- cheek blush ---- */}
        <ellipse cx="56" cy="118" rx="14" ry="9" fill="#f48fb1" opacity="0.55" />
        <ellipse cx="144" cy="118" rx="14" ry="9" fill="#f48fb1" opacity="0.55" />

        {/* ---- snout ---- */}
        <ellipse cx="100" cy="120" rx="24" ry="17" fill="#f06292" />
        {/* nostrils */}
        <ellipse cx="91" cy="120" rx="4.5" ry="5" fill="#c2185b" opacity="0.55" />
        <ellipse cx="109" cy="120" rx="4.5" ry="5" fill="#c2185b" opacity="0.55" />

        {/* ---- eyes (visible when NOT covering) ---- */}
        <g style={{
          opacity: passwordFocused ? 0 : 1,
          transition: 'opacity 0.25s ease',
        }}>
          {/* left eye */}
          {blink ? (
            <line x1="72" y1="94" x2="88" y2="94" stroke="#4a2040" strokeWidth="3" strokeLinecap="round" />
          ) : (
            <>
              <ellipse cx="80" cy="93" rx="9" ry="10" fill="white" />
              <ellipse cx="82" cy="94" rx="5" ry="5.5" fill="#3a1a30" />
              <ellipse cx="83.5" cy="92" rx="2" ry="2" fill="white" />
            </>
          )}
          {/* right eye */}
          {blink ? (
            <line x1="112" y1="94" x2="128" y2="94" stroke="#4a2040" strokeWidth="3" strokeLinecap="round" />
          ) : (
            <>
              <ellipse cx="120" cy="93" rx="9" ry="10" fill="white" />
              <ellipse cx="118" cy="94" rx="5" ry="5.5" fill="#3a1a30" />
              <ellipse cx="116.5" cy="92" rx="2" ry="2" fill="white" />
            </>
          )}
        </g>

        {/* ---- closed-eye lines (visible when covering) ---- */}
        <g style={{
          opacity: passwordFocused ? 1 : 0,
          transition: 'opacity 0.25s ease',
        }}>
          {/* squeeze shut eyes */}
          <path d="M72 95 Q80 88 88 95" stroke="#4a2040" strokeWidth="2.8" fill="none" strokeLinecap="round" />
          <path d="M112 95 Q120 88 128 95" stroke="#4a2040" strokeWidth="2.8" fill="none" strokeLinecap="round" />
        </g>

        {/* ---- hooves covering eyes ---- */}
        <g style={{
          transform: passwordFocused ? 'translateY(0px)' : 'translateY(-50px)',
          opacity: passwordFocused ? 1 : 0,
          transition: 'transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.25s ease',
        }}>
          {/* left hoof */}
          <ellipse cx="78" cy="93" rx="20" ry="14" fill="#f8a4b8" stroke="#f06292" strokeWidth="1.5" />
          <path d="M64 90 Q65 96 70 96" stroke="#e91e63" strokeWidth="1.2" fill="none" opacity="0.4" />
          <path d="M72 87 Q73 96 78 96" stroke="#e91e63" strokeWidth="1.2" fill="none" opacity="0.4" />

          {/* right hoof */}
          <ellipse cx="122" cy="93" rx="20" ry="14" fill="#f8a4b8" stroke="#f06292" strokeWidth="1.5" />
          <path d="M136 90 Q135 96 130 96" stroke="#e91e63" strokeWidth="1.2" fill="none" opacity="0.4" />
          <path d="M128 87 Q127 96 122 96" stroke="#e91e63" strokeWidth="1.2" fill="none" opacity="0.4" />
        </g>

        {/* ---- tiny mouth ---- */}
        <path
          d={passwordFocused ? "M93 138 Q100 141 107 138" : "M93 138 Q100 144 107 138"}
          stroke="#c2185b"
          strokeWidth="2"
          fill="none"
          strokeLinecap="round"
          style={{ transition: 'd 0.3s ease' }}
        />
      </svg>
    </div>
  )
}
