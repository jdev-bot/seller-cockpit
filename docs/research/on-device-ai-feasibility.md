# Research: On-Device AI for Product Identification & Listing Generation

## Date: May 2026
## Context: AI Seller Cockpit for Physical Products

---

## Executive Summary

**Short answer: Currently NO.** Neither Apple Intelligence / Core ML Vision nor Android AI Core provide the generative multimodal capabilities needed for this product's core AI pipeline (product identification from images, condition assessment, listing text generation, pricing reasoning). We should continue planning for **cloud AI as primary**, with **optional on-device pre-processing as a future optimization**.

---

## 1. Apple Intelligence / iOS / Core ML

### 1.1 What Apple Platform AI Actually Provides

Apple's on-device AI ecosystem is excellent at **specific, narrow tasks** but weak at **generative understanding**:

| Capability | API/Framework | Available On | Usefulness for Product Listing |
|---|---|---|---|
| Object detection/classification | `VNRecognizeObjectsRequest`, `VNClassifyImageRequest` | iOS 13+ | Can detect "furniture", "electronics" — but NOT brand/model/variant |
| Text recognition (OCR) | `VNRecognizeTextRequest` | iOS 13+ | Can read serial numbers, brand names from packaging — USEFUL |
| Face/body/pose detection | `VNDetectFaceRectanglesRequest`, `VNDetectHumanRectanglesRequest` | iOS 11+ | Not relevant for this product |
| Animal recognition | `VNRecognizeAnimalsRequest` | iOS 13+ | Not relevant |
| Feature extraction (embedding) | `VNGenerateImageFeaturePrintRequest` | iOS 13+ | Can compare images for similarity — MODERATELY USEFUL |
| Scene classification | `VNClassifyImageRequest` (scene category) | iOS 13+ | "indoor", "outdoor", "work" — not useful for product IDs |
| Image segmentation | `VNGenerateForegroundInstanceMaskRequest` | iOS 17+ | Could remove background — USEFUL for image prep |
| Core ML custom models | `MLModel` | iOS 11+ | Can run trained PyTorch/TensorFlow models — requires training OUR model |
| **Apple Intelligence (generative)** | Foundation Models, Image Playground | iOS 18+/iPhone 15 Pro+ | Available only to Apple apps + limited partner access via `App Intents`; NOT available for custom generative image analysis |
| **On-device LLM inference** | No public API for custom LLMs | N/A | Apple does NOT expose an on-device LLM API that 3rd party devs can use for arbitrary prompts |

### 1.2 Critical Limitations for Our Use Case

1. **No product/brand/model identification built-in**: Apple's models can say "this is a keyboard" or "this is a watch" but cannot say "Apple Magic Keyboard with Numeric Keypad" or "iPhone 13 Pro Max 256GB Graphite". This is the CORE requirement of the app.

2. **No generative text APIs for Apple Intelligence**: Apple Intelligence's on-device Foundation Models are NOT accessible via 3rd-party APIs. The `App Intents` framework only allows very limited, Apple-controlled interactions (e.g., summarizing text, rewriting). You cannot feed it an image and ask for JSON structured output about product facts.

3. **Custom Core ML training is possible but impractical**: We could train our own Core ML classifier for specific product categories, but:
   - Would require millions of labeled product images
   - Would not generalize to arbitrary items (every user's product is different)
   - Would not handle condition assessment, defect detection, or listing generation
   - Model size/complexity limited to what fits on Neural Engine (typically <200MB for fast inference)

4. **Vision OCR is useful but ancillary**: `VNRecognizeTextRequest` could extract visible brand names or serial numbers from close-up photos — could be used as an AUGMENTATION signal sent to the cloud AI, but not a replacement.

---

## 2. Android AI Core / Gemini Nano

### 2.1 What Android Platform AI Actually Provides

Google's on-device AI is more advanced than Apple's in terms of **generative multimodal capabilities**:

| Capability | API | Required Level | Usefulness |
|---|---|---|---|
| Text summarization, rewriting | AICore / Gemini Nano (Prompt API) | Pixel 8+, Pixel 9, Samsung S24+, Android 15 (some) | **Useful** for condensing listing descriptions; NOT for image analysis |
| **Image description / captioning** | Gemini Nano Multimodal | Pixel 9 series, flagships 2025+ | **USEFUL** — can describe images; accuracy unknown on consumer products |
| **Speech recognition** | AICore Speech Recognition | Pixel 8+ | Not relevant |
| Content moderation, smart reply | Gemini Nano | Limited devices | Not directly relevant |
| ML Kit (traditional) | Barcode scanning, face detection, custom TFLite | Most Android devices | Not useful for product identification |
| Gemma 4 on-device | ML Kit GenAI APIs | High-end devices | Very limited availability; in preview |
| Full Gemini cloud | `generativelanguage.googleapis.com` | All devices | Requires cloud API key |

### 2.2 Critical Limitations for Our Use Case

1. **Gemini Nano is text-only on most devices**: The on-device Gemini Nano that ships on Pixel 8 and most Android 15 devices is ~3.2B parameters and **TEXT-ONLY**. It cannot process images. The **multimodal version** (which CAN see images) only runs on:
   - Pixel 9 / Pixel 9 Pro / Fold (and later)
   - Samsung Galaxy S25+ (and very latest flagships)
   - Very limited adoption — maybe 5-10% of Android users globally

2. **No structured JSON output from on-device nano**: Even the multimodal nano lacks the instruction-following reliability of GPT-4o-mini or Gemini 1.5 Flash for producing strict JSON schemas with product fields (brand, model, condition, etc.). On-device models are optimized for speed, not reasoning.

3. **Cannot identify specific consumer product SKUs**: Like Apple, even the multimodal Gemini Nano on Pixel 9 cannot reliably differentiate between "Logitech MX Master 3S" and "Logitech MX Master 3" just from a photo, or identify clothing sizes, storage capacities, etc. It might say "keyboard" or "mouse" but not the specific model needed for marketplace listings.

4. **Hardware fragmentation**: Android's on-device AI requires Google Play Services + AICore + latest OS. Many Android phones in Europe (a key market for Kleinanzeigen) are mid-range devices without AICore support.

---

## 3. What COULD Be Done On-Device (Hybrid Model)

While generative product identification from image → structured facts is **not feasible** on-device today, there are some opportunities for hybrid approaches:

### 3.1 Feasible On-Device Tasks

| Task | Feasibility | Value | Notes |
|---|---|---|---|
| **Background removal** (image segmentation) | HIGH (iOS VNGenerateForegroundInstanceMaskRequest, Android ML Kit Selfie Segmentation or custom TFLite) | Medium | Makes product photos cleaner; reduces need for cloud image optimization |
| **OCR** (read brand/model from packaging/labels) | HIGH (both platforms) | Medium | Could extract serial numbers, model names to verify cloud AI output |
| **Image quality scoring** (blur detection, exposure) | HIGH (both platforms) | Medium | Could pre-filter bad frames before uploading to cloud |
| **Barcode/QR reading** | HIGH (both platforms) | Low | Only if packaging has barcodes |
| **Duplicate frame detection** (similarity) | MEDIUM (Core ML feature extraction, TFLite embeddings) | Low | Could reduce upload bandwidth |
| **Basic scene/location detection** | MEDIUM | Low | Not useful for marketplace listings |
| **Simple classification** ("electronics" vs "furniture" vs "clothing") | HIGH | Low | Not enough granularity for marketplace listings |

### 3.2 What MUST Stay in Cloud

| Task | Required Capability | On-Device Feasibility |
|---|---|---|
| Product identification (brand/model/variant) | Requires training on millions of SKUs or internet-scale knowledge | NO |
| Condition assessment (scratches, wear, cracks) | Fine-grained visual understanding + domain knowledge | NO |
| Listing title/description generation | Large language model reasoning + marketplace optimization | NO |
| Market research (price estimation) | Web scraping + LLM reasoning across platforms | NO |
| Pricing calculation (VAT, margin, fees) | Deterministic math, could run anywhere, but data must come from research | YES technically, but data unavailable |
| Image selection ("which photo is best") | Could be heuristic-based locally, AI ranking better in cloud | MAYBE hybrid |

---

## 4. Cross-Platform Local AI Frameworks

### 4.1 React Native / Expo Native Modules
Neither React Native nor Expo have built-in support for Core ML or AICore. You need:
- **iOS**: Native Swift/Kotlin module wrapping Core ML + Vision APIs
- **Android**: Native module wrapping AI Core / ML Kit

This would require a separate native module package, maintained in the monorepo.

### 4.2 Lightweight Local LLMs
Consider third-party on-device LLM SDKs:
- **MLC LLM** (Apache TVM): Runs Llama, Mistral on mobile; very slow, limited to simple text tasks
- **llama.cpp**: iOS/Android ports exist; too slow for real-time image processing
- **MediaPipe LLM Inference**: Google's cross-platform LLM runner; limited model support
- **ONNX Runtime Mobile**: Can run quantized models, but requires training your own

**Reality**: Running a 3B+ parameter multimodal model on a phone is technically possible on latest flagships, but:
- Inference time: 10-30 seconds per image
- Battery drain significant
- Accuracy far below cloud GPT-4o-mini or Gemini 1.5 Flash
- No proven models for product identification available off-the-shelf

---

## 5. Strategic Recommendation

### Recommended Architecture: Cloud AI Primary + On-Device Pre-Processing Optional

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER SMARTPHONE                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │ Camera/Video  │→ │ On-Device    │→ │ Cloud Upload │           │
│  │ Capture       │   │ Pre-Filter   │    │ (photos only)│           │
│  └──────────────┘  │  - Blur check  │  └──────────────┘           │
│                    │  - Segmentation│                              │
│                    │  - OCR hints    │                              │
│                    └──────────────┘                               │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌───────────────────────────────────────────────────────────────────────┐
│                          CLOUD BACKEND                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ AI Product   │→ │ Market       │→ │ Pricing      │→ │ Listing      │   │
│  │ Recognition   │   │ Research     │    │  Engine      │    │ Generator    │   │
│  │ (GPT-4o-mini)│   │ (eBay+Scraper)│   │               │    │               │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
└───────────────────────────────────────────────────────────────────────────┘
```

### Phase Rollout Recommendation

| Phase | On-Device AI | Cloud AI | Rationale |
|---|---|---|---|
| **MVP (now)** | None | OpenAI GPT-4o-mini, Gemini 1.5 Flash | Fastest to market, proven accuracy |
| **V2 (post-launch)** | OCR + blur filtering on-device | Same cloud AI | Reduce upload bandwidth, improve UX speed |
| **V3 (future)** | Image segmentation for background removal on-device | Same cloud AI | Better listing photos, less backend processing |
| **V4 (long-term)** | Monitor Apple/Google on-device genAI API evolution | Keep cloud fallback | Only if/when Apple/Google expose structured multimodal AI APIs to 3rd parties |

### Cost Implications
- **On-device approach**: Would save ~$0.005-0.02 per product case in API costs
- **BUT**: Would require building and maintaining separate iOS (Swift) and Android (Kotlin) native AI modules
- **BUT**: Would dramatically reduce supported device base (only flagship phones 2024+)
- **Conclusion**: Not worth the engineering cost at current model capability levels

---

## 6. Conclusion

**Do not rewrite the AI pipeline for on-device inference at this time.**

The product's core value proposition — "film your item, get a researched, priced, optimized listing" — requires capabilities that simply do not exist on-device in 2026:
- Recognizing specific product SKUs from photos
- Assessing condition and defects
- Researching comparable marketplace prices
- Generating platform-optimized listing text

**What to monitor for the future:**
1. Apple potentially expanding Apple Intelligence API to allow 3rd-party custom prompts and image analysis
2. Google expanding AICore Prompt API to full multimodal on mid-range devices
3. Qualcomm Snapdragon 8 Gen 4 / Apple M4-class NPUs enabling better local LLM inference
4. Open-source multimodal models (e.g., LLaVA variants) becoming viable on-device with sufficient accuracy

---

## Sources
- Apple Developer: Core ML documentation (developer.apple.com/documentation/coreml)
- Apple Developer: Vision framework (developer.apple.com/documentation/vision)
- Android Developers: AI on Android (developer.android.com/ai)
- Android Developers: ML Kit (developers.google.com/ml-kit)
- Google AI Blog: Gemini Nano on-device announcements
- Qualcomm: Snapdragon Neural Processing Engine capabilities
- Industry consensus from 2024-2025 WWDC, Google I/O, and AI Edge Summit conferences
