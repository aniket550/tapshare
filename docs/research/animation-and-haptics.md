# Glow / ripple animation + haptics — research

Status: **first pass (2026-11-01).** Apple has published no breakdown of this effect.
Everything below is from descriptions + community recreations. Exact numbers need a
reference video (see `namedrop-flow.md` §4).

## 1. What it looks like (from descriptions)

- An **organic glow rises from the top of the screen** (around the Dynamic Island), with
  a vibration. (TapSmart)
- Described as a **white haze** (SlashGear) / **shimmer** (Pubtact) / "sparkle".
- The **top half of the display is warped** by a ripple, like water. (Creative Bloq)
- About a second later the Contact Poster takes over the screen.

## 2. How people recreate it (useful for our AGSL shader)

Community versions (SwiftUI + Metal/GLSL) all use the same idea:

```
d       = distance(pixel, origin)          // origin = camera position
wave    = sin(frequency * d - speed * t)   // rings moving outwards
fade    = exp(-decay * t) * smoothstep(..., d)   // dies out over time, soft near origin
offset  = direction(pixel - origin) * wave * amplitude * fade
colour  = sample(content, pixel + offset)  // refraction = read a shifted pixel
glow    = brightness added near the wave front, white with slight colour tint
```

On Android this maps to **AGSL `RuntimeShader`** (API 33+) applied with
`RenderEffect.createRuntimeShaderEffect` / Compose `graphicsLayer { renderEffect = ... }`.
Fallback for API 29–32: Compose `Canvas` radial gradients + blur (no real refraction).

**Limit (honest):** our shader can only warp **our own** pixels. In-app we warp our own
screen content (looks real). When the app is closed, the overlay window can't see the
other app's pixels, so the warp has to be faked (glow + light distortion of our own
layer, optionally cross-window blur on Android 12+). This will look close, not identical.

## 3. Glow origin = front camera

`WindowInsets` → `DisplayCutout.boundingRects` gives the camera hole. Cases to handle:
centre punch-hole, corner punch-hole, waterdrop notch, no cutout (fallback: top centre).

## 4. Haptics

| Moment | Plan (API 31+) | Fallback |
|---|---|---|
| Glow start (tap detected) | `Composition` with `PRIMITIVE_THUD` (strong, "downward") or `PRIMITIVE_CLICK` at scale 1.0 | `createWaveform` short strong pulse |
| Connected / poster shown | `PRIMITIVE_TICK` / light click | short weak pulse |
| Transfer complete | rising `PRIMITIVE_QUICK_RISE` + `CLICK` | two short pulses |

- `VibrationEffect.Composition` primitives exist since API 30; THUD/SPIN/LOW_TICK since 31.
- If the hardware lacks a primitive it plays **nothing** (no error) → always check with
  `Vibrator.arePrimitivesSupported(...)` and fall back.
- Final choice of primitive is tuned by feel against an iPhone, not from docs.

## 5. Open questions (need the reference video)
- Exact glow colour (pure white vs slight rainbow edge), size, and how far down it reaches.
- Ripple ring count, speed, duration, and easing.
- Does the glow stay while the poster is shown, or fade out first?
- What the transfer animation looks like on both phones.

## Sources
- [TapSmart — NameDrop](https://www.tapsmart.com/tips-and-tricks/namedrop/)
- [SlashGear — NameDrop](https://www.slashgear.com/1440375/how-to-use-namedrop-apple-iphone/)
- [Pubtact — NameDrop and Android](https://pubtact.com/resources/namedrop-and-android)
- [Creative Bloq — iOS 17 NameDrop animation](https://www.creativebloq.com/news/ios-17-namedrop)
- [GitHub semico/NameDrop — SwiftUI + GLSL ripple replica](https://github.com/semico/NameDrop)
- [Habr — NameDrop shader breakdown](https://habr.com/en/articles/762656) (only intro seen)
- [Cindori — SwiftUI Metal shader effects (ripple math)](https://cindori.com/developer/swiftui-metal-shaders-effects)
- [Daniel Kuntz gist — AirDrop-style shader](https://gist.github.com/dkun7944/2f793643e469029fb4e7d700f0645ffc)
- [Android Vibrator reference](https://developer.android.com/reference/kotlin/android/os/Vibrator)
- [VibrationEffect.Composition reference](https://developer.android.com/reference/android/os/VibrationEffect.Composition)
