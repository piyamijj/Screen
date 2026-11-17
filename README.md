# Screen

Ekranın üzerinde sürüklenebilir bir düğme gösterir; düğmeye dokununca ekran görüntüsünü Pictures/Screenshots klasörüne PNG olarak kaydeder. Android 5.0 ve üzerini destekler. © Piyami Polat.

## Kurulum ve kullanım

`Screen.apk` dosyasını telefona aktarın ve açın. Gerekirse cihaz ayarlarından bu kaynak için bilinmeyen kaynaklardan uygulama yüklemeye izin verin.

Uygulamada **Başlat** düğmesine bir kez dokunun. İstenirse depolama izni verin (Android 6–9); düğmenin görünmesi için izin sorusunda **Evet**'e dokunup diğer uygulamaların üzerinde gösterme iznini açın ve uygulamaya dönün. Ardından Android'in ekran paylaşımı penceresinde **Şimdi başlat**'ı onaylayın. Bu sistem onayı servis her başlatıldığında bir kez zorunludur ve kaldırılamaz. Başlatmak görüntü kaydetmez.

Uygulama arka plana geçer; ekrandaki yuvarlak düğmeyi sürükleyebilir ve istediğiniz kadar görüntü almak için ona dokunabilirsiniz. Düğme çekim sırasında kısa süreliğine gizlenir. Uygulamayı tekrar açıp **Durdur**'a veya servisin bildirimindeki **Durdur** eylemine basarak düğmeyi ve ekran paylaşımını kapatabilirsiniz. Android 13 ve sonrasında bildirim izni isteğe bağlıdır; izin verilmezse bildirimdeki durdurma eylemi görünmeyebilir.

Ekran paylaşımı sistem tarafından durdurulursa yeniden başlatmak ve onay vermek gerekir. Ekran paylaşımı penceresinde tüm ekranı seçin. Bazı uygulamaların korumalı içerikleri ekran görüntüsünde görünmeyebilir.

## Derleme

Eski cihazlar için ayrı paket adlı `Screen-Eski.apk` (Android 5–8.1) denenebilir; normal Screen uygulamasını güncellemez ve onunla yan yana kurulabilir. Bu sürüm Android 9 ve üzerinde çalışmaz; bu cihazlarda normal `Screen.apk` kullanılmalıdır. Android 6–8.1'de izinler kapatılmışsa uygulama ayarlarından tekrar açılmalıdır.

JDK 17, Android SDK platform 34 ve build-tools 34.0.0 ile `ANDROID_HOME` veya `ANDROID_SDK_ROOT` ayarlayın. `KEYSTORE_PATH=/anahtar/yolu ./gradlew signCompatRelease` küçültülmüş, eski imza uyumluluğuna göre imzalanmış `Screen.apk` üretir (takma ad `key`, parola `android`). Alternatif olarak aynı anahtarla `KEYSTORE_PATH=/anahtar/yolu ./build.sh` çalıştırılabilir; bu yöntem küçültme yapmaz. İki yöntem de imza için minimum API 16'yı kullanır; uygulamanın minimum Android sürümü 5.0 (API 21) olarak kalır. Uygulamayı güncellemek için önceki sürümle aynı imza anahtarı gereklidir.

`KEYSTORE_PATH=/anahtar/yolu ./build-eski.sh` ayrı kaynak ve manifest kullanarak `Screen-Eski.apk` üretir; `Screen.apk` dosyasını değiştirmez.

Telif © Piyami Polat
