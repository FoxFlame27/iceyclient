# Icey Client network — self-hosted

Same endpoints as the Cloudflare Worker variant (see `../backend`), runs on plain Node.js. Use this when you'd rather host on your own box than on Cloudflare.

## What's in this folder

| File | Purpose |
|---|---|
| `server.js` | Express server. Capes on disk, presence in-memory. |
| `package.json` | One dep: express. |
| `icey-network.service` | systemd unit so it starts on boot + restarts on crash. |

## Setup on your Hetzner VPS

SSH into the box, then:

```sh
# Install Node 22 if you don't have it
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs

# Copy this folder up (run from your laptop, NOT the VPS)
scp -r backend-server/ root@<YOUR_IP>:/home/icey/icey-network

# Back on the VPS
cd /home/icey/icey-network
npm install
```

Test it runs:

```sh
node server.js
# should print: Icey network listening on :8787
```

`Ctrl+C` to stop. Then set it up as a service so it survives reboots:

```sh
sudo cp icey-network.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now icey-network
sudo systemctl status icey-network
```

## Open the firewall

```sh
# UFW
sudo ufw allow 8787/tcp

# Or iptables / Hetzner's web firewall — open TCP 8787 inbound from anywhere.
```

## Verify from outside

From your laptop:

```sh
curl http://<YOUR_VPS_IP>:8787/health
# {"ok":true,"service":"icey-client-network"}
```

If you get a connection refused or timeout, double-check the firewall and that `systemctl status icey-network` shows it as active.

## What to send back

Just paste the URL: `http://<YOUR_VPS_IP>:8787`. I'll wire that into `ICEY_NETWORK_BASE_URL` in `main.js` and ship a launcher build that points at it.

## Endpoints

| Method | Path | Notes |
|---|---|---|
| `PUT` | `/capes/:uuid` | upload PNG (body raw, `content-type: image/png`, ≤64 KB) |
| `GET` | `/capes/:uuid` | fetch PNG, 404 if none |
| `DELETE` | `/capes/:uuid` | remove cape |
| `POST` | `/presence/:uuid` | heartbeat, 90s TTL |
| `GET` | `/presence?uuids=a,b,c` | batch lookup |

UUIDs accepted with or without dashes; normalized to dashed lowercase.

## Storage layout

- Capes: `data/capes/<uuid>.png` (each ≤64 KB).
- Presence: in-memory Map. Lost on restart but the launcher heartbeats every 60s so it repopulates in ~1 minute.

If you want presence to survive restarts (probably overkill), swap the Map for SQLite — happy to write the patch.

## Plain HTTP is fine for v1

The launcher's `fetch` accepts `http://` URLs. The mod's Java HttpClient does too. Data over the wire is just cape PNGs and UUIDs — not credentials. If you want HTTPS later, easiest path is Cloudflare Tunnel (uses your existing CF account, no domain or cert needed) or Caddy + Let's Encrypt (needs a domain pointing at the VPS).
