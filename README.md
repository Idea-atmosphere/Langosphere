> 🌐 **خوندن به فارسی:** [فارسی](./docs/README-Fa.md)

---

## About This Project

I was always looking for an app that would help me learn languages. I had this idea in my head since I was 12 or 13, and with the rise of AI, it finally became possible for me to turn what was in my mind into reality—so that I could use it myself and also make it available to everyone completely for free.

---

## How It Works and Features

This app is suitable for people who know a little language and want to learn along with movies, songs, articles, or PDF books.

### Main Features:
* **PDF File Support:** You can open and read your desired PDF file directly from inside the app.
* **Video and Music Player:** By clicking the player tab at the top of the page, you can import your favorite video or song.
* **Synchronized Subtitles:** Selecting English subtitles and synchronized Persian subtitles; English and Persian texts are displayed simultaneously in boxes, and by **clicking on any word**, its meanings from the selected dictionary will be displayed.
* **Dictionary Support:** Support for MDict dictionary files (currently tested on the **Aryanpour Dictionary**) and `.db` database files—which I asked the AI to make something for, and if I get a database file, I will improve it accordingly so the app can read the file better.
* **Leitner Box and AnkiDroid:** Ability to add new words to the Leitner box and export directly to the **AnkiDroid** app.
* **Any Language Pair:** Settings ▸ **Prompts** has two free-text fields: the language you are learning, and the language you want to be taught in.
* **Quiz from JSON (BETA):** the tab is now **Better Learning Tools** with two tidy options: the Leitner box, and a quiz built from any imported AI learning file. The quiz is offline, asks about the file's words, sentences and grammar points, and pushes every word you miss into the Leitner box with one tap.
* **Online tab (YouTube through Invidious / Piped):** stream clips from a built-in list of English-teacher channels (BBC Learning English, English with Lucy, Rachel's English, mmmEnglish, Speak English With Vanessa, …) or any channel you follow, through free-software Invidious/Piped instances — no Google account and no proprietary SDK. The clip's captions load into the same tap-a-word subtitle player, a second track can be shown as the translation, captions can be **copied to the clipboard or saved as SRT/TXT** with one tap, and an AI-made **JSON learning file can be attached** to the online clip. The instance list is editable (add a self-hosted one, disable or reorder instances). Public instances are often rate-limited by YouTube, so channel listings fall back to the channels' public Atom feeds when no instance can list them. Each clip is first resolved **directly from YouTube's InnerTube API** (the JavaScript-free client yt-dlp uses; plain HTTPS, no Google library), so it plays in the app's own Media3 player without YouTube's title bar, channel avatar, watermark or end-screen cards, and its caption tracks load as WebVTT into the transcript. When that is refused, playback fails over automatically (an HTTP 403, 5xx or timeout moves on to the next mirror, up to four instances), opens muxed audio+video MP4 streams first, and when YouTube blocks direct streams everywhere the clip plays in the **fallback web player** — YouTube's own embed in a WebView — with YouTube's overlays hidden by an injected stylesheet and with subtitles, word lookups and JSON lessons still in sync. Captions come from YouTube's own track list or, failing that, an instance or YouTube timedtext. The tab opens on a **hub**: a floating search bar that also takes a pasted YouTube link (with a paste-from-clipboard button), three sections — **Following** (a tray of round channel avatars with a dot on channels that uploaded something new, and `+ Follow channel` at its start), **Saved** (clips bookmarked with ⭐) and **History** — all shown as a responsive card grid whose cards carry a green *Smart / translated* badge when an AI JSON lesson is attached to the clip. Opening a clip glides into the **learning player**: a rounded 16:9 video card with a bookmark button, a toolbar (copy for AI, import JSON, loop sentence, challenge mode, 0.75×/1×/1.25×, caption track) and the transcript as cue cards (time, play-this-segment, `+ Leitner`, tappable English words, the translation in its own direction, a grammar-tip badge) where the spoken cue lights up and stays centred.
* **Smart Assistant:** The assistant section currently works, and over time its issues will be fixed and improved.

### Feature Status

Implemented, partially completed, and pending features that have not been added yet:

| Feature | Status |
| --- | --- |
| Book reader and PDF/EPUB support | ✅ Added |
| Video and music player (Offline) | ✅ Added |
| Online videos (YouTube / Invidious / Piped) | ✅ Added |
| Synchronized bilingual subtitles and word lookup | ✅ Added |
| Attach JSON lessons (Online player, offline player, and book reader) | ✅ Added |
| Copy subtitles and export SRT/TXT | ✅ Added |
| Leitner box | ✅ Added |
| AnkiDroid export | ✅ Added |
| Custom learning and teaching languages | ✅ Added |
| Offline quiz from imported JSON lessons | ✅ Added (beta) |
| Five app designs, colors, fonts and tab layout | ✅ Added |
| Reading dictionary files (MDict and .db) | ⚠️ In progress (Partial) |
| Smart assistant | ⚠️ In progress (Partial) |
| Offline OCR | ❌ Not added yet |
| Offline translator | ❌ Not added yet |
| TTS (text-to-speech) | ❌ Not added yet |
| Shadowing mode (repeat along with the original audio) | ❌ Not added yet |
| Voice recording and comparison with native pronunciation | ❌ Not added yet |
| Dictation (listening exercise) | ❌ Not added yet |
| Progress stats and daily reminders | ❌ Not added yet |
| Vocabulary games from the Leitner box | ❌ Not added yet |
| Full-text search in books and subtitles | ❌ Not added yet |
| Data backup and restore | ❌ Not added yet |
| Two-way Anki import | ❌ Not added yet |

### App Screenshots

<table>
  <tr>
    <td align="center"><img src="./docs/Shots/01.webp" alt="Langosphere app screenshot 1" width="200"></td>
    <td align="center"><img src="./docs/Shots/02.webp" alt="Langosphere app screenshot 2" width="200"></td>
    <td align="center"><img src="./docs/Shots/03.webp" alt="Langosphere app screenshot 3" width="200"></td>
  </tr>
  <tr>
    <td align="center"><img src="./docs/Shots/04.webp" alt="Langosphere app screenshot 4" width="200"></td>
    <td align="center"><img src="./docs/Shots/05.webp" alt="Langosphere app screenshot 5" width="200"></td>
  </tr>
</table>

---

## Design Languages

Settings ▸ Theme ▸ **App design** lets you swap the app's entire look. (The
Theme dialog is a small hub: one button each for App design, App colors and
Font. Every section opens on its own screen and has a Back control that
returns you to the hub, so hopping between them is one tap.) There are five
design languages, and each one is a full visual system — colors, shapes,
type, motion and component styling all change together:

| Design | Look |
| --- | --- |
| **Langosphere** | The original: glass cards, gradients, a liquid tab bar. |
| **Material 3** | Google's standard Material palette and components. |
| **Material You** | Material 3 plus wallpaper-based dynamic color. |
| **Neobrutalism** | Flat blocks, thick ink borders, hard offset shadows. |
| **Anime (Toon)** | A kawaii/manga skin. |
---

## Development Process and How It Was Built

I am not a programmer, and my programming knowledge is basically zero. I spent about 3 months building this app, and the development process was like this:

1. **Base and Core Structure:** Creation of the project base by **AI Studio**.
2. **Further Development:** Using a combination of Hermes + 9Router + OpenCode with the **MiMo 2.5 Free** model.
3. **Further Development again:** Using Notion's free 6-month plan, I managed to use **GLM 5.2** and **Claude 5 Sonnet** models to complete the project.

Which still has a long way to go to get better.

---

## Contact Me

To stay updated on the latest updates, report issues, or suggest your ideas, you can use the following links:

* **Telegram Channel:** [Idea_atmosphere](https://t.me/Idea_atmosphere)
* **Telegram Topic:** [Idea_atmosphere_topic](https://t.me/Idea_atmosphere_topic)
  * **General Section:** General chat
  * **Issues Section:** Report bugs and app problems
  * **Ideas Section:** Send suggestions to make the app better

---

## Acknowledgments and Thanks

* Special thanks to **mumu-lhl** and the [Ciyue](https://github.com/mumu-lhl/Ciyue) project, without which reading the `Aryanpour Dictionary.mdx` file would have been much harder.
* Thanks to **0xdolan** for the [AryanpourDictionary](https://github.com/0xdolan/AryanpourDictionary) project.

---
## License

This project is licensed under the [MIT License](LICENSE).
