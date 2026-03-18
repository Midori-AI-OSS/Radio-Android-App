# UI/UX Standards

This document defines the UI/UX standards for the Radio Android App. Use it as the baseline for all new UI work and UI refactors.

## 1. Core Framework Standards

| Standard | Requirement |
|---|---|
| UI framework | Jetpack Compose only |
| XML layouts | Not used for UI |
| Design system | Material 3 |
| Default theme | Dark theme (`useDarkTheme: Boolean = true`) |
| Dynamic color | Disabled by default |

## 2. Design Language

- Favor clean, rectangular layouts.
- Keep surfaces visually calm and readable.
- Use consistent spacing and typography hierarchy.
- Prefer theme tokens over hardcoded values.

## 3. Color Standards

### Primary Palette

| Token | Value | Usage |
|---|---:|---|
| `MidoriAIBackground` | `#141618` | Primary dark background |
| `MidoriAIBackgroundAlt` | `#1E2126` | Alternative background |
| `MidoriAISurface` | `#24282E` | Cards, sheets |
| `MidoriAISurfaceVariant` | `#30363E` | Elevated surfaces |
| `MidoriAIFrostWhite` | `#ECE8E2` | Primary text, primary color |
| `MidoriAISoftSilver` | `#C7CED6` | Secondary text |
| `MidoriAIIceBlue` | `#A5B4C2` | Tertiary text, accent |
| `MidoriAIErrorTint` | `#F28E8E` | Error states |
| `MidoriAIOnBackground` | `#F1EEE9` | On-background content |
| `MidoriAIOnSurface` | `#E8E4DF` | On-surface content |
| `MidoriAIOnSurfaceVariant` | `#B6BDC6` | Muted text |
| `MidoriAIOutline` | `#4D5660` | Borders, dividers, outlines |

### Light Theme Backgrounds

| Token | Value |
|---|---:|
| Background | `#F3F1ED` |
| Surface | `#E7E4DE` |

### Color Usage Rules

- Use `MaterialTheme.colorScheme.*` instead of direct color references in composables when possible.
- Reserve error tint for validation, failure, or destructive feedback.
- Use surface and surface-variant colors to create depth instead of adding heavy decoration.

## 4. Typography Standards

| Style | Weight | Size | Line Height | Usage |
|---|---|---:|---:|---|
| `headlineLarge` | SemiBold | `30sp` | `36sp` | Screen titles |
| `headlineMedium` | SemiBold | `24sp` | `30sp` | Screen headers |
| `titleLarge` | SemiBold | `18sp` | `24sp` | Section headers |
| `titleMedium` | Medium | `16sp` | `22sp` | Card titles, track titles |
| `bodyLarge` | Normal | `16sp` | `22sp` | Subtitles, descriptions |
| `bodyMedium` | Normal | `14sp` | `20sp` | Secondary text, artist names |
| `labelLarge` | Medium | `12sp` | `16sp` | Labels, tags |

### Typography Rules

- Use `MaterialTheme.typography.*` tokens consistently.
- Preserve hierarchy: titles must be visually distinct from supporting text.
- Use single-line truncation for constrained metadata.

```kotlin
Text(
    text = title,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)
```

## 5. Shape Standards

All shapes use zero corner radius.

| Standard | Value |
|---|---|
| Corner treatment | `RoundedCornerShape(0.dp)` |
| Visual style | Clean rectangular edges |

## 6. Spacing and Padding Standards

| Value | Usage |
|---:|---|
| `4.dp` | Tight spacing, such as title-to-subtitle |
| `6.dp` | Small gaps |
| `10.dp` | Medium gaps, vertical item spacing |
| `12.dp` | Card internal padding, `LazyRow` item spacing |
| `14.dp` | Art-to-text spacing |
| `16.dp` | Standard horizontal screen padding |
| `18.dp` | Control dock padding |
| `20.dp` | Large horizontal padding |
| `22.dp` | Section spacing |

### Spacing Rules

- Default to `16.dp` horizontal padding at screen edges.
- Keep internal component spacing consistent with the scale above.
- Avoid introducing ad hoc spacing values unless there is a strong layout reason.

## 7. Component Patterns

### Required Patterns

| Pattern | Standard |
|---|---|
| Modifier parameter | `modifier: Modifier = Modifier` |
| Theme access | `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*` |
| Text truncation | `maxLines = 1` + `TextOverflow.Ellipsis` |
| Card colors | `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)` |

### Example

```kotlin
@Composable
fun TrackCard(
    title: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

## 8. Icon and Control Sizing

| Button Size | Icon Size | Usage |
|---:|---:|---|
| `68.dp` | `38.dp` | Primary play/pause |
| `56.dp` | `30.dp` | Secondary controls, such as skip |
| `48.dp` | `24.dp` | Tertiary controls |

## 9. Animation Standards

| Interaction | Standard |
|---|---|
| Easing | `FastOutSlowInEasing` |
| Channel transition | `320ms` |
| Gradient transition | `820ms` |
| Swipe lock duration | `500ms` |
| Channel switch fade | `220ms` |

### Animation Rules

- Use motion to clarify state changes, not decorate them.
- Keep transitions smooth and consistent with the timing standards above.
- Reuse shared easing and timing values where possible.

## 10. State Management Standards

| Area | Standard |
|---|---|
| UI state | `StateFlow` |
| Screen state holder | `ViewModel` |
| Compose collection | `collectAsState()` |
| Playback modeling | Sealed interfaces |
| User preferences | DataStore Preferences |

### State Rules

- Keep composables stateless where practical.
- Hoist state to the appropriate `ViewModel`.
- Model playback and related UI states with sealed interfaces for clarity and exhaustiveness.

## 11. Navigation Standards

| Area | Current Standard |
|---|---|
| Navigation library | Navigation Compose |
| Current app structure | Single-screen |
| Active screen | `NowPlayingScreen` |
| Future support | `NavController` available for expansion |

### Navigation Rules

- Continue using Navigation Compose for future multi-screen growth.
- Keep navigation concerns separate from screen UI logic.

## 12. Responsive Layout Standards

Use `BoxWithConstraints` to scale layout based on available width.

| Breakpoint | Layout Mode | Artwork Size | Title Size |
|---|---|---:|---:|
| `maxWidth <= 420.dp` | Compact | `224.dp` | `24.sp` |
| `maxWidth < 600.dp` | Medium | `252.dp` | `26.sp` |
| `else` | Expanded | `320.dp` | `30.sp` |

### Example

```kotlin
BoxWithConstraints {
    val (artSize, titleSize) = when {
        maxWidth <= 420.dp -> 224.dp to 24.sp
        maxWidth < 600.dp -> 252.dp to 26.sp
        else -> 320.dp to 30.sp
    }
}
```

## 13. Implementation Summary

When building or updating UI in this app:

1. Use Jetpack Compose and Material 3 only.
2. Default to the dark theme styling model.
3. Follow the defined color, typography, spacing, and sizing tokens.
4. Keep edges square and layouts structured.
5. Use theme-based styling, responsive sizing, and consistent animation timings.
