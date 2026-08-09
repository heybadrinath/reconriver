# Design QA

**final result: blocked**

The stable frontend and API are public, and production desktop/mobile captures now cover the landing page and real reconciliation workbench. The combined reference comparison found no P0–P2 visual mismatch. Final acceptance remains blocked while one serious mobile axe incomplete is corrected and redeployed, and until the genuine cold-start state is recorded.

## Visual truth and evidence

- Source visual truth (local-only; not redistributed):
  - `frontend/.design-qa/reference.webp` — 2048 × 1536 px.
  - `frontend/.design-qa/reference-mobile.png` — 2140 × 1606 px.
- Browser-rendered implementation (local-only):
  - `frontend/.design-qa/final-local-desktop-1280x720.png` — 1280 × 720 px at a 1280 × 720 CSS viewport and device scale factor 1.
  - `frontend/.design-qa/final-local-mobile-375x812.png` — 375 × 812 px at a 375 × 812 CSS viewport and device scale factor 1.
  - `frontend/.design-qa/final-local-fee-state.png` — selected fee-discrepancy interaction at 1280 × 720.
- Public production captures:
  - [Desktop landing capture](docs/assets/reconriver-live-desktop.jpg) — live desktop landing page at 1585 × 892 px.
  - [Desktop workbench capture](docs/assets/reconriver-live-workbench.jpg) — live desktop Reconciliation River and run workbench at 1585 × 892 px.
  - [Mobile landing capture](docs/assets/reconriver-live-mobile.png) — live mobile landing page at 375 × 844 px.
  - [Mobile workbench capture](docs/assets/reconriver-live-mobile-workbench.png) — live mobile run heading, progress, and summary cards at 375 × 844 px; the River is below this viewport.
- Combined comparison inputs:
  - `frontend/.design-qa/final-local-comparison.png` — all three images displayed at equal panel width on one 1600 × 900 canvas.
  - `frontend/.design-qa/final-local-comparison-focused.png` — enlarged desktop shell/controls and mobile hierarchy crops on one 1600 × 900 canvas.
  - `frontend/.design-qa/reconriver-production-comparison.jpg` — local-only comparison of both supplied references with live desktop and 375 px states; excluded from publication because the references are third-party art direction.
- States: public landing page and API-backed mixed-exceptions overview/workbench, at desktop and mobile widths.

The source images are art-direction references rather than screen-for-screen product mocks. Comparisons therefore judge the requested visual language—shell, hierarchy, density, contrast, typography, controls, semantic color, and responsive discipline—without treating domain-specific content differences as drift.

## Full-view comparison

ReconRiver carries the first reference's pale-grey environment, light inset canvas, fine borders, large restrained radii, compact utility navigation, decisive black anchor surface, and dense information hierarchy. It takes the second reference's condensed display typography, confident coral/green/yellow/violet signals, and disciplined mobile stacking without copying its device collage, illustrations, characters, or saturated periwinkle field.

The result stays specific to reconciliation: ledger-to-processor-to-batch-to-bank flow, rule explanations, synthetic-data disclosure, and operator actions replace generic sales cards and restaurant imagery.

## Focused fidelity checks

- **Fonts and typography:** Barlow Condensed provides the tall, compressed headline rhythm visible in both references; Manrope remains readable for body/interface text; mono labels are reserved for operational context. Desktop and 375 px captures show no clipped or overlapping text.
- **Spacing and layout rhythm:** the desktop 5/7 hero split, compact 12-column language, card borders, shell inset, and shallow elevation match the calmer reference. Mobile becomes a single-column reading flow with a one-line header action. At the production 390 px check, `innerWidth` and document `scrollWidth` were both 390 px; the landing page's content width computed to 375 px after the scrollbar, with the header and hero fully visible.
- **Colors and tokens:** the neutral shell and black anchor dominate. Coral identifies the primary exception/action signal; green, yellow, and violet retain semantic roles with text labels rather than color-only meaning. The muted text token was strengthened from `#68706e` to `#606765` during accessibility QA.
- **Image quality and assets:** no reference logo, people, illustration, chart, or character was copied. Lucide supplies interface icons. The required Reconciliation River is a product visualization backed by API results, not a decorative imitation.
- **Copy and content:** text describes implemented Java generation, Spring Batch ingestion/recovery, deterministic rules, synthetic-only workspaces, and public-demo limits. No customer, revenue, scale, or impact claim is fabricated.

## Interaction and browser evidence

- Primary navigation, landing actions, semantic headings, and preview controls were present in the browser accessibility tree.
- Selecting **Fee discrepancy** set `aria-pressed="true"` and changed the visible explanation to the configured fee-policy exception.
- The local landing page emitted no browser console warnings or errors during the checked states.
- Frontend unit/accessibility suite: 12 files and 35 tests passed.
- Two Playwright projects—desktop Chromium and mobile Chromium—passed the real-API journey locally, including scenario loading, imports, reconciliation, resolve/reopen, audit history, CSV download, and axe checks.
- The deployed desktop axe scan recorded 29 passes, 0 violations, and 0 incomplete checks. The deployed mobile scan recorded 29 passes, 0 violations, and 1 serious incomplete check for an `aria-label` on a roleless mobile River container; this is being corrected before acceptance.
- The deployed landing and workbench checks emitted 0 browser console warnings or errors.
- At the mobile run-detail route, River cards stack vertically and the desktop SVG is hidden rather than compressed off-screen.
- Reduced-motion behavior and the Reconciliation River's equivalent table are covered in the frontend tests; the final redeploy must repeat mobile axe plus keyboard, focus, console, overflow, and visual checks.

## Findings

- No local P0, P1, or P2 visual finding remains.
- **Blocking accessibility follow-up:** axe could not complete one mobile River name check because `aria-label` was placed on a `div` without an applicable role. It reported an incomplete, not a violation, but the markup is being corrected rather than waived.
- **Remaining evidence gap:** repeat the mobile production audit after redeployment and record the genuine cold-start message/state.

## Comparison history

### Iteration 1

- **P2 — Mobile header action wrapped and weakened the header rhythm.**
- Fix: use the concise visible label **Try demo** below the small breakpoint while preserving the full action meaning for desktop and assistive technology.
- Post-fix evidence: `frontend/.design-qa/final-local-mobile-375x812.png` shows a single-line action, aligned wordmark, intact 44 px target, and no horizontal overflow.

### Iteration 2

- Combined full-view and focused comparisons found no new actionable P0, P1, or P2 difference. No visual code change was made in response, so this is the required no-fix comparison pass.

### Iteration 3

- The production comparison combines both supplied references with the live desktop landing/workbench and live 375 px landing/workbench captures.
- Review found no P0, P1, or P2 mismatch. The implementation carries the pale shell, dense bordered cards, black anchor surface, condensed type, semantic contrast, and disciplined mobile stacking without copying reference assets or layouts.
- Apparent landing clipping in an earlier image was a capture artifact, not layout overflow. A 390 px DOM check measured document `scrollWidth` equal to the viewport, with the full header and hero visible. The mobile run DOM exposes the vertical **Reconciliation flows in mobile layout** region while the desktop SVG is hidden.

## Final acceptance checklist

- [x] Deploy the stable API and frontend.
- [x] Run the real desktop and mobile browser journey with no mocked API results.
- [x] Capture production landing and API-backed workbench screens at desktop and mobile widths.
- [x] Record desktop/mobile overflow, console, responsive River, and initial axe evidence.
- [x] Build the combined reference-versus-production comparison and verify no P0–P2 visual mismatch.
- [ ] Redeploy the mobile River accessibility correction and repeat the affected checks.
- [ ] Capture the genuine idle-service cold-start message/state.
