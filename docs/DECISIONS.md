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

### [DEC-005] Dokuz Eylül Webmail (Roundcube) Entegrasyonu ve Çoklu Hesap (ÇAP) Mimarisi
* **Tarih:** 2026-10-11
* **Karar:** Uygulamaya isteğe bağlı DEU Öğrenci Webmail (`ogrmail.deu.edu.tr`) takibi eklenmiş ve Çift Anadal / Yan Dal öğrencileri için çoklu hesap desteği getirilmiştir.
* **Gerekçe:** Öğrencilerin önemli üniversite duyuruları, staj ve sınav bildirimlerini kaçırmaması; birden fazla öğrenci numarası olan öğrencilerin tüm bildirimleri tek uygulamada izole şekilde alabilmesi amaçlanmıştır.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *IMAP Protokolü:* Doğrudan soket bağlantısı mobil ağlarda bağlantı kopmalarına ve yüksek pil tüketimine sebep olduğu için elenmiş; hafif Roundcube HTTP endpoint'leri tercih edilmiştir.
* **Etkilenen Kapsam:** `DeuWebmailClient.kt`, `SakaiAccount.kt`, `SecurePreferencesManager.kt`, `MainScreen.kt`.

### [DEC-006] Sessiz E-posta Başlatma (Baseline) ve Anti-Flood Batch Bildirimleri
* **Tarih:** 2026-10-11
* **Karar:** Webmail ilk kez açıldığında mevcut inbox sessizce baseline olarak kaydedilecek (Silent Mail Bootstrap); 3'ten fazla yeni e-posta geldiğinde ise bildirim paneli patlatılmayıp tek bir `InboxStyle` özet bildirim kartı oluşturulacaktır.
* **Gerekçe:** Kullanıcının telefonuna yüzlerce eski e-posta için sesli/titreşimli bildirim patlaması yaşanmasını önlemek ve bildirim çubuğunu temiz tutmak.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *Her Mail İçin Bağımsız Bildirim:* 10+ mail geldiğinde telefonu kilitleme noktasına getirdiği için elenmiştir.
* **Etkilenen Kapsam:** `NotificationStateManager.kt`, `NotificationHelper.kt`, `SakaiNotificationWorker.kt`.

### [DEC-007] OEM Pil Kısıtlamaları ve Android 11+ Hibernation Uyarı Kartı
* **Tarih:** 2026-10-11
* **Karar:** Xiaomi, Samsung gibi agresif OEM pil kısıtlamalarına ve Android 11+ kullanılmayan uygulama etkinliğini durdurma (App Hibernation / Auto-Revoke) kısıtlamalarına karşı ana ekranda rehberlik kartları ve doğrudan sistem ayarları kısayolu sunulmuştur.
* **Gerekçe:** Öğrencilerin günlerce uygulamayı açmadığında arka plan kontrollerinin işletim sistemi tarafından sessizce durdurulmasını engellemek.
* **Etkilenen Kapsam:** `MainScreen.kt`, `README.md`.

### [DEC-008] Doze Mode ve OEM Kısıtlamaları İçin Exact Alarm (setExactAndAllowWhileIdle) Entegrasyonu
* **Tarih:** 2026-10-14
* **Karar:** Arka plan periyodik kontrol mekanizması ertelenebilir `PeriodicWorkRequest`'ten, donanım seviyesinde Doze modunu ve Xiaomi MIUI/HyperOS arka plan uyutmasını delen `AlarmManager.setExactAndAllowWhileIdle()` ve `WakeLock` + `goAsync()` tabanlı sessiz donanım alarmı döngüsüne yükseltilmiştir.
* **Gerekçe:** Xiaomi Mi 12 ve Android 12+ cihazlarda ekran kapalıyken JobScheduler ve Doze kısıtlarının periyodik kontrolleri saatlerce dondurmasını engellemek; kullanıcının seçtiği dakikada (min 20 dk) CPU'yu 2-3 saniye sessizce uyandırıp tam zamanında bildirim kontrolünü garanti altına almak.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *Kalıcı Ön Plan Servisi (Foreground Service):* Bildirim çubuğunu sürekli meşgul edip gereksiz kalabalık yaratacağı için yedek opsiyon olarak tutulmuş; birincil yöntem olarak tamamen sessiz ve görünmez AlarmManager seçilmiştir.
* **Etkilenen Kapsam:** `AndroidManifest.xml`, `AlarmScheduler.kt`, `AlarmReceiver.kt`, `BootReceiver.kt`, `MainScreen.kt`, `MainActivity.kt`.

### [DEC-009] Çoklu Hesap E-posta Oturum İzolasyonu, Dinamik Çerez Deposu ve Cooldown Mekanizması
* **Tarih:** 2026-10-14
* **Karar:** Çoklu hesap e-posta kontrollerinde oturumların birbirine sızmasını önlemek için dinamik `Set-Cookie` deposu (F5 yük dengeleyici ve Roundcube çerezleri dahil), her oturum sonrasında graceful `logout` çağrısı ve ardışık hesap taramalarında 1.5 saniye anti-throttle cooldown beklemesi eklenmiştir.
* **Gerekçe:** DEU F5 yük dengeleyicisinin sticky session çerezlerinin kaybolmasını önlemek, açık kalan PHP oturumlarının sonraki hesabı bozmasını engellemek ve Roundcube/WAF brute-force (login throttle) kısıtına takılmamak.
* **Etkilenen Kapsam:** `DeuWebmailClient.kt`, `MainScreen.kt`, `AlarmReceiver.kt`, `SakaiNotificationWorker.kt`.



