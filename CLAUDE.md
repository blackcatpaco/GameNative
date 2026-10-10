# Project notes for Claude

## VOTV reference material

- **https://voicesofthevoid.wiki.gg/wiki/Guides** — community wiki for Voices of the Void.
  Useful for item/icon names, mechanics, and other game knowledge when building
  VOTV-specific features (hub Map/Terminal/Inventory pages, keybind choices, etc.).
  Blocked by this sandbox's egress policy (`wiki.gg`, 403) as of 2026-10 — ask the
  user to paste content/screenshots, or try again from an environment without that
  restriction, rather than assuming it's unreachable everywhere.

- **https://github.com/Questwalker/votv-map** — community-built interactive overworld
  map (Leaflet.js, `L.CRS.Simple`). GPL-3.0 licensed; no separate permission is stated
  for its map images or point data (`dat_points.js`), so don't bundle those into this
  app without the user's explicit go-ahead or the author's permission. The coordinate
  *math* itself (not copied code) is fair to reuse and is already implemented in
  `app/src/main/java/app/gamenative/externaldisplay/VotvMapView.kt`:
  Unreal world units / 100 = "game units"; a square map of side `mapSize = 1489.995`
  game units with the origin at image center; place a point as
  `colFraction = 0.5 + (unrealX/100)/mapSize`, `rowFraction = 0.5 + (unrealY/100)/mapSize`
  (no axis sign-flip needed after simplifying their `convertGameToLeaflet` transform).
  `github.io` (their Pages site, as opposed to `github.com`) is also blocked by this
  sandbox's egress policy — the repo itself on `github.com` is reachable.

## VOTV launcher variant

See `app/src/main/java/app/gamenative/externaldisplay/HubLayout.kt` and sibling files
for the dual-screen hub (external display becomes the game, device's own screen becomes
a quick-access hub: mouse/keyboard, hotkeys, hotbar, live Map, Inventory, Terminal
placeholder), gated behind `BuildConfig.VOTV_LAUNCHER`. Live game state comes from a
UE4SS Lua mod (`GameNativeBridge`, bundled at `app/src/main/assets/ue4ss-votv.tzst` and
auto-installed by `VotvUe4ssInstaller.kt`) polling `state.json` — see
`VotvBridgeState.kt` for the exact fields it reports and which VOTV object/property each
one maps to.
