# docs/ARCHITECTURE.md — DEU Sakai Notifier Android Mimari Şartnamesi

Bu belge, **DEU Sakai Notifier Android** (`deu-sakai-notifier-android`) projesinin birincil mimari planı ve teknik şartnamesidir.

---

## 1. Sistemin Amacı ve Özeti

DEU Sakai Notifier Android; Dokuz Eylül Üniversitesi öğrencilerinin kendi telefonlarında çalışan, merkezi sunucu gerektirmeyen, şifreleri cihazın güvenli hafızasında (Android KeyStore) saklayan ve arka planda (`WorkManager`) DEU Sakai duyuru ve ödevlerini periyodik olarak kontrol edip yeni içerik geldiğinde anlık sesli/titreşimli yerel sistem bildirimi üreten modern bir **Universal Android (.apk)** uygulamasıdır.

---

## 2. Temel Mimari Prensipler

1. **Sıfır Sunucu (Zero Server Architecture):** Öğrenci şifreleri üçüncü şahıs sunuculara kesinlikle gitmez; tüm HTTP istekleri öğrencinin telefonundan doğrudan DEU Sakai sunucularına yapılır.
2. **Donanım Destekli Güvenlik:** Şifreler `EncryptedSharedPreferences` / Android KeyStore ile cihazda şifrelenir.
3. **Minimum 20 Dakika Kısıtı (Sunucu Koruması):** Uygulama yaygın paylaşıldığında DEU sunucularını yormamak ve WAF/IP engellerini önlemek için kontrol aralığı **en az 20 dakika (1200 saniye)** olacaktır.
4. **Sessiz Başlatma (Silent Bootstrap):** Uygulama ilk kez çalıştırıldığında eski duyurular sessizce hafızaya alınır, kullanıcıya spam bildirim patlaması yapılmaz. Yalnızca sonradan düşen yeni bildirimlerde sesli/titreşimli alert üretilir.
5. **Modern Android Ekosistemi:** Kotlin, Jetpack Compose, Material 3, WorkManager, OkHttp 4.
6. **Universal APK Dağıtımı:** Tek bir universal `.apk` ile tüm modern Android cihazlara doğrudan kurulum (sideload).

---

## 3. Modül ve Klasör Yapısı

```text
deu-sakai-notifier-android/
├── AGENTS.md
├── GOVERNANCE.md
├── SKILL-CATALOG.md
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DECISIONS.md
│   └── SPEC.md
├── .github/
│   └── workflows/
│       └── build-apk.yml
└── app/
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── res/
            │   └── values/
            │       ├── strings.xml
            │       └── colors.xml
            └── java/
                └── tr/edu/deu/sakainotifier/
                    ├── MainActivity.kt
                    ├── SakaiApplication.kt
                    ├── data/
                    │   ├── model/
                    │   │   └── SakaiNotification.kt
                    │   ├── api/
                    │   │   ├── SakaiAuthClient.kt
                    │   │   └── SakaiApiClient.kt
                    │   └── storage/
                    │       ├── SecurePreferencesManager.kt
                    │       └── NotificationStateManager.kt
                    ├── worker/
                    │   ├── SakaiNotificationWorker.kt
                    │   ├── WorkScheduler.kt
                    │   └── BootReceiver.kt
                    ├── notification/
                    │   └── NotificationHelper.kt
                    └── ui/
                        ├── theme/
                        │   ├── Color.kt
                        │   └── Theme.kt
                        └── MainScreen.kt
```

---

## 4. Sprint ve Görev Planı (Checklist)

### Sprint 0: Android Project Scaffolding & Architecture Setup
- [x] 0.1: Git deposunu ve Android `.gitignore` dosyasını oluştur
- [x] 0.2: Protokol belgelerini (`AGENTS.md`, `GOVERNANCE.md`, `SKILL-CATALOG.md`, `docs/DECISIONS.md`, `docs/ARCHITECTURE.md`, `docs/SPEC.md`) tanımla
- [x] 0.3: Gradle projesini (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle-wrapper`) yapılandır

### Sprint 1: Sakai HTTP & Authentication Client (OkHttp)
- [x] 1.1: `SakaiNotification` veri modellerini ve JSON ayrıştırıcıyı (`SakaiNotification.kt`, `SakaiParser.kt`) oluştur
- [x] 1.2: `SakaiAuthClient.kt` ile `POST /portal/xlogin` oturum açma, cookie yönetimi ve hata kontrolünü kodla
- [x] 1.3: `SakaiApiClient.kt` ile `/api/users/me/notifications` veri çekme akışını kodla

### Sprint 2: Secure Storage & Deduplication State (EncryptedPreferences)
- [x] 2.1: `SecurePreferencesManager.kt` ile kullanıcı adı, şifre ve yoklama aralığını Android KeyStore ile şifrelenmiş saklama katmanını kodla
- [x] 2.2: `NotificationStateManager.kt` ile görülen bildirim kimliklerini ve "Silent Bootstrap" ilk başlatma mantığını kodla

### Sprint 3: Background Worker & Native Notification Manager (WorkManager)
- [ ] 3.1: `NotificationHelper.kt` ile Android sistem bildirim kanalı (`NotificationChannel`) ve push uyarılarını kodla
- [ ] 3.2: `SakaiNotificationWorker.kt` CoroutineWorker sınıfını yaz ve WorkManager planlayıcısını (`WorkScheduler.kt`) kodla
- [ ] 3.3: `BootReceiver.kt` ile telefon yeniden başladığında arka plan servisini ayağa kaldıran alıcıyı kodla

### Sprint 4: Jetpack Compose User Interface & Configuration
- [ ] 4.1: `MainActivity.kt` ve Material 3 modern tasarım temasını (`Theme.kt`) kur
- [ ] 4.2: Öğrenci no, şifre girişi, "Girişi Test Et" butonu ve minimum 20 dk sınırlandırılmış aralık seçicisini içeren ana ekranı (`MainScreen.kt`) kodla
- [ ] 4.3: Son bildirimler listesi ve servis başlat/durdur toggle kontrolünü arayüze entegre et

### Sprint 5: Universal APK Build Pipeline & GitHub Actions Release CI
- [ ] 5.1: `AndroidManifest.xml` izinlerini (`INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`) ve bileşenleri yapılandır
- [ ] 5.2: Universal APK derleme yapılandırmasını (`app/build.gradle.kts`) tamamla
- [ ] 5.3: `.github/workflows/build-apk.yml` GitHub Actions CI hattını ekle ve `README.md` dokümantasyonunu hazırla
