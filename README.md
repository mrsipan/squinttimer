# Visual Timer for Kids

A browser-based visual countdown timer inspired by the [Time Timer](https://www.timetimer.com/). A large red disc shrinks clockwise as time runs out — intuitive for kids who can't read clocks yet.

Built in [Squint ClojureScript](https://github.com/squint-cljs/squint) and developed with the [cljsquintbrowser](https://github.com/earendil/cljsquintbrowser) AI agent.

## Quick Start

```bash
cd squinttimer
python3 -m http.server 9090
# Open http://127.0.0.1:9090
```

## Features

- **Red circular disc** — 300px timer with conic-gradient that shrinks clockwise
- **Drag to set time** — grab the white handle and rotate: clockwise = more time, counter-clockwise = less
- **Large MM:SS display** — centered on the disc, easy for kids to read
- **START/PAUSE** — begins countdown, pauses without resetting
- **RESET** — returns to the set duration
- **🔊 Beep at zero** — plays a tone when time runs out
- **Clean, minimal design** — white background, red timer, no distractions

## Files

| File | Purpose |
|---|---|
| `index.html` | Standalone (source baked in) |
| `app.cljs` | Squint ClojureScript source |

## How It Works

The timer uses `conic-gradient(red 0% X%, transparent X% 100%)` for the disc. As time counts down, X decreases from 100 to 0, making the red area shrink clockwise. A white handle at the boundary between red and transparent shows the current position.

Dragging the handle calls `atan2` to convert mouse coordinates to an angle, then maps that to minutes (0–30 range). The handle and disc use the same angle formula so they stay synchronized.

All DOM updates use direct property access (`style.left`, `textContent`, `style.background`) — no innerHTML replacement that would destroy event listeners.

## License

Apache 2.0
