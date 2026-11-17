# Screen

Ekranın üzerinde sürüklenebilir bir düğme gösterir; düğmeye dokununca ekran görüntüsünü Pictures/Screenshots klasörüne PNG olarak kaydeder. Android 5.0 ve üzerini destekler. © Piyami Polat.

## Kurulum ve kullanım

`Screen.apk` dosyasını telefona aktarın ve açın. Gerekirse cihaz ayarlarından bu kaynak için bilinmeyen kaynaklardan uygulama yüklemeye izin verin.

Uygulamada **Başlat** düğmesine bir kez dokunun. İstenirse depolama izni verin (Android 6–9); düğmenin görünmesi için izin sorusunda **Evet**'e dokunup diğer uygulamaların üzerinde gösterme iznini açın ve uygulamaya dönün. Ardından Android'in ekran paylaşımı penceresinde **Şimdi başlat**'ı onaylayın. Bu sistem onayı servis her başlatıldığında bir kez zorunludur ve kaldırılamaz. Başlatmak görüntü kaydetmez.

Uygulama arka plana geçer; ekrandaki yuvarlak düğmeyi sürükleyebilir ve istediğiniz kadar görüntü almak için ona dokunabilirsiniz. Düğme çekim sırasında kısa süreliğine gizlenir. Uygulamayı tekrar açıp **Durdur**'a veya servisin bildirimindeki **Durdur** eylemine basarak düğmeyi ve ekran paylaşımını kapatabilirsiniz. Android 13 ve sonrasında bildirim izni isteğe bağlıdır; izin verilmezse bildirimdeki durdurma eylemi görünmeyebilir.

Ekran paylaşımı sistem tarafından durdurulursa yeniden başlatmak ve onay vermek gerekir. Ekran paylaşımı penceresinde tüm ekranı seçin. Bazı uygulamaların korumalı içerikleri ekran görüntüsünde görünmeyebilir.

## Derleme

JDK 17, Android SDK platform 34 ve build-tools 34.0.0 ile `ANDROID_HOME` veya `ANDROID_SDK_ROOT` ayarlayın. `./gradlew assembleRelease` küçültülmüş ve varsayılan debug anahtarıyla imzalanmış APK üretir. Kendi anahtarınızı kullanmak için `KEYSTORE_PATH` değişkenini ayarlayın (takma ad `key`, parola `android`). Alternatif olarak aynı anahtarla `KEYSTORE_PATH=/anahtar/yolu ./build.sh` çalıştırılabilir. `build.sh`, JDK ve Android SDK araçlarını kullanır, küçültme yapmaz.

Telif © Piyami Polat
