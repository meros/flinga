# Flinga UX-arkitektur: Två ytor och en list

**In short**: Flinga ersätter fem överlappande menylager med två ytor, en bottenlist och ett universellt inställningsblad. Alla kanaler, arkivprogram och inställningar nås med högst två knapptryck från ren TV-bild. Varje knapp har en entydig funktion över alla sändningstyper, anpassat för Chromecast-fjärrkontrollen.

---

## 1. Problemet: Fem lager över en TV-bild

Dagens version (`docs/UX-MAP.md`) har snabb uppspelning, men har samlat på sig strukturell komplexitet:

1. **Ytspret**: Ren video ligger under en passiv banner, en full overlay med knappar, en 92 dp vänster-rail med fem flikar, en utfällbar panellista, en separat fullskärmstablå och tre olika inställningsblad. Fem separata gränssnitt konkurrerar om bilden.
2. **Modala trösklar**: Spelarens overlay är uppdelad i ett "passivt" läge (enbart text) och ett "interaktivt" läge (med knappar). Tittaren måste trycka OK bara för att väcka knapparna.
3. **Tangentkrockar**: `←` spolar bakåt under video, men öppnar vänstermenyn från listrader.
4. **Nästlade blad**: För att ändra undertext navigerar användaren in i `Alternativ`, som i sin tur öppnar ett nytt underblad `Ljud och text`.
5. **Hårdvarans villkor**: Målenheten (Chromecast Voice Remote) saknar Guide-, Menu- och sifferknappar. Hela gränssnittet måste styras med **↑, ↓, ←, →, OK och Back**.

---

## 2. Arkitekturen: Två ytor, en list, ett blad

Gränssnittet bantas till tre beståndsdelar:

```
┌────────────────────────────────────────────────────────┐
│ Yta 1: Helskärmsvideo (Hem)                            │
│  └─ Enhetlig bottenlist (tidslinje + kanoniska knappar) │
├────────────────────────────────────────────────────────┤
│ Yta 2: Webbläsaren (Guide)                             │
│  ├─ Tablån (Kanaler × Tid, heldagsschema med nu-linje) │
│  └─ Katalogen & Sök (Fortsätt titta + fasta 16:9-kort) │
├────────────────────────────────────────────────────────┤
│ Yta 3: Väljarbladet (Högerställt 400 dp inställningsblad)
└────────────────────────────────────────────────────────┘
```

### Yta 1: Helskärmsvideo med bottenlist (Hem)
Video fyller skärmen. En enda bottenlist bär all information:
- **Enhetlig form**: Kanallogotyp, programtitel, en sammanhängande tidslinje med live-indikator (`DIREKT`) samt en kanonisk knapprad.
- **Ingen passiv/interaktiv klyvning**: Listen visar framsteg direkt. Tryck på `OK` fokuserar omedelbart första handlingsknappen.
- **Kanonisk knappordning**:
  `[Paus / Spela]` · `[Från början]` · `[Hoppa över intro]` · `[Direkt]` · `[Tablå]` · `[Ljud & text]` · `[Alternativ]`
  (Endast relevanta knappar visas; inga inaktiva platshållare).
- **Ortogonal tidsaxel**: `←` och `→` spolar i programmet. Spolning bakåt förbi start hoppar direkt in i föregående programs catch-up; spolning framåt når direktsänd kant (`DIREKT`).

### Yta 2: Webbläsaren (Fullskärmsguide)
Ersätter den gamla vänster-railen, den skjutbara panelen och den fristående tablån. Nås med **ett klick på `Back`** från ren video (eller `Guide` på fulla fjärrkontroller):
- **Tablån (Kanaler × Tid)**: Horisontella tidsrader per kanal med nu-linje. Rulla vertikalt för kanaler, horisontellt för schema över hela dygnet. `OK` på ett pågående eller tidigare program startar uppspelning direkt.
- **Katalog & Sök**: Nås direkt under kanalraderna eller via toppfliken. Visar `Fortsätt titta`, kategorier med fasta 16:9-affischkort (stabil geometri enligt regel E6) och integrerat sökfält.
- **Direkt bildkontakt**: Pågående TV-ström fortsätter spela i bakgrunden under bläddring.

### Yta 3: Det universella väljarbladet (Picker Sheet)
Ett gemensamt 400 dp blad på skärmens högra sida för alla kontextuella val:
- Ersätter `Alternativ`, `Ljud och text`, `Nästa avsnitt` och utloggningsbekräftelse.
- Rader med direktväxling inline (ingen öppning av underblad):
  - `Spela från början` (Tidsangivelse)
  - `Undertext` (Av / Svenska / Svenska SDH)
  - `Ljudspår` (Svenska / Original)
  - `Bildformat` (Anpassa till skärm / 16:9)
  - `Min lista` (+ Lägg till serie)
- Val verkställs omedelbart. `Back` stänger bladet och återställer fokus med ett tryck.

---

## 3. Tangentkarta: 100 % konsekvent överallt

| Tangent | På ren video | På bottenlisten | I Webbläsaren (Tablå/Katalog) | I Väljarbladet |
|---|---|---|---|---|
| **↑ / ↓** | Byt kanal direkt (zap) | Växla mellan knappar och tidslinje | Flytta kanalrad / bläddra hyllor | Flytta fokusrad |
| **← / →** | Spola tid (hoppar programgränser) | Flytta fokus mellan knappar / spola | Rulla i tid / bläddra affischer | Ändra valt värde inline |
| **OK** | Tänd bottenlist med knapp fokuserad | Aktivera vald knapp | Spela program / öppna detalj | Bekräfta / stega val |
| **Back** | **Öppna Tablån (1 tryck)** · Dubbel-Back stänger appen | Dölj listen till ren video | Stäng Tablån och återgå till video | Stäng väljarbladet |
| **Guide / Menu** | Öppna Tablån | Öppna Tablån | Stäng Tablån och återgå till video | Stäng väljarbladet |
| **CH ±** | Byt kanal direkt | Byt kanal direkt | Hoppa 5 kanaler i tablån | — |

*Anpassning för Chromecast Remote*: Eftersom fjärrkontrollen saknar Guide-knapp är **`Back` på ren bild den primära 1-klicksvägen till Tablån**. Det följer standarden på Android TV: *Innehåll → `Back` → Tablå → `Back` → Innehåll*.

---

## 4. Jämförelse i tryckantal (Värsta fall)

| Uppgift | Före (Dagens build) | Efter (Clean Room) | Förbättring |
|---|---|---|---|
| Byt kanal direkt | `↑` (1 tryck) | `↑` (1 tryck) | Samma (omedelbar) |
| Öppna Tablån från ren bild | `Guide` (1) eller `←` till rail → Tablå (2–3) | **`Back` (1 tryck på alla dosor)** | **1 tryck** |
| Se gårdagens program i catch-up | `Guide` → `←×N` → rad → `OK` (6–12 tryck) | `Back` → `←×N` → `OK` (5–10 tryck) | Snabbare och enklare |
| Byta undertext under uppspelning | `OK` → `→×N` → `OK` → rad → `OK` (6 tryck) | `OK` → `Ljud & text` → `OK` (3 tryck) | **Halverad tid (-50 %)** |
| Hitta serieavsnitt | `Menu` → `Sök` → skriv → `OK` → serie → avsnitt (7+) | `Back` → `↓` till katalog → skriv → `OK` (5 tryck) | Mindre navigation |
| Återgå till live från catch-up | `→×N` eller `Direkt`-knapp (2 tryck) | `→` till kant eller `Direkt`-knapp (1–2 tryck) | Samma / rakare |

---

## 5. Vad som tas bort och vad som sparas

### Vad som tas bort (Städat)
- **Vänster-railen (92 dp) och den skjutbara panelen**: Sparar minne och eliminerar onödigt navigationsdjup.
- **Kanaler som separat textlista**: Tablågriden visar redan alla kanaler med realtidsdata.
- **Klyvningen lätt vs full overlay**: Bottenlisten har en enda konsekvent form.
- **Nästlade inställningsblad**: Alla val samlas i ett enda väljarblad med expanderande rader.
- **Rail peek vid spolning**: Vänsterpil är alltid spolning, aldrig menyöppning.

### Vad som sparas (Beprövat på TV)
- Blixtsnabb zappning med bibehållen uppspelningsposition (delayed place).
- Automatisk programfortsättning (F14) över programgränser.
- Gränshopp med 60 s landning och 20 s spärr mot oavsiktliga studsningar.
- Passiv tidsstyrning (4 s automatisk borttoning av kontroller).
- Stabil geometri (E6) med fasta layoutplatser.

---

## 6. Genomförandeplan i tre steg

1. **Steg 1: Enhetlig bottenlist**. Ta bort uppdelningen mellan passiv och interaktiv banner. `OK` på ren bild tänder listen med primär knapp fokuserad. Vänsterpil öppnar inte längre vänstermenyn.
2. **Steg 2: Det universella väljarbladet**. Ersätt `Alternativ` och `Ljud och text` med det gemensamma 400 dp bladet med direktväxling inline.
3. **Steg 3: Webbläsaren i fullskärm**. Slå samman tablån och katalogen till en gemensam vy nådd med `Back` på ren bild. Avveckla 92 dp-railen och panelhanteraren.

---

## 7. Som byggt (2026-10-19)

Förslaget är genomfört i `client/`. Wireframes i `ux-cleanroom.html` visar förslaget, inte det byggda. Här är
var bygget gick längre än texten ovan, eller valde annorlunda. `client/design/UX.md` har reglerna i sin
helhet, `REQUIREMENTS.md` (U12 till U14, F24) har bevisen.

- **Spolning = en tidslinje per kanal (U12).** Avsnitt 2 och 5 beskrev hopp över programgränser. Det
  ersattes av en markör längs kanalens sändningstid: ← → flyttar bara markören, över programkanter, och
  strömmen som håller tidpunkten laddas när tangenterna tystnar. Listen visar programmet markören står i.
  Ett grannprogram utan något att spela stoppar markören vid kanten. Inget går förbi live.
- **Back steg för steg i webbläsaren (U13).** Avsnitt 3 sa att Back stänger Tablån. Det byggda: Back
  från innehållet går först till flikraden (ett tryck från vilken rad som helst), Back på flikraden stänger.
  Menu och Guide stänger direkt. Flikbyte med ← → lämnar fokus på flikraden, ↓ går in.
- **Katalog & Sök i två lägen (U14).** Bläddra: fältet som en rad överst, hyllor som rader som scrollar i
  sidled. Skriv: tangentbordet som en 312 dp-kolumn till vänster under fältet, resultaten som ett rutnät av
  kompakta 16:9-kort till höger. Förslagets "fasta 16:9-kort" gäller i båda lägena, med två fasta storlekar.
- **Min lista och Logga ut (F24).** Min lista är första hylla i Katalog & Sök och en rad i Alternativ.
  Logga ut är sista raden i Alternativ och frågar en gång i samma blad (Avbryt förvalt).
- **Bottenlisten** har Tablå och Alternativ som fasta knappar (avsnitt 2 nämnde dem som tillval).

