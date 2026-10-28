# ⚔️ CF Arena — Competitive Programming Battle & Tournament Platform

A full-stack real-time competitive programming platform where developers duel head-to-head on algorithmic problems and compete in single-elimination tournament brackets. Features cryptographic identity verification via **Codeforces OAuth 2.0 / OpenID Connect (OIDC)**, immutable match handle snapshotting, live WebSocket contest tracking, and submission evaluation.

---

## 📌 Key Features

### 🔐 Authentication & Identity Verification
- **Dual Authentication**: Secure email/password login and Google OAuth 2.0 with JWT tokens.
- **Codeforces OIDC Verification**: Direct cryptographic identity linking using Codeforces OAuth 2.0 / OIDC (`authorization_code` flow + signed ID token claim validation).
- **Anti-Impersonation & Duplicate Protection**: Prevents claiming arbitrary handles. Every Codeforces subject and handle can only be linked to a single CF Arena account.
- **Active Match Protection**: Prevents handle re-linking or switching while in an active duel or tournament match.
- **Match Handle Snapshotting**: Snapshots immutable `player1CFHandle` and `player2CFHandle` into the match document at creation/join time to prevent mid-match handle tampering.

### ⚔️ Duel System
- Real-time 1v1 duels with custom durations (15m, 30m, 45m, 60m) and difficulty tiers (`EASY`, `MEDIUM`, `HARD`).
- Live WebSocket match synchronization and real-time score updates.
- Time-bounded submission evaluation: checks only Codeforces submissions with `verdict == OK` made strictly after `startTime` and before `endTime`.

### 🏆 Tournament Bracket System
- Single-elimination tournament brackets (8, 16, 32 players).
- Automated round generation, match lobby creation, and bracket progression.

---

## 🏗️ Architecture & Tech Stack

- **Frontend**: React (Vite), Axios, Tailwind/Custom CSS with responsive monospace UI.
- **Backend**: Java 17, Spring Boot 3.x, Spring Security, RestTemplate, Nimbus JOSE+JWT / JJWT.
- **Database**: MongoDB (Atlas or local instance).
- **Protocol**: REST APIs + WebSockets / STOMP.
- **Deployment**: Docker & Docker Compose.

---

## 📂 Project Structure

```
CFArena/
├── Backend/
│   ├── src/main/java/CF_DuelProject/
│   │   ├── config/             # Security, CORS, WebSocket configs
│   │   ├── controller/         # REST API Controllers (Auth, Codeforces, Match, Tournament, User)
│   │   ├── model/              # User, MatchPrimary, MatchSecondary, Tournament, Submission
│   │   ├── repository/         # Spring Data MongoDB Repositories
│   │   └── service/            # CodeforcesOidcService, MatchService, TournamentService, UserService
│   ├── src/main/resources/
│   │   └── application.properties # Spring configuration with env-var fallbacks
│   └── .env.example
├── myapp/                      # React Frontend (Vite)
│   ├── src/
│   │   ├── components/         # Navbar (with CF connect modal & verified badge), widgets
│   │   ├── context/            # AuthContext
│   │   ├── pages/              # Auth, Dashboard, Match Create/Join/Room, Tournament
│   │   └── utils/              # apiPaths, axiosInstance, cfApi
│   └── .env.example
├── docker-compose.yml          # Production compose file
├── .env.example                # Root environment template
└── README.md
```

---

## ⚙️ Configuration & Environment Variables

### Backend (`Backend/.env` or Docker environment)

| Variable | Description | Default / Example |
| :--- | :--- | :--- |
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/cf_duel_db` |
| `MONGODB_DATABASE` | MongoDB database name | `cf_duel_db` |
| `JWT_SECRET` | Secret key for signing CF Arena JWTs | `your_jwt_secret_min_256_bits` |
| `FRONTEND_URL` | Frontend URL for CORS and redirects | `http://localhost:5173` |
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID | `your_google_client_id` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret | `your_google_client_secret` |
| `GOOGLE_REDIRECT_URI` | Google OAuth2 Redirect URI | `http://localhost:8080/login/oauth2/code/google` |
| `CODEFORCES_CLIENT_ID` | Codeforces OAuth 2.0 / OIDC Client ID | `your_codeforces_client_id` |
| `CODEFORCES_CLIENT_SECRET` | Codeforces OAuth 2.0 / OIDC Client Secret | `your_codeforces_client_secret` |
| `CODEFORCES_REDIRECT_URI` | Backend Callback URL for Codeforces OIDC | `http://localhost:8080/api/auth/codeforces/callback` |
| `CODEFORCES_AUTH_URL` | Codeforces Authorization Endpoint | `https://codeforces.com/oauth/authorize` |
| `CODEFORCES_TOKEN_URL` | Codeforces Token Endpoint | `https://codeforces.com/oauth/token` |
| `CODEFORCES_ISSUER` | Expected OIDC Issuer (`iss`) | `https://codeforces.com` |
| `CODEFORCES_JWKS_URL` | Codeforces JWKS Endpoint (if verifying via keyset) | `https://codeforces.com/oauth/jwks` |

### Frontend (`myapp/.env`)

| Variable | Description | Default |
| :--- | :--- | :--- |
| `VITE_API_URL` | Backend API base URL | `http://localhost:8080` |
| `VITE_WS_URL` | WebSocket server base URL | `http://localhost:8080` |

---

## 🚀 Running Locally

### 1. Backend
```bash
cd Backend
cp .env.example .env
# Edit .env with your credentials
mvn spring-boot:run
```

### 2. Frontend
```bash
cd myapp
cp .env.example .env
npm install
npm run dev
```

---

## 🔄 Authentication & Codeforces OIDC Flow

```text
[User]                 [CF Arena Frontend]            [CF Arena Backend]           [Codeforces OIDC Provider]
  │                             │                              │                               │
  │─── Click "Connect CF" ─────▶│                              │                               │
  │                             │─── GET /api/auth/codeforces ─▶                               │
  │                             │    (with JWT Bearer token)   │─── Generate state & nonce ───▶│
  │                             │                              │    (Store in memory / 5m TTL) │
  │                             │◀── Return authUrl ───────────│                               │
  │◀── Redirect to CF OAuth ────│                              │                               │
  │                                                                                            │
  │─── Approve Application on Codeforces ─────────────────────────────────────────────────────▶│
  │                                                                                            │
  │◀── Redirect to Callback URL with ?code=...&state=... ──────────────────────────────────────│
  │                             │                              │                               │
  │                             │                              │─── POST /oauth/token ────────▶│
  │                             │                              │◀── Return id_token & tokens ──│
  │                             │                              │                               │
  │                             │                              │─── Validate id_token ─────────┤
  │                             │                              │    • Verify iss & aud         │
  │                             │                              │    • Verify exp & nonce       │
  │                             │                              │    • Extract subject & handle │
  │                             │                              │    • Check duplicate claims   │
  │                             │                              │    • Check active match lock  │
  │                             │                              │                               │
  │                             │                              │─── Update User Document ──────┤
  │                             │                              │    • codeforcesHandle         │
  │                             │                              │    • codeforcesVerified=true  │
  │                             │                              │    • codeforcesSubject        │
  │                             │                              │    • codeforcesVerifiedAt     │
  │                             │                              │                               │
  │◀── Redirect to Dashboard with ?cf_verified=true ───────────│                               │
```

---

## 🧪 Verification & Test Scenarios

### Scenario 1: Unverified User Match Creation Blocked
- Attempt to create a match via `POST /match/create` without verified Codeforces handle.
- **Expected**: Backend responds with `400 Bad Request: "Codeforces account not verified. Please connect your Codeforces account before creating a match."` Frontend shows notification guiding user to connect CF.

### Scenario 2: Unverified User Match Join Blocked
- Attempt to join a match via `POST /match/join?inviteCode=...` without verified Codeforces handle.
- **Expected**: Backend responds with `400 Bad Request: "Codeforces account not verified. Please connect your Codeforces account before joining a match."`

### Scenario 3: Successful Codeforces OIDC Account Linking
- Initiate OIDC flow via `GET /api/auth/codeforces`. Complete consent on Codeforces.
- **Expected**: Codeforces returns authorization code; backend exchanges for `id_token`, validates claims, sets `codeforcesVerified = true`, `codeforcesHandle = <handle>`, `codeforcesSubject = <sub>`. User is redirected to `/dashboard?cf_verified=true` and verified badge is displayed.

### Scenario 4: Duplicate Handle / Subject Protection
- User A links Codeforces handle `handleX`.
- User B logs in and attempts to link the same Codeforces account `handleX`.
- **Expected**: Backend rejects linking with `409 Conflict: "This Codeforces account is already linked to another CF Arena user."`

### Scenario 5: Handle Change During Active Match Blocked
- User is currently participating in a match with status `WAITING`, `READY`, or `ONGOING`.
- User attempts to link a different Codeforces account or re-authenticate.
- **Expected**: Backend rejects linking with `400 Bad Request: "Cannot change or link Codeforces account while participating in an active match."`

### Scenario 6: Match Handle Snapshotting & Tamper Resistance
- User A (`handleA`) and User B (`handleB`) create and join a match lobby.
- `MatchPrimary` and `MatchSecondary` store snapshots `player1CFHandle = "handleA"`, `player2CFHandle = "handleB"`.
- Any subsequent profile change does not affect the active match; submission checks strictly verify `handleA` and `handleB`.

### Scenario 7: Time-Bounded & Problem-Specific Submission Validation
- Submission check polls Codeforces `user.status` for the match problem ID.
- Submissions before match `startTime` or with verdict $\ne$ `OK` are ignored.
- Only a submission with `verdict == OK`, matching problem ID, by the snapshotted handle, created at $\ge startTime$ triggers a problem solve.

### Scenario 8: Tournament Creation & Registration Protection
- Unverified users are rejected when attempting to create a tournament or register for an open bracket.
- Registered tournament matches snapshot player handles directly into match lobbies.

---

## 📄 License
MIT License.
