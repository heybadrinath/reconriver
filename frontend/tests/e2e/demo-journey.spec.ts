import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test.describe("anonymous reconciliation journey", () => {
  test.skip(
    !process.env.E2E_API_URL,
    "Set E2E_API_URL to a running Spring API; this test never stubs job progress or data.",
  );

  test("loads mixed exceptions, completes a real job, resolves and reopens", async ({
    page,
  }) => {
    test.setTimeout(420_000);
    await page.goto("/");
    expect(await page.evaluate(() => sessionStorage.length)).toBe(0);

    await page
      .getByRole("button", { name: /Start with synthetic data/i })
      .click();
    await expect(page).toHaveURL(/\/scenarios$/);

    await page.getByRole("button", { name: /Mixed exceptions/i }).click();
    const scenarioCard = page.getByRole("button", {
      name: /Mixed exceptions/i,
    });
    const expectedExceptions = Number(
      (await scenarioCard.textContent())
        ?.match(/Exceptions\s*(\d[\d,]*)/i)?.[1]
        ?.replaceAll(",", "") ?? "0",
    );
    await page.getByRole("button", { name: "Load sample data" }).click();
    await expect(page).toHaveURL(/\/imports\?loaded=1$/);
    await expect(page.getByText("Scenario files ready")).toBeVisible({
      timeout: 180_000,
    });

    const continueToRuns = page.getByRole("link", {
      name: /Continue to runs/i,
    });
    await expect(continueToRuns).toBeVisible();
    await continueToRuns.click();
    await page.getByRole("button", { name: "Run reconciliation" }).click();
    await expect(page).toHaveURL(/\/runs\/[a-zA-Z0-9-]+$/);
    await expect(page.getByText(/^completed$/i)).toBeVisible({
      timeout: 150_000,
    });

    const exceptionMetric = page
      .getByText("Exceptions", { exact: true })
      .locator("..", { hasText: String(expectedExceptions) });
    await expect(exceptionMetric).toBeVisible();
    await page.getByRole("link", { name: /Open workbench/i }).click();

    await expect(
      page.getByRole("heading", { name: /Exception workbench/i }),
    ).toBeVisible();
    await page
      .getByLabel("Operator note")
      .fill(
        "Verified the synthetic source evidence and accepted this controlled discrepancy.",
      );
    await page.getByRole("button", { name: "Resolve exception" }).click();
    await expect(page.getByText(/^resolved$/i)).toBeVisible();
    await page.getByRole("tab", { name: "Audit timeline" }).click();
    await expect(
      page.getByText(/Resolved|Exception resolved/i).first(),
    ).toBeVisible();

    await page.getByRole("tab", { name: "Decision" }).click();
    await expect(
      page.getByRole("button", { name: "Reopen exception" }),
    ).toBeVisible();
    await page
      .getByLabel("Operator note")
      .fill(
        "Reopened to verify that the controlled audit transition remains reversible.",
      );
    await page.getByRole("button", { name: "Reopen exception" }).click();
    await expect(page.getByText(/^reopened$/i).first()).toBeVisible();
    await page.getByRole("tab", { name: "Audit timeline" }).click();
    await expect(
      page.getByText(/Reopened|Exception reopened/i).first(),
    ).toBeVisible();

    await page.goBack();
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Export CSV" }).click();
    expect((await download).suggestedFilename()).toMatch(
      /reconciliation-.+\.csv/,
    );

    const accessibility = await new AxeBuilder({ page }).analyze();
    expect(accessibility.violations).toEqual([]);
  });
});
