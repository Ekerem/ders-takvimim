# Ders Takvimim

Özel derslerini telefonundaki Google Takvim'den okuyup kutu olarak gösterir. Ücreti alınan dersler yeşil, alınmayanlar kırmızı. Her kutunun altında o öğrencinin o ana kadar ücreti alınmamış ders sayısı yazar.

*made by us*

## Nereden açılır

- **Android (APK):** `https://github.com/Ekerem/ders-takvimim/releases/latest/download/ders-takvimim.apk`
- **Web:** `https://ekerem.github.io/ders-takvimim/` (takvime yalnızca APK içinde bağlanır)

APK, internet varken web sürümünü açar; böylece web'e gelen her değişiklik uygulamaya da gelir, yeni APK kurmak gerekmez. Açılan son sürüm cihazda saklanır (web/sw.js), internet yokken o açılır.

## Nasıl çalışır

- Başlığında **"Özel Ders"** geçen etkinlikler ders sayılır (ör. "Aysima Özel Ders"). Kelime Ayarlar'dan değişir.
- Ödeme durumu etkinliğin Google Takvim'deki rengidir: **Fesleğen (yeşil) = ödendi**, **Domates (kırmızı) = ödenmedi**. Rengi hiç değiştirilmemiş dersler ödenmemiş sayılır.
- **Kırmızı kutuya dokun:** ders ödendi olur (5 saniye içinde "Geri al" ile geri alınır).
- **Yeşil kutuya dokun / herhangi bir kutuya basılı tut:** dersi aç, düzenle, sil.
- Haftalık derslerde bir haftayı işaretlemek yalnızca o haftayı değiştirir.
- Aynı öğrencinin aynı saatte iki kaydı varsa tek kutu gösterilir, işaret ikisine birden uygulanır.

## Arkadaşlarla paylaşma

APK bağlantısını gönder. Uygulama ilk açılışta takvim izni ister ve **o kişinin telefonundaki Google hesabının** takvimine bağlanır; başkasının derslerini görmez. Birden fazla takvimi olan, Ayarlar'dan hangisinin kullanılacağını seçer.

## Yeni sürüm nasıl çıkarılır

1. `web/index.html` dosyasında değişikliği yap.
2. `web/index.html` içindeki `APP_VERSION` ile `web/version.json` içindeki `version` değerini aynı yeni sayıya yükselt (ör. `1.0.0` → `1.1.0`).
3. `main` dalına gönder. GitHub Actions web sayfasını yayınlar, APK'yı derler ve **Releases** bölümüne yükler.
4. Uygulamadaki **🔄 Güncelle** düğmesi yeni sürümü bulur; telefondaki uygulama bir sonraki açılışta zaten yeni sürümle açılır.

APK'nın kendisinde (Android kodu, ör. `CalendarBridge.java`) bir değişiklik olursa `version.json` içindeki `apkMin` değerini de yükselt; eski APK'lar "Yeni APK'yı indir" bağlantısını gösterir.

## Klasörler

| Klasör | İçerik |
|---|---|
| `web/` | Uygulamanın tamamı (tek HTML dosyası), `sw.js` ve `version.json` |
| `android/` | Web sayfasını gösteren küçük Android uygulaması ve takvim köprüsü |
| `.github/workflows/` | Otomatik yayın ve APK derleme |
| `config.json` | GitHub kullanıcı adı ve depo adı |

## İlk kurulumda bir kez

Deponun **Settings → Pages** sayfasında **Source** olarak **GitHub Actions** seç.

## Not

APK, depodaki `android/app/derstakvimim.keystore` anahtarıyla imzalanır; bu sayede yeni APK eskisinin üstüne kurulur. Arkadaşlar arası dağıtım için yeterlidir; Play Store'a yüklemek için ayrı, gizli bir anahtar kullanılmalıdır.
