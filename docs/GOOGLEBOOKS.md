# Googlebooks: Shrimpl's primary devices

Shrimpl works on every Android form factor (R1.1), but its primary users are on Googlebooks: the
Android laptops Google launched on 2026-09-21 (preorders) and 2026-10-04 (US retail), running
Googlebook OS (codename Aluminium OS). They are not Chromebooks. Size windowing and resizing
decisions for these screens first, and check them against this file.

Researched 2026-11-04 from launch coverage and maker listings (main sources at the end). Update this file
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

Shrimpl lays out by window width in dp (a window sharing the screen, as snapped windows do, shows two
panes from 1000 dp; docs/ADAPTIVE.md), so the system's
display scale decides what a user sees. The user can change it (Settings, Display, Display size).

Confirmed on the owner's device, a Dell XPS Googlebook at its default display size: snapped to half
the screen, Shrimpl's window is 849 dp. Half of 2560 px is 1280 px, so the scale is 1.5 (240 dpi), the
full screen is about 1707 dp, and snapping leaves about 4 dp between the two windows. Snapping offers
halves and full screen only; quarters aren't possible (confirmed on the device).

No source gives the default scale of the 2880 px models. At the same 1.5 they would be 1920 dp wide;
scaled up for their slightly higher density (243 ppi against the Dell's 225), about 1.62, 1777 dp.

| Model | Scale | Full screen | Snapped to half | Panes snapped (sharing the screen: from 1000 dp) |
|---|---|---|---|---|
| Dell XPS (confirmed) | 1.5 | 1707 dp | 849 dp | One |
| 14" 2880 px models, same scale as the Dell | 1.5 | 1920 dp | about 956 dp | One |
| 14" 2880 px models, scaled for density | about 1.62 | 1777 dp | about 885 dp | One |
| Lenovo 15 (lower density, likely 1.5 or less) | 1.5 | 1920 dp | about 956 dp | One |

At full screen every model is in Material's Extra-large width class (1600 dp and up), where the list
pane widens to 540 dp: four tiles across at the same size as three in the 412 dp pane (SHRIMPL_SPEC
R1.1). The HP's 1920 × 1200 option is the exception to watch: at a scale of 1.25 or more its full
screen is under 1600 dp, and it keeps three tiles.

So at the default display size every snapped window shows one pane and every full screen two, with
the settings at their maximum width. A user who makes the display size much smaller (a scale under
about 1.4 on the 2880 px models) gets two panes when snapped.

## Tested

`WindowSizesTest` (emulator) checks the full screens in the table above; snapped windows rest on
`PaneWidthTest` (Robolectric) and the Dell's confirmed 849 dp, since the emulator can't snap a window
(docs/TESTING.md).

## To confirm on a device

- The default scale of the 2880 px models: Settings, Display, Display size, or over adb `wm size` and
  `wm density` (density / 160 is the scale).
- The caption bar's height, which takes from the window's height, not its width.

## Sources

- [Notebookcheck: all the new Googlebooks](https://www.notebookcheck.net/These-are-all-the-new-Googlebooks-releasing-today-with-standardized-high-end-specs-and-premium-builds.1404726.0.html)
- [9to5Google: Googlebook launch details](https://9to5google.com/2026/09/21/googlebook-launch/)
- [HP: Googlebook 14c listing](https://www.hp.com/us-en/shop/pdp/hp-googlebook-14c-cf0815nr)
- [Notebookcheck: HP Googlebook 14c review](https://www.notebookcheck.net/HP-Googlebook-14c-Review-A-serious-Windows-rival-or-just-an-expensive-Chromebook.1418377.0.html)
- [gbookhub: Googlebook OS windowing gestures](https://gbookhub.io/en/articles/googlebook-os-windowing-gestures-demo)
