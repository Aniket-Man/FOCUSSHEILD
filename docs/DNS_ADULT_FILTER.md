# DNS-level 18+ Website Filtering (Local VPN)

The system-wide enforcement layer behind FocusShield's **existing** 18+ blocking toggle. It adds
no UI, no settings screen and no second state: the toggle in *Website Blocker* (and every other
writer of the same preference) remains the single source of truth, and this engine simply follows it.

```
Existing 18+ Toggle (WebsiteBlockerSheet switch — unchanged)
        ↓
Existing state: FocusPreferences.isAutoAdultWebsiteBlockingEnabled
(DataStore key "auto_adult_website_blocking", written by WebsiteBlockerViewModel / strict mode / cloud sync)
        ↓
VpnFilterEngineController  (observes the preference, owns no duplicate state)
        ↓
VpnEnginePolicy            (pure decision: start / stop / prompt / defer)
        ↓
AdultContentVpnService     (local VpnService, foreground service)
        ↓
DNS interception           (tun routes only the fake resolver IPs: 10.243.53.53/32, fd42:53::53/128)
        ↓
18+ Domain Blocklist       (DomainBlocklist: the AdultLexicalClassifier corpus + adult TLDs)
        ↓
BLOCK (0.0.0.0 / :: / NODATA)  or  ALLOW (cached / forwarded upstream, protected socket)
```

## Why the existing toggle keeps working unchanged

* **No new state variable.** The engine reads `preferencesRepository.preferencesFlow` and maps it to
  `isAutoAdultWebsiteBlockingEnabled` — nothing else. Turning strict mode on (which forces the
  preference to `true`), a cloud-sync pull, or the in-app switch all drive the engine identically.
* **Restore on restart is free.** Because the controller reacts to the *preference* (not to UI
  events), a fresh process with the toggle ON starts the engine as soon as consent exists.
  `START_STICKY` restarts re-read the toggle before coming back up.
* **VPN consent is UI-less.** When the toggle is ON and `VpnService.prepare()` is not satisfied,
  the *system* consent dialog is launched while the app is visible — once per process for
  background re-evaluations, and on every explicit OFF→ON toggle flip. No FocusShield screen is
  added or changed.

## How the filter works

1. `Builder` configures the interface with fake, tunnel-local DNS addresses and routes **only**
   those addresses (`/32` IPv4 + `/128` IPv6). The OS therefore sends every DNS query into the tun
   while all other traffic bypasses the VPN through the real network.
2. A dedicated reader thread reads packets, parses IPv4/IPv6 + UDP + DNS (`IpPacketCodec`,
   `DnsPacketCodec`), and hands the datagram to `DnsFilter`:
   * **allowlist match** → forward (allowlist wins; no user allowlist exists today, but the
     matcher and precedence are implemented and tested);
   * **blocklist match** → answer locally: `A` → `0.0.0.0`, `AAAA` → `::`, other QTYPEs
     (HTTPS/SVCB…) → NOERROR with no answers so embedded IP hints cannot leak;
   * **cache hit** → serve the cached upstream answer with the new transaction ID patched in;
   * **otherwise** → relay the exact query bytes to the current network's resolvers over a
     `protect()`ed UDP socket (`UpstreamDnsResolver`) and cache the reply (TTL-bounded).
3. Malformed/unparseable DNS is *forwarded unchanged* (fail-open on the parsing path), so
   exotic-but-valid DNS features keep working; block decisions are fail-closed against the list.

### Matching semantics

`DomainBlocklist` stores every entry in `HashSet`s and matches by walking the query's label
suffixes (`a.b.example.com` → `b.example.com` → `example.com` → `com`): subdomains of blocked
domains are blocked, while `pornhub.com.attacker.net` and `notpornhub.com` are **not** (whole-label
comparison only). Adult TLDs (`.xxx`, `.porn`, …) block every domain under them. The list is the
exact `AdultLexicalClassifier` corpus (500+ domains) plus its TLD set — one list, two enforcement
layers. Lexical stem matching is deliberately **not** applied at the DNS layer: stems need the full
URL context, and a DNS-level false positive would break unrelated lookups system-wide.

### Network changes & recovery

* A default-network callback refreshes the upstream resolver list from the new `LinkProperties`
  and flushes the answer cache on Wi-Fi ↔ mobile-data transitions (the tun itself survives).
* Unexpected tun loss re-establishes the tunnel with exponential backoff
  (1s → 30s, 5 attempts, then stop).
* `onRevoke()` (user revoked consent, or another VPN took over) stops the engine cleanly; the
  controller re-prompts/restarts when the app is next visible.

## Permissions

| Permission | Status |
|---|---|
| VPN consent (`VpnService.prepare`) | New, user-granted at runtime via the **system** dialog |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Already declared |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_DATA_SYNC` | Already declared |
| `POST_NOTIFICATIONS` | Already requested at first launch (status notification only) |

No root, no AccessibilityService, no new manifest permission.

## Known limitations (honest engineering boundaries)

* **Apps with hardcoded DNS** (e.g. their own 8.8.8.8 or DoH) bypass a DNS-only tunnel. Sealing
  that requires routing all traffic and blocking port 853/known DoH endpoints — deliberately out of
  scope for this engine.
* **Android "Private DNS" (DoT)** set by the user bypasses the filter for the same reason.
* **Only one VPN can run at a time.** Starting another VPN revokes ours (`onRevoke`); the engine
  returns when that VPN goes away and the app is opened.
* The accessibility-based browser filter (`WebsiteBlockerEngine`) remains the enforcement layer for
  URL-level nuance (search queries, lexical classification); the VPN layer covers every app's DNS.
* OEM battery managers can kill the service like any other foreground service; the engine restores
  itself on the next app open when that happens.
