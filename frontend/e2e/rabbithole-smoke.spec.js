import { expect, test } from '@playwright/test'

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const PAGE_ERRORS = Symbol('pageErrors')

test.beforeEach(async ({ page }) => {
  const errors = []
  page[PAGE_ERRORS] = errors
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') {
      const text = message.text()
      if (!/Failed to load resource: the server responded with a status of (401|403)/.test(text)) {
        errors.push(text)
      }
    }
  })
})

test.afterEach(async ({ page }) => {
  const errors = page[PAGE_ERRORS] || []
  expect(errors, errors.join('\n')).toEqual([])
})

test('renders the main music client and searches songs', async ({ page }) => {
  await page.goto(BASE_URL)
  await expect(page).toHaveTitle(/RabbitHole\.fm/)
  await expect(page.getByText('RabbitHole.fm').first()).toBeVisible()

  const searchInput = page.getByPlaceholder('搜索歌曲、歌手或专辑').first()
  await searchInput.fill('晴天')
  const searchResponse = page.waitForResponse((response) =>
    response.url().includes('/api/music/search') && response.status() === 200,
  )
  await searchInput.press('Enter')
  await searchResponse
  await expect(page.getByText('Search Results')).toBeVisible({ timeout: 15000 })
  await expect(page.getByRole('option').first()).toContainText(/晴天/)

  const themeButton = page.locator('button[aria-label*="主题"], button[title*="主题"]').first()
  await themeButton.click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', /light|dark/)
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', /light|dark/)
})

test('renders request, explore, playlists, history, favorites, and login views', async ({ page }) => {
  await page.goto(`${BASE_URL}/request?channelId=32953014`)
  await expect(page.getByRole('heading', { name: '点歌留言' })).toBeVisible()
  await page.getByPlaceholder('搜索歌曲、歌手或专辑').fill('晴天')
  await page.getByRole('button', { name: /搜索/ }).click()
  await expect(page.getByText(/晴天/).first()).toBeVisible({ timeout: 15000 })

  await page.goto(`${BASE_URL}/explore`)
  await expect(page.getByRole('heading', { name: '探索' })).toBeVisible()

  await page.goto(`${BASE_URL}/playlists`)
  await expect(page.getByRole('heading', { level: 1, name: '我的歌单' })).toBeVisible()

  await page.goto(`${BASE_URL}/history`)
  await expect(page.getByText(/历史|欢迎回来/).first()).toBeVisible()

  await page.goto(`${BASE_URL}/favorites`)
  await expect(page.getByText(/收藏|欢迎回来/).first()).toBeVisible()

  await page.goto(`${BASE_URL}/login`)
  await expect(page.getByLabel('登录注册切换').getByRole('button', { name: '登录' })).toBeVisible()
  await page.getByRole('button', { name: '注册' }).click()
  await expect(page.getByRole('button', { name: '注册并登录' })).toBeVisible()
})

test('keeps mobile layout usable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(BASE_URL)
  await expect(page.getByRole('main')).toContainText('RabbitHole.fm')
  await expect(page.getByPlaceholder('搜索歌曲、歌手或专辑').first()).toBeVisible()
  await expect(page.locator('.player-bar, .mobile-player, .transport-deck').first()).toBeVisible()
})
