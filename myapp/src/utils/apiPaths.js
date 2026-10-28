// In dev: use '' (relative) so Vite proxy handles routing — no CORS issues.
// In production: set VITE_API_URL to the backend's public URL.
export const BASE_URL = import.meta.env.VITE_API_URL || '';

export const API_PATHS = {
  AUTH: {
    REGISTER: "/auth/register",
    LOGIN: "/auth/login",
    OAUTH: "oauth2/authorization/google",

  },
  USER: {
    ME: '/user/me',  
    ADD_CF_HANDLE: '/user/add-cf',
    GENERATE_CHALLENGE: '/user/add-cf',
    VERIFY_CHALLENGE: '/user/verify-challenge',
    ACTIVE_CHALLENGE: '/user/verification-challenge',
  },
  MATCH: {
    CREATE: '/api/match/create',
    JOIN: '/api/match/join',
    START: '/api/match/start',
    STOP: (code) => `/api/match/${code}/stop`,
    STATUS: '/api/match/status',
    HISTORY: '/api/match/history',  // ✅ No userId param — returns own history
    SKIP: '/api/match/skip',
    CHAT: '/api/match/chat',
    EMOTE: '/api/match/emote',
  },
  TOURNAMENT: {
    CREATE: '/api/tournament/create',
    ACTIVE: '/api/tournament/active',
    MY: '/api/tournament/my',
    DETAILS: '/api/tournament',       // + /{id}
    REGISTER: '/api/tournament',      // + /{id}/register
    UNREGISTER: '/api/tournament',    // + /{id}/unregister
    REQUEST_JOIN: (id) => `/api/tournament/${id}/join-request`,
    CANCEL_REQUEST: (id) => `/api/tournament/${id}/join-request/cancel`,
    ACCEPT_REQUEST: (id, player) => `/api/tournament/${id}/accept-request?playerHandle=${encodeURIComponent(player)}`,
    DECLINE_REQUEST: (id, player) => `/api/tournament/${id}/decline-request?playerHandle=${encodeURIComponent(player)}`,
    START: '/api/tournament',         // + /{id}/start
    BRACKET: '/api/tournament',       // + /{id}/bracket

  },
};