# Design QA

**final result: passed**

The stable frontend and API are public. Production desktop/mobile captures cover the landing page and real reconciliation workbench, while DOM and Render timing evidence cover the genuine idle-wake state. Final deployed desktop and mobile audits are green, and comparison against both supplied references found no P0–P2 visual mismatch.

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
  - [Mobile River capture](docs/assets/reconriver-live-mobile-river.jpg) — final deployed vertical River cards at 375 × 844 px.
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
- The final deployed mobile run `200291e1-8569-472d-ad2a-f1a5204da843` completed with 1,244 results: 1,007 matched and 237 exceptions in 42.4 seconds.
- At 375 × 844, window inner width, document client width, and document scroll width were all 375 px with `scrollX=0`. Only the equivalent result table's intentional 780 px minimum width exceeded the viewport, and it remained inside its horizontal-overflow container.
- All 49 visible application-owned interactive targets measured at least 44 × 44 px. The vertical mobile River `role="group"` was visible at 301 px wide; the desktop SVG was absent or hidden.
- The final mobile axe scan for WCAG 2.0, 2.1, and 2.2 A/AA recorded 29 passes, 0 violations, and 0 incomplete checks. The deployed page emitted 0 console warnings or errors.
- With `prefers-reduced-motion` matched, the deployed page had zero animated elements.
- Visible production focus rings were verified on representative controls: logo (152 × 44), menu (44 × 44), export (151 × 44), mobile flow (301 × 317), and summary disclosure (267 × 48). The local keyboard River test remains green; natural sequential Tab order was not separately claimed.
- After 20m19.9s without public API activity, the live **Start with synthetic data** action triggered the real cold path. By +5.3 seconds, the DOM exposed `role="status"` with **The backend is waking up. This can take several minutes...**, and both start actions were disabled. The browser reached `/scenarios` with a real workspace by +266.512 seconds, so the end-to-end result is recorded as an observed 266.5-second upper bound rather than an exact response time.
- Reduced-motion behavior and the Reconciliation River's equivalent table are also covered in the frontend tests.

## Findings

- No local or deployed P0, P1, or P2 visual or accessibility finding remains in the checked states.
- The free service's observed 266.5-second cold-wake upper bound is a disclosed platform limitation, not a design-QA failure: the UI announced the state and prevented duplicate starts.

## Comparison history

### Iteration 1

- **P2 — Mobile header action wrapped and weakened the header rhythm.**
- Fix: use the concise visible label **Try demo** below the small breakpoint while preserving the full action meaning for desktop and assistive technology.
- Post-fix evidence: `frontend/.design-qa/final-local-mobile-375x812.png` shows a single-line action, aligned wordmark, intact 44 px target, and no horizontal overflow.

### Iteration 2

- Combined full-view and focused comparisons found no new actionable P0, P1, or P2 difference. No visual code change was made in response, so this is the required no-fix comparison pass.

### Iteration 3

- The final reference review compared both supplied references with the live desktop landing/workbench and live 375 px landing/workbench captures.
- Review found no P0, P1, or P2 mismatch. The implementation carries the pale shell, dense bordered cards, black anchor surface, condensed type, semantic contrast, and disciplined mobile stacking without copying reference assets or layouts.
- Apparent landing clipping in an earlier image was a capture artifact, not layout overflow. A 390 px DOM check measured document `scrollWidth` equal to the viewport, with the full header and hero visible. The mobile run DOM exposes the vertical **Reconciliation flows in mobile layout** region while the desktop SVG is hidden.

### Iteration 4

- The initial deployed mobile axe scan returned one serious incomplete check because the River's `aria-label` was attached to a roleless container. It was not waived.
- Fix: give the mobile River an explicit group role, retain its accessible name, and raise remaining application-owned mobile controls to the 44 × 44 px target.
- Post-deployment evidence: 29 axe passes, 0 violations, 0 incomplete checks; all 49 visible application-owned targets met the size threshold; overflow, reduced-motion, console, River-layout, and representative focus-ring checks passed.

## Final acceptance checklist

- [x] Deploy the stable API and frontend.
- [x] Run the real desktop and mobile browser journey with no mocked API results.
- [x] Capture production landing and API-backed workbench screens at desktop and mobile widths.
- [x] Record desktop/mobile overflow, console, responsive River, and initial axe evidence.
- [x] Complete the final review against both supplied references and verify no P0–P2 visual mismatch.
- [x] Capture the genuine idle-service cold-start message/state and the transition to a real workspace.
- [x] Redeploy the mobile River accessibility correction and repeat the affected checks.
