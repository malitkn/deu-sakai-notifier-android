# DEÜ OBS / DEBİS NOT TAKİP MODÜLÜ MİMARİ ÖZETİ

> [!IMPORTANT]
> **Çalışma Dalı:** `feature/debis-grade-notifier`  
> **Tarih:** 2026-11-14  
> **Durum:** Yeni `obs.deu.edu.tr` sistemine göre CAPTCHA'sız otomatik oturum açma, AJAX not tarama ve kalıcı yerel not hafızası ile güncellendi (`[DEC-015]`).

---

## 1. Görevin Amacı ve OBS (`obs.deu.edu.tr`) Akışı

Dokuz Eylül Üniversitesi'nin yeni Öğrenci Bilgi Sistemi (**OBS — `https://obs.deu.edu.tr`**) üzerinden vize, quiz, ödev, final, bütünleme ve harf notlarının otomatik olarak takip edilmesi; yeni bir değerlendirme notu açıklandığında Android push bildirimi gönderilmesi hedeflenmektedir.

### Yeni OBS İstek Zinciri:
1. **Otomatik Oturum Açma (`POST https://obs.deu.edu.tr/login.php`):**
   - Form alanları (`application/x-www-form-urlencoded`): `mail=<eposta_kullanici_adi>&pass=<sifre>&mail_domain=%40ogr.deu.edu.tr`
   - Başarılı girişte `302 Found -> //index.php` ve `Set-Cookie: PHPSESSID=...` döner (CAPTCHA yoktur).
2. **Yetki/Oturum Isıtması ve Aktif Dönem Seçimi (`GET https://obs.deu.edu.tr/modules/ogrenci/notgoruntuleme/index.php`):**
   - Her oturumda `index.ajax.php` çağrısından önce bu sayfanın ziyaret edilmesi zorunludur; aksi halde AJAX uç noktası izin hatası döner.
   - `<select name='donem'>` içindeki en yüksek dönem kimliği (ör. `333` — `2026-2027 Güz`) otomatik seçilir.
3. **Ders Listesi (`POST .../index.ajax.php`):**
   - Gövde: `islem=dersgetir&donem=<termId>` + `X-Requested-With: XMLHttpRequest`
   - Dönen JSON dizisindeki tam `optionValue` (ör. `"1128_194_1"`) benzersiz `courseCode` olarak kullanılır.
4. **Ders Not Tablosu (`POST .../index.ajax.php`):**
   - Gövde: `islem=notgetir&donem=<termId>&ders=<optionValue>`
   - `ISO-8859-9` HTML içindeki `#ogrnottable` tablosundan tüm değerlendirme satırları (`Vize`, `Quiz`, `Ödev`, `Final`, `Bütünleme Notu`, `Yarıyıl Sonu Başarı Notu`) ayrıştırılır.

---

## 2. Kalıcı Yerel Hafıza ve Güç Tasarrufu

* **Kapatıp Açmada Hafıza Koruması:** Kullanıcı ana ekrandaki **"📊 DEBİS / OBS Sınav Notları"** anahtarını kapattığında arka plan istekleri tamamen durur, ancak cihazdaki yerel JSON not snapshot'ı (`deu_debis_grade_state`) silinmez. Anahtar tekrar açıldığında eski notlar için gereksiz bildirim gönderilmez, sadece kapalı kalınan sürede açıklanan yeni notlar yakalanır.
* **Gece Sessizliği (00:00 - 05:00):** Otomatik yeniden giriş mümkün olduğu için gece keep-alive uyanmaları tamamen kaldırılmıştır; cihaz gece boyunca Doze modunda kalır.
* **Sıfır Hardcoded Secret:** Kaynak kod, test veya dokümantasyonda gerçek öğrenci parolası bulunmaz.

---

## 3. Güncellenen Dosyalar

| Dosya | İşlev |
| :--- | :--- |
| [`data/model/DebisGrade.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/model/DebisGrade.kt) | `CourseGrade` (`termId`, dinamik `evaluations` haritası, `formatEvaluationsSummary()`) ve `DebisCourseOption` modelleri. |
| [`data/parser/DebisParser.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/parser/DebisParser.kt) | `donem` seçici, `index.ajax.php` JSON ders ayrıştırıcı ve `#ogrnottable` HTML tablo ayrıştırıcı. |
| [`data/api/DebisClient.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/api/DebisClient.kt) | OkHttpClient tabanlı CAPTCHA'sız OBS oturum açma, `ISO-8859-9` / `UTF-8` kod çözme ve tek noktadan dönem notu çekme motoru. |
| [`data/storage/DebisGradeStateManager.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/data/storage/DebisGradeStateManager.kt) | Kalıcı yerel snapshot yönetimi, dönem değişimi sıfırlaması ve dinamik değerlendirme farkı (diff) hesaplaması. |
| [`notification/NotificationHelper.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/notification/NotificationHelper.kt) | `CHANNEL_ID_GRADES` kanalı üzerinden yeni/güncellenmiş not bildirimleri. |
| [`worker/AlarmScheduler.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/AlarmScheduler.kt) | Gece 00:00 - 05:00 derin uyku tasarrufunun tüm modüller için korunması. |
| [`worker/AlarmReceiver.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/AlarmReceiver.kt) | Doze delen periyodik OBS not kontrolü. |
| [`worker/SakaiNotificationWorker.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/worker/SakaiNotificationWorker.kt) | WorkManager yedek periyodik OBS not kontrolü. |
| [`ui/MainScreen.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/main/java/tr/edu/deu/sakainotifier/ui/MainScreen.kt) | CAPTCHA diyaloğunun kaldırılması, tek dokunuşla otomatik OBS not takibi ve hafıza rozeti/tablo gösterimi. |
| [`app/src/test/java/.../DebisParserTest.kt`](file:///C:/Users/Administrator/Desktop/projects/deu-sakai-notifier-android/app/src/test/java/tr/edu/deu/sakainotifier/DebisParserTest.kt) | Yeni OBS dönem, JSON ders listesi, Quiz/Vize/Final HTML ve JSON serileştirme birim testleri. |
