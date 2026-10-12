# 0008. Stateless JWT with live account checks

- Status: Accepted
- Date: 2026-10-12

## Context

The React SPA calls a REST/SSE API and needs registration, login and per-user data. JWT authentication is required.
Administrators must be able to disable or delete users and have that take effect immediately, not when a token
expires. The backend should scale horizontally without a shared session store.

## Decision

- On login, the backend issues an HS256-signed JWT (Spring Security with Nimbus). The token carries the user id
  (subject), username and role. It lives for `JWT_TTL` (default 12 h) and is signed with `JWT_SECRET` (32+ bytes; a
  random key is generated when empty, which logs everyone out on restart).
- The SPA stores the token in `localStorage` and sends it as `Authorization: Bearer`. No cookies are used, so there
  is no CSRF surface.
- `CurrentUserJwtConverter` loads the account's current status and role on every request, from a Caffeine cache
  with a 30 s TTL that is invalidated when an administrator changes an account. Disabled or deleted accounts get 401
  immediately, and role changes apply immediately. Temporary lockouts (failed logins) only block password login.
- Passwords are hashed with BCrypt (strength 12). Five failed logins lock the account for 15 minutes. Login and
  registration are rate-limited per IP.
- Every repository query is scoped to the authenticated owner. Accessing another user's conversation returns 404.

## Consequences

- No server-side session state; any instance can serve any request.
- Immediate revocation for disable and delete, at the cost of a cached account lookup per request.
- Logout is client-side (the token is discarded). A stolen token stays valid until expiry unless the account is
  disabled.
- Storing the token in `localStorage` exposes it to XSS. Mitigations: React escaping, Markdown rendered without raw
  HTML, and a strict Content-Security-Policy from nginx (`script-src 'self'`, `connect-src 'self'`).

## Alternatives considered

- **Server sessions with cookies**: easy revocation, but needs CSRF protection and sticky or shared sessions.
- **Short-lived access tokens with refresh tokens**: smaller exposure window, but more moving parts. It can be added
  later without changing the authorization model.
- **Opaque tokens with introspection**: a database or cache lookup per request anyway, with no self-contained claims.
