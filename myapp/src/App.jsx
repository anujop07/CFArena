import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Landing from './pages/Landing'
import Login from './pages/auth/login'
import Signup from './pages/auth/signup'
import OAuthCallback from './pages/auth/OAuthCallback'
import Dashboard from './pages/Dashboard'
import MatchCreate from './pages/match/Matchcreate'
import MatchJoin from './pages/match/MatchJoin'
import MatchRoom from './pages/match/MatchRoom'
import Results from './pages/match/Results'
import History from './pages/match/History'

import TournamentList from './pages/tournament/TournamentList'
import TournamentCreate from './pages/tournament/TournamentCreate'
import TournamentBracket from './pages/tournament/TournamentBracket'
import Developers from './pages/Developers'

import { useContext } from "react";
import { AuthContext } from "./context/AuthContext";

/* ── Global styles injected once ─────────────────────────────────────────── */
const globalCSS = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap');

  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  html { scroll-behavior: smooth; }

  body {
    background: #0a0a0a;
    color: #e8e8e0;
    font-family: 'Space Mono', monospace;
    -webkit-font-smoothing: antialiased;
    overflow-x: hidden;
  }

  a { cursor: crosshair; }
  button { cursor: crosshair; }

  /* Ticker animation */
  @keyframes ticker {
    from { transform: translateX(0); }
    to   { transform: translateX(-50%); }
  }

  /* Blinking cursor */
  @keyframes blink {
    0%, 100% { opacity: 1; }
    50%       { opacity: 0; }
  }

  /* Live dot pulse */
  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50%       { opacity: 0.2; }
  }

  /* Scrollbar */
  ::-webkit-scrollbar       { width: 6px; }
  ::-webkit-scrollbar-track { background: #0a0a0a; }
  ::-webkit-scrollbar-thumb { background: #2a2a2a; border-radius: 3px; }
  ::-webkit-scrollbar-thumb:hover { background: #3d3d3d; }
`

if (typeof document !== "undefined") {
  const style = document.createElement("style");
  style.innerHTML = globalCSS;
  document.head.appendChild(style);
}
const ProtectedRoute = ({ children }) => {
  const { userToken, loading } = useContext(AuthContext);

  if (loading) return <div>Loading...</div>;

  return userToken ? children : <Navigate to="/login" replace />;
};

function App() {
  return (
    <BrowserRouter>
      <Routes>

        {/* 🌐 Public Routes */}
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route path="/auth/callback" element={<OAuthCallback />} />

        {/* 🔒 Protected Routes */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        
        <Route
          path="/history"
          element={
            <ProtectedRoute>
              <History />
            </ProtectedRoute>
          }
        />

        <Route
          path="/match/create"
          element={
            <ProtectedRoute>
              <MatchCreate />
            </ProtectedRoute>
          }
        />

        <Route
          path="/match/join"
          element={
            <ProtectedRoute>
              <MatchJoin />
            </ProtectedRoute>
          }
        />

        <Route
          path="/match/:inviteCode"
          element={
            <ProtectedRoute>
              <MatchRoom />
            </ProtectedRoute>
          }
        />

        <Route
          path="/results/:inviteCode"
          element={
            <ProtectedRoute>
              <Results />
            </ProtectedRoute>
          }
        /> 

        <Route
          path="/developers"
          element={
            <ProtectedRoute>
              <Developers />
            </ProtectedRoute>
          }
        />

        {/* 🏆 Tournament Routes */}
        <Route
          path="/tournaments"
          element={
            <ProtectedRoute>
              <TournamentList />
            </ProtectedRoute>
          }
        />
        <Route
          path="/tournament/create"
          element={
            <ProtectedRoute>
              <TournamentCreate />
            </ProtectedRoute>
          }
        />
        <Route
          path="/tournament/:id"
          element={
            <ProtectedRoute>
              <TournamentBracket />
            </ProtectedRoute>
          }
        />

        {/* 🔁 Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />

      </Routes>
    </BrowserRouter>
  );
}

export default App;