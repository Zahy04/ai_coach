# AI Coach 🏋️

An AI-powered fitness companion for Android. Track what you eat and how you train by simply telling your personal coach in chat — the app automatically logs meals, workouts, body weight and long-term facts about you, then helps you stay on top of your goals.

Built with Kotlin + Jetpack Compose, powered by the Google Gemini API.

## ✨ Features

### 💬 AI Chat Coach
- Natural conversation with streaming responses (like the Gemini web UI)
- Every message carries a timestamp so the coach understands *when* you ate or trained
- **Tool calling** — the model writes data straight into your logs:
  - `save_weight`, `log_food`, `log_water`, `log_workout`, `save_fact`, `delete_fact`
- Meals composed of multiple components are logged item-by-item for accuracy
- Nutrition values come from the [Open Food Facts](https://world.openfoodfacts.org/) database instead of pure LLM guessing
- Long-term memory: the coach remembers your preferences, goals and habits between sessions
- Voice input (Czech), photo attachments (camera/gallery), stop generation, copy & regenerate messages
- Markdown rendering in replies

### 🍽️ CTU Canteen Menus
- Scrapes the daily lunch menus of two Prague CTU canteens (Studentský dům & Technická menza) from agata.suz.cvut.cz
- Every meal gets an AI-predicted calorie range, macros and a **fitness verdict** (🟢🟡🔴) based on calories, protein and fat content
- One tap logs a meal into your diary

### 📊 Tracking & Insights
- Weight log with an animated chart: raw measurements, quadratic least-squares trend line and a 7-day moving average
- Set a goal weight and see a **projected reach date** based on your trend
- Food diary grouped by day with daily calorie goal rings and macro bars (protein/carbs/fat)
- Water intake tracker with daily goal
- Streaks 🔥 and achievement badges for consistency

### 🖼️ Progress Photos
- Capture or pick photos, stored privately inside the app
- Gallery with a draggable **before/after comparison slider**

### ⚙️ Extras
- Weekly AI recap of your training and eating habits
- Model switcher directly in the chat header
- Report bugs straight from the app to this repository's GitHub Issues (optional PAT)

## 🛠️ Tech Stack

| Layer      | Technology |
|------------|------------|
| UI         | Jetpack Compose, Material 3 (custom dark fitness theme, Manrope typeface) |
| Architecture | MVVM + Repository pattern, Hilt dependency injection |
| Data       | Room (schema-migrated), DataStore Preferences |
| Networking | OkHttp + kotlinx.serialization (Gemini SSE streaming, REST, Open Food Facts) |
| Scraping   | Jsoup |
| Images     | Coil |
| Charts     | Custom Canvas rendering |

Unit tests cover the menu scraper (against real saved HTML) and the Gemini estimator core (MockWebServer).

## 🚀 Getting Started

1. Clone the repository and open it in **Android Studio**
2. Let Gradle sync (first run downloads dependencies)
3. Get a free API key at [aistudio.google.com](https://aistudio.google.com) → *Create API key*
4. Run the app on a device/emulator (min SDK 26) and paste the key in **Settings**
5. Optionally set your daily calorie/protein/water goals and goal weight

> Tip: The default model can be changed anytime — either from the dropdown in the chat header or in Settings.

## 🔐 Privacy

- Your Gemini API key and GitHub token are stored locally on the device (DataStore) and never leave it except when calling the respective APIs
- All health data lives exclusively in the app's local database — there is no server, no analytics, no tracking

## 🧪 Tests

```bash
./gradlew :app:testDebugUnitTest --tests "cz.rzahr.aicoach.mensa.*"
```

Covers the canteen HTML parser (against a real captured page) and the Gemini estimation pipeline (envelope extraction, JSON fallbacks, retry/backoff behaviour) via MockWebServer.
