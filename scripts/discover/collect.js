#!/usr/bin/env node
// Collects evidence about a website for scripts/discover_site.py: metadata, web manifest, icons,
// network hosts, API usage in scripts, header colors (light/dark), and how the site reacts to an
// Android WebView user agent. Needs the `playwright` package and its Chromium build.
//
//   node scripts/discover/collect.js https://example.com > evidence.json

const { chromium } = require('playwright');

const NAV_TIMEOUT = 45000;
const IDLE_TIMEOUT = 15000;
const SCRIPT_BYTES_LIMIT = 20 * 1024 * 1024;
const WEBVIEW_UA =
  'Mozilla/5.0 (Linux; Android 17; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36; wv';
const SIGNALS = {
  getUserMedia: /getUserMedia\s*\(/,
  notificationPermission: /Notification\.requestPermission|Notification\.permission/,
  showNotification: /showNotification\s*\(/,
  pushManager: /pushManager\.subscribe/,
  serviceWorker: /serviceWorker\.register\s*\(/,
  webRtc: /RTCPeerConnection/,
};

// Runs in the page: metadata, links and the background color behind the top edge of the viewport.
function pageInfo() {
  // Computed colors may be oklab()/color() etc.; painting one pixel yields plain sRGB.
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 1;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  const toHex = (color) => {
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = '#000';
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data;
    return a < 128 ? null : '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase();
  };
  const background = (el) => {
    for (; el; el = el.parentElement) {
      const hex = toHex(getComputedStyle(el).backgroundColor);
      if (hex) return hex;
    }
    return toHex(getComputedStyle(document.body || document.documentElement).backgroundColor) || '#FFFFFF';
  };
  const samples = {};
  for (const fx of [0.15, 0.3, 0.5, 0.7, 0.85]) {
    const el = document.elementFromPoint(Math.floor(innerWidth * fx), 3);
    const c = background(el);
    samples[c] = (samples[c] || 0) + 1;
  }
  const topColor = Object.entries(samples).sort((a, b) => b[1] - a[1])[0][0];
  return {
    title: document.title,
    lang: document.documentElement.lang || '',
    metas: [...document.querySelectorAll('meta')].map((m) => ({
      name: (m.getAttribute('name') || m.getAttribute('property') || '').toLowerCase(),
      content: m.getAttribute('content') || '',
      media: m.getAttribute('media') || '',
    })).filter((m) => m.name),
    links: [...document.querySelectorAll('link[rel]')].map((l) => ({
      rel: l.getAttribute('rel').toLowerCase(),
      href: l.href,
      sizes: l.getAttribute('sizes') || '',
      type: l.getAttribute('type') || '',
      color: l.getAttribute('color') || '',
    })),
    topColor,
    bodyColor: background(document.body),
    themeColors: [...document.querySelectorAll('meta[name="theme-color"]')].map((m) => ({
      color: toHex(m.getAttribute('content') || '') || '',
      media: m.getAttribute('media') || '',
    })),
  };
}

async function visit(browser, url, options) {
  const context = await browser.newContext(options);
  const page = await context.newPage();
  const requests = [];
  const signals = Object.fromEntries(Object.keys(SIGNALS).map((k) => [k, []]));
  const navigations = [];
  let scriptBytes = 0;

  page.on('request', (r) => {
    requests.push({ url: r.url(), type: r.resourceType() });
    if (r.isNavigationRequest() && r.frame() === page.mainFrame()) navigations.push(r.url());
  });
  page.on('websocket', (ws) => requests.push({ url: ws.url(), type: 'websocket' }));
  page.on('response', async (response) => {
    if (response.request().resourceType() !== 'script' || scriptBytes > SCRIPT_BYTES_LIMIT) return;
    try {
      const text = await response.text();
      scriptBytes += text.length;
      for (const [name, pattern] of Object.entries(SIGNALS)) {
        if (pattern.test(text) && signals[name].length < 5) signals[name].push(response.url());
      }
    } catch { /* body unavailable (redirects, aborted requests) */ }
  });

  const result = { requestedUrl: url, requests, signals, navigations };
  try {
    const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: NAV_TIMEOUT });
    result.status = response ? response.status() : null;
    result.headers = response ? await response.allHeaders() : {};
    const chain = [];
    for (let r = response && response.request(); r; r = r.redirectedFrom()) chain.unshift(r.url());
    result.redirectChain = chain;
    await page.waitForLoadState('networkidle', { timeout: IDLE_TIMEOUT }).catch(() => {});
    result.finalUrl = page.url();
    result.page = await page.evaluate(pageInfo);
    result.text = (await page.evaluate(() => document.body ? document.body.innerText : '')).slice(0, 3000);
  } catch (error) {
    result.error = error.message.split('\n')[0];
    result.finalUrl = page.url();
  }

  const manifestLink = result.page && result.page.links.find((l) => l.rel.split(/\s+/).includes('manifest'));
  if (manifestLink) {
    result.manifestUrl = manifestLink.href;
    try {
      const response = await context.request.get(manifestLink.href, { timeout: 20000 });
      result.manifest = JSON.parse(await response.text());
    } catch (error) {
      result.manifestError = error.message.split('\n')[0];
    }
  }
  await context.close();
  return result;
}

(async () => {
  const url = process.argv[2];
  if (!url) {
    console.error('usage: collect.js URL');
    process.exit(2);
  }
  const browser = await chromium.launch();
  try {
    const desktop = await visit(browser, url, { colorScheme: 'light', viewport: { width: 1280, height: 800 } });
    const target = desktop.finalUrl && desktop.finalUrl.startsWith('http') ? desktop.finalUrl : url;
    const dark = await visit(browser, target, { colorScheme: 'dark', viewport: { width: 1280, height: 800 } });
    const webview = await visit(browser, url, {
      userAgent: WEBVIEW_UA,
      viewport: { width: 412, height: 915 },
      isMobile: true,
      hasTouch: true,
    });
    const slim = (v) => ({
      status: v.status, error: v.error, finalUrl: v.finalUrl, title: v.page && v.page.title,
      topColor: v.page && v.page.topColor, metas: v.page && v.page.metas, text: v.text, navigations: v.navigations,
    });
    process.stdout.write(JSON.stringify({
      collectedAt: new Date().toISOString(),
      desktop,
      dark: slim(dark),
      webview: slim(webview),
    }, null, 1));
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
