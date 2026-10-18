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

### [DEC-010] Webmail Arama Yanıtı Yanlış Pozitif ("Giriş") Hatasının Giderilmesi ve Gelen Kutusu Oturum Isıtması (Inbox Warmup)
* **Tarih:** 2026-10-16
* **Karar:** Roundcube AJAX JSON arama yanıtlarında oturum düşmesini yakalamak için kullanılan genel `rawBody.contains("Giriş")` kontrolü kaldırılmış, bunun yerine yanıtın HTML içeriği ve login formu (`rcmloginuser` / `task-login`) varlığı doğrulanacak şekilde düzeltilmiştir. Ayrıca başarılı login sonrasında gelen kutusuna (`INBOX_URL`) doğrudan GET isteği atılarak güncel oturum CSRF belirteci (`requestToken`) ve IMAP oturumu ısıtması sağlanmış; arayüzdeki hata kırpma kısıtı (`take(30)`) genişletilmiştir.
* **Gerekçe:** Öğrencinin gelen kutusunda "Giriş" kelimesi geçen (örn: "Ders Kayıt Girişi", "Girişimcilik") geçerli bir e-posta bulunduğunda istemcinin bunu oturum düşmesi zannedip döngüye girmesini ve "Yeniden bağlanma sonrasında e-postalar alınamadı" hatası vermesini engellemek.
* **Etkilenen Kapsam:** `DeuWebmailClient.kt`, `MainScreen.kt`.

### [DEC-011] Kalıcı Release Keystore (Sabit İmza) ve Paket Çakışması Önleme
* **Tarih:** 2026-10-16
* **Karar:** GitHub Actions CI derleme hattında her sürümde rastgele anahtar üreten geçici `keytool -genkey` adımı kaldırılmış; tek seferlik üretilen kalıcı release keystore GitHub Actions Secret (`RELEASE_KEYSTORE_BASE64`) içerisine tanımlanarak tüm sürümlerin aynı sertifika ile imzalanması sağlanmıştır.
* **Gerekçe:** Android işletim sisteminde `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ("Paket çakıştığı için kurulamadı") imza uyuşmazlığı hatasını engellemek; öğrencilerin yeni sürüm APK'yı indirdiklerinde eski sürümü silmek zorunda kalmadan doğrudan güncelleyebilmesini, bu sayede kayıtlı hesaplarının ve sistem bildirim izinlerinin eksiksiz korunmasını güvenceye almak.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *Her Derlemede Rastgele Keystore (Mevcut Durum):* Her sürümde imza değiştiği için güncelleme imkansız hale geliyor ve kullanıcı her defasında uygulamayı silip hesaplarını baştan girmek zorunda kalıyordu.
* **Etkilenen Kapsam:** `.github/workflows/build-apk.yml`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`.

### [DEC-012] Canlı Servis Durumu Göstergesi, Tam Duyuru Metni Saklama ve Genişletilebilir Detay Kartları
* **Tarih:** 2026-10-16
* **Karar:** Ana ekranın en üstüne arka plan servisinin çalışıp çalışmadığını, son başarılı kontrol saatini ve bir sonraki tahmini kontrol zamanını gösteren canlı durum kartı eklenmiştir. Sakai duyurularının gövde açıklaması hiçbir kırpılma olmaksızın tam metin olarak saklanmakta; Android sistem bildirimlerinde `BigTextStyle` ile sistemin izin verdiği maksimum metin genişliği kullanılmakta ve uygulama arayüzünde uzun duyurular için genişletilebilir ("Daha Fazla Göster") kart yapısı ile varsayılan portal linki fallback'i sunulmaktadır.
* **Gerekçe:** Öğrencilerin arka plan yoklamasının aktifliğini teyit edebilmesi, uzun duyuruların kesilmeden bildirim çekmecesinde ve uygulama içinde baştan sona okunabilmesi, duyuru bağlantısı boş gelse dahi tek dokunuşla DEU Sakai portalına erişilebilmesi.
* **Etkilenen Kapsam:** `SakaiNotification.kt`, `SakaiParser.kt`, `NotificationHelper.kt`, `MainScreen.kt`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`.

### [DEC-013] Gece Sessizliği ve Derin Uyku Pil Tasarrufu (00:00 - 05:00)
* **Tarih:** 2026-10-18
* **Karar:** Gece saat 00:00 - 05:00 arasında DEU Sakai ve Webmail sistemlerinde yeni duyuru ve e-posta akışının durması sebebiyle, arka plan periyodik kontrol mekanizması bu saat aralığında geçici olarak uyutulacaktır. `AlarmScheduler` bir sonraki alarmı doğrudan sabah saat 05:00:00'a kurarak telefonun gece boyunca Doze (derin uyku) modunda kalmasını sağlar; `AlarmReceiver` ve `SakaiNotificationWorker` ise gece saatlerinde tetiklense dahi hiçbir ağ isteği veya WakeLock tutmadan işlemi atlar. Kullanıcıya bu özelliği kapatabilmesi için arayüzde bir tercih anahtarı (`isNightQuietHoursEnabled`, varsayılan: açık) sunulmuştur.
* **Gerekçe:** Öğrencinin gece uykusundayken telefonunun her 20 dakikada bir CPU ve Wi-Fi/Hücresel radyo uyandırarak pil ve mobil veri tüketmesini önlemek; üniversite sunucularına gereksiz gece istekleri göndermeyerek sunucu kaynaklarını korumak.
* **Değerlendirilen Alternatifler & Seçilmeme Sebepleri:**
  * *Tüm Gece Boyunca Normal 20 Dakikalık Taramaya Devam Etmek:* Gece 00:00 - 05:00 arasında akademik duyuru yayınlanma ihtimali neredeyse sıfır olduğundan bataryayı ve sistemi boş yere yorduğu için elenmiştir.
  * *Uygulamayı Gece Tamamen Kapatmak (AlarmScheduler.cancel):* Sabah 05:00'te takibin tekrar başlaması için kullanıcının manuel uygulamayı açması gerekeceğinden elenmiş; sabah 05:00'e otomatik tek bir hedef alarm kurma yöntemi seçilmiştir.
* **Etkilenen Kapsam:** `SecurePreferencesManager.kt`, `AlarmScheduler.kt`, `AlarmReceiver.kt`, `SakaiNotificationWorker.kt`, `MainScreen.kt`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`.

