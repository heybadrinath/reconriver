# ReconRiver design system

ReconRiver uses a restrained operations-dashboard language: a pale application canvas, dense bordered cards, condensed display type, black anchor panels, and semantic color used only where it improves decision-making. The supplied visual reference establishes the composition and tone; its brand assets and content are not part of this repository.

## Foundations

| Role                   | Token                 |
| ---------------------- | --------------------- |
| Environment            | `#DFE0E2`             |
| Application canvas     | `#F0F6F4`             |
| Primary surface        | `#FCFDFC`             |
| Muted surface          | `#EDF2F0`             |
| Primary ink            | `#101112`             |
| Muted ink              | `#66706D`             |
| Strong muted ink       | `#46504D`             |
| Border / strong border | `#D9E0DE` / `#CBD3D0` |
| Exception              | `#FF6449`             |
| Exception emphasis     | `#BD402D`             |
| Matched                | `#50C990`             |
| Late settlement        | `#F6CA45`             |
| Refund                 | `#8A64E8`             |
| Critical               | `#D83B32`             |
| Focus                  | `#9D7410`             |

Soft semantic surfaces use `#FFEDE8`, `#DDF5E9`, `#FFF4C9`, and `#EEE8FF`. Cards use a `1.4rem` radius on larger screens and `1.25rem` on mobile, with fine borders and shallow elevation. The application shell uses a stronger, diffuse shadow to separate it from the grey environment.

## Typography

- **Barlow Condensed** (500–800) carries page titles, card headings, and metrics.
- **Manrope** carries navigation, controls, body copy, and explanations.
- **IBM Plex Mono** (400–600) carries IDs, rule codes, timestamps, checksums, and uppercase utility labels.

Display text is compact and high contrast; body text remains sentence case with comfortable line height. Mono text is reserved for technical context rather than general interface copy.

## Layout

The application canvas is capped at `1440px`. Desktop screens sit inside a rounded, inset shell and use a 12-column grid with `0.75rem` gaps. Page regions are intentionally asymmetric: high-value operational context receives more width, while status and action cards stay compact.

At `640px` and below, shared grids collapse to one column, the outer shell becomes edge-to-edge, and the sticky desktop navigation becomes a menu drawer. Mobile pages preserve the task order—context, primary action, status, detail—without hiding critical results or exception actions.

## Components and interaction

- Navigation uses rounded pills; the current section is black with white text.
- Cards use border, spacing, and semantic edge color rather than heavy shadow to communicate grouping and state.
- Primary actions are black or coral. Secondary actions remain white with a visible border.
- The reconcile journey uses three explicit stages: choose data, review sources, and reconcile.
- Loading, empty, error, completed, and restartable states keep the next action visible and explain what changed.
- The Reconciliation River connects internal ledger, processor, settlement batch, and bank evidence. Outcome labels and an equivalent table remain authoritative; color is never the only status cue.
- Mobile navigation is a modal drawer with focus management and an explicit close control.

## Accessibility

- Interactive targets are at least `44 × 44px`.
- Keyboard focus uses a visible 3px focus ring.
- Semantic state colors are paired with labels, icons, or line treatment.
- Technical values wrap instead of forcing document-level horizontal scrolling.
- Reduced-motion preferences disable non-essential animation and smooth scrolling.
- Page structure uses one clear heading hierarchy, a skip link, labelled navigation, and accessible loading/status regions.
- Dense visualizations provide a text or table equivalent.
