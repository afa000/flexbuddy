# FlexBuddy — Play Console production access application

## Result: more testing required (October 24, 2026)

Google replied by email on October 24, 2026: the app "requires more testing before you can access production". It
named two possible reasons, testers not engaged with the app during the closed test, and not following testing best
practices "which may include gathering and acting on user feedback through updates to your app". It asks for another
14 days of closed testing with real testers before applying again.

What Google could see against what the answers below claimed:

- **No updates during the test.** The closed track received version 2 on September 26, 2026 and nothing after it.
  Every change in the answers (offline adding, Spanish, Google sign-in, password reset and the rest) shipped through
  Render, as the closed test plan said to, so none of it appeared as an update to the app. The answers say the app
  changed because of feedback; the release history shows one update, near the start.
- **Tester recruitment.** Testers came partly from the Testers Community app, a tester-exchange service whose members
  tend to open apps to stay counted rather than use them. The answers themselves say few testers tried taxes or
  backups.
- **Vague feedback.** The feedback summary lists topics, not what a tester reported, when, and what changed because of
  it.

The next attempt follows [closed-test-round-2.md](closed-test-round-2.md).

## First application

Submitted in Play Console on October 22, 2026 at 1:50 AM with the answers below, exactly as written. Play shows them to
Google only, not on the store listing. Google said it usually replies by email to the account owner within 7 days.

Facts checked in Play Console on October 22, 2026: all three closed-test requirements met; version 2 available to
closed testers in 178 countries; 20 device acquisitions and 20 monthly active devices in the last 28 days; no crashes
or ANRs in the last 28 days. App changes come from the Git history since September 14, 2026.

## 1. About your closed test

**How did you recruit users for your closed test?**

> I invited friends, family and other Amazon Flex drivers I know, and found more testers through the Testers
> Community app. I added each tester's Play Store email to a tester list and sent the opt-in link with a checklist of
> features to try.

**How easy was it to recruit testers for your app?** Neither difficult or easy

**Describe the engagement you received from testers during your closed test.**

> 20 devices installed the app and 20 were active in the last 28 days. Testers made accounts, logged and imported
> blocks, added expenses and checked reports, as a driver would in a working week. Fewer tried taxes or backups,
> which drivers use now and then.

**Provide a summary of the feedback that you received from testers. Include how you collected the feedback.**

> Feedback came through the Testers Community app, the support email and in person. Testers asked for offline
> adding, Spanish, Google sign-in and faster screenshot import, and reported small-phone layout issues.

## 2. About your app

**Who is the intended audience of your app?**

> Independent delivery drivers, mainly Amazon Flex drivers in the US, who want to track their blocks, earnings,
> mileage and expenses. The app is in English and Spanish.

**How does your app provide value to users?**

> Drivers see what each block really earned after mileage and expenses, get reminders for scheduled blocks, check
> payouts, estimate taxes and import a block from a Flex screenshot instead of typing it. Data is private to each
> account.

**How many installs do you expect your app to have in your first year?** 0 - 10K

## 3. Your production readiness

**What changes did you make to your app based on what you learned during your closed test?**

> I added offline adding, Spanish, Google and two-step sign-in, password reset, reminders, goals, payout checks, tax
> estimates and share-to-import, enlarged tap targets, fixed phone layouts and made screenshot import more accurate
> and memory-safe.

**How did you decide that your app is ready for production?**

> Testers used it for over a month with no crashes or ANRs in Android vitals. Every change passes automated tests
> before release, and the privacy policy, Data safety form and in-app account deletion are in place.
