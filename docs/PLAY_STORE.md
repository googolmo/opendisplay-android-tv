# Google Play preparation

## Store listing copy

Localized store listing text is maintained in separate files:

- [English (United States), en-US](play-store/en-US.md)
- [Japanese, ja-JP](play-store/ja-JP.md)
- [Simplified Chinese, zh-CN](play-store/zh-CN.md)
- [Traditional Chinese, zh-TW](play-store/zh-TW.md)
- [German, de-DE](play-store/de-DE.md)
- [Korean, ko-KR](play-store/ko-KR.md)

## Graphics

- **Feature graphic:** [`assets/play-feature-graphic.jpg`](assets/play-feature-graphic.jpg), 1024 × 500 JPEG.
- **App icon:** [`assets/play-app-icon.png`](assets/play-app-icon.png), 512 × 512 PNG.
- **Android TV banner:** the app references `@mipmap/ic_banner`; its five density variants include the app name and use the expected 16:9 dimensions from 160 × 90 through 640 × 360. Confirm legibility on a TV before submission.
- **Android TV screenshots:** capture at least one unaltered, high-resolution 16:9 screenshot from the current app running on an Android TV device. Save originals under `docs/assets/tv-screenshot-01.png`, etc. The feature graphic is promotional artwork and cannot substitute for this required screenshot. Do not create a synthetic UI screenshot.

The feature graphic is original promotional artwork generated for this listing. It shows the product context, not the app UI.

## Submission checklist

- Publish [the privacy policy draft](PRIVACY_POLICY.md) as a publicly accessible, non-editable webpage after replacing its developer name and contact placeholders; add the URL in Play Console and make the policy available in the app.
- Capture and review a real 16:9 Android TV screenshot from the release build.
- Confirm the Android TV banner and store icon exports meet the current Play Console size and format checks.
- Complete the app access, Data safety, content rating, target audience, ads, and privacy policy declarations in Play Console based on the final release behavior.
- Verify D-pad navigation and operation on a physical Android TV / Google TV device.
- Confirm package name `im.amomo.opendisplay`, release version code, target API requirement, and store availability before upload.
- Upload an Android App Bundle signed with the upload key, enroll in Play App Signing, and retain the upload keystore and its credentials securely.
- Test an internal track release before wider rollout.

Play listing assets and review requirements can change; confirm the latest requirements in Play Console before submission.

## References

- [Add preview assets to showcase your app](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Android TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [Android TV app icon and banner guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines)
- [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
