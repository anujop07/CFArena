import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS } from '../../utils/apiPaths'
import Navbar from '../../components/Navbar'

export default function History() {
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)
  const [user, setUser] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    // 1. Get the current user first
    axiosInstance.get(API_PATHS.USER.ME)
      .then((res) => {
        setUser(res.data)
        // 2. Fetch match history
        return axiosInstance.get(API_PATHS.MATCH.HISTORY)
      })
      .then((res) => {
        setHistory(res.data || []) // 3. Save the matches to React State
      })
      .catch((err) => console.error("Failed to fetch history:", err))
      .finally(() => setLoading(false))
  }, [])

  const isUserWinner = (match) => {
    if (!user || !match.winnerId || match.winnerId === 'DRAW' || match.winnerId === 'CANCELLED') return false
    const w = match.winnerId.toLowerCase()
    return (user.cfHandle && w === user.cfHandle.toLowerCase()) ||
           (user.codeforcesHandle && w === user.codeforcesHandle.toLowerCase()) ||
           (user.email && w === user.email.toLowerCase())
  }

  return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', color: '#e8e8e0', fontFamily: 'monospace' }}>
      <Navbar />
      <div style={{ padding: '40px', maxWidth: '800px', margin: '0 auto' }}>
        <h1 style={{ fontSize: '32px', marginBottom: '20px', color: '#c8ff00' }}>Match History</h1>
        
        {loading ? (
          <p style={{ color: '#888' }}>Checking database...</p>
        ) : history.length === 0 ? (
          <p style={{ color: '#888' }}>You have no completed duels yet. Go fight!</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
            {history.map((match) => (
              <div key={match.id} style={{ border: '1px solid #2a2a2a', padding: '20px', borderRadius: '4px', background: '#0f0f0f' }}>
                <h3 style={{ marginBottom: '10px' }}>
                  {match.user1} <span style={{ color: '#888', fontSize: '14px' }}>VS</span> {match.user2}
                </h3>
                <p style={{ color: match.winnerId === 'DRAW' ? '#fb923c' : isUserWinner(match) ? '#c8ff00' : '#ff4444' }}>
                  Winner: {match.winnerId === 'DRAW' ? 'DRAW' : match.winnerId}
                  {match.endedReason === 'CREATOR_STOPPED' && (
                    <span style={{ color: '#888', fontSize: '12px', marginLeft: '8px' }}>(Stopped by creator)</span>
                  )}
                </p>
                <div style={{ fontSize: '12px', color: '#888', marginTop: '10px', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Score: {match.score1} - {match.score2}</span>
                  <span>{match.endTime ? new Date(match.endTime).toLocaleDateString() : '—'}</span>
                </div>
              </div>
            ))}
          </div>
        )}
        
        <button 
          onClick={() => navigate('/dashboard')} 
          style={{ marginTop: '30px', padding: '10px 20px', background: '#2a2a2a', color: 'white', border: 'none', cursor: 'pointer', borderRadius: '2px' }}
        >
          Back to Dashboard
        </button>
      </div>
    </div>
  )
}
