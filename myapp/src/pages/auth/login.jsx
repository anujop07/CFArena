
import { Link, useLocation, useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS, BASE_URL } from '../../utils/apiPaths'
import './auth.css'
import { useState, useContext } from 'react'
import { AuthContext } from '../../context/AuthContext'
import CutePig from '../../components/CutePig'
export default function Login() {
 const { login } = useContext(AuthContext)

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [passwordFocused, setPasswordFocused] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const justRegistered = Boolean(location?.state?.justRegistered)

  const onChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleGoogleLogin = () => {
    window.location.href = `${BASE_URL.replace(/\/$/, "")}/${API_PATHS.AUTH.OAUTH}`}

  const onSubmit = async (event) => {
    event.preventDefault()
    setError('')

    if (!form.email.trim() || !form.password.trim()) {
      setError('Email and password are required.')
      return
    }

    setIsSubmitting(true)
    try {
      const response = await axiosInstance.post(API_PATHS.AUTH.LOGIN, {
        email: form.email.trim(),
        password: form.password,
      })

      const token = response?.data?.token
      if (!token) {
        throw new Error('Invalid auth response from server.')
      }
        login(token)   // ✅ updates context + localStorage
        navigate('/dashboard', { replace: true })
    } catch (err) {
      const message =
        err?.response?.data?.message ||
        err?.response?.data ||
        'Unable to sign in right now. Please try again.'
      setError(typeof message === 'string' ? message : 'Login failed. Check your credentials.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-shell">
        <aside className="auth-brand">
          <p className="auth-mini">CF ARENA</p>
          <h1>
            Practice duels
            <br />
            with real match pressure.
          </h1>
          <p className="auth-subtitle">Sign in to continue your competitive routine.</p>
        </aside>

        <article className="auth-card">
          <CutePig passwordFocused={passwordFocused} />
          <h2>Welcome back</h2>
          {justRegistered ? <p className="auth-note">Account created. Sign in to start dueling.</p> : null}

          <div className="auth-segment">
            <Link to="/login" className="is-active">
              Login
            </Link>
            <Link to="/signup" className="is-inactive">
              Signup
            </Link>
          </div>

          <form className="auth-form" onSubmit={onSubmit}>
            <div className="auth-field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                name="email"
                type="email"
                placeholder="name@example.com"
                value={form.email}
                onChange={onChange}
                autoComplete="email"
              />
            </div>

            <div className="auth-field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                name="password"
                type="password"
                placeholder="Enter password"
                value={form.password}
                onChange={onChange}
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
                autoComplete="current-password"
              />
            </div>

            {error ? <p className="auth-error">{error}</p> : null}

            <button type="submit" className="auth-action" disabled={isSubmitting}>
              {isSubmitting ? 'Signing in...' : 'Sign in'}
            </button>

            <p className="auth-split">or</p>
            <button type="button" className="auth-google" onClick={handleGoogleLogin}>
              Continue with Google
            </button>

            <p className="auth-note">
              New to CF Arena? <Link to="/signup">Create your account</Link>
            </p>
          </form>
        </article>
      </section>
    </main>
  )
}
