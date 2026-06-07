# Icey Client network backend

A small Cloudflare Worker that powers two community features:

1. **Cape sync** — Icey Client users see each other's custom capes in-game.
2. **Online badge** — players using Icey Client get a small logo next to their name (TAB + nameplate) for other Icey Client users.

The mod and launcher are the consumers. Vanilla / non-Icey players see nothing — there is no way to push a custom cape to a player whose client doesn't have the code to fetch it.

## Endpoints

| Method | Path | Notes |
|---|---|---|
| `PUT` | `/capes/:uuid` | upload PNG (body raw, content-type `image/png`, ≤64 KB) |
| `GET` | `/capes/:uuid` | fetch PNG, `404` if none |
| `DELETE` | `/capes/:uuid` | remove cape |
| `POST` | `/presence/:uuid` | heartbeat — KV record with 90s TTL |
| `GET` | `/presence?uuids=a,b,c` | batch lookup, returns `{ presence: { uuid: bool, ... } }` |

UUIDs are accepted with or without dashes; normalized to dashed lowercase before use.

## Deploy

```sh
cd backend
npm i -g wrangler
wrangler login

# create the bindings
wrangler r2 bucket create icey-capes
wrangler kv:namespace create ICEY_PRESENCE
# paste the returned namespace id into wrangler.toml

wrangler deploy
```

The default URL is `icey-client-network.<your-account>.workers.dev`. To use a custom domain (e.g. `api.iceyclient.app`), configure it in the Cloudflare dashboard under the Worker > Triggers tab.

## Auth posture (MVP)

Open. The worst case from an open cape upload is someone overwriting a stranger's cape; open presence means someone can mark a stranger as "online". Neither is catastrophic. A signed-token flow (verify against Mojang session, issue short-lived bearer) is a follow-up — not blocking the v1 launch.

## Configuration (in the launcher)

The launcher reads the backend URL from `main.js`'s `ICEY_NETWORK_BASE_URL` constant. Change it after deploying if you use a custom domain.
