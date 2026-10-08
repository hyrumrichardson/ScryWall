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

Every release is signed with the same key, so new versions install over old ones. To check
a download, compare it with the values in the release notes:

- **Signing certificate SHA-256:**
  `32:AF:72:02:29:F1:E6:C1:AD:B7:11:54:C5:FC:2D:EB:5B:EF:1C:41:C3:31:63:6E:7D:09:DC:41:1C:B4:C1:17`
- **APK SHA-256:** listed in each release's notes.

> Builds before October 2026 used an older key. If you installed one of those, uninstall
> ScryWall once before installing a newer release.

### Signing setup (maintainers)

The release key is never stored in the repo. The workflow reads it from two repository
secrets: `SCRYWALL_KEYSTORE_BASE64` (the `.jks` file, base64-encoded) and
`SCRYWALL_KEYSTORE_PASSWORD`. Local debug builds use Android's standard debug key and
install as a separate app (`com.hyrumrichardson.scrywall.debug`).

## Notes

- Scryfall's art crops are about 626 px wide, so "Art only" can look soft on high-res
  screens. "Full card" images are higher resolution.
- Android limits background jobs to at most once every 15 minutes, and battery saver may
  delay changes.
- Card data and images come from Scryfall. Card art © Wizards of the Coast. This app is not
  affiliated with Scryfall or Wizards of the Coast.
