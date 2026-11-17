# Screen

Tek düğmeyle ekran görüntüsü alır ve Pictures/Screenshots klasörüne PNG olarak kaydeder. Android 5.0 ve üzerini destekler. © Piyami Polat.

## Kurulum ve kullanım

`Screen.apk` dosyasını telefona aktarın ve açın. Gerekirse cihaz ayarlarından bu kaynak için bilinmeyen kaynaklardan uygulama yüklemeye izin verin. Düğmeye basınca Android'in ekran görüntüsü alma izin penceresini onaylayın; eski Android sürümlerinde depolama izni de gereklidir. Uygulama çekimden önce arka plana geçer.

## Derleme

JDK 17, Android SDK platform 34 ve build-tools 34.0.0 ile `ANDROID_HOME` veya `ANDROID_SDK_ROOT` ayarlayın. `./gradlew assembleRelease` küçültülmüş ve varsayılan debug anahtarıyla imzalanmış APK üretir. Kendi anahtarınızı kullanmak için `KEYSTORE_PATH` değişkenini ayarlayın (takma ad `key`, parola `android`). Alternatif olarak aynı anahtarla `KEYSTORE_PATH=/anahtar/yolu ./build.sh` çalıştırılabilir. `build.sh`, JDK ve Android SDK araçlarını kullanır, küçültme yapmaz.

Telif © Piyami Polat
