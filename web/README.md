# WatchShark web (SvelteKit SPA)

Single-page app replacing the hand-written `public/` site. Talks to the
same Javalin JSON API over same-origin `fetch` with cookies.

## Develop

```sh
bun install --cwd web
bun run --cwd web dev
```

Point the dev server at the backend or run against
`https://watchshark.duckdns.org` (same-origin cookies only work same-site).

## Build

```sh
bun run --cwd web build
```

Static output lands in `web/build/` (`index.html` fallback SPA).
Not wired into deployment yet — `public/` is still what the server serves.
