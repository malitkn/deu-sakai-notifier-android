# GOVERNANCE.md — Proje Yönetişim, Revizyon ve Karar Kayıt Protokolü

Bu belge, **DEU Sakai Notifier Android** (`deu-sakai-notifier-android`) projesinin yaşayan bir mobil sistem olarak nasıl yönetileceğini, revizyonların hangi güvenlik katmanlarından geçeceğini, yerel şifreleme ve pil yönetişimini düzenler. Temel amacı, projenin yapay zeka ajanları (Antigravity CLI) veya geliştiriciler tarafından yapılacak tüm değişikliklerinin güvenli, geriye dönük uyumlu ve mimari bütünlüğü bozmayacak şekilde yürütülmesini garanti altına almaktır.

---

## 1. Revizyon Protokolü (Nasıl Değişiklik Yapılır)

Projede herhangi bir değişiklik, geliştirme veya iyileştirme talebi geldiğinde ajan kod yazmaya veya komut çalıştırmaya başlamadan önce talebi katı bir şekilde iki kategoriden birine sınıflandıracaktır:

### 1.1 Değişiklik Sınıflandırması

* **(a) Kozmetik ve Yüzeysel Değişiklikler:**
  * **Kapsam:** Arayüz metinleri, buton renkleri, padding/margin boşlukları, Compose önizleme şablonları, kod içi docstring satırları.
  * **Prosedür:** Doğrudan koda uygulanabilir. Şartname belgesinde (`docs/ARCHITECTURE.md`) revizyon yapılması zorunlu değildir.
* **(b) Mimari ve Altyapısal Değişiklikler:**
  * **Kapsam:** Arka plan `WorkManager` zamanlama mantığı, OkHttp oturum/çerez yönetimi, `EncryptedSharedPreferences` şema değişiklikleri, yeni Android izinleri (`POST_NOTIFICATIONS`, `BOOT_COMPLETED`), Gradle bağımlılıkları.
  * **Prosedür:** KODA DOKUNMADAN ÖNCE sırasıyla:
    1. Birincil şartname belgesinde (`docs/ARCHITECTURE.md`) ilgili maddeler güncellenir.
    2. Kullanıcıya yazılı olarak sunulur ve açık onay alınır.
    3. Kullanıcı onayı alındıktan SONRA kod geliştirmesine başlanır.

---

### 1.2 Üstü Çizili Tarihçe İlkesi (Strikethrough Preservation)

Mimari bir değişiklik onaylandığında, plan ve şartname belgelerindeki eski maddenin tamamen silinmesi KESİNLİKLE YASAKTIR:

* **Format Kuralı:**
  ```markdown
  ~~eski değer veya şartname maddesi~~ → yeni değer veya şartname maddesi (Tarih: YYYY-AA-GG, Gerekçe: <kısa ve net açıklama>)
  ```

---

## 2. Karar Kaydı (Decision Log — `docs/DECISIONS.md`)

Projede mimari hafızayı korumak için `docs/DECISIONS.md` kütüğü tutulur. Her kritik karar `[DEC-XXX]` formatında işlenecektir.

---

## 3. Mobil ve Yerel İstemci Yönetişimi (Özel Protokol)

Android platformunda çalışan bu dağıtılabilir mobil uygulamada aşağıdaki katı operasyonel kurallar zorunludur:

### 3.1 Yerel Veri Gizliliği ve Şifre Güvenliği
- **Merkezi Sunucu Yasağı:** Hiçbir öğrencinin kullanıcı adı veya şifresi harici bir sunucuya, analitik servisine veya log havuzuna aktarılamaz.
- **Donanım Destekli Şifreleme:** Kimlik bilgileri cihaz üzerinde Android KeyStore tarafından desteklenen `EncryptedSharedPreferences` (veya MasterKeys) ile şifrelenerek saklanır.
- **Log Hijyeni:** Logcat çıktılarında şifreler asla açık metin yazdırılamaz.

### 3.2 Minimum 20 Dakika Kuralı & Sunucu Koruması
- **Katı Alt Sınır:** Kullanıcı arayüzünde yoklama aralığı seçicisi **minimum 20 dakika (1200 saniye)** altına indirilemez.
- **Doze Mode & Pil Tasarrufu:** Arka plan görevleri `WorkManager.PeriodicWorkRequestBuilder` ile yürütülür ve pil tüketimini minimize edecek şekilde işletim sistemi kısıtlarına (Constraints) bağlanır.

### 3.3 Bildirim Güvenilirliği (Android 13+ İzinleri)
- Android 13 (API 33) ve üzerinde `POST_NOTIFICATIONS` çalışma zamanı (runtime) izni kullanıcıdan açıkça talep edilmeli, izin verilmeden bildirim tetiklenemeyeceği kullanıcıya açıklanmalıdır.
- Bildirim kanalları (`NotificationChannel`) "Önemli Duyurular" başlığıyla yüksek öncelikli (`IMPORTANCE_HIGH`) olarak tanımlanmalıdır.

---

## 4. Universal APK Derleme ve Dağıtım Yönetişimi

- **Tek APK Çıktısı:** Dağıtım için mimari bazlı split APK değil, tüm CPU mimarilerini (`arm64-v8a`, `armeabi-v7a`, `x86_64`) kapsayan tek bir evrensel APK üretilmelidir.
- **GitHub Actions Entegrasyonu:** Her GitHub Release etiketi (`v*`) açıldığında otomatik olarak imzalı APK derleyen ve GitHub Release'e ekleyen CI akışı bulunmalıdır.
