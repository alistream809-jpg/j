# ChestESP (Fabric 1.21.1)

Client-side Fabric mod: sandik, trapped chest, ender chest, varil ve shulker box'lari duvarlarin arkasindan renkli cerceveyle gosterir.
Konteynerler yuklenmis chunk'lardaki gercek block entity'lerden okunur (sahte/sabit konum yok).

## Ozellikler
- Tum konteyner tipleri icin ayri renk + ac/kapat, cift sandik birlestirme
- Gercek geometri ile cizilen cerceve (kalinlik ayarlanabilir), seffaf dolgu, duvar arkasi gorunum (depth test kapali)
- Render mesafesi, mesafeye gore solma, cerceve/dolgu opaklik
- Koyu cam (glass) arayuz: kenar cubugu, toggle, slider, renk secici, keybind degistirme
- Ayarlar `.minecraft/config/chestesp.json` icinde kalici
- "Disable on multiplayer servers" secenegi

## Tuslar (Controls > ChestESP ya da menu icinden degistirilebilir)
| Islem | Varsayilan |
|---|---|
| Ayar menusu | Right Shift |
| ChestESP ac/kapat | H |
| Tip bazli ac/kapat (5 adet) | atanmamis |

## Kurulum (SKLauncher)
1. SKLauncher'i ac -> surum secimi -> **1.21.1** sec ve "Fabric" secenegini isaretle (Fabric loader ile 1.21.1 kur).
2. Modrinth'ten **Fabric API (1.21.1)** indir. `Win+R` -> `%appdata%\.minecraft\mods` klasorune koy (yoksa olustur).
3. `chestesp-1.0.0.jar` dosyasini ayni `mods` klasorune koy.
4. Fabric 1.21.1 profiliyle oyunu baslat (Java 21 gerekir; SKLauncher 1.21.1 icin genelde otomatik indirir).
5. Dunyaya gir, **Right Shift** ile menuyu ac.

## Derleme
- GitHub: repoyu push et -> Actions > "Build ChestESP" -> artifact `ChestESP-project` (kaynak + JAR zip'i).
- Yerel: Gradle 8.10+ ve JDK 21 kuruluyken `gradle build` -> `build/libs/chestesp-1.0.0.jar`.

## Uyari
Cogu multiplayer sunucusu bu tur modlari yasaklar. Yalnizca izin verilen yerlerde kullan.
