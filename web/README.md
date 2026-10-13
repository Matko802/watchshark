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
`public/` holds the deployed copy: rebuild, then copy `web/build/.`
over `public/` and commit. The compose stack mounts `./public` live,
and the backend serves `index.html` for all app routes.
