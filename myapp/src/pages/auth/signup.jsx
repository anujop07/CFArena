import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import axiosInstance from '../../utils/axiosInstance'
import { API_PATHS , BASE_URL } from '../../utils/apiPaths'
import './auth.css'
import CutePig from '../../components/CutePig'

const initialForm = {
  name: '',
  email: '',
  password: '',
  cfHandle: '',
}

export default function Signup() {
  const [form, setForm] = useState(initialForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [passwordFocused, setPasswordFocused] = useState(false)
  const navigate = useNavigate()

  const onChange = (event) => {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleGoogleLogin = () => {
  window.location.href = `${BASE_URL.replace(/\/$/, "")}/${API_PATHS.AUTH.OAUTH}`  // ✅ uses localhost:8080
}

  const onSubmit = async (event) => {
    event.preventDefault()
    setError('')

    if (!form.name.trim() || !form.email.trim() || !form.password.trim()) {
      setError('Name, email, and password are required.')
      return
    }

    if (form.password.length < 6) {
      setError('Password should be at least 6 characters.')
      return
    }

    setIsSubmitting(true)
    try {
      await axiosInstance.post(API_PATHS.AUTH.REGISTER, {
        name: form.name.trim(),
        email: form.email.trim().toLowerCase(),
        password: form.password,
        cfHandle: form.cfHandle.trim() || null,
      })

      navigate('/login', {
        replace: true,
        state: { justRegistered: true },
      })
    } catch (err) {
      const data = err?.response?.data
      const message =
        data?.error ||
        data?.message ||
        (typeof data === 'string' ? data : null) ||
        'Unable to create account right now. Please try again.'
      setError(message)
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
            Build your duel identity
            <br />
            and enter the arena.
          </h1>
          <p className="auth-subtitle">
            Create your account, link your handle, and start challenging your Codeforces rivals.
          </p>
        </aside>

        <article className="auth-card">
          <CutePig passwordFocused={passwordFocused} />
          <h2>Create account</h2>

          <div className="auth-segment">
            <Link to="/login" className="is-inactive">
              Login
            </Link>
            <Link to="/signup" className="is-active">
              Signup
            </Link>
          </div>

          <form className="auth-form" onSubmit={onSubmit}>
            <div className="auth-field">
              <label htmlFor="name">Name</label>
              <input
                id="name"
                name="name"
                type="text"
                placeholder="Your name"
                value={form.name}
                onChange={onChange}
                autoComplete="name"
              />
            </div>

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
              <label htmlFor="cfHandle">Codeforces Handle (Optional · Verified via OIDC)</label>
              <input
                id="cfHandle"
                name="cfHandle"
                type="text"
                placeholder="tourist"
                value={form.cfHandle}
                onChange={onChange}
                autoComplete="username"
              />
            </div>

            <div className="auth-field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                name="password"
                type="password"
                placeholder="Create password"
                value={form.password}
                onChange={onChange}
                onFocus={() => setPasswordFocused(true)}
                onBlur={() => setPasswordFocused(false)}
                autoComplete="new-password"
              />
            </div>

            {error ? <p className="auth-error">{error}</p> : null}

            <button type="submit" className="auth-action" disabled={isSubmitting}>
              {isSubmitting ? 'Creating account...' : 'Create account'}
            </button>

            <p className="auth-split">or</p>
            <button type="button" className="auth-google" onClick={handleGoogleLogin}>
              Continue with Google
            </button>

            <p className="auth-note">
              Already have an account? <Link to="/login">Sign in</Link>
            </p>
          </form>
        </article>
      </section>
    </main>
  )
}
