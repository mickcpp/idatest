# Importazione programmazioni

L'app importa file JSON UTF-8. Un esempio completo è disponibile in [`import-example.json`](import-example.json), e nell'app puoi aprire **Formato e esempio** per copiarlo.

## Struttura

- `version`: deve essere `1`.
- `batchId`: identificatore stabile (lettere, numeri, punto, trattino o underscore; massimo 64 caratteri). Reimportando lo stesso `batchId`, l'app sostituisce le programmazioni di quel batch invece di duplicarle.
- `phone` e `times` alla radice: valori predefiniti facoltativi.
- `keywords`: gruppi di messaggi. Ogni gruppo richiede `keyword` e `messages`; può sovrascrivere `phone` e `times`.
- Ogni messaggio richiede `text`; `name`, `phone` e `times` sono facoltativi. I valori del messaggio prevalgono su quelli del gruppo e della radice.

I numeri devono includere il prefisso internazionale, per esempio `+393331234567`. Gli orari usano il formato 24 ore `HH:mm` e il fuso orario del telefono. Ogni testo deve contenere la parola chiave del proprio gruppo, senza distinzione tra maiuscole e minuscole.

Ogni orario del file crea un invio ricorrente giornaliero e senza scadenza. Se quell'orario è già passato oggi, il primo invio avviene domani. Per evitare che WhatsApp apra più messaggi insieme, uno stesso orario non può essere assegnato a più invii e l'app blocca anche le sovrapposizioni con le programmazioni già presenti.

Il file viene validato interamente e mostra un riepilogo prima del salvataggio; se trova un errore, non importa nessuna programmazione. Sono accettati file fino a 512 KB e batch fino a 200 invii giornalieri.
