# CP Duel — Live Competition Rehearsal Plan

**Status: NOT YET PERFORMED.** Every box below is unchecked on purpose. Nothing here has been run against a live
backend with two real browsers; only the automated tests listed in the final report have run.

## 0. Preconditions (do these first)

- [ ] Backend started with all required variables set (see "Environment" below) — it must **fail to start** if any is missing.
- [ ] `GET /actuator/health` returns `UP`.
- [ ] Atlas: the app created its indexes on first start. In Atlas → Collections → `cf_duel_db`, check:
      `users.email` (unique), `primary_matches.inviteCode` (unique), `secondary_matches.inviteCode` (unique).
- [ ] Two accounts (A and B) exist, each with a **verified** Codeforces handle, in two different browser profiles
      (or one normal + one incognito window). Account O (organizer) exists and is listed in `ADMIN_EMAILS`
      (or has `admin: true` in Atlas).
- [ ] You have two throw-away Codeforces accounts (or two real ones) that can submit solutions during the test.
- [ ] Open the browser dev tools Network tab (WS) on both browsers.

## 1. Duel lifecycle

| # | Step | Expected | Pass |
|---|------|----------|------|
| 1 | A: Create duel (30 min, EASY) | Lobby shows a 6-char invite code; A listed as host | [ ] |
| 2 | B: Join with the code typed in **lower case** | Joins the same lobby; A's screen shows B **without refresh** (WS push) | [ ] |
| 3 | Both: compare lobby screens | Same code, same two handles, same duration | [ ] |
| 4 | B: refresh the lobby page 3× | B still shown as the second player; no duplicate, no error | [ ] |
| 5 | Third account C: join with the same code | Rejected ("Match already full"); A/B unaffected | [ ] |
| 6 | A: double-click Start | Exactly one match starts; second click shows "already started" or is ignored | [ ] |
| 7 | Both: compare the room | Same 5 problems in the same order, same end time; countdowns agree within ~1 s (change one PC's clock by +2 min: the countdown must still agree) | [ ] |
| 8 | Both: chat / emote | Messages appear on both; sender name is the real handle; C (outsider, if logged in) sees nothing | [ ] |
| 9 | Both: skip vote | After A votes, B sees "opponent wants to skip"; after both vote the current problem advances on both screens, once | [ ] |
| 10 | B: refresh mid-match, then close tab and reopen the link | State (problem, score, votes, remaining time) is restored from the server | [ ] |
| 11 | Stop the backend for 30 s while the match runs, then restart it | Match continues; timer still based on the original end time; reconnect works after the 3 s poll | [ ] |

## 2. Solve detection, deadlines, ties

| # | Step | Expected | Pass |
|---|------|----------|------|
| 12 | A submits an accepted solution to the current problem | Both screens show A's solve within ~5–45 s (Codeforces is polled with a ~2.1 s spacing, so more simultaneous duels = longer worst-case delay) | [ ] |
| 13 | A submits a WRONG answer | Nothing is scored | [ ] |
| 14 | B solves a problem that A already solved | No double scoring; B gets nothing | [ ] |
| 15 | Both solve the same problem between two polls | The earlier submission wins; same second ⇒ lower Codeforces submission id wins | [ ] |
| 16 | Submit an accepted solution **after** the end time | Not counted | [ ] |
| 17 | Submit an accepted solution ~10 s **before** the end time, nothing else | Counted even if the poll notices it after the deadline | [ ] |
| 18 | Block `codeforces.com` on the server (or unplug it) near the deadline | Match stays open up to 3 minutes after the deadline, then ends honestly (no invented winner); a message is logged | [ ] |
| 19 | Let a match expire 0–0 | Result DRAW, reason TIME_EXPIRED | [ ] |
| 20 | Creator stops a running duel | Both are redirected to Results; the result is stored once | [ ] |
| 21 | Results page on both browsers | Identical final score/winner; also present in History for both | [ ] |
| 22 | Restart the backend; open Results/History again | Result still there (from Atlas `secondary_matches`) | [ ] |

## 3. Tournament

| # | Step | Expected | Pass |
|---|------|----------|------|
| 23 | A (not an organizer) tries to create a tournament | 403 | [ ] |
| 24 | O creates an 8-player tournament; 8 verified players register | Bracket initialised | [ ] |
| 25 | O starts the tournament and round 1 | Every match has the same start/end time; each pair gets the **same** problems | [ ] |
| 26 | A player tries to stop a tournament match | 403; only O can stop it | [ ] |
| 27 | O presses "advance" while a match is still running | 400 "no final result yet" | [ ] |
| 28 | Finish all matches; O advances (double-click it) | Next round created once; second click → "already advanced"/400 | [ ] |
| 29 | A cancelled match (stopped by O) then advance | Rejected: "no valid winner" — nobody is promoted by accident | [ ] |
| 30 | A DRAW | **Organizer decision needed** — current behaviour is a logged coin flip; agree a rule first | [ ] |
| 31 | Restart the backend mid-tournament | Bracket and pending matches intact | [ ] |

## 4. Security spot checks (use a second browser / curl, never real passwords in shared logs)

- [ ] `GET /api/match/status?inviteCode=<code>` as an account that is not a player → 403.
- [ ] `GET /api/match/history/<someone else's handle>` → 403.
- [ ] A STOMP client connected **without** a token cannot subscribe to `/topic/match/<code>` nor send to `/app/...`.
- [ ] After Google sign-in the address bar shows no `?token=` and the server access log has no token.
- [ ] 20 rapid `POST /api/match/join` from one account → 429 after 15 (also via the `/match/join` alias).

## Environment (names only — never paste values into chat, tickets or git)

Required: `MONGODB_URI`, `MONGODB_DATABASE` (default `cf_duel_db`), `JWT_SECRET` (≥ 32 bytes, 64+ recommended),
`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `FRONTEND_URL`.
Optional: `ADMIN_EMAILS` (comma-separated organizers), `TOURNAMENT_ADMIN_ONLY` (default `true`).
