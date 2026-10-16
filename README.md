# DEU Sakai Notifier 📱

Dokuz Eylül Üniversitesi Sakai LMS sisteminde bildirimlerin çalışmaması/gelmemesi sorununu çözen, yeni duyuru ve ödevleri arka planda kontrol edip telefonunuza anında sesli ve titreşimli sistem bildirimi gönderen bağımsız bir Android uygulamasıdır.

---

## 🌟 Öne Çıkan Özellikler

* 🔔 **Çalışmayan Bildirimlere Çözüm:** Sakai'ye her gün manuel olarak girip duyuru kontrol etme derdini bitirir; yeni bir duyuru yayınlandığında telefonunuzun bildirim çubuğunda anında görünür.
* ✉️ **Öğrenci E-postası Takibi (İsteğe Bağlı):** Dokuz Eylül Webmail (`ogrmail.deu.edu.tr`) sistemiyle entegre çalışır. Hesabınıza öğrenci e-posta adresinizi ekleyerek yeni gelen öğrenci maillerini de anlık bildirim olarak alabilirsiniz.
* 👥 **Çoklu Hesap Desteği (ÇAP & Yan Dal):** Çift anadal (ÇAP) veya yan dal yapan öğrenciler için birden fazla hesabı aynı anda takip eder. Bildirimler ilgili öğrenci numarasıyla etiketlenir (`2021123456: [Ders] Başlık` veya `✉️ 2021123456: [Gönderen] Konu`).
* ✏️ **Kolay Hesap Düzenleme:** Önceden eklediğiniz hesapların bilgilerini veya e-posta adresini dilediğiniz zaman düzenleyip güncelleyebilirsiniz.
* 🔒 **%100 Güvenli (Sıfır Harici Sunucu):** Giriş bilgileriniz harici hiçbir sunucuya gitmez. Tamamen kendi telefonunuzda, cihazın donanım kasasında (`Android KeyStore` & `EncryptedSharedPreferences`) şifrelenerek saklanır.
* 🔋 **Pil Dostu & Akıllı Takip:** Arka planda Android'in modern `WorkManager` altyapısıyla çalışır, şarj tüketmez.
* 🚀 **Otomatik Güncelleme Uyarısı:** Yeni bir sürüm çıktığında uygulama içerisinden tek tıkla doğrudan güncelleyebilirsiniz.
* 🗑️ **Geçmişi Temizleme & Filtreleme:** Bildirimlerinizi öğrenci numarasına göre filtreleyebilir veya geçmişi tek tıkla temizleyebilirsiniz.

---

## 📥 İndirme ve Kurulum

Herhangi bir mağazaya ihtiyaç duymadan doğrudan APK olarak kurulabilir:

1. **İndir:** En güncel [Universal Release APK](../../releases/latest) dosyasını telefonunuza indirin.
2. **Kur:** İndirilen `.apk` dosyasına dokunun ve kurulumu onaylayın.
3. **Giriş Yap:** Öğrenci numaranızı, şifrenizi ve dilerseniz öğrenci e-posta adresinizi girip *"Hesabı Doğrula ve Kaydet"* butonuna basın. (ÇAP/yan dal yapıyorsanız ikinci hesabınızı da ekleyebilirsiniz.)
4. **Takibi Başlatın:** *"Sakai Takibi"* ve/veya *"Öğrenci E-postası Takibi"* anahtarını açın. Artık tüm yeni Sakai duyuruları ve öğrenci e-postaları anında cebinizde!

> 💡 **Sürüm Güncellemeleri Hakkında:**  
> `v1.2.9` sürümünden itibaren tüm APK sürümleri kalıcı ve sabit bir dijital sertifika ile imzalanmaktadır. Yeni bir sürüm çıktığında mevcut uygulamayı silmeden doğrudan üzerine kurabilir (in-place update); kayıtlı öğrenci hesaplarınızı ve bildirim izinlerinizi kaybetmeden anında güncelleyebilirsiniz. *(Eski `v1.2.8` veya öncesi bir sürümden `v1.2.9`'a geçerken imza altyapısı değiştiği için bir defaya mahsus eski sürümü silip `v1.2.9`'u kurmanız gerekir).*

---

### ⚠️ Kesintisiz Bildirim İçin Önemli Cihaz Ayarları

Android'in (özellikle Xiaomi, Samsung ve Huawei) arka planda çalışan servisleri uyutmasını engellemek için kurulum sonrası şu ayarları yapmanız önerilir:

* 🔋 **Pil Tasarrufu (Kısıtlama Yok):**  
  Uygulama simgesine basılı tutun > **Uygulama Bilgisi** > **Pil / Pil Tasarrufu** bölümünden **"Kısıtlama Yok" (Unrestricted)** seçeneğini işaretleyin. *(Uygulama açılışındaki sarı uyarı kartında yer alan "Pil Ayarını Aç ⚙️" butonuna basarak da bu ekrana doğrudan ulaşabilirsiniz.)*
* ⏸️ **Kullanılmadığında Etkinliği Duraklat:**  
  Yine Uygulama Bilgisi ekranında bulunan **"Kullanılmadığında uygulama etkinliğini duraklat"** (veya *"Kullanılmıyorsa izinleri kaldır ve yer aç"*) seçeneğini **KAPATIN**. Böylece uygulamayı günlerce açmasanız dahi arka plan kontrolü durmaz.
* 🚀 **Otomatik Başlatma (Xiaomi / MIUI / HyperOS):**  
  Xiaomi cihaz kullanıyorsanız Uygulama Bilgisi ekranındaki **"Otomatik Başlatma" (Autostart)** iznini aktif edin.

---

## 🛠️ Kendiniz Derlemek İsterseniz

```bash
git clone https://github.com/malitkn/deu-sakai-notifier-android.git
cd deu-sakai-notifier-android
./gradlew assembleRelease
```
Üretilen APK konumu: `app/build/outputs/apk/release/deu-sakai-notifier-universal-release.apk`
