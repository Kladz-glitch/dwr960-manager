# D-Link DWR960 Manager

**Un'app Android per il router D-Link DWR-960, che un'app ufficiale non l'ha mai avuta.**

Ho creato questa applicazione perché per il D-Link DWR-960 non è mai esistita un'app: l'unico modo per controllarlo era aprire la pagina web del router dal browser, scomoda da usare da telefono. Così ho deciso di costruirne una io, con un'interfaccia moderna pensata per lo smartphone, che mostra in tempo reale i dati veri del router.

Sviluppata da **Clacson**.

## Cosa può fare

**Dashboard**
- Segnale 4G con livello (Eccellente, Buono, Discreto, Scarso, Nullo), tacche e legenda che ne spiega il significato
- Operatore, banda LTE, Cell ID, IP pubblico e stato della connessione
- Velocità di download e upload e ping, con grafici in tempo reale
- Consumo dati con limite personalizzabile

**Dispositivi**
- Elenco dei dispositivi collegati (Wi-Fi 2.4 GHz, 5 GHz e cavo)

**SMS**
- Lettura dei messaggi ricevuti, segnati come letti
- Invio di nuovi SMS ed eliminazione dei messaggi

**Rete**
- Scelta del tipo di rete: Auto, 4G, 3G o 2G
- Scelta dell'APN, automatico o manuale

**Altro**
- Rinomina delle reti Wi-Fi
- Invio di codici USSD
- Informazioni del modem (modello, firmware, hardware) e tempo di attività
- Riavvio del router e uscita sicura (chiude la sessione sul router)

**Notifiche**
- Avvisi per connessione dati caduta, SIM rimossa, nessun servizio, nuovi SMS e router non raggiungibile

**Impostazioni dell'app**
- 12 lingue (italiano, inglese, spagnolo, francese, tedesco, portoghese, rumeno, olandese, polacco, turco, russo, cinese)
- Tema chiaro, scuro o automatico
- Versione dell'app e link per una donazione con PayPal
- Icona compatibile con Material You

## Come si usa
1. Collega il telefono al Wi-Fi del router (indirizzo `192.168.0.1`).
2. Apri l'app e accedi con utente e password del router.
3. Il router consente un solo accesso alla volta: esci dalla pagina web del router prima di usare l'app.

## Build
Apri la cartella in Android Studio, sincronizza Gradle e usa *Build > Generate Signed App Bundle / APK*.
Tecnologie: Kotlin, Jetpack Compose, OkHttp. Requisiti: Android 8.0 (API 26) o superiore.

## Sostieni il progetto
Se l'app ti è utile puoi offrirmi un caffè: [PayPal](https://www.paypal.com/paypalme/claudiorubin)
