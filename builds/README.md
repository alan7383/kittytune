# builds

Manually built APKs, kept here so a build can be passed around without a tagged
release. Tagged releases are built and published by `.github/workflows/release.yml`
instead, and do not belong here.

Each file is named `kittytune-v<versionName>-<buildType>-<commit>.apk`, where
`<commit>` is the commit the APK was built from.

Debug builds carry the Android debug certificate, so they install alongside a
fresh device but not over an existing release install of KittyTune — the
signatures differ. Uninstall the release build first, or sideload onto a device
that does not have it.
