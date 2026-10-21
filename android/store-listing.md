# FlexBuddy — Google Play store listing draft

Draft for Stage 7. Every statement below describes functionality that exists in the current
release. Recheck it against the live app before submitting, and update it with the privacy
policy and Data safety form if features or data handling change.

## App details

| Field | Value | Limit |
|---|---|---|
| App name | `FlexBuddy: Shift Tracker` (24 characters) | 30 |
| Short description | `Track delivery shifts, earnings, mileage, and expenses in one private place.` (76 characters) | 80 |
| Category | Productivity (alternative: Finance) | — |
| Contact email | `flexbuddysupport@gmail.com` | — |
| Website | `https://flexbuddy.onrender.com` | — |
| Privacy policy | `https://flexbuddy.onrender.com/privacy` | — |
| Account deletion | `https://flexbuddy.onrender.com/delete-account` | — |

The handoff guide suggested `FlexBuddy – Delivery Shift Tracker`, but that is 34 characters and
Play limits app names to 30.

## Full description

Updated October 20, 2026 for offline adding, Google and two-step sign-in, Spanish, goals, payouts,
taxes and standing. Paste it into Play with a line break only between paragraphs and bullets; the
wrapping below is for this file.

```text
FlexBuddy is a private tracker for independent delivery drivers. Log every block, see what you
really earned after costs, and plan the week ahead.

TRACK EVERY BLOCK
• Add a block from a screenshot: FlexBuddy reads the station, date, times, and pay, and you check
  every value before saving. Screenshots in English or Spanish work, and you can share one straight
  from your gallery.
• Record base pay, tips, miles, actual start and finish times, and stops, packages, and returns.
• Add blocks and expenses even without a signal; they sync when you are back online.

SEE YOUR REAL EARNINGS
• A Home screen built around this week: earnings against your weekly goal, the next block, and the
  next payout.
• Estimated net pay after mileage and expenses, using standard mileage or actual costs.
• Check each payout against what its blocks earned.
• Reports by station, week, month, or year, with charts, your best times to work, and CSV export.
• Evaluate an offer before you accept it: what it nets you per hour, and how much is surge over
  your usual rate.

PLAN YOUR SCHEDULE
• Add scheduled blocks, then mark them completed, cancelled, or forfeited.
• A countdown to your next block and its forfeit deadline, a 14-day list, and a monthly calendar.
• Subscribe to your schedule from your phone's calendar, or turn on optional reminders.
• Log your standing and see it next to forfeits and cancellations.

EXPENSES AND TAXES
• Fuel, tolls, parking, maintenance, and other costs, optionally linked to a block.
• An estimate of what to set aside for taxes, due-date reminders, and a printable year summary for
  your tax preparer.

YOUR DATA STAYS YOURS
• A private account with email and password or Google sign-in, and optional two-step sign-in.
• Recently deleted items can be restored for 30 days.
• Download a full backup at any time and restore it later.
• Delete your account and its data from inside the app.
• No ads and no analytics.
• Available in English and Spanish.

Earnings, mileage, net, and tax figures are estimates for your own planning and are not tax or
financial advice.

FlexBuddy is an independent app and is not affiliated with, endorsed by, or sponsored by Amazon
or any delivery platform.
```

## Spanish listing (es-419 and es-US)

The app is in Spanish, so the listing can be too. It uses the app's own Spanish wording and the
formal "usted", as the app does.

| Field | Value | Limit |
|---|---|---|
| Short description | `Registre bloques, ganancias, millas y gastos de reparto en un lugar privado.` (76 characters) | 80 |

```text
FlexBuddy es un registro privado para repartidores independientes. Anote cada bloque, vea lo
que realmente ganó después de los costos y planifique la semana.

REGISTRE CADA BLOQUE
• Agregue un bloque desde una captura de pantalla: FlexBuddy lee la estación, la fecha, las horas
  y el pago, y usted revisa cada valor antes de guardar. Funciona con capturas en inglés o en
  español, y puede compartirlas directamente desde su galería.
• Registre el pago base, las propinas, las millas, las horas reales de inicio y fin, y las paradas,
  los paquetes y las devoluciones.
• Agregue bloques y gastos aunque no tenga señal; se sincronizan cuando vuelve a tener conexión.

VEA SUS GANANCIAS REALES
• Una pantalla de Inicio centrada en esta semana: sus ganancias frente a su meta semanal, el
  próximo bloque y el próximo depósito.
• Pago neto estimado después del millaje y los gastos, con la tarifa estándar por milla o con los
  costos reales.
• Compare cada depósito con lo que ganaron sus bloques.
• Informes por estación, semana, mes o año, con gráficos, sus mejores horarios para trabajar y
  exportación a CSV.
• Evalúe una oferta antes de aceptarla: cuánto le deja por hora y cuánto es aumento sobre su
  tarifa habitual.

PLANIFIQUE SU SEMANA
• Agregue bloques programados y luego márquelos como completados, cancelados o perdidos.
• Una cuenta regresiva para su próximo bloque y su límite para perderlo, una lista de 14 días y un
  calendario mensual.
• Suscríbase a su programación desde el calendario de su teléfono o active recordatorios opcionales.
• Registre su nivel y véalo junto a los bloques perdidos y cancelados.

GASTOS E IMPUESTOS
• Combustible, peajes, estacionamiento, mantenimiento y otros costos, con la opción de vincularlos
  a un bloque.
• Un estimado de cuánto apartar para impuestos, recordatorios de fechas de pago y un resumen anual
  imprimible para su preparador de impuestos.

SUS DATOS SON SUYOS
• Una cuenta privada con correo y contraseña o con Google, y verificación en dos pasos opcional.
• Los elementos eliminados recientemente se pueden restaurar durante 30 días.
• Descargue una copia de seguridad completa en cualquier momento y restáurela después.
• Elimine su cuenta y sus datos desde la app.
• Sin anuncios y sin analíticas.
• Disponible en inglés y español.

Las ganancias, el millaje, el neto y los impuestos son estimaciones para su propia planificación y
no constituyen asesoría fiscal ni financiera.

FlexBuddy es una app independiente y no está afiliada, respaldada ni patrocinada por Amazon ni por
ninguna plataforma de reparto.
```

## Graphics

| Asset | File | Required size | Status |
|---|---|---|---|
| App icon | `android/store_icon.png` | 512 × 512 PNG | The F-and-outline mark redrawn from the provided icon sheet; full-bleed, opaque; Play applies its own corner mask |
| Feature graphic | `android/feature_graphic.png` | 1024 × 500 PNG/JPEG | Original FlexBuddy branding with the new app icon |
| Phone screenshots | `android/store-screenshots/*.png` | 2–8, 16:9 or 9:16, 320–3840 px sides | 1242 × 2208 (exactly 9:16), 24-bit PNG |
| 7-inch tablet screenshots | `android/store-screenshots/tablet-7/*.png` | Up to 8, 16:9 or 9:16, 320–3840 px sides | 1224 × 2176 (exactly 9:16), 24-bit PNG |
| 10-inch tablet screenshots | `android/store-screenshots/tablet-10/*.png` | Up to 8, 16:9 or 9:16, 1080–7680 px sides | 1080 × 1920 (exactly 9:16), 24-bit PNG |

The icon was redrawn as vector shapes from the provided icon sheet on September 14, 2026, so every
size (Play, web app manifest, maskable, Android launcher, splash, and notification) is sharp and
consistent. The Android launcher, splash, and notification icons only reach devices in a new app
bundle (version code 2); the web icons update with the next deploy.

Tablet screenshots use the same capture as the phone set: a 612 × 1088 viewport at 2x for 7-inch
tablets and an 810 × 1440 viewport at 1.33x for 10-inch tablets, both in portrait so the app's
tablet layout fills the frame.

The screenshots were retaken on October 20, 2026, after the Home and Reports screens, the floating
menu and the header changed. The Play Reviewer account's sample data dates from September, so its
Home showed an empty week; nothing may be added to that production account. They were captured
instead from the same app run locally against a throwaway database, signed in to a made-up demo
driver ("Sam", stations Downtown Hub, Airport Depot, Northside and Lakeview) with blocks, expenses,
a weekly goal and a tax set-aside dated around that week. Chrome on the Pixel 10 Pro emulator
rendered them through DevTools, so the fonts are Android's, at a 414 x 736 phone viewport at 3x,
without the Android status bar. Chrome's install prompt, which never appears in the installed app,
was suppressed, and transient toasts were hidden.

Suggested upload order (the same order for both tablet sets, prefixed `tablet7-` and `tablet10-`):

1. `01-home.png`: this week's earnings against the goal, the next block and the next payout.
2. `02-schedule.png`: the next block with its countdown, forfeit deadline and actions.
3. `03-reports.png`: earned, estimated net, average hourly and base versus tips.
4. `04-expenses.png`: expense totals and the expense form.
5. `05-import.png`: the screenshot import step.

A screenshot of the import review with parsed fields is not included: it would need a sample
screenshot image, and it must not show another app's screen.

Do not show third-party logos, other apps' screens, or real personal data in any screenshot.
