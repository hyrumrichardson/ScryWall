# ScryWall

An Android app that sets your wallpaper to Magic: The Gathering card art from any
[Scryfall search](https://scryfall.com/docs/syntax), and keeps changing it on a schedule.

## How it works

1. Type a Scryfall search (for example `t:dragon is:fullart` or `a:"Rebecca Guay"`).
   Or flip the **Moxfield** switch in the search box and paste a link to a public (or unlisted)
   Moxfield deck, such as `https://moxfield.com/decks/abc123`. Every card in the deck except the
   maybeboard is used.
2. Tap **Search** (or **Load deck**) to see 5 random cards. Tap any of them to preview it.
3. Choose:
   - **Image**: the art alone, or the whole card
   - **Scaling**: Fill, Fit, Fit + blur, Stretch or Center
   - **Apply to**: home screen, lock screen or both
   - **Change every**: 15 minutes up to weekly, or never
4. Check the phone-shaped preview, then tap **Set wallpaper**. ScryWall picks a random
   card from the *whole* search and keeps changing it on your schedule, even after a restart.

## Getting the APK

Every push to `main` builds the app with GitHub Actions and publishes `ScryWall.apk`
on the repo's **Releases** page. On your phone, open the latest release, tap
`ScryWall.apk`, and allow installing from your browser when Android asks.

All builds share the same signing key (`keystore/`), so new versions install over old ones.

## Notes

- Scryfall's art crops are about 626 px wide, so "Art only" can look soft on high-res
  screens. "Full card" images are higher resolution.
- Android limits background jobs to at most once every 15 minutes, and battery saver may
  delay changes.
- Card data and images come from Scryfall. Card art © Wizards of the Coast. This app is not
  affiliated with Scryfall or Wizards of the Coast.
