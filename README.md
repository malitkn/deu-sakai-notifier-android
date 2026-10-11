# DEU Sakai Notifier 📱

Dokuz Eylül Üniversitesi Sakai LMS sisteminde bildirimlerin çalışmaması/gelmemesi sorununu çözen, yeni duyuru ve ödevleri arka planda kontrol edip telefonunuza anında sesli ve titreşimli sistem bildirimi gönderen bağımsız bir Android uygulamasıdır.

---

## 🌟 Öne Çıkan Özellikler

* 🔔 **Çalışmayan Bildirimlere Çözüm:** Sakai'ye her gün manuel olarak girip duyuru kontrol etme derdini bitirir; yeni bir duyuru yayınlandığında telefonunuzun bildirim çubuğunda anında görünür.
* 👥 **Çoklu Hesap Desteği (ÇAP & Yan Dal):** Çift anadal (ÇAP) veya yan dal yapan öğrenciler için birden fazla hesabı aynı anda takip eder. Bildirimler ilgili öğrenci numarasıyla etiketlenir (`2021123456: [Ders] Başlık`).
* 🔒 **%100 Güvenli (Sıfır Harici Sunucu):** Giriş bilgileriniz harici hiçbir sunucuya gitmez. Tamamen kendi telefonunuzda, cihazın donanım kasasında (`Android KeyStore` & `EncryptedSharedPreferences`) şifrelenerek saklanır.
* 🔋 **Pil Dostu & Akıllı Takip:** Arka planda Android'in modern `WorkManager` altyapısıyla çalışır, şarj tüketmez.
* 🚀 **Otomatik Güncelleme Uyarısı:** Yeni bir sürüm çıktığında uygulama içerisinden tek tıkla doğrudan güncelleyebilirsiniz.
* 🗑️ **Geçmişi Temizleme & Filtreleme:** Bildirimlerinizi öğrenci numarasına göre filtreleyebilir veya geçmişi tek tıkla temizleyebilirsiniz.

---

## 📥 İndirme ve Kurulum

Herhangi bir mağazaya ihtiyaç duymadan doğrudan APK olarak kurulabilir:

1. **İndir:** En güncel [Universal Release APK](../../releases/latest) dosyasını telefonunuza indirin.
2. **Kur:** İndirilen `.apk` dosyasına dokunun ve kurulumu onaylayın.
3. **Giriş Yap:** Öğrenci numaranızı ve şifrenizi girip *"Hesabı Doğrula ve Kaydet"* butonuna basın. (ÇAP/yan dal yapıyorsanız ikinci hesabınızı da ekleyebilirsiniz.)
4. **Takibi Başlatın:** *"Takip Aktif"* anahtarını açın. Artık tüm yeni Sakai duyuruları anında cebinizde!

---

## 🛠️ Kendiniz Derlemek İsterseniz

```bash
git clone https://github.com/malitkn/deu-sakai-notifier-android.git
cd deu-sakai-notifier-android
./gradlew assembleRelease
```
Üretilen APK konumu: `app/build/outputs/apk/release/deu-sakai-notifier-universal-release.apk`
