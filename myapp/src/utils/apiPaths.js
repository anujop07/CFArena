export const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const API_PATHS = {
  AUTH: {
    REGISTER: "/auth/register",
    LOGIN: "/auth/login",
    OAUTH: "oauth2/authorization/google",
  },
  USER: {
    ME: '/user/me',  
    ADD_CF_HANDLE: '/user/add-cf',
  },
  MATCH: {
    CREATE: '/api/match/create',
    JOIN: '/api/match/join',
    START: '/api/match/start',
    STATUS: '/api/match/status',
    HISTORY: '/api/match/history',  // ✅ No userId param — returns own history
  },
  TOURNAMENT: {
    CREATE: '/api/tournament/create',
    ACTIVE: '/api/tournament/active',
    MY: '/api/tournament/my',
    DETAILS: '/api/tournament',       // + /{id}
    REGISTER: '/api/tournament',      // + /{id}/register
    UNREGISTER: '/api/tournament',    // + /{id}/unregister
    START: '/api/tournament',         // + /{id}/start
    BRACKET: '/api/tournament',       // + /{id}/bracket

  },
};