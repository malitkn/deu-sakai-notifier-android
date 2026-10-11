# SKILL-CATALOG.md — Mobil Proje Kural ve Beceri Kataloğu

Bu belge, **DEU Sakai Notifier Android** projesinde devreye giren özel kural setlerini, mimari kalıpların ve mobil güvenlik standartlarını tanımlar.

---

## Kategori 1: Mobil & Android İstemci Standartları (Jetpack & WorkManager)

> **Odak Alanı:** Pil koruması, yerel şifreleme, runtime izinleri, sessiz başlatma.

* **Android WorkManager ile Güvenilir Arka Plan Görevleri:**
  - Arka plan kontrolü `CoroutineWorker` üzerinden yürütülmelidir.
  - Cihaz yeniden başladığında `BOOT_COMPLETED` alıcısı ile periyodik iş otomatik yeniden planlanmalıdır.
  - Ağ bağlantısı kısıtı (`NetworkType.CONNECTED`) konulmalıdır; internetsiz ortamda gereksiz döngü çalıştırılamaz.
* **Sıfır Spam İlkesi (Silent Bootstrap):**
  - Uygulama ilk kez kurulduğunda veya ilk kez başlatıldığında mevcut duyurular yerel `HashSet` / SharedPreferences'a sessizce eklenir. Kullanıcıya tek seferde onlarca eski bildirim sesi çıkarılamaz.
* **Android 13+ Bildirim Kanalı Standartları:**
  - `POST_NOTIFICATIONS` runtime izni dinamik istenmelidir.
  - Bildirime tıklandığında `PendingIntent` ile ilgili duyurunun web URL'si doğrudan sistem tarayıcısında açılmalıdır.

---

## Kategori 2: 3. Parti API & Entegrasyon Standartları (DEU Sakai)

> **Odak Alanı:** Sıfır varsayım (Zero Assumption), oturum yaşam döngüsü, throttling.

* **Doğrulanmış xlogin Akışı:**
  - `POST https://online.deu.edu.tr/portal/xlogin`
  - Headers: `Content-Type: application/x-www-form-urlencoded`, `Referer: https://online.deu.edu.tr/portal/xlogin`
  - Cookies: `SAKAI2SESSIONID` ve `SAKAI_AFFINITY` çerezlerinin yakalanması.
* **Bildirim Uç Noktası:**
  - `GET https://online.deu.edu.tr/api/users/me/notifications`
* **Minimum 20 Dakika Kısıtı:**
  - Üniversite sunucularının toplu öğrenci kullanımında kilitlenmesini engellemek için kontrol aralığı kesinlikle 20 dakikadan daha sık ayarlanamaz.
