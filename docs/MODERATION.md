# Moderation actions

A flagged comment in NoHate is only useful if it's easy to *do something about it*. This page is the spec for the moderation surface: what the user can trigger, from where, and which platform APIs back each action.

## Action matrix

| Action | Where it lives | Backend | Auth required | Available when |
|---|---|---|---|---|
| Copy | Review row overflow | local | none | always |
| Share | Review row overflow | system share sheet | none | always |
| Open in Instagram | Review row | deep-link / browser | none | comment has a `sourceUrl` |
| Report comment | Review row | deep-link to Instagram report flow | none | comment has a `sourceUrl` |
| Report user | Review row | deep-link to user profile → IG's report flow | none | comment has an `authorHandle` |
| Hide comment | Review row | IG Graph: `POST /{comment-id}` `hide=true` | Graph token | user owns the post AND `commentId` known |
| Delete comment | Review row | IG Graph: `DELETE /{comment-id}` | Graph token | user owns the post AND `commentId` known |
| Block author | Review row overflow | IG Graph: `POST /{ig-user-id}/blocked_users` | Graph token | user owns the parent media |
| Monitor post | Home / Train | local (`SecureStore.monitoredUrls`) | none | always |
| Stop monitoring | Home / Train | local | none | post is currently monitored |

> If both the OAuth Graph and the personal-session providers are connected, the Graph token takes precedence for moderation calls.

## UX rules

1. **Visible-only-when-actionable.** A button that the user can't successfully invoke (no token, wrong post owner, no `commentId`) is hidden, not greyed. Greying nudges users into 404s.
2. **Confirm-before-destructive.** Delete and Block surface a confirmation sheet with the comment text and a 5-second undo (where the API supports it; Block doesn't).
3. **Optimistic UI with rollback.** Tapping Hide / Delete immediately moves the row to the Hidden list; if the API call fails the row pops back with an error snackbar.
4. **Local-only by default.** "Not hate" / "Mark safe" never touch the network — they update lexicons in `SecureStore` only.
5. **No silent telemetry on outcomes.** Counts (hidden, deleted, reported) live in `SecureStore` metrics and are visible to the user; nothing is exfiltrated.

## Data model additions

`FlaggedItem` (`app/src/main/java/com/nohate/app/data/FlaggedItem.kt`) needs a few optional fields populated by the provider where available:

- `commentId: String?` — required for Hide / Delete.
- `authorId: String?` — required for Block.
- `authorHandle: String?` — drives the "Report user" deep-link.
- `ownedByMe: Boolean` — true if the parent media belongs to the connected account.

`InstagramGraphProvider` and `InstagramSessionProvider` are already wired into the scan pipeline — they need to populate these fields when they have them. When unknown, leave them null and the corresponding UI affordances hide themselves.

## API endpoints (Instagram Graph)

Reference (Graph v18+). All require the appropriate OAuth scope on a Business / Creator account:

- `instagram_basic`, `instagram_manage_comments` — Hide, Delete.
- `instagram_manage_insights`, `pages_show_list`, `pages_read_engagement` — owner verification.
- `instagram_manage_messages` is **not** sufficient.
- Block is currently routed through the parent Facebook Page (`/{page-id}/blocked` + the IGSID). Validate at implementation time; this endpoint has changed twice in two years.

OAuth + PKCE flow already lives under `app/src/main/java/com/nohate/app/auth/`. Tokens are stored encrypted via `SecureStore.setOAuthToken`.

## Phasing

Phase 4.5 work order:

1. `FlaggedItem` schema extension + provider wiring (commentId, authorId, authorHandle, ownedByMe).
2. Review row: visibility rules, overflow menu, confirmation sheet.
3. Graph API wrappers under `platform/InstagramGraphModeration.kt` (new) with per-action methods.
4. Personal-session path (no Graph token) — best-effort scraping for Hide / Delete on own posts; document limitations.
5. Block flow + endpoint validation.
6. Tests: instrumented test stubs that mock the Graph client to confirm the right endpoint + scope is selected per action.

## Open questions

- Should "Report user" ever escalate to local blocklists (don't surface this user's future comments at all)? Probably yes, but gate behind a Settings toggle to avoid surprise muting.
- Block via personal session is fragile (Instagram rate-limits the endpoint aggressively). Leave to Phase 5 unless requested.
