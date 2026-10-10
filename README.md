# D-Link DWR960 Manager

App Android nativa (Kotlin + Jetpack Compose) per monitorare e gestire il router **D-Link DWR-960**, che non ha un'app ufficiale.

Sviluppata da **Clacson**.

## Funzioni
- Dashboard: segnale (con legenda delle tacche), banda, cella, IP, velocità download/upload, ping, consumo dati con limite personalizzabile
- Dispositivi collegati
- SMS: lista, lettura, invio, eliminazione
- Rete: tipo di rete (Auto/4G/3G/2G) e APN
- Altro: rinomina Wi-Fi, USSD, informazioni di sistema, riavvio, esci
- Notifiche per disconnessioni, SIM rimossa, nessun servizio, nuovi SMS
- 12 lingue, tema chiaro/scuro, icona Material You

## Build
Apri la cartella in Android Studio, sincronizza Gradle e usa *Build > Generate Signed App Bundle / APK*.
Il router deve essere raggiungibile a `192.168.0.1` (collegati al suo Wi-Fi).

## Note
Il router consente un solo accesso alla volta: esci dalla pagina web prima di usare l'app.
