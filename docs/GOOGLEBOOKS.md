# Googlebooks: Shrimpl's primary devices

Shrimpl works on every Android form factor (R1.1), but its primary users are on Googlebooks: the
Android laptops Google launched on 2026-09-21 (preorders) and 2026-10-04 (US retail), running
Googlebook OS (codename Aluminium OS). They are not Chromebooks. Size windowing and resizing
decisions for these screens first, and check them against this file.

Researched 2026-11-04 from launch coverage and maker listings (sources at the end). Update this file
when a model is added or a figure is confirmed on a device.

## Released models

| Model | Panel | Pixels | Aspect | Density | Refresh | Touch / form |
|---|---|---|---|---|---|---|
| Acer Googlebook 14 | 14" OLED, 500 nits | 2880 × 1800 | 16:10 | 243 ppi | 120 Hz | Touch; the only convertible (tablet and tent modes) |
| ASUS Googlebook 14 | 14" OLED, 500 nits | 2880 × 1800 | 16:10 | 243 ppi | 120 Hz | Touch; clamshell |
| HP Googlebook 14 (model 14c-cf0xx) | 14" OLED, 500 nits SDR | 2880 × 1800 | 16:10 | 243 ppi | Up to 120 Hz | Touch; clamshell. Notebookcheck reports a 1920 × 1200 OLED option too (162 ppi) |
| Lenovo Googlebook 15 | 15.3" OLED | 2880 × 1800 | 16:10 | 222 ppi | 120 Hz | Touch; clamshell |
| Dell XPS Googlebook | 13.4" LCD (IPS), 500 nits | 2560 × 1600 | 16:10 | 225 ppi | Not stated | Touch; clamshell |

What the lineup has in common, for layout:

- Every panel is 16:10 and touch. Pointer, keyboard and touch all matter on every model (R1.1).
- Four of five are 2880 × 1800. The Dell is the smallest and the only one under 2.8K; the Lenovo has
  the same pixels on a larger panel, so the system will likely scale it less (more dp).
- Google mandates the platform specs (16 GB RAM or more, touch, up to 2.8K), not a density.

## In dp

Shrimpl lays out by window width in dp (two panes from 880 dp, docs/ADAPTIVE.md), so the system's
display scale decides what a user sees. The user can change it (Settings, Display, Display size).

Confirmed on the owner's device, a Dell XPS Googlebook at its default display size: snapped to half
the screen, Shrimpl's window is 849 dp. Half of 2560 px is 1280 px, so the scale is 1.5 (240 dpi), the
full screen is about 1707 dp, and snapping leaves about 4 dp between the two windows. Snapping offers
halves and full screen only; quarters aren't possible (confirmed on the device).

No source gives the default scale of the 2880 px models. At the same 1.5 they would be 1920 dp wide;
scaled up for their slightly higher density (243 ppi against the Dell's 225), about 1.62, 1777 dp.

| Model | Scale | Full screen | Snapped to half | With today's 880 dp rule |
|---|---|---|---|---|
| Dell XPS (confirmed) | 1.5 | 1707 dp | 849 dp | One pane |
| 14" 2880 px models, same scale as the Dell | 1.5 | 1920 dp | about 956 dp | Two panes, settings pane about 500 dp |
| 14" 2880 px models, scaled for density | about 1.62 | 1777 dp | about 885 dp | Two panes, just past the switch |
| Lenovo 15 (lower density, likely 1.5 or less) | 1.5 | 1920 dp | about 956 dp | Two panes |

So the 880 dp switch was tuned on the Dell, and on the other four models a snapped window likely
lands just above it and shows two panes. Full screen is two panes on every model, with the settings at
their 816 dp maximum.

## To confirm on a device

- The default scale of the 2880 px models: Settings, Display, Display size, or over adb `wm size` and
  `wm density` (density / 160 is the scale).
- The caption bar's height, which takes from the window's height, not its width.

## Sources

- [Notebookcheck: all the new Googlebooks](https://www.notebookcheck.net/These-are-all-the-new-Googlebooks-releasing-today-with-standardized-high-end-specs-and-premium-builds.1404726.0.html)
- [9to5Google: Googlebook launch details](https://9to5google.com/2026/09/21/googlebook-launch/)
- [Android Authority: Googlebook is official](https://www.androidauthority.com/google-googlebook-laptops-launch-features-models-price-specs-availability-3713215/)
- [PCWorld: I tried all 5 Googlebooks](https://www.pcworld.com/article/3239947/the-first-googlebooks-are-here-i-tried-all-5-from-acer-dell-hp-asus-and-lenovo.html)
- [Techlicious: all 5 models compared](https://www.techlicious.com/guide/which-googlebook-should-you-buy/)
- [Engadget: every new Googlebook](https://www.engadget.com/2263652/i-tried-every-new-googlebook-announced-today/)
- [Android Infotech: all 5 models explained](https://www.androidinfotech.com/all-5-googlebook-models-explained/)
- [HP: Googlebook 14c listing](https://www.hp.com/us-en/shop/pdp/hp-googlebook-14c-cf0815nr)
- [Notebookcheck: HP Googlebook 14c review](https://www.notebookcheck.net/HP-Googlebook-14c-Review-A-serious-Windows-rival-or-just-an-expensive-Chromebook.1418377.0.html)
- [Wikipedia: Googlebook](https://en.wikipedia.org/wiki/Googlebook)
- [gbookhub: Googlebook OS windowing gestures](https://gbookhub.io/en/articles/googlebook-os-windowing-gestures-demo)
