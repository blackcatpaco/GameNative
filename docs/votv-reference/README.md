# VOTV reference data

Reference material for Claude (and contributors) to consult when building VOTV-specific
features, pulled from community sources. Not bundled into the app — for context only.

## `items.json`

387 items parsed from https://voicesofthevoid.wiki.gg/wiki/Items (saved-page upload, 2026-10-10,
since that domain is blocked by this sandbox's egress policy — see root `CLAUDE.md`). Each entry:

```json
{
  "section": "Store Bought Items" | "Environmental Items",
  "subsection": "Crafting Items" | "List of items in the vending machine" | null,
  "category_tab": "Essentials" | "Food" | "Tools" | "Machines" | "Storage" | "Deco" | "Misc"
                  | "Upgrades" | "Seasonal" | "Scrap" | "Items" | "Robot Pals" | null,
  "name": "Drive",
  "image_file": "Item Drive Ingame Transparent.png",
  "fields": { "...": "raw table cell values, column names vary by table" }
}
```

**`image_file` is a filename/alt-text only — no actual icon image data was included in the
source upload** (only the page's HTML, not its `_files` image folder). If actual icon artwork
is wanted for in-app use, it needs to be sourced separately (e.g. the user exporting a complete
webpage incl. images, or individual icon files), and its licensing/reuse terms checked — same
caveat as the Questwalker map assets noted in root `CLAUDE.md`.

Parsed with `bs4` from the saved HTML; see git history around 2026-10-10 for the parsing script
if the source page structure changes and this needs regenerating.

## `gamemodes-tutorial.md`

Full text (converted to Markdown, tables flattened) of
https://voicesofthevoid.wiki.gg/wiki/Gamemodes/Tutorial — the in-game tutorial level's walkthrough,
including VOTV's **default keybind list** (used to cross-check/correct the controller bindings in
`VotvViewModel.kt`'s `ensureVotvControlsProfile`) plus secrets/trivia about the tutorial area
itself (lower priority, general game knowledge).
