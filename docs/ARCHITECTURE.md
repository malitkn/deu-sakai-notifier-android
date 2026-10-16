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
~~5. Modern Android Ekosistemi: Kotlin, Jetpack Compose, Material 3, WorkManager, OkHttp 4.~~ → 5. **Modern Android Ekosistemi:** Kotlin, Jetpack Compose, Material 3, AlarmManager (Exact Alarm + Doze Bypass), WorkManager, OkHttp 4. (Tarih: 2026-10-14, Gerekçe: Xiaomi Mi 12 ve Android 12+ Doze Mode'da periyodik WorkManager'ın süresiz ertelenmesini önlemek).
6. **Universal APK Dağıtımı:** Tek bir universal `.apk` ile tüm modern Android cihazlara doğrudan kurulum (sideload).

---

## 3. Modül ve Klasör Yapısı

```text
deu-sakai-notifier-android/
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
                    │   │   ├── SakaiAccount.kt
                    │   │   └── SakaiNotification.kt
                    │   ├── api/
                    │   │   ├── SakaiAuthClient.kt
                    │   │   ├── SakaiApiClient.kt
                    │   │   └── DeuWebmailClient.kt
                    │   ├── storage/
                    │   │   ├── SecurePreferencesManager.kt
                    │   │   └── NotificationStateManager.kt
                    │   └── updater/
                    │       └── UpdateChecker.kt
                    ├── worker/
                    │   ├── SakaiNotificationWorker.kt
                    │   ├── WorkScheduler.kt
                    │   ├── AlarmScheduler.kt
                    │   ├── AlarmReceiver.kt
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
- [x] 0.2: Teknik dokümantasyon belgelerini (`docs/DECISIONS.md`, `docs/ARCHITECTURE.md`, `docs/SPEC.md`) tanımla
- [x] 0.3: Gradle projesini (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle-wrapper`) yapılandır

### Sprint 1: Sakai HTTP & Authentication Client (OkHttp)
- [x] 1.1: `SakaiNotification` veri modellerini ve JSON ayrıştırıcıyı (`SakaiNotification.kt`, `SakaiParser.kt`) oluştur
- [x] 1.2: `SakaiAuthClient.kt` ile `POST /portal/xlogin` oturum açma, cookie yönetimi ve hata kontrolünü kodla
- [x] 1.3: `SakaiApiClient.kt` ile `/api/users/me/notifications` veri çekme akışını kodla

### Sprint 2: Secure Storage & Deduplication State (EncryptedPreferences)
- [x] 2.1: `SecurePreferencesManager.kt` ile kullanıcı adı, şifre ve yoklama aralığını Android KeyStore ile şifrelenmiş saklama katmanını kodla
- [x] 2.2: `NotificationStateManager.kt` ile görülen bildirim kimliklerini ve "Silent Bootstrap" ilk başlatma mantığını kodla

### Sprint 3: Background Worker & Native Notification Manager (WorkManager)
- [x] 3.1: `NotificationHelper.kt` ile Android sistem bildirim kanalı (`NotificationChannel`) ve push uyarılarını kodla
- [x] 3.2: `SakaiNotificationWorker.kt` CoroutineWorker sınıfını yaz ve WorkManager planlayıcısını (`WorkScheduler.kt`) kodla
- [x] 3.3: `BootReceiver.kt` ile telefon yeniden başladığında arka plan servisini ayağa kaldıran alıcıyı kodla

### Sprint 4: Jetpack Compose User Interface & Configuration
- [x] 4.1: `MainActivity.kt` ve Material 3 modern tasarım temasını (`Theme.kt`) kur
- [x] 4.2: Öğrenci no, şifre girişi, "Girişi Test Et" butonu ve minimum 20 dk sınırlandırılmış aralık seçicisini içeren ana ekranı (`MainScreen.kt`) kodla
- [x] 4.3: Son bildirimler listesi ve servis başlat/durdur toggle kontrolünü arayüze entegre et

### Sprint 5: Universal APK Build Pipeline & GitHub Actions Release CI
- [x] 5.1: `AndroidManifest.xml` izinlerini (`INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`) ve bileşenleri yapılandır
- [x] 5.2: Universal APK derleme yapılandırmasını (`app/build.gradle.kts`) tamamla
- [x] 5.3: `.github/workflows/build-apk.yml` GitHub Actions CI hattını ekle ve `README.md` dokümantasyonunu hazırla

### Sürüm İyileştirmeleri (v1.2.0 — v1.2.4)
- [x] Multi-Account & ÇAP: Birden çok öğrenci hesabı ekleme, düzenleme ve silme desteği
- [x] DEU Webmail Takibi: `DeuWebmailClient.kt` ile Roundcube tabanlı öğrenci webmail entegrasyonu
- [x] Silent Baseline & Batching: İlk e-posta taramasında baseline sessiz alma ve toplu iletilerde `InboxStyle` tek kart
- [x] Hibernation & Battery Kılavuzu: Xiaomi/Samsung arka plan optimizasyonu ve Android 11+ otomatik uyutma kısayolu
- [x] In-App Sürüm Güncelleme: `UpdateChecker.kt` ile GitHub Releases API kontrolü ve doğrudan APK indirme

### Revision: Exact Alarm Background Scheduler (Doze & Xiaomi Bypass)
- [x] R.1: `AndroidManifest.xml` izinlerini (`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `WAKE_LOCK`) ve `AlarmReceiver` bileşenini yapılandır
- [x] R.2: `AlarmScheduler.kt` ile donanım seviyesinde `setExactAndAllowWhileIdle` zamanlayıcısını ve iptal mantığını kodla
- [x] R.3: `AlarmReceiver.kt` ile Doze modunda `WakeLock` + `goAsync()` üzerinden hızlı yoklama ve zincirleme yeniden kurma döngüsünü kodla
- [x] R.4: `BootReceiver.kt` ve `MainActivity.kt` üzerinde alarm döngüsünü başlat ve senkronize et
- [x] R.5: `MainScreen.kt` arayüz toggle ve aralık kontrollerini `AlarmScheduler` ile entegre et
- [x] R.6: Xiaomi Mi 12 için "Otomatik Başlatma (Autostart)" doğrudan ayarlar kısayolunu rehber kartına ekle
- [x] R.7: Derleme ve kod bütünlüğünü doğrula (v1.2.5 versionCode 17)

### Revision: Multi-Account Mail Sync & Filter Resolution (v1.2.6)
- [x] M.1: `DeuWebmailClient.kt` içinde Roundcube e-posta yanıtları için iç içe JSON ve özel karakterleri hatasız ayrıştıran derinlik kontrollü ayrıştırıcıyı kodla
- [x] M.2: `DeuWebmailClient.kt`, `AlarmReceiver.kt` ve `SakaiNotificationWorker.kt` içinde çoklu hesap oturum ve bağlantı havuzu (`evictAll`) izolasyonunu sağla
- [x] M.3: `NotificationHelper.kt` bildirim yöneticisinde hesap kapsamlı ID (`uniqueId`) kullanarak bildirim çubuğu üzerine yazılma hatasını düzelt
- [x] M.4: `NotificationStateManager.kt` içinde sessiz e-posta baseline mantığını (az sayıda yeni mailde bildirim gönderecek, eski yığınları sessiz alacak şekilde) ve tahribatsız önbellek birleştirmeyi kodla
- [x] M.5: `MainScreen.kt` üzerinde hesap ve tür filtresi sayaçları ile liste görünümünü tam senkronize hale getir
### Task 6: Multi-Account Mail Session Isolation, Cookie Jar & Cooldown (v1.2.7)
- [x] 6.1: `DeuWebmailClient.kt` içinde tüm `Set-Cookie` başlıklarını (F5/yük dengeleyici ve Roundcube çerezleri) saklayan dinamik çerez yönetimini kodla
- [x] 6.2: `DeuWebmailClient.kt` içine oturum bittiğinde sunucu tarafında temiz kapanma sağlayan `logout` fonksiyonunu ekle
- [x] 6.3: `MainScreen.kt`, `AlarmReceiver.kt` ve `SakaiNotificationWorker.kt` içinde ardışık hesap taramalarında WAF/throttle önleyici güvenli bekleme (cooldown) ve tam oturum izolasyonunu sağla
- [x] 6.4: Sürümü `1.2.7` (versionCode 19) olarak güncelle ve derleme/test bütünlüğünü doğrula

### Task 7: Webmail Search False-Positive Resolution, Inbox Session Warmup & Error Truncation Fix (v1.2.8)
- [x] 7.1: `DeuWebmailClient.kt` içindeki hatalı `rawBody.contains("Giriş")` kontrolünü kaldırarak JSON arama yanıtlarında oturum düşmesini doğru (HTML login formu tespitiyle) ayrıştır
- [x] 7.2: `DeuWebmailClient.kt` içinde başarılı oturum açma sonrası gelen kutusu (`INBOX`) GET isteği yaparak gerçek oturum token'ı (`requestToken`) ve IMAP oturumunu güvenceye al
- [x] 7.3: `MainScreen.kt` üzerinde hata mesajı kırpma (`take(30)`) sınırını genişleterek ve `DeuWebmailClient.kt` içinde açıklayıcı hata nedenlerini koru
- [x] 7.4: Sürümü `1.2.8` (versionCode 20) olarak güncelle ve değişiklikleri doğrula

### Task 8: Persistent Release Keystore & In-Place Update Compatibility (v1.2.9)
- [ ] 8.1: Sabit release keystore oluştur ve GitHub Secrets (`RELEASE_KEYSTORE_BASE64`) içerisine güvenli şekilde kaydet
- [ ] 8.2: `.github/workflows/build-apk.yml` CI derleme hattını sabit keystore'u decode edip kullanacak şekilde güncelle
- [ ] 8.3: `app/build.gradle.kts` ve `README.md` yapılandırmasını kalıcı imza ve in-place güncelleme uyumluluğu ile senkronize et
- [ ] 8.4: Sürümü `1.2.9` (versionCode 21) olarak güncelle ve değişiklikleri doğrula
