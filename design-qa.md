# Design QA

**final result: blocked**

The local landing-page preflight has no actionable P0, P1, or P2 finding. Final acceptance remains blocked until the stable API is deployed and the complete desktop/mobile journey is captured against real Render and Neon responses.

## Visual truth and evidence

- Source visual truth (local-only; not redistributed):
  - `frontend/.design-qa/reference.webp` — 2048 × 1536 px.
  - `frontend/.design-qa/reference-mobile.png` — 2140 × 1606 px.
- Browser-rendered implementation (local-only):
  - `frontend/.design-qa/final-local-desktop-1280x720.png` — 1280 × 720 px at a 1280 × 720 CSS viewport and device scale factor 1.
  - `frontend/.design-qa/final-local-mobile-375x812.png` — 375 × 812 px at a 375 × 812 CSS viewport and device scale factor 1.
  - `frontend/.design-qa/final-local-fee-state.png` — selected fee-discrepancy interaction at 1280 × 720.
- Combined comparison inputs:
  - `frontend/.design-qa/final-local-comparison.png` — all three images displayed at equal panel width on one 1600 × 900 canvas.
  - `frontend/.design-qa/final-local-comparison-focused.png` — enlarged desktop shell/controls and mobile hierarchy crops on one 1600 × 900 canvas.
- State: public landing page, default reference-match preview plus selected fee-discrepancy preview.

The source images are art-direction references rather than screen-for-screen product mocks. Comparisons therefore judge the requested visual language—shell, hierarchy, density, contrast, typography, controls, semantic color, and responsive discipline—without treating domain-specific content differences as drift.

## Full-view comparison

ReconRiver carries the first reference's pale-grey environment, light inset canvas, fine borders, large restrained radii, compact utility navigation, decisive black anchor surface, and dense information hierarchy. It takes the second reference's condensed display typography, confident coral/green/yellow/violet signals, and disciplined mobile stacking without copying its device collage, illustrations, characters, or saturated periwinkle field.

The result stays specific to reconciliation: ledger-to-processor-to-batch-to-bank flow, rule explanations, synthetic-data disclosure, and operator actions replace generic sales cards and restaurant imagery.

## Focused fidelity checks

- **Fonts and typography:** Barlow Condensed provides the tall, compressed headline rhythm visible in both references; Manrope remains readable for body/interface text; mono labels are reserved for operational context. Desktop and 375 px captures show no clipped or overlapping text.
- **Spacing and layout rhythm:** the desktop 5/7 hero split, compact 12-column language, card borders, shell inset, and shallow elevation match the calmer reference. Mobile becomes a single-column reading flow with a one-line header action and no horizontal document overflow (`375 px` viewport; measured scroll width `360 px`).
- **Colors and tokens:** the neutral shell and black anchor dominate. Coral identifies the primary exception/action signal; green, yellow, and violet retain semantic roles with text labels rather than color-only meaning. Contrast will be rechecked with the production accessibility run.
- **Image quality and assets:** no reference logo, people, illustration, chart, or character was copied. Lucide supplies interface icons. The required Reconciliation River is a product visualization backed by API results, not a decorative imitation.
- **Copy and content:** text describes implemented Java generation, Spring Batch ingestion/recovery, deterministic rules, synthetic-only workspaces, and public-demo limits. No customer, revenue, scale, or impact claim is fabricated.

## Interaction and browser evidence

- Primary navigation, landing actions, semantic headings, and preview controls were present in the browser accessibility tree.
- Selecting **Fee discrepancy** set `aria-pressed="true"` and changed the visible explanation to the configured fee-policy exception.
- The local landing page emitted no browser console warnings or errors during the checked states.
- Frontend unit/accessibility suite: 10 files and 30 tests passed.
- Reduced-motion behavior and the Reconciliation River's equivalent table are covered in the frontend tests; final axe and keyboard checks will be repeated on the live journey.

## Findings

- No local P0, P1, or P2 visual finding remains.
- **Blocking evidence gap:** overview, imports, run progress, Reconciliation River, exception workbench, transaction trace, resolve/reopen, audit history, CSV download, cold-start state, and mobile app-shell navigation still require final browser evidence against the deployed API.

## Comparison history

### Iteration 1

- **P2 — Mobile header action wrapped and weakened the header rhythm.**
- Fix: use the concise visible label **Try demo** below the small breakpoint while preserving the full action meaning for desktop and assistive technology.
- Post-fix evidence: `frontend/.design-qa/final-local-mobile-375x812.png` shows a single-line action, aligned wordmark, intact 44 px target, and no horizontal overflow.

### Iteration 2

- Combined full-view and focused comparisons found no new actionable P0, P1, or P2 difference. No visual code change was made in response, so this is the required no-fix comparison pass.

## Final acceptance checklist

- Deploy the stable API and frontend.
- Run the real desktop and mobile browser journey with no mocked API results.
- Capture the seven required production screens plus cold-start/recovery evidence.
- Run axe, keyboard, focus, reduced-motion, console, overflow, trusted/rejected CORS, and download checks.
- Build one final combined reference-versus-production comparison and change `final result` to `passed` only if no P0–P2 finding remains.
