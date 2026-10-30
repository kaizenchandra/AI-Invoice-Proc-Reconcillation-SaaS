import { test, expect, type APIRequestContext } from "@playwright/test";
import path from "node:path";
import AxeBuilder from "@axe-core/playwright";
test.beforeEach(async ({ request }) => {
  await expect
    .poll(
      async () => {
        try {
          return (await request.get("/api/v1/csrf")).status();
        } catch {
          return 0;
        }
      },
      { timeout: 30000 },
    )
    .toBe(200);
});
async function command(
  request: APIRequestContext,
  url: string,
  data: unknown,
  tenant?: string,
) {
  const token = await (await request.get("/api/v1/csrf")).json();
  const result = await request.post("/api/v1" + url, {
    headers: {
      [token.header]: token.token,
      ...(tenant ? { "X-Organization-ID": tenant } : {}),
    },
    data,
  });
  expect(result.ok(), await result.text()).toBeTruthy();
  return result.status() === 204 ? null : result.json();
}
test("onboarding, scanned fixture, correction, PO reconciliation, approval, export and audit", async ({
  page,
  browser,
}) => {
  const suffix = Date.now(),
    email = `processor-${suffix}@example.test`,
    password = "Synthetic-test-passphrase-2026";
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page.getByLabel("Full name").fill("Ananya Test");
  await page.getByLabel("Work email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(page.getByText("Let’s set up your workspace.")).toBeVisible();
  await page
    .getByLabel("Legal business name")
    .fill("Kaizen Manufacturing — Synthetic QA");
  await page.getByLabel("GSTIN (if registered)").fill("27AAPFU0939F1ZV");
  await page.getByLabel("State code").fill("27");
  await page.getByLabel("Financial year", { exact: true }).fill("2026-2027");
  await page
    .getByLabel("Registered address")
    .fill("Pune, Maharashtra (synthetic test organization)");
  await page
    .getByRole("button", { name: "Create workspace", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "A clear view of what’s next." }),
  ).toBeVisible();
  const orgs = await (await page.request.get("/api/v1/organizations")).json(),
    tenant = orgs[0].id;
  const supplier = await command(
    page.request,
    "/suppliers",
    {
      name: "Pune Components",
      gstin: "27AAPFU0939F1ZV",
      pan: "AAPFU0939F",
      state: "27",
      address: "Pune",
      contact: "",
      version: 0,
    },
    tenant,
  );
  await command(
    page.request,
    "/purchase-orders",
    {
      supplierId: supplier.id,
      number: "PO-2026-001",
      date: "2026-10-01",
      currency: "INR",
      lines: [
        {
          code: "STEEL-01",
          description: "Steel brackets",
          quantity: "10",
          price: "100",
          taxRate: "18",
        },
      ],
    },
    tenant,
  );
  const approverEmail = `approver-${suffix}@example.test`;
  const invitation = await command(
    page.request,
    `/organizations/${tenant}/invitations`,
    { email: approverEmail, role: "APPROVER" },
  );
  const second = await browser.newContext({ baseURL: "http://127.0.0.1:3107" });
  const approverPage = await second.newPage();
  await approverPage.goto("/");
  await command(second.request, "/register", {
    name: "Rohan Approver",
    email: approverEmail,
    password,
  });
  const token = await (await second.request.get("/api/v1/csrf")).json();
  expect(
    (
      await second.request.post("/api/v1/session", {
        headers: { [token.header]: token.token },
        form: { email: approverEmail, password },
      })
    ).ok(),
  ).toBeTruthy();
  await command(second.request, "/invitations/accept", {
    token: invitation.token,
  });
  const approverUser = await (await second.request.get("/api/v1/me")).json();
  await page.goto("/?view=upload");
  await page
    .locator("input[type=file]")
    .setInputFiles(path.resolve("../fixtures/synthetic-scanned-invoice.pdf"));
  await page
    .getByRole("button", { name: "Upload documents", exact: true })
    .click();
  await expect(
    page.getByText("Accepted", { exact: false }).first(),
  ).toBeVisible();
  let invoiceId = "";
  await expect
    .poll(
      async () => {
        const list = await (
          await page.request.get("/api/v1/invoices", {
            headers: { "X-Organization-ID": tenant },
          })
        ).json();
        invoiceId = list.items[0]?.id;
        return list.items[0]?.processing_state;
      },
      { timeout: 45000 },
    )
    .toBe("PROCESSED");
  await page.goto("/?view=invoice&id=" + invoiceId);
  await expect(
    page.getByRole("heading", { name: "TEST-2026-001", exact: true }),
  ).toBeVisible();
  await page.locator("#field-invoiceNumber").fill("TEST-2026-001-CORRECTED");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByText("All changes saved")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Submit for approval", exact: true }),
  ).toBeEnabled();
  await page.screenshot({
    path: "test-results/review-desktop.png",
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Submit for approval", exact: true })
    .click();
  await page.getByLabel("Assign approver").selectOption(approverUser.id);
  await page
    .getByRole("button", { name: "Confirm submit", exact: true })
    .click();
  await expect(
    page.getByText("Ready for approval", { exact: false }).first(),
  ).toBeVisible();
  await approverPage.goto("/?view=invoice&id=" + invoiceId);
  await approverPage
    .getByRole("button", { name: "Approve invoice", exact: true })
    .click();
  await approverPage
    .getByLabel("Decision note")
    .fill("Verified synthetic source and purchase order.");
  await approverPage
    .getByRole("button", { name: "Confirm approve", exact: true })
    .click();
  await expect(
    approverPage.getByText("Approved", { exact: true }).first(),
  ).toBeVisible();
  await page.goto("/?view=exports");
  await page
    .getByRole("checkbox", { name: "Select TEST-2026-001-CORRECTED" })
    .check();
  await page.getByRole("button", { name: "Preview export" }).click();
  const downloaded = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Export & download CSV", exact: true })
    .click();
  const artifact = await downloaded;
  expect(artifact.suggestedFilename()).toBe("kaizen-invoices.csv");
  await expect(page.getByText("Completed", { exact: true })).toBeVisible();
  await page.goto("/?view=activity");
  await expect(
    page.getByText("Export completed", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Invoice approve", { exact: true }),
  ).toBeVisible();
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Recent invoices", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".loading")).toHaveCount(0);
  const accessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(accessibility.violations).toEqual([]);
  await page.screenshot({
    path: "test-results/overview-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: "Open navigation" }),
  ).toBeVisible();
  await expect
    .poll(async () =>
      page
        .locator(".sidebar")
        .evaluate((el) => el.getBoundingClientRect().right),
    )
    .toBeLessThanOrEqual(0);
  await page.screenshot({
    path: "test-results/overview-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  const layout = await page.evaluate(() => ({
    width: innerWidth,
    scroll: document.documentElement.scrollWidth,
    overflow: [...document.querySelectorAll("*")]
      .filter((e) => e.getBoundingClientRect().right > innerWidth + 1)
      .map((e) => ({
        tag: e.tagName,
        classes: e.className,
        right: e.getBoundingClientRect().right,
        width: e.getBoundingClientRect().width,
      }))
      .slice(0, 30),
  }));
  expect(layout.scroll, JSON.stringify(layout)).toBeLessThanOrEqual(
    layout.width,
  );
  await second.close();
});
test("authentication keyboard and responsive states", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("Work email").fill("unknown@example.test");
  await page.getByLabel("Password", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "Sign in to workspace" }).click();
  await expect(page.locator(".error-box[role=alert]")).toContainText(
    "Email or password is incorrect",
  );
  await page.setViewportSize({ width: 768, height: 1024 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/signin-tablet.png",
    fullPage: true,
  });
});
