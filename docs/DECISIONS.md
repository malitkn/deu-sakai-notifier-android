# Mimari Karar Kayıtları (Architecture Decision Log)

Bu belge, **DEU Sakai Notifier Android** (`deu-sakai-notifier-android`) projesinde alınan tüm kritik mimari, teknik, kütüphane ve altyapı kararlarını kronolojik olarak kayıt altına alır.

---

## Proje Kararları

### [DEC-001] Teknoloji Yığını: Kotlin, Jetpack Compose ve WorkManager Seçimi
* **Tarih:** 2026-10-11
* **Karar:** Uygulamanın geliştirme dili olarak Kotlin 2.0, UI katmanı için modern Jetpack Compose ve Material 3, arka plan periyodik bildirim motoru için Android WorkManager, HTTP oturum yönetimi için OkHttp 4 seçilmiştir.
* **Gerekçe:** Android işletim sisteminde pil optimizasyonu (Doze Mode) ve arka plan güvenilirliği için Google'ın resmi tavsiyesi `WorkManager`'dır. Jetpack Compose deklaratif, hafif ve modern bir arayüz sağlar.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *Flutter / React Native:* Ekstra framework derleme yükü ve binary boyutunun (~30-50 MB) büyümesi sebebiyle elenmiştir. Saf Kotlin ile APK boyutu < 8 MB seviyesindedir.
* **Etkilenen Kapsam:** Tüm Android proje mimarisi.

### [DEC-002] Minimum 20 Dakika (1200 Saniye) Yoklama Kısıtı ve Anti-Ban Koruması
* **Tarih:** 2026-10-11
* **Karar:** Uygulama arayüzünde öğrencinin seçebileceği kontrol aralığı için katı bir alt sınır olarak minimum 20 dakika (1200 saniye) belirlenmiştir.
* **Gerekçe:** Universal APK yüzlerce öğrenci tarafından paylaşıldığında, DEU Sakai sunucularına eşzamanlı agresif istek gitmesini önlemek ve üniversitenin IP/WAF blokajı uygulamasını engellemek zorunludur.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *1-5 Dakikalık Yoklama:* Sunucu yükü ve hesap kilitlenme riski nedeniyle kesin olarak reddedilmiştir.
* **Etkilenen Kapsam:** `MainScreen.kt`, `WorkScheduler.kt`, `SakaiNotificationWorker.kt`.

### [DEC-003] Cihazda Donanım Destekli Şifreleme (Android KeyStore & EncryptedSharedPreferences)
* **Tarih:** 2026-10-11
* **Karar:** Öğrenci numarası ve şifresi merkezi bir backend'e gönderilmeksizin, Android KeyStore ile donanım seviyesinde korunan `EncryptedSharedPreferences` üzerinde saklanacaktır.
* **Gerekçe:** Sıfır sunucu maliyeti ile tam KVKK/GDPR uyumu sağlanır; geliştirici dahil hiçbir 3. taraf öğrenci şifrelerine erişemez.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *Düz SharedPreferences:* Root'lu cihazlarda şifrenin düz metin okunabilmesi riski nedeniyle elenmiştir.
* **Etkilenen Kapsam:** `SecurePreferencesManager.kt`.

### [DEC-004] Universal APK ve GitHub Actions Otomatik Derleme Hattı
* **Tarih:** 2026-10-11
* **Karar:** Dağıtım için tek bir universal APK üretilecek ve her sürümde `.github/workflows/build-apk.yml` GitHub Actions CI hattı ile derlenip GitHub Releases'e otomatik yüklenecektir.
* **Gerekçe:** Öğrencilerin kendi telefonlarına (ARM64, ARMv7, x86) işlemci mimarisi düşünmeden doğrudan APK'yı indirip kurabilmesi sağlanır.
* **Etkilenen Kapsam:** `.github/workflows/build-apk.yml`, `app/build.gradle.kts`.
