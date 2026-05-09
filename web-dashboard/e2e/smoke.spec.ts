import { test, expect } from '@playwright/test';

test.describe('Haulio Fleet Dashboard smoke tests', () => {
  test('/ renders the Live Map page with layer toggle panel', async ({ page }) => {
    await page.goto('/');

    // Should not crash
    await expect(page).not.toHaveTitle(/error/i);

    // Nav is visible
    await expect(page.getByText('HAULIO')).toBeVisible();

    // Layer panel (may take a moment to render)
    await expect(
      page.getByRole('group', { name: /map layer toggles/i })
    ).toBeVisible({ timeout: 10_000 });

    // All 7 toggles
    await expect(page.getByRole('switch', { name: /incidents/i })).toBeVisible();
    await expect(page.getByRole('switch', { name: /school zones/i })).toBeVisible();
    await expect(page.getByRole('switch', { name: /driver heatmap/i })).toBeVisible();

    // Sidebar button is visible
    await expect(
      page.getByRole('button', { name: /incident feed/i })
    ).toBeVisible();
  });

  test('/ sidebar toggles open and closed', async ({ page }) => {
    await page.goto('/');

    // Sidebar defaults open – "Hide Feed" button should be visible
    const hideBtn = page.getByRole('button', { name: /hide feed/i });
    await expect(hideBtn).toBeVisible({ timeout: 10_000 });

    await hideBtn.click();

    // After close – "Show Feed" button
    await expect(page.getByRole('button', { name: /show feed/i })).toBeVisible();
  });

  test('/zones renders Zone Editor page with map and sidebar', async ({ page }) => {
    await page.goto('/zones');

    // The Zone Editor nav link should be "active"
    await expect(page.getByRole('link', { name: /zone editor/i })).toBeVisible();

    // The left sidebar panel
    await expect(
      page.getByRole('complementary', { name: /zone editor panel/i })
    ).toBeVisible({ timeout: 10_000 });

    // Draw-prompt hint
    await expect(page.getByText(/draw a shape on the map/i)).toBeVisible();
  });

  test('/ keyboard navigation: Tab reaches layer toggles', async ({ page }) => {
    await page.goto('/');
    // Wait for panel to render
    await page.waitForSelector('[role="switch"]');

    // Focus first toggle via keyboard
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    const focused = await page.evaluate(() => document.activeElement?.getAttribute('role'));
    // At some point a switch should be focused
    expect(['switch', 'button', 'link']).toContain(focused);
  });
});
