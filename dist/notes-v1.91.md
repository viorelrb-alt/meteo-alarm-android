# Meteo Alarm Android v1.91

- Reparat: când serverul de prognoză Open-Meteo răspunde „ocupat” (HTTP 503, ca pe 2 noiembrie pe A54 și A70), aplicația cere imediat prognoza din nou fără modelul ICON (combinația de modele a Open-Meteo), apoi încă o dată după 3 secunde. Jurnalul notează fiecare încercare și răspunsul serverului.
- Widget-uri: modelul se alege după forma ramei (lat și jos = 4x1, lat = 4x2, pătrat = 2x2), deci o ramă de 3x2 sau 4x2 nu mai rămâne 2x2 mărit. Un model nu se mai micșorează sub 80%; pentru ramele prea mici (de exemplu 3x1) e un model mic nou, 2x1, cu iconul și temperatura. Pe Android 12+ widget-ul nu se mai poate face mai înalt de circa 2 rânduri.
- Trasul în jos de mai multe ori la rând nu mai pornește mai multe descărcări: cât timp se descarcă sau în 10 secunde de la ultima descărcare, o notificare îți spune că vremea tocmai a fost actualizată (sau că tocmai s-a încercat).
- Jurnal: etichetele sunt aliniate (de exemplu „SENZOR busola”), apar avertismentele (W) și se notează când și de ce se închide aplicația, ca să vedem pornirea dublă de pe A54.
- Ajutorul e actualizat.

## English

- Fixed: when the Open-Meteo forecast server answers "busy" (HTTP 503, as on 2 November on the A54 and A70), the app asks for the forecast again at once without the ICON model (Open-Meteo's own mix of models), then once more 3 seconds later. The log notes each try and the server's answer.
- Widgets: the layout is chosen by the frame's shape (wide and low = 4x1, wide = 4x2, square = 2x2), so a 3x2 or 4x2 frame no longer stays a zoomed 2x2. A layout is never shrunk below 80%; frames too small (for example 3x1) get a new small 2x1 layout with the icon and temperature. On Android 12+ the widget can no longer be made taller than about 2 rows.
- Pulling down again and again no longer starts several downloads: while it downloads, or within 10 seconds of the last download, a notice says the weather was just updated (or just tried).
- Log: the labels are aligned (e.g. "SENZOR busola"), warnings (W) are shown, and when and why the app closes is noted, to find the double start seen on the A54.
- Help updated.
