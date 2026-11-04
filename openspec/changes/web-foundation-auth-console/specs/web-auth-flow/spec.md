# Spec Delta

## Purpose

Defines the frontend authentication session: the login page, JWT session handling, route
guarding, the dynamic navigation and button gating driven by the `/me` permission set, and
logout. The SPA has no server-side session of its own - this capability owns how the browser
acquires, keeps, loses, and uses the token.

## ADDED Requirements

### Requirement: Login Page
The application SHALL provide a login page (OPS-style centered card with the product title)
that posts credentials to `POST /api/v1/system/login`. Submission SHALL be triggerable by both
the login button and the Enter key, SHALL show a pending state while the request is in flight,
and SHALL surface the failure `msg` of any `A`-class error envelope (e.g. wrong password) as a
user-visible message without leaving the page.

#### Scenario: Successful login
- **WHEN** a user submits valid credentials
- **THEN** the JWT and profile from the response are stored as the session
- **AND** the user is routed into the application shell

#### Scenario: Wrong password
- **WHEN** a user submits an invalid password
- **THEN** the envelope's `A`-class `msg` (e.g. wrong-password text) is shown on the login page
- **AND** no session is created and the route does not change

#### Scenario: Enter submits
- **WHEN** the password field has focus and the user presses Enter
- **THEN** the login submits exactly as if the button was pressed

### Requirement: Session Guard
Every route except the login route SHALL require a live session. An unauthenticated visitor
SHALL be redirected to the login page. When any API response carries the transport-auth
envelope code `"203"` (missing/expired/invalid token, HTTP 200 per the platform semantic), the
client SHALL clear the session and redirect to the login page. The guard SHALL NOT call any
endpoint before the session exists.

#### Scenario: Unauthenticated access
- **WHEN** a visitor opens a protected route with no stored session
- **THEN** they land on the login page
- **AND** no authenticated API request was issued

#### Scenario: Expired token mid-session
- **WHEN** an API call returns envelope code `"203"`
- **THEN** the stored session is cleared and the user is redirected to the login page

### Requirement: Dynamic Navigation from the Permission Set
The shell navigation SHALL render from the logged-in user's menu tree returned by
`GET /api/v1/system/me`, not from a hard-coded list. Button-level controls SHALL be gated by the
`perms` strings of the same permission set: an action the user lacks `perms` for SHALL be
hidden or disabled. The department placeholder routes stay reachable according to the same
menu data.

#### Scenario: Menus follow /me
- **WHEN** a user with a given role completes login
- **THEN** the shell navigation shows exactly the menu tree that user's permission set carries

#### Scenario: Button gating
- **WHEN** a screen renders an action whose `perms` string is absent from the user's set
- **THEN** that control is hidden or disabled for this user

### Requirement: Logout
The application SHALL offer a logout action that clears the local session and returns the user
to the login page. No backend logout endpoint is required for this behavior (the session lives
client-side; the copied backend logout machinery is out of scope here).

#### Scenario: Logout returns to login
- **WHEN** a logged-in user activates logout
- **THEN** the session is cleared and the login page renders
- **AND** a subsequent navigation attempt to a protected route redirects to login again
