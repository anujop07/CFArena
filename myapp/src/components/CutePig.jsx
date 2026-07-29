import { useState, useEffect } from 'react'

/**
 * CutePig – a tiny animated pig mascot themed to match
 * the dark / neon-green (#c8ff00) auth UI.
 *
 * It has visible arms that swing up to cover its eyes
 * when the password field is focused.
 *
 * Usage: <CutePig passwordFocused={bool} />
 */
export default function CutePig({ passwordFocused = false }) {
  const [blink, setBlink] = useState(false)

  useEffect(() => {
    if (passwordFocused) return
    const id = setInterval(() => {
      setBlink(true)
      setTimeout(() => setBlink(false), 180)
    }, 3000)
    return () => clearInterval(id)
  }, [passwordFocused])

  const covering = passwordFocused

  return (
    <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '4px', pointerEvents: 'none' }}>
      <svg
        width="82"
        height="100"
        viewBox="0 0 220 260"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        style={{ overflow: 'visible' }}
      >
        <defs>
          {/* subtle glow behind head */}
          <radialGradient id="pgGlow" cx="50%" cy="42%" r="48%">
            <stop offset="0%" stopColor="#c8ff00" stopOpacity="0.06" />
            <stop offset="100%" stopColor="#c8ff00" stopOpacity="0" />
          </radialGradient>
          {/* body gradient – dark muted pink/mauve */}
          <linearGradient id="pgBody" x1="50%" y1="0%" x2="50%" y2="100%">
            <stop offset="0%" stopColor="#3d2a35" />
            <stop offset="100%" stopColor="#2a1c26" />
          </linearGradient>
          {/* head gradient */}
          <linearGradient id="pgHead" x1="50%" y1="0%" x2="50%" y2="100%">
            <stop offset="0%" stopColor="#4a3342" />
            <stop offset="100%" stopColor="#3a2533" />
          </linearGradient>
          {/* ear inner */}
          <linearGradient id="pgEarInner" x1="50%" y1="0%" x2="50%" y2="100%">
            <stop offset="0%" stopColor="#5c3a50" />
            <stop offset="100%" stopColor="#4a2d40" />
          </linearGradient>
          {/* snout */}
          <linearGradient id="pgSnout" x1="50%" y1="0%" x2="50%" y2="100%">
            <stop offset="0%" stopColor="#5c3a50" />
            <stop offset="100%" stopColor="#4d2e42" />
          </linearGradient>
          {/* arm gradient */}
          <linearGradient id="pgArm" x1="50%" y1="0%" x2="50%" y2="100%">
            <stop offset="0%" stopColor="#4a3342" />
            <stop offset="100%" stopColor="#3a2533" />
          </linearGradient>
          {/* hoof (hand) gradient */}
          <linearGradient id="pgHoof" x1="50%" y1="0%" x2="50%" y2="100%">
            <stop offset="0%" stopColor="#5c3a50" />
            <stop offset="100%" stopColor="#4d2e42" />
          </linearGradient>
        </defs>

        {/* ambient glow */}
        <ellipse cx="110" cy="108" rx="100" ry="90" fill="url(#pgGlow)" />

        {/* ========== EARS ========== */}
        {/* left ear */}
        <ellipse cx="66" cy="52" rx="22" ry="28" fill="url(#pgHead)"
          stroke="#5c3a50" strokeWidth="1"
          style={{ transform: 'rotate(-16deg)', transformOrigin: '66px 52px' }} />
        <ellipse cx="66" cy="52" rx="12" ry="16" fill="url(#pgEarInner)"
          style={{ transform: 'rotate(-16deg)', transformOrigin: '66px 52px' }} />

        {/* right ear */}
        <ellipse cx="154" cy="52" rx="22" ry="28" fill="url(#pgHead)"
          stroke="#5c3a50" strokeWidth="1"
          style={{ transform: 'rotate(16deg)', transformOrigin: '154px 52px' }} />
        <ellipse cx="154" cy="52" rx="12" ry="16" fill="url(#pgEarInner)"
          style={{ transform: 'rotate(16deg)', transformOrigin: '154px 52px' }} />

        {/* ========== HEAD ========== */}
        <ellipse cx="110" cy="108" rx="60" ry="56" fill="url(#pgHead)"
          stroke="#5c3a50" strokeWidth="1" />

        {/* ========== BODY (small, below head) ========== */}
        <ellipse cx="110" cy="195" rx="42" ry="38" fill="url(#pgBody)"
          stroke="#5c3a50" strokeWidth="1" />
        {/* belly highlight */}
        <ellipse cx="110" cy="192" rx="24" ry="20" fill="#3d2a35" opacity="0.5" />

        {/* ========== CHEEK BLUSH (subtle neon glow) ========== */}
        <ellipse cx="64" cy="120" rx="13" ry="7" fill="#c8ff00" opacity="0.07" />
        <ellipse cx="156" cy="120" rx="13" ry="7" fill="#c8ff00" opacity="0.07" />

        {/* ========== SNOUT ========== */}
        <ellipse cx="110" cy="122" rx="22" ry="15" fill="url(#pgSnout)"
          stroke="#6b4060" strokeWidth="0.8" />
        {/* nostrils */}
        <ellipse cx="102" cy="122" rx="4" ry="4.5" fill="#2a1c26" opacity="0.7" />
        <ellipse cx="118" cy="122" rx="4" ry="4.5" fill="#2a1c26" opacity="0.7" />

        {/* ========== EYES ========== */}
        <g style={{
          opacity: covering ? 0 : 1,
          transition: 'opacity 0.22s ease',
        }}>
          {/* left eye */}
          {blink ? (
            <line x1="82" y1="98" x2="96" y2="98" stroke="#c8ff00" strokeWidth="2.5" strokeLinecap="round" opacity="0.8" />
          ) : (
            <>
              <ellipse cx="90" cy="97" rx="9" ry="10" fill="#1a1118" />
              <ellipse cx="90" cy="97" rx="7" ry="8" fill="#0d080b" />
              {/* iris glow */}
              <ellipse cx="92" cy="95" rx="3.5" ry="3.5" fill="#c8ff00" opacity="0.85" />
              <ellipse cx="93" cy="93.5" rx="1.5" ry="1.5" fill="#e8ffe0" opacity="0.9" />
            </>
          )}
          {/* right eye */}
          {blink ? (
            <line x1="124" y1="98" x2="138" y2="98" stroke="#c8ff00" strokeWidth="2.5" strokeLinecap="round" opacity="0.8" />
          ) : (
            <>
              <ellipse cx="130" cy="97" rx="9" ry="10" fill="#1a1118" />
              <ellipse cx="130" cy="97" rx="7" ry="8" fill="#0d080b" />
              <ellipse cx="128" cy="95" rx="3.5" ry="3.5" fill="#c8ff00" opacity="0.85" />
              <ellipse cx="127" cy="93.5" rx="1.5" ry="1.5" fill="#e8ffe0" opacity="0.9" />
            </>
          )}
        </g>

        {/* closed eyes (behind hooves when covering) */}
        <g style={{
          opacity: covering ? 1 : 0,
          transition: 'opacity 0.22s ease',
        }}>
          <path d="M82 100 Q90 93 98 100" stroke="#c8ff00" strokeWidth="2" fill="none" strokeLinecap="round" opacity="0.6" />
          <path d="M122 100 Q130 93 138 100" stroke="#c8ff00" strokeWidth="2" fill="none" strokeLinecap="round" opacity="0.6" />
        </g>

        {/* ========== ARMS + HANDS ========== */}
        {/* Left arm — pivots from body-shoulder area up to eyes */}
        <g style={{
          transformOrigin: '72px 170px',
          transform: covering ? 'rotate(-45deg) translate(-8px, -20px)' : 'rotate(0deg)',
          transition: 'transform 0.38s cubic-bezier(0.34, 1.56, 0.64, 1)',
        }}>
          {/* arm */}
          <path
            d={covering
              ? "M72 170 Q58 140 70 100"
              : "M72 170 Q55 190 48 215"
            }
            stroke="url(#pgArm)" strokeWidth="18" fill="none" strokeLinecap="round"
            style={{ transition: 'd 0.38s cubic-bezier(0.34, 1.56, 0.64, 1)' }}
          />
          {/* hand/hoof */}
          <g style={{
            transform: covering ? 'translate(-18px, -68px)' : 'translate(0px, 0px)',
            transition: 'transform 0.38s cubic-bezier(0.34, 1.56, 0.64, 1)',
          }}>
            <ellipse
              cx={covering ? "86" : "46"}
              cy={covering ? "98" : "222"}
              rx="16" ry="12"
              fill="url(#pgHoof)" stroke="#6b4060" strokeWidth="0.8"
            />
            {/* hoof split lines */}
            <line
              x1={covering ? "82" : "42"} y1={covering ? "94" : "218"}
              x2={covering ? "82" : "42"} y2={covering ? "102" : "226"}
              stroke="#3a2030" strokeWidth="1" opacity="0.5"
            />
            <line
              x1={covering ? "90" : "50"} y1={covering ? "94" : "218"}
              x2={covering ? "90" : "50"} y2={covering ? "102" : "226"}
              stroke="#3a2030" strokeWidth="1" opacity="0.5"
            />
          </g>
        </g>

        {/* Right arm */}
        <g style={{
          transformOrigin: '148px 170px',
          transform: covering ? 'rotate(45deg) translate(8px, -20px)' : 'rotate(0deg)',
          transition: 'transform 0.38s cubic-bezier(0.34, 1.56, 0.64, 1)',
        }}>
          <path
            d={covering
              ? "M148 170 Q162 140 150 100"
              : "M148 170 Q165 190 172 215"
            }
            stroke="url(#pgArm)" strokeWidth="18" fill="none" strokeLinecap="round"
            style={{ transition: 'd 0.38s cubic-bezier(0.34, 1.56, 0.64, 1)' }}
          />
          <g style={{
            transform: covering ? 'translate(18px, -68px)' : 'translate(0px, 0px)',
            transition: 'transform 0.38s cubic-bezier(0.34, 1.56, 0.64, 1)',
          }}>
            <ellipse
              cx={covering ? "134" : "174"}
              cy={covering ? "98" : "222"}
              rx="16" ry="12"
              fill="url(#pgHoof)" stroke="#6b4060" strokeWidth="0.8"
            />
            <line
              x1={covering ? "130" : "170"} y1={covering ? "94" : "218"}
              x2={covering ? "130" : "170"} y2={covering ? "102" : "226"}
              stroke="#3a2030" strokeWidth="1" opacity="0.5"
            />
            <line
              x1={covering ? "138" : "178"} y1={covering ? "94" : "218"}
              x2={covering ? "138" : "178"} y2={covering ? "102" : "226"}
              stroke="#3a2030" strokeWidth="1" opacity="0.5"
            />
          </g>
        </g>

        {/* ========== FEET (tiny at bottom) ========== */}
        <ellipse cx="92" cy="230" rx="12" ry="7" fill="url(#pgHoof)" stroke="#6b4060" strokeWidth="0.8" />
        <ellipse cx="128" cy="230" rx="12" ry="7" fill="url(#pgHoof)" stroke="#6b4060" strokeWidth="0.8" />

        {/* ========== MOUTH ========== */}
        <path
          d={covering ? "M103 138 Q110 141 117 138" : "M103 138 Q110 145 117 138"}
          stroke="#c8ff00"
          strokeWidth="1.5"
          fill="none"
          strokeLinecap="round"
          opacity="0.5"
          style={{ transition: 'd 0.3s ease' }}
        />
      </svg>
    </div>
  )
}
