# DEU Sakai Notifier Android 📱

Dokuz Eylül Üniversitesi Sakai LMS (Öğrenme Yönetim Sistemi) üzerindeki yeni duyuruları, ödevleri ve bildirimleri periyodik olarak kontrol eden, yeni içerik tespit ettiğinde anlık sesli/titreşimli yerel sistem bildirimi üreten modern **Universal Android (.apk)** uygulaması.

---

## 🌟 Neden Bu Uygulama?

* **🔒 %100 Yerel ve Güvenli (Sıfır Sunucu Maliyeti):**
  Öğrenci numarası ve şifreniz **asla üçüncü şahıs bir sunucuya gitmez**. Bilgileriniz cihazınızın donanım destekli güvenlik kasasında (`Android KeyStore` & `EncryptedSharedPreferences`) şifrelenerek saklanır.
* **🛡️ Üniversite Sunucusu Koruması (Minimum 20 Dk Kısıtı):**
  Uygulama öğrenciler arasında yaygın dağıtıldığında üniversite sunucularının kilitlenmesini veya IP engeline takılmasını önlemek amacıyla yoklama aralığı **en az 20 dakika** olarak sınırlandırılmıştır.
* **🔕 Sessiz Başlatma (Silent Bootstrap):**
  Uygulamayı ilk kurduğunuzda geçmişte yayınlanmış tüm eski duyurular sessizce hafızaya alınır. Telefonunuz tek seferde onlarca eski bildirim sesiyle çalmaz; yalnızca o andan sonra yayınlanan **yeni** duyurularda bildirim alırsınız.
* **🔋 Pil Dostu Arka Plan (`WorkManager`):**
  Android'in en modern `WorkManager` API'si ile Doze Mode kurallarına tam uyumlu çalışır. Telefon yeniden başlatılsa dahi (`BOOT_COMPLETED`) takip kaldığı yerden devam eder.

---

## 📥 Universal APK İndirme ve Kurulum

Herhangi bir merkezi sunucuya ihtiyaç duymadan, tek bir APK dosyasıyla tüm modern Android cihazlara (`arm64-v8a`, `armeabi-v7a`, `x86_64`) kurulabilir.

1. **İndir:** Repodaki **[Releases](../../releases)** veya GitHub Actions sekmelerinden en güncel `deu-sakai-notifier-universal-release.apk` dosyasını telefonunuza indirin.
2. **Kur:** İndirilen APK'ya dokunun ve kurulumu onaylayın.
3. **Bilgilerinizi Girin:** DEU Öğrenci Numaranızı ve Sakai Şifrenizi girin.
4. **Test Edin:** *"Girişi Test Et"* butonuna basarak oturumun başarıyla açıldığını doğrulayın.
5. **Takibi Başlatın:** *"Otomatik Bildirim Takibi"* anahtarını açın. Artık Sakai'deki tüm yeni duyurular telefonunuzun bildirim çubuğuna düşecektir!

---

## 🛠️ Geliştirici & Derleme Rehberi

### Android Studio ile Açma
1. Projeyi bilgisayarınıza klonlayın.
2. **Android Studio**'yu açın ve *"Open"* seçeneğiyle bu klasörü seçin.
3. Gradle senkronizasyonunun tamamlanmasını bekleyin.
4. Menüden **Build > Build Bundle(s) / APK(s) > Build APK(s)** seçeneğine tıklayarak kendi imzalı universal APK'nızı saniyeler içinde üretin.

### Komut Satırından Derleme (Gradle)
```bash
# Windows
.\gradlew.bat assembleRelease

# Linux / macOS
chmod +x gradlew
./gradlew assembleRelease
```
Üretilen APK konumu: `app/build/outputs/apk/release/deu-sakai-notifier-universal-release.apk`

---

## 📜 Belgeler ve Şartname

* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Birincil mimari şartname ve sprint planı
* [docs/DECISIONS.md](docs/DECISIONS.md) — Mimari Karar Kayıtları (ADR)
* [docs/SPEC.md](docs/SPEC.md) — Doğrulanmış DEU Sakai API uç nokta sözleşmesi
