# Best practices and security alignment update: capability-gated image intents

- **Improvement Description:** avoid launching an unresolved implicit document
  intent, prefer permission-free system pickers, and constrain the only
  permission-bearing fallback to image-only MediaStore access after an explicit
  operator action.
- **Priority Level:** High — prevents a dead-end external intent while avoiding
  filesystem overreach and retaining private-copy validation.
- **Alignment Action:** added capability resolution, Photo Picker fallback,
  API-scoped runtime permission decisions, bounded MediaStore paging, localized
  failures and focus restoration. No nested intent forwarding, exported
  component, provider, pending intent or dependency was added.

## Files modified

- `apps/tv-android/src/main/AndroidManifest.xml`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/presentation/NamazTvApp.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/presentation/SettingsShell.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/presentation/MediaStoreImagePickerScreen.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/repository/AndroidOperatorImageSelectionEnvironment.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/repository/OperatorImageSelectionCoordinator.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/repository/OperatorMediaImageCatalog.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/repository/OperatorImageAssetStore.kt`
- localized RU/EN string resources and corresponding manifest, coordinator,
  importer, pager, focus and Compose integration tests.

## Implementation diff

Security-critical manifest boundary:

```diff
 <uses-permission android:name="android.permission.INTERNET" />
+<uses-permission
+    android:name="android.permission.READ_EXTERNAL_STORAGE"
+    android:maxSdkVersion="32" />
+<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

Capability decision (the external intent is never launched solely by
assumption):

```diff
-backgroundPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
+when (imageSelectionCoordinator.decide(imageEnvironment.capabilities())) {
+    OpenDocument -> documentPicker.launch(OPERATOR_IMAGE_MIME_TYPES)
+    PhotoPicker -> photoPicker.launch(PickVisualMediaRequest(ImageOnly))
+    MediaStore -> openMediaStore(slot)
+    is RequestPermission -> mediaPermission.launch(permissionName)
+}
```

The exact production/test diff is the local T043 media-picker checkpoint; raw
exceptions and selected URIs are neither surfaced to UI nor stored as prayer
or source data.

## Testing and verification

1. Run the coordinator matrix for OpenDocument, Photo Picker, API 28/32 and API
   33/35 grants, denial and revocation.
2. Run manifest source/merged-manifest tests to reject write/all-files access
   and cap legacy read at API 32.
3. Run importer JPEG/PNG/WebP/bounds/decode/storage regressions and the D-pad
   MediaStore/focus/application integration tests.
4. Run strict Android Lint and the repository security/secret gates before
   completion.

Runtime behavior on the controlled API 36 emulator will be recorded separately.
Physical-TV behavior remains `UNKNOWN` until the pilot hardware run.
