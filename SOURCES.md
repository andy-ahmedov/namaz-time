# SOURCES.md

Public sources were accessed during research in August 2026. Re-check current terms/content before implementation because pages and policies can change.

## Target application

- Google Play, `islam.islamapp.masjid`: https://play.google.com/store/apps/details?id=islam.islamapp.masjid
- RuStore, `islam.islamapp.masjid`: https://www.rustore.ru/catalog/app/islam.islamapp.masjid
- Sber/Salute Apps listing: https://apps.sber.ru/salute-apps/96137352-c082-473e-aa93-67552d02032f/
- Mobile IslamApp Google Play: https://play.google.com/store/apps/details?id=islam.islamapp
- Mobile IslamApp App Store: https://apps.apple.com/ru/app/islamapp/id1452368807

## Official Russian schedule sources

- DUM RF: https://www.dumrf.ru/
- New DUM RF prayer-time section: https://new.dumrf.ru/
- DUM Republic of Tatarstan timetable: https://dumrt.ru/ru/help-info/prayertime/
- Central DUM prayer-time pages: https://cdum.ru/time-namaz/

No stable nationwide documented public API was confirmed from these public pages. Contact/permission remains necessary.

## Other mosque display products

- MAWAQIT prayer-time product: https://mawaqit.net/en/prayer-times
- MAWAQIT annual CSV/PDF download help: https://help.mawaqit.net/en/articles/8999582-how-to-download-the-prayer-times-of-my-mosque
- MAWAQIT manual CSV adjustment/upload: https://help.mawaqit.net/en/articles/11813326-how-to-manually-adjust-prayer-times-shuruq-in-mawaqit
- MAWAQIT Box offline description: https://help.mawaqit.net/en/articles/11951302-what-is-the-mawaqit-box
- MAWAQIT offline TV behavior: https://help.mawaqit.net/en/articles/11791418-does-the-mawaqit-app-work-offline
- MAWAQIT API help article (2025 wording: private/not public): https://help.mawaqit.net/en/articles/11991838-can-i-use-your-api
- MAWAQIT official authenticated Python API client: https://github.com/mawaqit/mawaqit-py
- MAWAQIT verified PyPI package and release history: https://pypi.org/project/mawaqit/
- My-Masjid: https://my-masjid.com/
- ConnectMazjid: https://connectmazjid.com/
- Tarjiha prayer-time architecture/features: https://tarjiha.com/features
- mySalah App Store description: https://apps.apple.com/ru/app/mysalah/id1170337374

## Calculation/API references

- AlAdhan API: https://aladhan.com/prayer-times-api
- AlAdhan calculation methods: https://aladhan.com/calculation-methods
- BatoulApps Adhan Kotlin: https://github.com/batoulapps/adhan-kotlin

## Android TV and reliability

- Create an Android TV app: https://developer.android.com/training/tv/get-started/create
- TV focus system: https://developer.android.com/design/ui/tv/guides/styles/focus-system
- Leanback deprecation / Compose for TV: https://developer.android.com/jetpack/androidx/releases/leanback
- WorkManager periodic work: https://developer.android.com/reference/androidx/work/PeriodicWorkRequest
- Dedicated devices: https://developer.android.com/work/dpc/dedicated-devices

## Geocoding policy

- Nominatim public service usage policy: https://operations.osmfoundation.org/policies/nominatim/

The public service allows modest user-triggered use under strict limits, requires identification/attribution, recommends proxy/caching and forbids client autocomplete/systematic scraping. A curated mosque catalog or controlled provider is preferred for production.

## Supplied evidence

- two user-supplied photos of IslamApp running on a mosque TV;
- user-supplied `IslamApp для Мечети 1.6.2` APK from APKPure;
- sanitized static-analysis summary in `research/evidence/`.

The APK is research input only. It is not committed to this package and its signing identity has not yet been compared with an official-store binary.
