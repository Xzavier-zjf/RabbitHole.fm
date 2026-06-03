import { expect, test } from '@playwright/test'

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const PAGE_ERRORS = Symbol('pageErrors')

test.beforeEach(async ({ page }) => {
  const errors = []
  page[PAGE_ERRORS] = errors
  await page.addInitScript(() => {
    window.localStorage.removeItem('token')
  })
  await page.route('**/api/music/status', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        apiAlive: true,
        sources: [
          { key: 'netease', label: '网易云', alive: true },
          { key: 'free-api', label: 'Free API', enabled: true, alive: true },
        ],
      }),
    })
  })
  await page.route('**/api/music/search**', async (route) => {
    const url = new URL(route.request().url())
    const keyword = url.searchParams.get('keywords') || 'Mock'
    const source = url.searchParams.get('source') || 'all'
    const isFreeApi = source === 'free-api'
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: isFreeApi ? 7001 : 1001,
          name: isFreeApi ? 'Free API Mock Song' : `${keyword} Mock Song`,
          artists: ['RabbitHole Test'],
          source: isFreeApi ? 'free-api' : 'netease',
          sourceLabel: isFreeApi ? 'Free API' : '网易云',
          sourceSongId: isFreeApi ? 'free-7001' : '1001',
          songUrl: isFreeApi ? 'https://example.com/free-api-mock.mp3' : '',
        },
      ]),
    })
  })
  await page.route('**/api/user/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/favorites') || url.pathname.includes('/history')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
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

test('persists selected music source and sends it with search', async ({ page }) => {
  const searchRequests = []
  await page.route('**/api/music/search**', async (route) => {
    const url = new URL(route.request().url())
    searchRequests.push(url.searchParams.get('source'))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 7001,
          name: 'Free API Mock Song',
          artists: ['RabbitHole Test'],
          source: 'free-api',
          sourceLabel: 'Free API',
          sourceSongId: 'free-7001',
          songUrl: 'https://example.com/free-api-mock.mp3',
        },
      ]),
    })
  })

  await page.goto(BASE_URL)
  await page.getByRole('radio', { name: 'Free API' }).click()
  await page.getByPlaceholder('搜索歌曲、歌手或专辑').first().fill('mock')
  await page.getByPlaceholder('搜索歌曲、歌手或专辑').first().press('Enter')
  await expect(page.getByRole('option').first()).toContainText('Free API Mock Song')
  expect(searchRequests).toContain('free-api')

  await page.reload()
  await expect(page.getByRole('radio', { name: 'Free API' }).first()).toHaveAttribute('aria-checked', 'true')
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

test('skips unavailable request DJ intro and continues to the song', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('rabbithole:last-channel-id', '32953014')
    window.sessionStorage.removeItem('rabbithole:playback-context')
    Object.defineProperty(HTMLMediaElement.prototype, 'play', {
      configurable: true,
      value() {
        this.dispatchEvent(new Event('play'))
        return Promise.resolve()
      },
    })
  })

  await page.route('**/api/radio/channel/32953014', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          type: 'dj',
          name: '点歌口播（可跳过）',
          djUrl: '/api/radio/dj?nextId=1001&requester=小周&message=晚自习加油',
          requester: '小周',
          message: '晚自习加油',
          djSubtitle: '小周点给大家的歌，晚自习加油。',
          optional: true,
        },
        {
          type: 'song',
          songId: 1001,
          name: 'Mock Continuity Song',
          artists: ['RabbitHole Test'],
          source: 'netease',
          sourceSongId: '1001',
        },
      ]),
    })
  })
  await page.route('**/api/radio/dj**', async (route) => {
    await route.fulfill({
      status: 204,
      headers: { 'X-DJ-Skipped': 'tts-unavailable' },
    })
  })
  await page.route('**/api/radio/song/1001', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        url: 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAESsAACJWAAACABAAZGF0YQAAAAA=',
        lyric: null,
      }),
    })
  })
  await page.route('**/api/user/history', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
  })

  await page.goto(`${BASE_URL}/?channelId=32953014`)

  await expect(page.getByRole('heading', { name: 'Mock Continuity Song' })).toBeVisible({ timeout: 3000 })
  await expect(page.getByText('口播暂不可用，已继续播放下一首音乐。')).toBeVisible()
})
