import { test, expect } from '../fixtures/playwright-base';

const API_BASE_URL = 'http://localhost:8080'\;

test('inspect menu page', async ({ base }) => {
  const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
  });
  const loginBody = await loginResp.json();
  const token = loginBody.data.accessToken;
  const refreshToken = loginBody.data.refreshToken;
  await base.page.goto('http://localhost:5666', { waitUntil: 'domcontentloaded' });
  await base.page.waitForTimeout(500);
  await base.page.evaluate(
    ({ accessToken, refresh }) => {
      const namespace = 'vben-web-antd-5.7.0-dev';
      localStorage.setItem(`${namespace}-core-access`, JSON.stringify({ accessToken, refreshToken: refresh, accessCodes: [] }));
    },
    { accessToken: token, refresh: refreshToken },
  );
  await base.page.reload({ waitUntil: 'networkidle' });
  await base.navigateTo('/system/menu');
  await base.page.locator('.vxe-table--body').first().waitFor({ state: 'visible', timeout: 15000 });
  
  // Dump tree-related classes
  const treeClasses = await base.page.evaluate(() => {
    const elements = document.querySelectorAll('[class*="tree"]');
    return Array.from(elements).map(el => ({ tag: el.tagName, class: el.className, text: el.textContent?.substring(0, 50) })).slice(0, 20);
  });
  console.log('Tree elements:', JSON.stringify(treeClasses, null, 2));
  
  // Check first row structure
  const firstRowHtml = await base.page.locator('.vxe-table--body .vxe-body--row').first().innerHTML();
  console.log('First row HTML (truncated):', firstRowHtml.substring(0, 1000));
  
  // Check for action buttons
  const buttons = await base.page.locator('.vxe-table--body .vxe-body--row').first().locator('button, a').allTextContents();
  console.log('Buttons in first row:', buttons);
  
  // Check tag colors
  const tags = await base.page.evaluate(() => {
    const tagEls = document.querySelectorAll('.vxe-table--body .ant-tag');
    return Array.from(tagEls).slice(0, 5).map(el => ({ text: el.textContent, class: el.className, style: el.getAttribute('style') }));
  });
  console.log('Tags:', JSON.stringify(tags, null, 2));
});
