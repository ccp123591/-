import { test, expect } from '@playwright/test';

const base = 'http://127.0.0.1:25173';

test.use({ viewport: { width: 1440, height: 1000 } });

test('全站演示模式 Docker 冒烟', async ({ page }) => {
  const apiRequests = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url());
  });
  await page.addInitScript(() => localStorage.clear());

  const pages = [
    ['/train', '开始训练'],
    ['/records', '训练记录'],
    ['/plans', '7 天居家焕活'],
    ['/leaderboard', '健身达人'],
    ['/feed', '晨跑小林'],
    ['/challenges', '一周 300 次深蹲'],
    ['/emotion', '近 7 天情感分布'],
    ['/room', '识别结果 model=frontend-demo'],
    ['/profile', '演示账户'],
    ['/admin', '管理后台'],
    ['/settings', '重置演示数据']
  ];

  for (const [path, marker] of pages) {
    await page.goto(`${base}${path}`);
    await expect(page.getByText('全站演示模式', { exact: true })).toBeVisible();
    await expect(page.locator('.app-main').getByText(marker, { exact: true })).toBeVisible();
  }

  await page.goto(`${base}/feed`);
  await page.getByPlaceholder('分享一下今天的训练…').fill('演示模式发布测试：今天完成了拉伸。');
  await page.getByRole('button', { name: '发布' }).click();
  await expect(page.getByText('演示模式发布测试：今天完成了拉伸。', { exact: true })).toBeVisible();

  await page.goto(`${base}/plans`);
  const planCard = page.locator('.plan-card').filter({ hasText: '核心稳定进阶' });
  await planCard.getByRole('button', { name: '采用' }).click();
  await expect(page.locator('.mine-title').getByText('核心稳定进阶', { exact: true })).toBeVisible();

  await page.goto(`${base}/challenges`);
  await page.locator('.card').filter({ hasText: '核心耐力挑战' }).click();
  await page.getByRole('button', { name: '报名挑战' }).click();
  await expect(page.getByRole('button', { name: '已报名' })).toBeVisible();

  await page.goto(`${base}/emotion`);
  const mood = '演示模式让我很开心，今天顺利完成训练。';
  await page.getByPlaceholder('例如：今天完成了 15 个深蹲，太棒了，状态非常好！').fill(mood);
  await page.getByRole('button', { name: '分析并保存' }).click();
  await expect(page.getByText(mood, { exact: true })).toBeVisible();

  await page.goto(`${base}/train`);
  await page.getByTestId('demo-fall-alert').click();
  await expect(page.getByText('演示模式：检测到疑似跌倒，请确认是否安全', { exact: true })).toBeVisible();
  await expect(page.getByText('10s 后通知家属', { exact: true })).toBeVisible();
  await page.waitForTimeout(400);
  await page.screenshot({ path: '../temp/demo-fall-alert.png', fullPage: true });
  await page.getByRole('button', { name: '我没事，继续' }).click();

  await page.goto(`${base}/train`);
  const composer = page.getByPlaceholder('跟我说说今天怎么样…');
  if (!await composer.isVisible()) {
    await page.getByRole('button', { name: '陪伴教练' }).click();
  }
  await expect(composer).toBeVisible();
  const before = await page.locator('.msg.assistant').count();
  await composer.fill('今天练什么？');
  const sendButton = page.getByRole('button', { name: '发送' });
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect(page.locator('.msg.assistant')).toHaveCount(before + 1);
  await expect(page.locator('.msg.assistant').last()).toContainText('深蹲');
  await page.screenshot({ path: '../temp/demo-mode-train.png', fullPage: true });

  await page.goto(`${base}/settings`);
  const demoToggle = page.locator('label.toggle-item').filter({ hasText: '全站演示模式' }).locator('input[type="checkbox"]');
  await expect(demoToggle).toBeChecked();
  await demoToggle.uncheck();
  await expect(page.getByText('全站演示模式', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '登录' })).toBeVisible();
  await demoToggle.check();
  await expect(page.getByText('全站演示模式', { exact: true })).toBeVisible();
  await page.screenshot({ path: '../temp/demo-mode-settings.png', fullPage: true });

  expect(apiRequests, `演示模式不应发出真实 API 请求：${apiRequests.join(', ')}`).toEqual([]);
});
