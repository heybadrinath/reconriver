# ReconRiver design system

## Direction

ReconRiver is a calm finance-operations workbench with high-energy exception signals. The surrounding environment is pale grey, the application canvas is softly tinted, and dense white cards organize real reconciliation data. Black panels anchor explanations and architecture content. Coral, yellow, green, and violet are reserved for operational states.

The supplied references informed the density, rounded canvas, condensed numeric typography, decisive color blocks, and mobile discipline. ReconRiver does not reuse their logos, people, illustrations, charts, card ordering, navigation labels, or device-collage layouts.

## Tokens

| Role             | Value     |
| ---------------- | --------- |
| Environment      | `#E7E8EA` |
| Canvas           | `#F4F8F7` |
| Surface          | `#FCFDFC` |
| Surface muted    | `#EDF2F0` |
| Strong ink       | `#101112` |
| Muted ink        | `#606765` |
| Muted strong     | `#4E5654` |
| Border           | `#DCE3E1` |
| Border strong    | `#C9D1CF` |
| Exception        | `#FF6A50` |
| Exception strong | `#C53A27` |
| Late settlement  | `#F6C945` |
| Matched          | `#48C78E` |
| Refund           | `#8C63E7` |
| Critical         | `#D83B32` |
| Focus            | `#9D7410` |

Barlow Condensed carries page titles, display text, and major monetary values. Manrope carries interface and body text. IBM Plex Mono carries IDs, checksums, timestamps, and rule codes. Spacing follows a 4/8/12/16/24/32/48/64 scale. Controls range from 12–16 px radii to fully rounded action pills, cards use approximately 20–28 px, and the desktop canvas uses a 32 px radius.

## Layout

The desktop application uses a 12-column asymmetric grid. The Reconciliation River occupies seven or eight columns; results, explanations, and exception panels occupy the remaining four or five. Intermediate widths use component-specific two- and three-column layouts rather than a fixed eight-column system. At the mobile breakpoint, the shared data grid becomes one column and preserves labels, amounts, explanations, and primary actions.

```text
Desktop
┌─────────────────────────────────────────────────────────────┐
│ wordmark    workspace context     navigation     session   │
├───────────────────────────────────────┬─────────────────────┤
│ Reconciliation River                  │ summary / exceptions│
│ Internal → Processor → Batch → Bank   │ rules / aging       │
├──────────────────────────┬────────────┴─────────────────────┤
│ results and investigation│ audit / operator actions        │
└──────────────────────────┴──────────────────────────────────┘

Mobile
┌─────────────────────────────┐
│ context + primary action    │
├─────────────────────────────┤
│ Internal                    │
│   ↓                         │
│ Processor ── exception lane │
│   ↓                         │
│ Settlement batch           │
│   ↓                         │
│ Bank                        │
├─────────────────────────────┤
│ summary / investigation     │
└─────────────────────────────┘
```

## Signature interaction

The Reconciliation River is an accessible data visualization, not a decorative Sankey. It presents Internal Ledger → Processor → Settlement Batch → Bank. Green continuous paths mean matched, coral broken paths identify mismatches and other discrepancies, yellow continuous paths mean late, violet branches mean refunds, and grey dashed paths mean unresolved. Outcome text and accessible rule labels carry the precise state because color and line shape do not distinguish every outcome.

The visualization shows at most 14 flows in its full form and eight in compact form; it does not claim to aggregate the remaining results. The River and client-side table represent only the result slice fetched for that view; the API's paginated result collection remains authoritative. Filtering narrows the displayed sample locally, while selecting a flow opens an API-backed transaction trace. Keyboard focus and hover expose the outcome, explanation, identifier, currency-formatted amount, and an accessible rule label. The expandable River table repeats the displayed outcome, stage identifiers, and amount. Eligible continuous paths use a 1.4-second path-draw animation when the River renders; reduced-motion users see the final state immediately.

## Accessibility rules

- Keep all interactive targets at least 44 px.
- Use visible focus rings and semantic headings.
- Pair every status color with text and a distinguishable shape, line style, or icon.
- Keep critical copy visible on small screens.
- Give charts descriptions and the River a table equivalent.
- Test text and component contrast to WCAG AA.
- Avoid faint captions and motion loops present in the references.
