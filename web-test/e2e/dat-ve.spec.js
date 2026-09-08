import { test, expect } from "@playwright/test";

/**
 * Mot kich ban duy nhat, di het luong dat ve.
 *
 * Khong test tung component: voi mot SPA khong framework, chi phi dung ha tang test
 * component lon hon gia tri. Thu dang so nhat khi viet lai giao dien la lam hong luong cu,
 * va day la thu bat duoc dieu do.
 */
test("dat ve tu dau den cuoi", async ({ page }) => {
    await page.goto("/#/dang-nhap");
    await page.getByRole("button", { name: "Dung tai khoan demo" }).click();

    await expect(page.getByRole("heading", { name: "Dang chieu" })).toBeVisible();
    await page.locator('a[href^="#/phim/"]').first().click();

    await expect(page.getByRole("heading", { name: "Suat chieu" })).toBeVisible();
    await page.locator('a[href*="/ghe"]').first().click();

    // Hai ghe trong dau tien
    const gheTrong = page.locator("button.ghe-trong");
    await expect(gheTrong.first()).toBeVisible();
    await gheTrong.nth(0).click();
    await gheTrong.nth(0).click();

    await page.getByRole("button", { name: /^Giu \d+ ghe$/ }).click();

    // Dong ho dem nguoc phai CHAY, khong chi dung dinh dang. Doc hai lan cach nhau hon mot
    // giay va doi gia tri phai khac di — mot dong ho dung yen van khop /^\d\d:\d\d$/.
    await expect(page.locator("#conlai")).toHaveText(/^\d\d:\d\d$/);
    const lanDau = await page.locator("#conlai").textContent();
    await page.waitForTimeout(1500);
    await expect(page.locator("#conlai")).not.toHaveText(lanDau);

    // Thanh toan can HAI buoc. "Thanh toan" chi tao giao dich o trang thai cho; webhook chi
    // den khi cong gia lap duoc bam. Chi lam buoc dau roi doi la doi mai mai.
    await page.getByRole("button", { name: "Thanh toan" }).click();
    await page.getByRole("button", { name: "Gia lap thanh cong" }).click();

    // Webhook bat dong bo, va man hinh tu poll roi tu chuyen sang trang ve.
    // Ma ve co dang CB + 8 ky tu, KHONG co gach ngang (vi du CBSUMYP9JD).
    await expect(page.getByText(/^CB[A-Z0-9]{8}$/)).toBeVisible({ timeout: 30_000 });

    await page.goto("/#/ve-cua-toi");
    await expect(page.getByText("Da xac nhan").first()).toBeVisible();
});
