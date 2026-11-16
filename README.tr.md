# 8bitBites

Android için açık kaynaklı yemek, su ve takviye günlüğü. Kotlin, Jetpack Compose
ve Material 3 ile geliştirilir. Günlük kayıtları cihazda tutulur; temel takip
çevrimdışı çalışır.

[English](README.md) · [Derleme](docs/BUILDING.md) · [Veri tabanı](database/README.md)
· [Gizlilik](docs/PRIVACY.md) · [Katkıda bulunma](CONTRIBUTING.md)

**Geliştirme sürümü:** 0.7.0-dev. **Besin veri tabanı:** 0.4.0; 2.193 besin,
bunların 260'ı ETİ ürünü. Android 9 ve üzeri gerekir. Fiziksel cihaz testleri
henüz tamamlanmadı.

## Özellikler

- Yemek kaydı, gram bazlı porsiyonlar, favoriler ve son seçilen öğünü hatırlama.
- Yerel Türkçe besin kataloğu, kişisel besinler ve isteğe bağlı OFF/USDA araması.
- Özel ml/L miktarlarıyla su takibi. 2.000 ml hedef ve 100 ml birim seçilirse
  20 bardak görünür; **700 ml ve üzeri** birimler şişe olarak gösterilir.
- Öğüne eklerken **This is a supplement** kutucuğu; takviye aynı öğünde kalır
  ve besin değerleri bir kez sayılır. Protein, protein tozu ve kreatin ayrıdır.
- Takviye kartında günlük toplam ve her tüketim için yalnızca **protein,
  karbonhidrat ve şeker** gramları görünür. Örneğin 30 g tozdan gelen 24 g protein
  ayrı gösterilir. Eksik etiket değerleri sıfır kabul edilmez.
- İsteğe bağlı hedefler. Protein, karbonhidrat ve yağ hedefleri varsayılan olarak
  kapalıdır. Su hedefi kullanıcı tarafından değiştirilebilir; öneri zorlanmaz.
- Profil fotoğrafının altında günlük özet, su, takviyeler ve eğilimler.
  Öğünler ve Favoriler, Foods içindeki ayrı bölümlere taşındı.
- AI ayarlarında yerel model, OpenAI uyumlu uzak sunucu veya ChatGPT hesabı seçimi.
  Alt menüde seçilen bağlantının simgesi ve adı; AI ekranında yalnızca sohbet.
- Metin/fotoğraf mesajları, önceki öğünü düzeltme ve kaydetmeden önce öğünü inceleme.
- Resmî tarayıcı akışıyla kendi ChatGPT hesabını bağlama ve plan kullanım izni.
  Kullanılabilen modeller hesaptan alınır; bağlantı Android Keystore ile korunur.
  Gerçek hesapla S25 üzerinde giriş ve yanıt henüz doğrulanmadı.
- Cihazda desteklenen modelle veya kendi API anahtarınızla metin/fotoğraf
  tanıma, uygulama içi kamera ve arka planda model indirme.
- Yerel modeli günlükten ayrı çalıştırma; iptal/süre sınırı ve kopyalanabilir
  hata bilgisi. Qwen anahtar istemez; Gemma'nın Hugging Face anahtarı indirme erişimi içindir.
- İsteğe bağlı Health Connect beslenme aktarımı; su, günlük özet ve hızlı ekleme widget'ları.
- Material You ve sürüme yedi hızlı dokunuşla açılan tema paketi.
- Varsayılan İngilizce; Türkçe, Almanca, Fransızca, İspanyolca, İtalyanca,
  Portekizce ve Japonca arayüz seçenekleri.

Su kayıtları bu sürümde cihazda kalır. Temel su tahmini yetişkin profilindeki yaş, cinsiyet, boy ve kiloya göre değişir.
Takviye günlerine özel ek su hedefleri isteğe bağlıdır ve varsayılan olarak sıfırdır;
kreatin/protein tozu için kanıtlanmış bir ek litre katsayısı yoktur. Ekler yalnızca
o gün kaydedilen takviye miktarıyla, ayrı kesik çizgili şişeler olarak gösterilir.
Su widget'ındaki düğmeler uygulamayı açmadan su kaydeder.
AI sonuçları kaydetmeden önce kullanıcı tarafından incelenir. ChatGPT hesabıyla
bağlantı için API anahtarı gerekmez; uygun plan ve kullanım izni gerekir.
Uzak sunucunun API anahtarı ayrı bir bağlantıdır. Sohbet geçmişi oturumda tutulur.
Hatırlatıcılar, tarifler ve genişletilmiş Health
Connect kontrolleri planlanan işlerdir.

Veriler gönüllü katkılarla derlenmiştir. Değerler hatalı olabilir; lütfen ürünün
ambalaj etiketini kontrol edin.

`database/` altındaki sürümlü SQLite dosyası uygulamadan bağımsız güncellenebilir.
Ayarlar'dan veri tabanı güncellemesi veya uyumlu SQLite içe aktarma yapılabilir.
İsim 8bitBites oldu; mevcut verilerin korunabilmesi için Android paket kimliği
`org.eightbit.bite` olarak kalır. Kod lisansı GPL-3.0-or-later'dır.
