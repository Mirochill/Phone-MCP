# Phone MCP launch film — Remotion source

Current film: **22 seconds, 1920×1080, 30 fps**, H.264/AAC. The complete editable React/TypeScript source and pinned dependency lock are included here. This replaces the initial Python-rendered launch film.

## Reproduce

Use Node.js 22+ and npm. From this folder:

```powershell
npm ci
npm run check
npm run dev
```

In Studio select `PhoneMCP-Launch`. `Command`, `Control` and `Ecosystem` are individually previewable 5-, 9- and 8-second sections of the same world and camera timeline. To export:

```powershell
npm run render
```

The output is `../phone-mcp-launch.mp4`. When extracting the standalone project ZIP, that is the parent of the extracted project folder. The renderer downloads its official Chromium runtime on first use. No AI API key, hosted model, subscription or remote rendering account is required.

## Edit

- `src/Film.tsx`: scene objects, exact source imagery, camera destinations, frame-driven request routes, `EDIT_T` / `WORLD_T` timing map and the music.
- `src/Root.tsx`: full film and three chapter compositions.
- `public/`: original application mark converted without changing its Android vector paths, authentic screenshots, generated FlowMusic source and the normalized 22-second edit used in the timeline.
- Typography: Inter Tight and IBM Plex Mono loaded from pinned Fontsource packages. Their SIL Open Font Licenses are in `licenses/`.

The selected surface is a neutral light grid. No CSS transitions, CSS animations, neon, glow or invented product dashboards are used. A shared camera moves through one world; each action is resolved at a held destination. The last 2.5 seconds form a clean end hold.

## Authenticity and credits

The calculator is the actual authorized S24 Ultra result `137 × 24 = 3 288`, captured through Phone MCP in Codex. Session and Files are real isolated Android emulator captures. They show a stopped session and paused transfers; this film does not turn them into evidence of an active transfer or successful installation. The separated display planes, API routes, notification symbol and client branches are editorial explanations of the documented contract. They are not raw recordings of these operations or additional client tests.

The app icon preserves the exact geometry and colors in `android/app/src/main/res/drawable/ic_phone.xml`. The GitHub mark comes from the bundled skill logo library; GitHub owns its mark. Do not infer endorsement.

Motion workflow/reference: [Liamrjohnston/remotion-motion-graphics-skill](https://github.com/Liamrjohnston/remotion-motion-graphics-skill), principally its pipeline camera grammar and authentic working-surface mechanism. Its MIT notice is included. The film went through independent visual criticism and correction before delivery. Criticism inspected decoded media/transition frames; audio was checked technically, without a perceptual listening review by the agents.

Music: [Phone MCP — Command in Motion](https://www.flowmusic.app/song/c24bb286-e69f-432a-a15d-fff3d38fdb3e), generated in the maintainer's existing FlowMusic account. The 22-second edit uses loudness normalization and a 1.2-second ending fade. Provider terms apply to that media. See `../credits.md`.

The composition source is MIT licensed. Remotion and other npm dependencies retain their own licenses; consult their package terms. No dependency implementation is vendored in this source archive.
