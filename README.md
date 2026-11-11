# seeq

Find any photo on your phone by describing it. Type "blue car at night", "receipt from August 5" or "whiteboard
with a diagram" and seeq shows the matching photos from your gallery.

Everything runs **on the phone**. Your photos are never uploaded, and after a one-time model download search works
with no internet connection.

- Platform: Android 10+ (minSdk 29), built against Android 16 (API 36)
- Language: Kotlin, UI in Jetpack Compose
- Search model: OpenAI CLIP ViT-B/32, 8-bit quantised, run with ONNX Runtime
- Package name: `com.example.locallens` (the app is called seeq; the package kept its template name)

---

## Contents

1. [What it does](#what-it-does)
2. [How it works, in one picture](#how-it-works-in-one-picture)
3. [Tech stack](#tech-stack)
4. [The models](#the-models)
5. [How embedding works](#how-embedding-works)
6. [The indexing pipeline](#the-indexing-pipeline)
7. [The search pipeline](#the-search-pipeline)
8. [The index file](#the-index-file)
9. [Model download](#model-download)
10. [Permissions](#permissions)
11. [Project structure](#project-structure)
12. [Build, run and test](#build-run-and-test)
13. [Measured performance](#measured-performance)
14. [Design decisions and history](#design-decisions-and-history)
15. [Limitations and known issues](#limitations-and-known-issues)
16. [Troubleshooting](#troubleshooting)
17. [Licences and credits](#licences-and-credits)

---

## What it does

- **Searches by meaning, not by file name.** The query and every photo are turned into lists of numbers
  (embeddings) that sit close together when they mean the same thing, so "dog on a beach" finds dog-on-a-beach
  photos even though nothing in the photo is labelled.
- **Understands dates in the query.** "photos from august 5", "in march" or "in 2023" are pulled out of the text and
  used as an exact filter, because embeddings are poor at dates. The rest of the sentence is what gets embedded.
- **Indexes your whole gallery in the background.** A foreground job embeds each photo once and saves the result.
  New photos are picked up later, and photos you delete are dropped from the index.
- **Shows what it has indexed.** With an empty search box the home screen lists the indexed photos, newest first,
  with a count.
- **Presents results two ways.** The top 20 matches appear in a 3D circular carousel, and all results (up to 60)
  appear in a two-column staggered grid below it. Tapping a photo opens it in your gallery app.
- **Is private by design.** The only network traffic is the one-time model download from Hugging Face.

---

## How it works, in one picture

```
                    ONE TIME                                     EVERY PHOTO (background)
 ┌────────────────────────────────────┐      ┌───────────────────────────────────────────────────────────┐
 │ Hugging Face                       │      │ MediaStore ──► ImageDecoder ──► ClipImage ──► ONNX vision │
 │  vision_model_quantized.onnx 85 MB │      │ (list photos)  (decode at     (224x224,    model ──► 512  │
 │  text_model_quantized.onnx   61 MB │      │                224 px)        normalise)   numbers        │
 └──────────────┬─────────────────────┘      └───────────────────────────────────────────────┬───────────┘
                │ ModelDownloadWorker                                                          │ unit length
                ▼                                                                              ▼
        app-private storage                                              ┌───────────────────────────────┐
        files/models/*.onnx                                              │ VectorIndex (in memory + file) │
                                                                         │  photo id, date taken, vector  │
                                                                         └───────────────▲───────────────┘
                                                                                         │ dot product
          EVERY SEARCH                                                                   │
 ┌───────────────────────────────────────────────────────────────────────────────────────┴──────────────┐
 │ query text ─► QueryParser ─► date filter  +  text left over ─► ClipTokenizer ─► ONNX text model ─► 512 │
 │                                                                                          numbers ─────┘ │
 └─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
        top 60 photos by similarity (after the date filter) ─► carousel (top 20) + grid
```

---

## Tech stack

| Area | Choice | Notes |
| --- | --- | --- |
| Language | Kotlin 2.3.20 | JVM toolchain 17 |
| UI | Jetpack Compose, Material 3 | Compose BOM 2026.03.01. A custom "paper grid" theme (ink, paper, hairline) replaces stock Material styling |
| Architecture | One `ViewModel` plus a small app-wide singleton (`AppGraph`) | No dependency-injection framework |
| Async | Kotlin coroutines 1.10.2, `StateFlow` | Search is debounced and cancels stale queries |
| Background work | WorkManager 2.10.0 | Three jobs: model download, index now, index periodically |
| Machine learning runtime | ONNX Runtime for Android 1.31.1 (`com.microsoft.onnxruntime:onnxruntime-android`) | CPU execution, 4 threads |
| Models | OpenAI CLIP ViT-B/32 (image tower and text tower), 8-bit quantised ONNX | See [The models](#the-models) |
| Image decoding | `android.graphics.ImageDecoder` | Decodes straight to the needed size |
| Photo access | `MediaStore` | Read-only, newest first |
| Storage | Plain binary file for the vector index, app-private files for the models | No database |
| Build | Gradle with the wrapper, Android Gradle Plugin 9.0.1, version catalog in `gradle/libs.versions.toml` | `compileSdk` 36, `targetSdk` 36, `minSdk` 29 |
| Tests | JUnit 4.13.2, `kotlinx-coroutines-test`, `org.json` for the JVM | 28 unit tests, all on the JVM |

---

## The models

seeq uses **OpenAI CLIP ViT-B/32**, republished with ONNX weights by Xenova for Transformers.js
(`Xenova/clip-vit-base-patch32` on Hugging Face). CLIP is a pair of neural networks trained together:

| | Image tower | Text tower |
| --- | --- | --- |
| File on the phone | `clip-vit-b32-vision-q8.onnx` | `clip-vit-b32-text-q8.onnx` |
| Source file | `onnx/vision_model_quantized.onnx` | `onnx/text_model_quantized.onnx` |
| Size | 89,117,001 bytes (about 85 MB) | 64,504,507 bytes (about 61.5 MB) |
| Architecture | Vision Transformer, 32 x 32 pixel patches | Transformer over 49,408 byte-pair tokens |
| Input | `pixel_values`, float32, `[1, 3, 224, 224]` | `input_ids`, int64, `[1, tokens]` (this export takes no attention mask) |
| Output | `image_embeds`, float32, `[1, 512]` | `text_embeds`, float32, `[1, 512]` |
| Time to load | about 0.6 s (measured) | about 0.8 s (measured) |

- Both towers output **512 numbers**, and those numbers live in the **same space**, which is what lets a sentence
  be compared with a photo.
- The full models are 352 MB (image) and 254 MB (text) in 32-bit floats. The `quantized` variants store weights as
  8-bit integers, which makes them about four times smaller and faster on a phone CPU with a small quality cost.
- Together the two towers have roughly 151 million parameters and download as about **154 MB**.
- Licence: MIT (OpenAI CLIP and the Xenova export).
- The two small tokenizer files, `vocab.json` (862,328 bytes) and `merges.txt` (524,619 bytes), are **bundled in the
  app** under `app/src/main/assets/clip/`, so only the two model files are downloaded.
- Input and output names are read from the model at runtime, and the output is chosen by its name (`...embeds`)
  rather than by position, so a re-export that adds extra outputs still works. The names listed above were read from
  the running app's logs.

The models are stored in `files/models/` inside the app's private storage, where no other app can read them.

---

## How embedding works

An **embedding** is a list of numbers that captures the meaning of something. CLIP was trained on about 400 million
image and caption pairs with a *contrastive* objective: for a batch of pairs it learns to push a photo and its own
caption close together and push mismatched pairs apart. After training, a photo of a red bicycle and the sentence
"a red bicycle" produce vectors that point in nearly the same direction, and a photo of a beach does not.

### Turning a photo into a vector

Implemented in `ClipImage`, `IndexWorker.decode` and `OnnxEmbedder.embedImage`.

1. **Decode at the target size.** `ImageDecoder` is asked for a bitmap whose short side is 224 pixels. The system
   scales while decoding, so a 12-megapixel photo never exists in memory at full size. A software bitmap is used
   because the pixels are read back on the CPU.
2. **Centre-crop to 224 x 224.** The short side is already 224 (smaller photos are scaled up), and the long side is
   cropped equally on both ends. This matches how CLIP was trained.
3. **Normalise.** Each pixel's red, green and blue values are scaled to 0 to 1, then
   `(value - mean) / std` with CLIP's constants
   (mean `0.48145466, 0.4578275, 0.40821073`; std `0.26862954, 0.26130258, 0.27577711`).
4. **Lay out as planes.** The tensor is channel-first (all red values, then all green, then all blue), shape
   `[1, 3, 224, 224]`, which is what the ONNX model expects.
5. **Run the vision model.** It cuts the image into 7 x 7 = 49 patches of 32 x 32 pixels, runs them through a
   Transformer and projects the result to 512 numbers.
6. **Scale to unit length** (see below) and store the vector.

### Turning a query into a vector

Implemented in `ClipTokenizer` and `OnnxEmbedder.embedText`.

1. **Clean up**: trim, collapse repeated whitespace, lower-case.
2. **Split into words** with CLIP's regular expression (contractions such as `'s` and `'re`, runs of letters, single
   digits, runs of punctuation).
3. **Byte-pair encode.** Each word is written as bytes, mapped to printable characters through CLIP's reversible
   byte table, then repeatedly merged using the ranked merge list in `merges.txt` (48,894 merges) until no merge
   applies. Each resulting piece is looked up in `vocab.json` for its id. Unknown words are simply built from
   smaller pieces, so nothing is ever "out of vocabulary".
4. **Add markers.** The id list starts with `49406` (start of text) and ends with `49407` (end of text) and is cut
   to CLIP's limit of **77 tokens**. For example `a cat` becomes `[49406, 320, 2368, 49407]`.
5. **Run the text model** with the ids and take the 512-number projection. The embedder looks at the model's input
   names and only builds an attention mask if the model asks for one, which this export does not.
6. **Scale to unit length.**

Getting the tokenizer exactly right matters: the text model only gives sensible vectors for the ids it was trained
on. `ClipTokenizerTest` checks real CLIP ids against the real vocabulary file.

### Comparing them

Both vectors are scaled to **unit length** (length 1). For two unit vectors the dot product equals the cosine of the
angle between them, so a higher dot product means more similar. Searching is therefore just:

```
score(photo) = sum over 512 positions of photoVector[i] * queryVector[i]
```

Typical CLIP scores for a good match are around 0.2 to 0.35, so the *ranking* is what matters, not the absolute
value. seeq returns the 60 highest-scoring photos and does not apply a score cut-off.

### Why dates are handled separately

Embedding models do not reliably encode "August 5" or "2023". `QueryParser` extracts phrases such as `august 5`,
`5th of august 2024`, `in march` and `in 2023`, turns them into a month, day and year filter, and removes them from
the text. Photos are filtered by their date taken before similarity is computed. If the query is only a date, the
photos from that date are returned newest first with no embedding at all.

---

## The indexing pipeline

Everything below runs in `IndexWorker`, a WorkManager `CoroutineWorker`.

1. **List photos.** `MediaStoreSource.listImages()` queries `MediaStore.Images`, newest first. The date taken is
   used when present, otherwise the modified date. If photo access is missing it returns an empty list.
2. **Drop deleted photos.** `VectorIndex.retainOnly` removes index entries whose photo no longer exists.
3. **Find the work.** Photos not yet in the index are the pending list. Already indexed photos are never touched
   again.
4. **Promote to a foreground service.** `setForeground` is called **once**, with a progress notification, so Android
   treats the job as user-visible work and does not throttle it. After that the notification text is updated
   directly. Calling `setForeground` again for every update was found to be unsafe once the app is backgrounded.
5. **Decode ahead.** A producer coroutine on the IO dispatcher decodes photos into a channel with room for four, while
   the consumer embeds the current photo. Neither step waits for the other.
6. **Embed and store.** The consumer embeds each bitmap and adds the vector to the in-memory index, then recycles the
   bitmap.
7. **Tolerate bad photos.** A photo that cannot be decoded or embedded is skipped. Five failures in a row stop the job
   with an error, because that means the model itself is broken rather than one odd file.
8. **Save as it goes.** The index is written to disk every 25 photos and once more when the job ends or is cancelled,
   so being stopped by the system only costs the work since the last save. The notification is also updated every
   25 photos.

### Scheduling

| Job | Trigger | Constraints | Policy |
| --- | --- | --- | --- |
| Index now (`index-now`) | Opening the app with the model ready, and the end of a model download | Battery not low | `REPLACE`, with linear 10 second retry delay |
| Index periodically (`index-periodic`) | Every 12 hours | Device charging | Keep the existing schedule |
| Download model (`model-download`) | The "Download model" button | Network connected | Keep an existing job |

`index-now` uses `REPLACE` on purpose. WorkManager counts every system interruption (including a reinstall or a
process kill) as a failed attempt and delays the next start with growing backoff, so a stale waiting job could sit
idle for about an hour. The app only calls it when nothing is already indexing in the current process, and the retry
delay is short and linear.

---

## The search pipeline

1. The search box emits text into a `StateFlow`. After **300 ms without typing** the latest query is searched, and a
   newer query cancels the one still running.
2. `SearchEngine` runs `QueryParser` to split off any date filter.
3. If there is text left, `OnnxEmbedder.embedText` makes the query vector. The text model is loaded on the first
   search, not at app start.
4. `VectorIndex.search` scores every photo that passes the date filter against the query vector and keeps the top 60
   in a bounded min-heap, so it never sorts the whole index.
5. The ViewModel publishes the result. The UI shows the top 20 in the carousel and all 60 in the grid, or "No matches"
   with a hint if indexing is still running.

The search is an exact brute-force scan. At 512 dimensions a library of 2,650 photos is about 1.4 million
multiply-adds per query, which takes a few milliseconds, so no approximate index is needed.

---

## The index file

`files/index-clip-b32.bin` in app-private storage, written by `VectorIndex.save()`.

| Part | Type | Meaning |
| --- | --- | --- |
| Magic | int32 | `0x4C4C4958` ("LLIX") |
| Version | int32 | `1` |
| Dimension | int32 | `512` |
| Count | int32 | Number of photos |
| Records | repeated | `int64` MediaStore id, `int64` date taken in milliseconds, then 512 `float32` values |

Each photo costs 16 + 512 x 4 = **2,064 bytes**, so 2,650 photos use about 5.5 MB. The whole file is read into memory
on first use.

- **Safe writes.** The index is written to a temporary file and then moved over the real one, atomically where the
  file system allows, so a crash mid-save never leaves a half-written index.
- **Safe reads.** A missing file, a wrong magic number, a different dimension or a truncated file all produce an
  empty index rather than an error.
- **Model-specific name.** The file name contains the model (`clip-b32`) because vectors from different models cannot
  be compared. On first use the app deletes index files written for an earlier model.

---

## Model download

`ModelDownloadWorker` and `ModelManager` fetch the two ONNX files once.

- Runs as a **foreground job** with a progress notification, so it continues when you leave the app.
- **Resumable.** Downloads go to a `.part` file, and an interrupted download resumes with an HTTP `Range` request.
- **Retries on network errors** up to five times. An HTTP error (for example a wrong link) fails immediately with the
  status code instead of retrying.
- **One progress bar** across both files, using their known sizes.
- A file only gets its final name after it downloads completely, so a half-downloaded model is never used.
- After success the app deletes the earlier EmbeddingGemma model and caches (about 700 MB) if they are present, then
  starts indexing.

---

## Permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_IMAGES` (Android 13+), `READ_EXTERNAL_STORAGE` (up to Android 12) | Read the gallery to index it |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Lets you share only some photos. seeq works with whatever it can see |
| `INTERNET` | Only the one-time model download. Search never touches the network |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Run indexing and the download as foreground jobs |
| `POST_NOTIFICATIONS` (Android 13+) | Show the progress notification. Without it the job still runs, but a visible notification helps stop phones from pausing the app |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets you exempt seeq from battery optimisation so indexing is not paused in the background |

The app asks for photo access first. Once the model is ready, a "Keep indexing in the background" card offers the
notification and battery permissions and disappears when both are granted. Photo access is re-checked every time you
return to the app, because it can be revoked in Settings at any moment. While indexing runs with the app in front, the
screen is kept awake.

---

## Project structure

```
seeq/
├── build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml   build and dependency versions
└── app/src
    ├── main
    │   ├── AndroidManifest.xml
    │   ├── assets/clip/                  vocab.json, merges.txt  (CLIP tokenizer data)
    │   └── kotlin/com/example/locallens
    │       ├── MainActivity.kt           single activity, edge to edge
    │       ├── AppGraph.kt               app singletons: models, index, embedder, indexing progress
    │       ├── ProgressNotification.kt   the ongoing notification shared by both background jobs
    │       ├── data/
    │       │   ├── MediaStoreSource.kt   lists gallery photos
    │       │   └── VectorIndex.kt        in-memory vectors, brute-force search, file save and load
    │       ├── index/
    │       │   ├── IndexWorker.kt        decode, embed and store photos
    │       │   └── IndexScheduler.kt     index now, index periodically, download the model
    │       ├── ml/
    │       │   ├── Embedder.kt           Embedder interface and OnnxEmbedder
    │       │   ├── ClipTokenizer.kt      CLIP byte-pair tokenizer
    │       │   ├── ClipImage.kt          resize, crop and normalise a photo
    │       │   ├── ModelManager.kt       resumable download of the two models
    │       │   ├── ModelDownloadWorker.kt background job around ModelManager
    │       │   └── VectorMath.kt         scale to unit length
    │       ├── search/
    │       │   ├── QueryParser.kt        pulls dates out of the query
    │       │   └── SearchEngine.kt       date filter plus vector search
    │       └── ui/
    │           ├── PhotoPermission.kt    photo access screen
    │           ├── BackgroundAccess.kt   notification and battery prompts
    │           ├── main/                 LocalLensScreen, MainViewModel, CircularCarousel
    │           └── theme/                colours, type, shared components
    └── test/kotlin/com/example/locallens
        ├── data/VectorIndexTest.kt       8 tests
        ├── ml/ClipTokenizerTest.kt       5 tests, against the real vocabulary
        ├── ml/ClipImageTest.kt           3 tests
        ├── ml/VectorMathTest.kt          4 tests
        └── search/QueryParserTest.kt     8 tests
```

---

## Build, run and test

Requirements: JDK 17, the Android SDK with platform 36, and an Android phone with USB debugging on (or an emulator
with an ARM64 image).

```bash
# unit tests (JVM only, no phone needed)
./gradlew testDebugUnitTest

# build a debug APK
./gradlew assembleDebug

# build, install and start on a connected phone
./gradlew installDebug
adb shell am start -n com.example.locallens/.MainActivity
```

Use `gradlew.bat` on Windows. If the Kotlin compiler daemon fails to start on a low-memory machine, add
`-Pkotlin.compiler.execution.strategy=in-process`.

On first launch the app asks for photo access, then shows a "Download the search model" screen. Tap the button on
Wi-Fi (about 154 MB). Indexing starts automatically when the download ends.

---

## Measured performance

Measured on one device, a TECNO CM6 running Android 16 (API 36), with the app in the foreground and the phone
charging:

| | CLIP ViT-B/32 (current) | EmbeddingGemma 2, 740M (earlier) |
| --- | --- | --- |
| Time per photo | about 104 ms (including decoding) | about 2,300 ms |
| Whole library | 2,650 photos in about 5 minutes (9.5 photos per second) | about 4 hours, extrapolated |
| Model download | about 154 MB | about 485 MB |
| Index size | 5.5 MB for 2,650 photos | smaller vectors, 256 values each |

These are single-device, single-run figures, not a benchmark. Speed on other phones will differ.

---

## Design decisions and history

- **Why CLIP and not a larger model.** The first version used Google's EmbeddingGemma 2 (a 740M-parameter model that
  embeds text, images and audio) through LiteRT-LM. It worked, but on the test phone it needed about 2.3 seconds per
  photo and held about 3.7 GB of memory, so indexing a gallery took hours and the process was easily starved in the
  background. CLIP ViT-B/32 is far smaller, has mature on-device tooling, and keeps the same text-to-photo search
  idea. The `Embedder` interface made the swap local to the `ml` package.
- **Why ONNX Runtime.** The CLIP towers are published as ONNX with a quantised variant, and ONNX Runtime has a
  maintained Android build.
- **Why bundle the tokenizer.** It is 1.4 MB, and shipping it avoids two more downloads and removes a failure mode.
- **Why a plain file, not a database.** The index is read whole and searched by scanning, so a database would add
  complexity without speeding anything up.
- **Why photos are decoded directly.** An earlier version used the system's cached thumbnails, which could be tiny
  (for example 108 x 192 pixels), re-encoded them as JPEG, and let the model upscale them again. Decoding once, at the
  exact size the model needs, is both faster and truer to the original photo.
- **Why the date filter is separate.** See [Why dates are handled separately](#why-dates-are-handled-separately).
- **Why `setForeground` is called once.** Re-promoting the job for each progress update can stall once the app is
  backgrounded, so progress after the first call is a plain notification update.

---

## Limitations and known issues

- **CLIP ViT-B/32 is a small model.** It handles objects, scenes and colours well and struggles with fine detail, small
  text, counting and unusual concepts. A larger CLIP variant would improve quality at the cost of speed and size.
- **English-centric.** The text tower was trained mostly on English captions.
- **Centre crop.** Very wide or tall photos, such as screenshots, lose their edges before embedding, so content near
  the edges influences the match less.
- **Tokenizer shortcut.** The reference CLIP tokenizer repairs broken text with the `ftfy` library and unescapes HTML
  first. seeq skips both steps, which only matters for unusual input.
- **Background throttling on some phones.** Some manufacturers pause apps that are not on screen, even when they run a
  foreground service. During development the earlier, heavier model slowed to a crawl or stalled once the app left
  the screen. The foreground notification, the notification permission and the battery exemption prompt are the
  mitigations, and the much faster indexing makes the problem largely moot, but background behaviour of the current
  model has not been stress-tested across phones.
- **Photos only.** Videos are not indexed.
- **Brute-force search.** Exact and fast for tens of thousands of photos. A much larger library would want an
  approximate index.
- **Debug APK size.** The debug build is about 165 MB because ONNX Runtime ships native code for every phone
  architecture. Limiting the build to 64-bit ARM would remove most of it.
- **Tests are JVM-only.** Embedding quality and the Android UI are checked by hand on a phone, not by automated
  instrumented tests.

---

## Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| The setup screen shows an error after tapping Download | The message includes the HTTP status. Check the connection. Partial downloads resume on retry |
| "Download the search model" appears again after an update | The model changed, so the new files are fetched and the old index is rebuilt |
| Results look unrelated | The index may still be building. The screen shows "Indexing photos N of M". Wait for it to finish |
| Indexing seems to pause when you leave the app | Allow notifications and background running from the in-app card, or turn off battery optimisation for seeq in system settings |
| No photos appear | Photo access is off or limited. Open the app, grant "Allow all", or choose more photos on Android 14+ |
| A date in the query is ignored | Use forms like `august 5`, `5th of august 2024`, `in march` or `in 2023` |

---

## Licences and credits

- **OpenAI CLIP**, MIT licence. Model weights and tokenizer vocabulary.
- **Xenova/clip-vit-base-patch32**, MIT licence. The ONNX export and quantised variants used here.
- **ONNX Runtime**, MIT licence, from Microsoft.
- The circular carousel is adapted from the idea of React Bits' CircularCarousel.
- Android Jetpack, Kotlin and the other open-source libraries are used under their own licences.
