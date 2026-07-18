# MX Anticheat — Training Data Recording Guide

## Overview

MX uses two separate ML models:

| Model | Features | Window | Detects |
|-------|----------|--------|---------|
| **Physics** (`physics_*.onnx`) | drift, hspeed, vspeed, emaxh, air, accel, speed_ratio, ground, sprint, speed_var | 20 ticks (1s) | Movement cheats |
| **Baseline** (`baseline_*.onnx`) | yawDelta, pitchDelta, hspeed, clickInterval | 40 ticks (2s) | Aim/combat cheats |

---

## What Can ML Detect?

### Physics Model — Movement Cheats

| Cheat | ML Detectable? | Why |
|-------|---------------|-----|
| **Fly** | YES | vspeed, air ticks, ground state — model sees you floating |
| **Speed** | YES | speed_ratio, hspeed — model sees you exceeding expected max |
| **AirStrafe** | YES | air ticks + high hspeed — model sees impossible air movement |
| **Velocity** | YES | drift from simulation — model sees you ignoring knockback |

### Baseline Model — Aim/Combat Cheats

| Cheat | ML Detectable? | Why |
|-------|---------------|-----|
| **KillAura** | YES | rotation snaps, consistent targeting patterns |
| **AimAssist** | YES | smooth rotation interpolation, reduced mouse variance |
| **AutoClicker** | YES | click interval distribution — too consistent or too fast |

### Heuristic Checks (Not ML)

These cheats are better caught by server-side validation, not behavioral analysis:

| Cheat | Detection Method | Why ML Can't Do It |
|-------|-----------------|-------------------|
| **Reach** | Distance check (max 3.0-3.5 blocks) | Server knows exact hit distance |
| **Hitbox** | Hit registration validation | Server knows entity bounding boxes |
| **FastBreak** | Mining speed check | Server knows expected break time |
| **FastPlace** | Placement rate check | Server knows placement cooldown |
| **Scaffold** | Block placement direction | Server knows where blocks are placed |
| **NoFall** | Fall damage validation | Server applies fall damage |
| **Phase** | Collision detection | Server checks block collisions |
| **Timer** | Tick rate validation | Server tracks packet frequency |
| **Velocity** (not taking KB) | Server-side knockback tracking | Server knows velocity was sent |
| **Step** | Step height validation | Server knows entity step height |
| **Jesus** | Block type under feet | Server knows if block is walkable |
| **Criticals** | Jump + hit timing | Server knows fall distance and hit timing |
| **AutoBlock** | Block/attack click timing | Server knows item switch and block place timing |

---

## Physics Model — Recording Guide

### Legit Recording
Record normal gameplay — the model needs to learn what "real" movement looks like.

- Sprint around, jump, do parkour
- Walk, sneak, transition between states
- Swim, climb ladders/vines
- Move on different surfaces (soul sand, ice, packed ice)
- Use boats, minecarts, elytra
- Fly in creative (bypasses checks, but good for edge case data)
- Log in, teleport, get knocked back — all of it

**Minimum**: ~15-20 legit sessions (30 sec each)

### Cheat Recording

| Label | What to do | Why it helps |
|-------|-----------|--------------|
| `fly` | Enable fly hack, move around in the air at different heights | Core fly detection |
| `speed` | Enable speed hack, sprint around at various speed values | Core speed detection |
| `airstrafe` | Jump and strafe in the air with modified air movement | Air movement cheats |
| `velocity` | Get hit, then enable velocity cancel | Knockback immunity detection |

**Per label**: ~5-10 cheat sessions (30 sec each)

### Recording Command
```
/mx dataset cheat <player> fly
/mx dataset cheat <player> speed
/mx dataset legit <player>
```

### Training
```bash
python train_physics.py --label fly --letter A
python train_physics.py --label speed --letter A
```

---

## Baseline (Aim) Model — Recording Guide

### Legit Recording
Record normal PvP and combat — the model needs to learn real aiming patterns.

- Fight mobs with a sword (normal clicking, tracking)
- Fight other players in PvP
- Bow shooting — tracking targets
- Look around normally while fighting
- Mix of fast and slow reactions
- Different sensitivities if possible

**Minimum**: ~15-20 legit sessions (30 sec each)

### Cheat Recording

| Label | What to do | Why it helps |
|-------|-----------|--------------|
| `killaura` | Enable killaura, attack mobs/players, let it auto-rotate | Core killaura detection |
| `aimassist` | Enable aim assist with different smoothing values | Smooth aimbot detection |
| `autoclicker` | Enable autoclicker at various CPS | Auto-clicker detection |

**Per label**: ~5-10 cheat sessions (30 sec each)

### Recording Command
```
/mx dataset cheat <player> killaura
/mx dataset cheat <player> aimassist
/mx dataset legit <player>
```

### Training
```bash
python train_baseline.py --label killaura --letter A
python train_baseline.py --label aimassist --letter A
```

---

## Recording Tips

1. **Sessions are 30 seconds** — `/mx dataset cheat <player> fly` starts, `/mx dataset off <player>` stops and saves.
2. **Don't stand still** — always move around while recording. Stationary data is useless for physics training.
3. **Vary your movement** — sprint, walk, jump, sneak, strafe. The model needs diversity.
4. **Record across sessions** — multiple short sessions > one long session (reduces overfitting to one scenario).
5. **Use letters for variants** — letter A = one server/client, letter B = another. Helps the model generalize.
6. **Legit data is king** — more legit data = fewer false positives. Record more legit than cheat.
7. **Stop recording before logging off** — use `/mx dataset off <player>` to save the session.

## Folder Structure
```
dataset/
├── legit/           # legit recordings (no letter)
├── fly/
│   ├── A/           # fly variant A
│   └── B/           # fly variant B
├── speed/
│   └── A/
├── killaura/
│   └── A/
└── aimassist/
    └── A/
```
