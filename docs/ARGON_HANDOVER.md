# ARGON GÖREV DEVRİ VE DEBİS NOT TAKİP MODÜLÜ RAPORU

> [!IMPORTANT]
> **Çalışma Dalı:** `feature/debis-grade-notifier`  
> **Tarih:** 2026-11-10  
> **Durum:** Geliştirme tamamlandı, birim testleri yazıldı, mimari karar kaydı (`[DEC-015]`) eklendi. Argon incelemesi ve merge onayına hazır.

---

## 1. Görevin Amacı ve Kullanıcı Talepleri

Kullanıcı, Dokuz Eylül Üniversitesi'nin öğrenci bilgi sistemi olan **DEBİS** (`debis.deu.edu.tr`) üzerindeki sınav notlarının (vize, final, bütünleme ve yarıyıl sonu harf notları) otomatik olarak takip edilmesini ve yeni bir not açıklandığında Android uygulamamız üzerinden anında push bildirimi gönderilmesini talep etmiştir.

### Kullanıcı Tarafından Sağlanan Form & Payload Spesifikasyonu:
```text
ders=B_BA+_1_9634_3_46_&ogretim_donemi_id=324&relative=..%2F..%2F..%2F&ogrenci_no=2025282042&liste=
```
* `B`: Başarı Durumu (B = Başarılı / Geçti, K = Kaldı)
* `BA+`: Harf Notu (`BA ` — URL encode edilmiş boşluk karakteri)
* `1`: Şube / Grup Numarası
* `9634`: Açılan Dersin Benzersiz ID'si (CRN / Course ID)
* `3`: Dersin Kredisi / AKTS Değeri (Türk Dili II: 3 Kredi)
* `46`: Sınıf Mevcudu / Bağıl Değerlendirme Parametresi
* `ogretim_donemi_id=324`: 2025-2026 Bahar Dönemi
* `relative=../../../`: Göreceli kök dizin
* `ogrenci_no=2025282042`: Öğrenci okul numarası

---

## 2. Mimari Kararlar ve Güvenlik Uyumluluğu (`[DEC-015]`)

### A) Native CAPTCHA Görseli ve İnsan Girişi (Güvenlik Sınırı)
* **Kural:** Sistem ve yapay zeka yönergeleri gereği CAPTCHA otomatik OCR veya bot ile kırılamaz / bypass edilemez.
* **Mühendislik Çözümü:** `DebisClient.fetchCaptcha()` ile sunucudan CAPTCHA görselinin byte'ları ve `PHPSESSID` oturum çerezi çekilir. Jetpack Compose yerel arayüzünde (`DebisLoginDialog`) görsel öğrenciye gösterilir. Öğrenci kodu kendi gözüyle görüp klavyeyle girer. Form aynı `PHPSESSID` çereziyle post edilerek oturum açılır.

### B) Oturum Canlı Tutucu (Keep-Alive Heartbeat)
* **Sorun:** DEBİS'in hareketsizlik zaman aşımı (idle timeout - ~15 dakika) oturumun hemen kapanmasına yol açabilir.
* **Çözüm:** Arka plan servisi periyodik olarak hafif `sendKeepAlive()` HTTP GET isteği atarak PHP oturum sayacını sıfırlar ve öğrencinin oturumunu düşürmeden gün boyu uyanık tutar.

### C) Sessiz Başlangıç (Silent Bootstrap) & Durum Karşılaştırma (Diffing)
* **İlk Giriş:** `isFirstGradeBoot = true` olduğunda mevcut tüm derslerin notları `grade_snapshot_${user}.json` dosyasına sessizce kaydedilir; öğrenciye eski notlar için bildirim atılmaz.
* **Sonraki Taramalar:** Sadece yeni not açıklandığında (örneğin "İLAN EDİLMEMİŞ" → 88 olduğunda veya Harf Notu çıktığında) `NotificationHelper.showGradeNotification` tetiklenir.

---

## 3. Geliştirilen Dosyalar ve İşlevleri

| Dosya | İşlev |
| :--- | :--- |
| [`data/model/DebisGrade.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/model/DebisGrade.kt) | `CourseGrade`, `DebisCourseOption`, `GradeDifference`, `DebisCaptchaSession` veri modelleri ve JSON serileştiricileri. |
| [`data/parser/DebisParser.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/parser/DebisParser.kt) | Regex ve String splitting ile hafif, pil dostu HTML `<select name="ders">` ve not tablosu çözümleyicisi. |
| [`data/api/DebisClient.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/api/DebisClient.kt) | OkHttpClient tabanlı CAPTCHA görseli indirme, oturum devamlılığı ile login, keep-alive ve not sorgulama motoru. |
| [`data/storage/DebisGradeStateManager.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/storage/DebisGradeStateManager.kt) | Kullanıcı bazlı JSON snapshot saklama, sessiz başlangıç ve not farklarını (diff) hesaplama yöneticisi. |
| [`ui/DebisLoginDialog.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/ui/DebisLoginDialog.kt) | Güvenlik kodunu (CAPTCHA) öğrenciye doğrudan gösteren, yenileme butonu ve insan girişi içeren şık Jetpack Compose dialog'u. |
| [`notification/NotificationHelper.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/notification/NotificationHelper.kt) | `CHANNEL_ID_GRADES` (`deu_debis_grades`) yüksek öncelikli bildirim kanalı ve `showGradeNotification` fonksiyonu. |
| [`worker/SakaiNotificationWorker.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/SakaiNotificationWorker.kt) | Arka plan iş parçacığına 3. adım olarak DEBİS not kontrolü, keep-alive ve diff bildirimlerinin bağlanması. |
| [`ui/MainScreen.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/ui/MainScreen.kt) | Ana ekranda "DEBİS Sınav Notları" ayar anahtarı, oturum rozeti ("Bağlı") ve "Giriş Yap" diyaloğunun bağlanması. |
| [`app/src/test/java/.../DebisParserTest.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/test/java/tr/edu/deu/sakainotifier/DebisParserTest.kt) | Kullanıcının paylaştığı görsel ve payload ile birebir örtüşen kapsamlı birim test paketi. |
| [`docs/DECISIONS.md`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/docs/DECISIONS.md) | `[DEC-015]` mimari karar kaydı. |

---

## 4. Argon Mimari Denetim Bulguları ve Uygulanan Düzeltmeler (Argon Review & Hardening)

Argon tarafından `feature/debis-grade-notifier` dalı baştan sona incelenmiş; 3.8 Flash'ın kurduğu mimari temel (`DebisParser`, `DebisLoginDialog`, `Silent Bootstrap`) onaylanmış, ancak gerçek kullanımda sistemin çalışmasını engelleyecek **7 kritik hata/eksik** tespit edilerek doğrudan bu dalda giderilmiştir:

1. **🚨 Doze-Piercing `AlarmReceiver` ve `BootReceiver` Eksikliği (Giderildi):**
   - 3.8 Flash, DEBİS taramasını yalnızca `SakaiNotificationWorker` içine eklemiş; ancak uygulamanın Xiaomi/Samsung/Android 12+ Doze modunu delen asıl zamanlayıcısı olan [`AlarmReceiver.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/AlarmReceiver.kt) ve [`BootReceiver.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/BootReceiver.kt) sınıflarına eklemeyi unutmuştu. Her iki sınıfa da DEBİS taraması ve Keep-Alive entegre edildi.
2. **🚨 Yalnızca DEBİS Açıkken Servisin Tetiklenmemesi (Giderildi):**
   - `SakaiNotificationWorker`, `AlarmReceiver` ve `MainScreen` içinde `(!checkSakai && !checkMail)` kontrolü yüzünden, öğrenci sadece DEBİS Not Takibini açarsa arka plan servisi hiç başlamıyor veya durduruluyordu. Tüm giriş kapılarına ve zamanlayıcı tetikleyicilerine `checkGrades` (`isGradeMonitoringActive`) eklendi.
3. **🚨 Gece Sessizliğinde (00:00 - 05:00) DEBİS Oturumunun Ölmesi (Giderildi):**
   - Gece 00:00 - 05:00 arasında tüm ağ istekleri durdurulduğunda, DEBİS'in ~20 dakikalık PHP oturumu (`PHPSESSID`) her gece 00:20'de düşecekti. [`AlarmScheduler.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/AlarmScheduler.kt), [`AlarmReceiver.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/AlarmReceiver.kt) ve [`SakaiNotificationWorker.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/SakaiNotificationWorker.kt) güncellenerek gece sessizliğinde bildirim ve ağır taramalar uyutulurken sadece 1 KB'lık `debisClient.sendKeepAlive()` kalp atışının devam etmesi sağlandı.
4. **🚨 Reaktif Olmayan UI, Eksik İlk Senkronizasyon ve Not Tablosu Görünümü (Giderildi):**
   - [`MainScreen.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/ui/MainScreen.kt) içinde `hasDebisSession` reaktif `mutableStateOf` ve `SharedPreferences.OnSharedPreferenceChangeListener` ile bağlandı.
   - [`DebisLoginDialog.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/ui/DebisLoginDialog.kt) içine kayıtlı hesap şifresi (`initialPassword`) otomatik doldurma olarak eklendi (öğrencinin sadece CAPTCHA kodunu yazması yeterli).
   - Giriş başarılı olur olmaz 20 dakika beklemeden **anında ilk ders ve notların çekilmesi (Silent Bootstrap)**, **"Şimdi Kontrol Et 🔄"** butonuna DEBİS kontrolünün dahil edilmesi ve ana ekrana açılır/kapanır **"📋 Kayıtlı DEBİS Ders Notları"** tablosu eklendi.
5. **🚨 Oturum Düştüğünde Sessiz Kalma Sorunu (Giderildi):**
   - Oturum süresi dolduğunda (`IllegalStateException`) çerez sessizce siliniyor, öğrenciye haber verilmiyordu. [`NotificationHelper.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/notification/NotificationHelper.kt) içine tek seferlik `showDebisSessionExpiredNotification()` eklendi.
6. **🚨 `DebisClient.login()` Sahte Başarı (False-Positive) ve Çerez Yenilenmesi (Giderildi):**
   - [`DebisClient.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/api/DebisClient.kt) içinde redirect zincirindeki yeni `Set-Cookie: PHPSESSID` yakalama (`extractLatestSessionCookie`) ve giriş formu tekrar döndüğünde (`name="guvenlik_kod"`) sahte başarı dönmesini engelleyen doğrulama eklendi.
7. **🚨 Sabit Kodlanmış `ogretim_donemi_id = "324"` ve Çift Bildirim Önleme (Giderildi):**
   - [`DebisParser.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/parser/DebisParser.kt) içine `parseActiveTermId()` eklenerek aktif dönem ID'sinin HTML'den dinamik çekilmesi sağlandı; `Final`/`Bütünleme` ile `Harf Notu` aynı anda açıklandığında mükerrer bildirim atılması önlendi.

---

## 5. Argon Kontrol Listesi (Review & Merge Checklist)

- [x] **Git Dalı Kontrolü:** `feature/debis-grade-notifier` dalı aktif ve tüm düzeltmeler işlendi.
- [x] **Birim Testleri:** `DebisParserTest.kt` altındaki 4 test (`testParseCourseSelectOptions`, `testParseGradeTableWithAnnouncedGrades`, `testParseGradeTableWithPendingFinal`, `testParseActiveTermIdFromHtml`) doğrulandı.
- [x] **Cookie & Keep-Alive Devamlılığı:** Redirect çerez yakalama ve gece modu Keep-Alive koruması eklendi.
- [x] **Sessiz Başlangıç & Anlık Senkronizasyon:** İlk girişte anında sessiz başlangıç ve UI not tablosu gösterimi tamamlandı.
