// Runs in the hidden discovery WebView after the page loads (docs/UNIWRAP_SPEC.md R9.3). Ported from the
// retired scripts/discover/collect.js: metadata, links, the background color along the top edge,
// the resources the page loaded, and its inline scripts (scanned for API use by the app).
(() => {
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
    const c = background(document.elementFromPoint(Math.floor(innerWidth * fx), 3));
    samples[c] = (samples[c] || 0) + 1;
  }
  const topColor = Object.entries(samples).sort((a, b) => b[1] - a[1])[0][0];

  // Brand colors (docs/UNIWRAP_SPEC.md R9.2): CSS variables named for the brand (core ranks them), then
  // the colors the page paints its visible buttons with, by area. Invalid values are dropped, not read
  // as black.
  const parsed = (value) => {
    ctx.fillStyle = '#010203';
    ctx.fillStyle = value;
    return ctx.fillStyle === '#010203' && !/^#010203$/i.test(value.trim()) ? null : toHex(value);
  };
  const brandColors = [];
  const rootStyle = getComputedStyle(document.documentElement);
  for (let i = 0; i < rootStyle.length && brandColors.length < 80; i++) {
    const name = rootStyle[i];
    if (!name.startsWith('--') || !/brand|primary|accent/i.test(name)) continue;
    if (/text|fg|foreground|background|bg|border|shadow|hover|active|focus|pressed|disabled|muted|visited|icon|label/i.test(name)) continue;
    const hex = parsed(rootStyle.getPropertyValue(name).trim());
    if (hex) brandColors.push({ color: hex, source: 'CSS variable ' + name });
  }
  const areas = {};
  for (const el of document.querySelectorAll('button, [role="button"], input[type="submit"], a[class*="btn"], a[class*="button"]')) {
    const r = el.getBoundingClientRect();
    if (r.width < 24 || r.height < 16 || r.bottom < 0 || r.top > innerHeight) continue;
    const hex = toHex(getComputedStyle(el).backgroundColor);
    if (hex) areas[hex] = (areas[hex] || 0) + r.width * r.height;
  }
  for (const [color] of Object.entries(areas).sort((a, b) => b[1] - a[1]).slice(0, 3)) {
    brandColors.push({ color, source: 'button background' });
  }
  const types = { xmlhttprequest: 'xhr', fetch: 'fetch', script: 'script', img: 'image', css: 'stylesheet',
    link: 'stylesheet', iframe: 'document', beacon: 'ping', other: 'other' };
  let inline = '';
  for (const s of document.querySelectorAll('script:not([src])')) {
    if (inline.length > 2000000) break;
    inline += s.textContent + '\n';
  }
  return JSON.stringify({
    url: location.href,
    page: {
      title: document.title,
      metas: [...document.querySelectorAll('meta')].map((m) => ({
        name: (m.getAttribute('name') || m.getAttribute('property') || '').toLowerCase(),
        content: m.getAttribute('content') || '',
        media: m.getAttribute('media') || '',
      })).filter((m) => m.name),
      links: [...document.querySelectorAll('link[rel]')].map((l) => ({
        rel: l.getAttribute('rel').toLowerCase(),
        href: l.href,
        sizes: l.getAttribute('sizes') || '',
        color: l.getAttribute('color') || '',
      })),
      themeColors: [...document.querySelectorAll('meta[name="theme-color"]')].map((m) => ({
        color: toHex(m.getAttribute('content') || '') || '',
        media: m.getAttribute('media') || '',
      })),
      topColor,
      brandColors,
    },
    text: (document.body ? document.body.innerText : '').slice(0, 3000),
    resources: performance.getEntriesByType('resource').map((r) => ({
      url: r.name, type: types[r.initiatorType] || r.initiatorType,
    })),
    inlineScripts: inline,
  });
})();
