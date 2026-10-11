# AGENTS.md — Kod Ajanı Davranış ve Çalışma Protokolü

Bu belge, **DEU Sakai Notifier Android** (`deu-sakai-notifier-android`) projesinde görev alan Antigravity CLI (kod ajanı) için bağlayıcı ve katı kurallı operasyonel protokolü tanımlar. Ajan, projede herhangi bir işlem yapmadan önce bu protokolü okumak ve geliştirme süreci boyunca buradaki tüm kurallara istisnasız, harfiyen ve sorgusuzca uymakla yükümlüdür. Bu kurallar birer tavsiye veya genel prensip değil; ajanın ihlal etmesine kesinlikle izin verilmeyen katı yürütme emirleridir.

---

## 0. Çoklu-Repo ve Bağlam Farkındalığı (Multi-Repo Awareness)

Geliştirici aynı çalışma istasyonunda birden fazla bağımsız projeyi (Python backend, web, masaüstü, mobil araçlar vb.) eşzamanlı olarak yürütmektedir. Ajanın projeler arası kod, kural veya bağlam sızdırması KESİNLİKLE YASAKTIR:

- **Aktif Repo Doğrulaması:** Ajan herhangi bir komut çalıştırmadan veya dosyayı değiştirmeden önce geçerli çalışma dizinini ve git remote/status bilgisini doğrulamak zorundadır:
  - Hedef Proje: `DEU Sakai Notifier Android`
  - Beklenen Repo Tanımlayıcısı: `deu-sakai-notifier-android`
  - Doğrulama Komutu: `git status` / `git rev-parse --show-toplevel`
- **İzole Bağlam İlkesi:** Başka bir projenin paketleri, mimari alışkanlıkları, stil kuralları veya Python kodları bu Android Kotlin projesine referans gösterilemez veya doğrudan taşınamaz.
- **Commit ve Issue Kapsamı:** Tüm commit mesajlarında ve issue başlıklarında ait olunan modül/repo kapsamı açıkça belirtilmelidir (`[deu-sakai-notifier-android]`).

---

## 1. Issue Disiplini

- **Issue Açmadan Başlama Yasağı:** Her sprint veya geliştirme fazı (`Sprint N` veya `Task N`) için GitHub üzerinde bağımsız bir issue açılmadan o kapsama ait hiçbir kod yazılamaz, komut çalıştırılamaz veya dosya oluşturulamaz.
- **Sabit Başlık Formatı:** Issue başlığı harfi harfine şu formatta olmak zorundadır:
  ```text
  [deu-sakai-notifier-android] Sprint N: <sprint/görev adı>
  ```
  `<sprint/görev adı>` kısmı mimari plandaki (`docs/ARCHITECTURE.md`) başlık ile karakteri karakterine birebir aynı olacaktır.
- **Checklist Kopyalama Zorunluluğu:** Issue açılırken, mimari planda o sprint altında yer alan tüm checklist maddeleri (`- [ ]`) eksiksiz olarak issue gövdesine/açıklamasına kopyalanacaktır. Maddelerin sırası veya içeriği değiştirilemez.
- **Kapatma Kriteri (Done Tanımı):** Bir issue, açıklamasındaki **HER BİR** checklist maddesi tamamlanıp `[x]` olarak işaretlenmeden ASLA kapatılamaz. Kısmi tamamlanmış bir sprint kesinlikle "done" kabul edilemez; tamamlanmamış madde varken sonraki göreve geçilemez.

---

## 2. Commit Disiplini

- **Tek Madde — Tek Commit Kuralı:** Her checklist maddesi bağımsız olarak kendi commit'ine sahip olmak zorundadır. Birden fazla checklist maddesini tek bir commit altında birleştirmek KESİNLİKLE YASAKTIR.
- **Sabit Commit Formatı:** Commit mesajları istisnasız aşağıdaki şablona uyacaktır:
  ```text
  <tip>(<scope>): <kısa ve net açıklama>
  ```
  - `<tip>` değeri yalnızca şunlardan biri olabilir: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.
  - `<scope>` değeri çalışılan sprint veya modül adıdır (Örn: `sprint-1`, `auth`, `ui`, `worker`, `storage`, `notification`).
  - Örnek: `feat(ui): implement compose login and interval configuration screen`
  - Örnek: `feat(worker): implement SakaiNotificationWorker using Android WorkManager`
  - Örnek: `chore(deps): configure gradle plugins and compose dependencies`
- **İngilizce ve Spesifik Mesajlar:** Commit mesajı tamamen İngilizce yazılacaktır. Açıklama kısa, teknik ve neyin değiştiğini açıkça belirtecektir. Muğlak ifadeler (`update files`, `wip`, `fixes`, `changes`) KESİNLİKLE KULLANILAMAZ.
- **Commit Öncesi Kullanıcı Özeti:** Bir commit oluşturulmadan hemen önce, yapılan değişikliğin özeti (etkilenen dosyalar ve yapılma amacı) kullanıcıya açık ve yazılı olarak sunulacaktır.

---

## 3. Plan Sadakati (En Kritik Kural)

- **Sprint Öncesi Planı Okuma Zorunluluğu:** Ajan, her göreve başlamadan önce repoda yer alan birincil mimari plan belgesini (`docs/ARCHITECTURE.md`) baştan sona okumak zorundadır. Kendi hafızasından veya tahminlerinden hareketle geliştirme yapamaz.
- **Değiştirilemez Mimari Kararlar:** Aşağıda listelenen temel mimari kararlar KESİNLİKLE DEĞİŞTİRİLEMEZ:
  - **Stack:** Android (Kotlin, Jetpack Compose, Material 3, Android WorkManager, OkHttp 4, EncryptedSharedPreferences, NotificationManager, Gradle).
  - **Minimum 20 Dakika Kısıtı:** Kullanıcı yoklama sıklığını serbestçe seçebilecek ancak kod seviyesinde minimum 20 dakika (1200 saniye / `WorkManager.PeriodicWorkRequest` alt sınırı) altına inilmesine izin verilmeyecektir.
  - **Sıfır Sunucu & Cihazda İzolasyon:** Öğrenci kullanıcı adı ve şifresi merkezi hiçbir sunucuya gönderilmez; yalnızca kullanıcının telefonunda `EncryptedSharedPreferences` / Android KeyStore ile saklanır.
  - **Sessiz Başlatma (Silent Bootstrap):** Uygulama ilk kez çalıştırıldığında geçmiş tüm bildirimler yerel veritabanına/state'e sessizce işlenir; kullanıcıya eski bildirim spam'i yapılmaz, sadece yeni diff'ler Android bildirim çubuğuna düşer.
  - **Zero Assumption Kuralı:** Sakai yanıtı beklenenden farklıysa asla sessizce varsayım yapılmaz; hata loglanır ve sistem durur.
- **Teknik Engel ve Çelişkilerde Durma Kuralı:** Ajan mimariyle çelişen bir durumla karşılaşırsa KENDİ BAŞINA ÇÖZÜM ÜRETİP SESSİZCE UYGULAYAMAZ. Durumu ayrıntısıyla raporlar, kullanıcıdan açık yönlendirme bekler.

---

## 4. Doğrulama Zorunluluğu (Verification Obligations)

Ajan her değişiklik sonrasında projenin teknik yığınına uygun doğrulama adımlarını çalıştırmakla yükümlüdür:
- Kod Bütünlüğü / Syntax: Kotlin dosyaları şema ve syntax hatalarına karşı doğrulanacaktır.
- Manifest & İzinler: `POST_NOTIFICATIONS`, `INTERNET`, `RECEIVE_BOOT_COMPLETED` izinleri doğrulanacaktır.
- Testler: Model ayrıştırma ve kriptografik hash birim testleri hatasız tamamlanacaktır.

---

## 5. Sapma Anında Davranış

- **Varsayım Yapma Yasağı:** Herhangi bir belirsizlik tespit edildiğinde ajan derhal DURUR ve kullanıcıya sorar.
- **Kullanıcı Adına Karar Alma Yasağı:** Ajan hiçbir koşulda plan dışına çıkamaz, planda olmayan kütüphaneleri kendiliğinden ekleyemez.

---

## Proje Görev / Sprint Listesi Referansı

| Sprint | Başlık | Durum |
| :--- | :--- | :--- |
| **Sprint 0** | [deu-sakai-notifier-android] Sprint 0: Android Project Scaffolding & Architecture Setup | Tamamlandı |
| **Sprint 1** | [deu-sakai-notifier-android] Sprint 1: Sakai HTTP & Authentication Client (OkHttp) | Tamamlandı |
| **Sprint 2** | [deu-sakai-notifier-android] Sprint 2: Secure Storage & Deduplication State (EncryptedPreferences) | Tamamlandı |
| **Sprint 3** | [deu-sakai-notifier-android] Sprint 3: Background Worker & Native Notification Manager (WorkManager) | Tamamlandı |
| **Sprint 4** | [deu-sakai-notifier-android] Sprint 4: Jetpack Compose User Interface & Configuration | Tamamlandı |
| **Sprint 5** | [deu-sakai-notifier-android] Sprint 5: Universal APK Build Pipeline & GitHub Actions Release CI | Tamamlandı |
