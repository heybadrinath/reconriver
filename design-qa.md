# Design QA

**final result: blocked**

## Findings

- No actionable P0, P1, or P2 visual mismatch remains in the checked local states.
- Handoff remains blocked until the redesigned frontend is deployed and the same journey, responsive, accessibility, console, and reference-comparison checks pass against the public URL.

## Visual truth and normalization

- Source visual truth: user-supplied 2048 × 1536 dashboard reference, retained locally and intentionally not redistributed by the repository.
- Combined landing comparison: `frontend/.design-qa/redesign/reference-vs-landing-final.jpg` — 2880 × 900 px, local-only.
- Combined dashboard comparison: `frontend/.design-qa/redesign/reference-vs-overview.png` — 1600 × 600 px, local-only.
- Primary implementation capture: `frontend/.design-qa/redesign/landing-desktop-final-viewport.jpg` — 1440 × 900 px at a 1440 × 900 CSS viewport.
- Responsive implementation capture: `frontend/.design-qa/redesign/landing-mobile-final.jpg` — 390 × 844 px at a 390 × 844 CSS viewport.
- Implementation capture pixels match their CSS viewport dimensions, so no density normalization was required.

The source is a finance-dashboard reference rather than a screen-for-screen ReconRiver specification. The comparison therefore evaluates its visual language—shell, hierarchy, density, typography, surfaces, contrast, semantic color, and responsive discipline—without treating different product content as drift.

## Flow evidence

| Step | State                   | Accepted evidence                                                | Health          |
| ---: | ----------------------- | ---------------------------------------------------------------- | --------------- |
|    1 | Landing                 | `landing-desktop-final-viewport.jpg`, `landing-mobile-final.jpg` | Healthy locally |
|    2 | Scenario selection      | `scenarios-desktop.png`                                          | Healthy locally |
|    3 | Scenario file preview   | `scenario-preview-desktop.png`                                   | Healthy locally |
|    4 | Imported source review  | `imports-desktop.png`                                            | Healthy locally |
|    5 | Ready to reconcile      | `runs-ready-desktop.png`                                         | Healthy locally |
|    6 | Completed run           | `run-details-desktop-final.jpg`, `run-details-mobile.png`        | Healthy locally |
|    7 | Reconciliation overview | `overview-desktop.png`, `overview-mobile-v2.png`                 | Healthy locally |
|    8 | Exception investigation | `exceptions-desktop.png`                                         | Healthy locally |
|    9 | Dataset                 | `dataset-desktop.png`                                            | Healthy locally |
|   10 | Architecture            | `architecture-desktop.png`                                       | Healthy locally |

All evidence paths are under `frontend/.design-qa/redesign/` and remain local-only.

The final local mixed-exceptions journey completed with **1,244 total results**, **1,007 matched**, **237 exceptions**, and a displayed duration of **3.3 seconds**. The screenshots show API-backed data across scenario selection, imports, reconciliation, overview, run details, and investigation rather than disconnected presentation-only screens.

## Full-view comparison

The combined `reference-vs-landing-final.jpg` input places the supplied reference and the revised landing screen in one frame at matching 1440 × 900 dimensions. The `reference-vs-overview.png` comparison repeats the exercise with the API-backed reconciliation dashboard, where density and operational hierarchy are directly comparable. The implementation carries over the intended design language without copying the source layout or assets:

- a pale grey environment around a softly tinted, rounded application canvas;
- compact pill navigation and a decisive black active state;
- condensed, high-contrast headings and metrics;
- dense bordered cards with shallow elevation;
- black anchor panels paired with coral, green, yellow, and violet operational signals;
- compact KPI cards and restrained semantic accent bars in the real dashboard;
- strong above-the-fold hierarchy with the primary workspace action visible.

No P0–P2 visual mismatch is visible in this local comparison. The source and implementation have different domains and information architecture, so exact card positions and content are intentionally not compared pixel for pixel.

## Focused fidelity review

- **Typography:** Barlow Condensed provides the source-like headline and metric density; Manrope keeps explanatory and control copy readable; IBM Plex Mono distinguishes operational metadata. The accepted captures show no clipped headings, broken labels, or accidental fallback typography.
- **Spacing and layout:** desktop uses a consistent inset shell, compact header, asymmetric cards, fine borders, and restrained radii. Mobile follows a single-column task order without collapsing labels or actions. At both 1280 × 720 and 390 × 844, page `scrollWidth` equalled the viewport width.
- **Colors and tokens:** the neutral shell and black panels establish hierarchy. Coral is reserved for attention and primary emphasis, green for matched/completed state, yellow for timing, and violet for refund or secondary operational context. Status meaning is repeated in text.
- **Image and icon quality:** no photographic, avatar, illustration, or logo asset from the supplied reference is reused. ReconRiver uses its own wordmark and consistent Lucide interface icons; there are no placeholder images or low-resolution raster substitutes in the checked screens.
- **Copy and content:** the interface describes the real reconciliation workflow in concise operator language. Actions state the next step, completed runs expose totals and duration, and investigation copy explains the rule and source evidence.
- **Controls and states:** active navigation, step progression, completion badges, filters, empty state, export, and investigation actions share one control language. The mobile menu opens, exposes navigation, and closes correctly.

The full-size landing, run, overview, and investigation captures made the required typography, spacing, token, icon, and copy surfaces readable; an additional crop was not needed beyond the two combined comparison inputs.

## Accessibility and responsive evidence

- Desktop viewport: 1440 × 900. Mobile viewport: 390 × 844.
- Desktop and mobile document `scrollWidth` matched their viewport width in the checked pages.
- Visible application-owned interactive targets measured at least 44 × 44 px.
- The completed mobile run returned 29 axe passes, 0 violations, and 0 incomplete checks.
- The mobile navigation menu was exercised successfully.
- With the exception queue scrolled to `720px`, the sticky header remained at the top of the viewport. Selecting a queue row moved focus to the labelled exception-details region and scrolled that region into view.
- The screenshots alone do not establish full accessibility compliance. Keyboard sequence, reduced motion, zoom/text scaling, and console output must be repeated after deployment.

## Comparison history

### Iteration 1

- **P2 — Desktop hero density pushed the result preview too far below the fold.**
- Evidence: `landing-desktop-v1.png` wrapped the main statement across three display lines and consumed more vertical space than the reference's compact dashboard rhythm.
- Fix: reduce the desktop display scale and rebalance the hero columns so the statement uses two lines while keeping the explanatory copy and actions aligned.
- Post-fix evidence: `landing-desktop-v2.png` and `reference-vs-landing-v2.png` show the denser hero and more of the reconciliation summary above the fold.

### Iteration 2

- The revised landing page and API-backed overview were each compared with the supplied reference in combined local-only images.
- The full flow was then inspected at 1280 × 720, with landing, run details, and overview also checked at 390 × 844.
- No new actionable P0, P1, or P2 mismatch was found; no additional visual change was required from this pass.

### Iteration 3

- **P1 — A selected exception could remain in the inspector after filters removed it from the queue.** Selection is now derived only from the current filtered result set, and mobile queue selection scrolls and focuses the inspector.
- **P1 — Settlement aging was calculated from a 100-result page but presented like a full-run aggregate.** It is now labelled as a sample with both the loaded and total result counts.
- **P2 — The scenario action was buried below the file inventory.** The selected scenario and its single `Load sources` action now sit directly below the progress tracker; file details are collapsed by default.
- **P2 — The canvas overflow rule prevented sticky navigation.** The shell now uses clipping without creating a scroll container; the mobile header remains at `top: 0` after scrolling.
- **P2 — Workflow and navigation state needed clearer assistive output.** Each step announces completed/current/not-completed state, grouped navigation uses visual state without a false `aria-current`, and exception tabs retain 44px targets.
- **Runtime — Source rows with no currency caused the completed-run screen to fail.** Money formatting now handles absent or invalid currency values explicitly, with regression coverage and a successful reload of the 1,244-result run.

## Production acceptance checklist

- [x] Compare the supplied desktop reference with the revised landing and API-backed overview in combined inputs.
- [x] Complete the local mixed-exceptions journey with API-backed results.
- [x] Capture the landing, reconcile flow, completed run, overview, exceptions, dataset, and architecture screens.
- [x] Check desktop and mobile document overflow.
- [x] Check visible mobile target sizes and run axe on the completed mobile state.
- [x] Exercise the mobile navigation menu.
- [ ] Deploy the redesigned frontend.
- [ ] Repeat the real public mixed-exceptions journey.
- [ ] Repeat public desktop/mobile screenshots and overflow checks.
- [ ] Repeat public axe, keyboard, reduced-motion, zoom/text-scaling, focus, and console checks.
- [ ] Compare the deployed landing screen with the supplied reference and confirm no P0–P2 regression.

**Blocking condition:** the redesigned build has not yet completed production redeploy and live verification.
