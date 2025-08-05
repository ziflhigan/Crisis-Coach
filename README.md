# Crisis Coach

*Your On-Device AI Partner for Mission-Critical Emergency Response*

Crisis Coach is a revolutionary, offline-first mobile assistant designed for the front lines of 
emergency and disaster response. It harnesses the power of Google's state-of-the-art Gemma 3n model, 
running entirely on-device to deliver critical AI-powered support without any reliance 
on network connectivity. This ensures that first responders have the tools they need to 
make informed decisions, communicate effectively, and save lives, 
even when infrastructure is compromised. 
The application is currently available for Android, with an iOS version planned for future release.

## Overview
![overview-cc](https://github.com/user-attachments/assets/b9855eb3-c436-4a06-95e6-0bdc58ebd391)
![settings-tab](https://github.com/user-attachments/assets/4652bcb4-f7d6-4e1c-91b9-db66eef9dbf6)


## Translation
![Translatation](https://github.com/user-attachments/assets/1d1fccf8-7744-4f5b-9c44-e4d6294a649a)


## Image Triage Analysis
![Image Analysis](https://github.com/user-attachments/assets/b255ff6c-893a-4eb3-af5b-771c9a96bb9c)


## On-Device Knowledge Access
![Knowledge](https://github.com/user-attachments/assets/2bcf81aa-877a-48e5-8d1b-c737ffb739e0)


## Core Features

* **100% Offline Functionality:** All AI processing, from language translation to image analysis, happens locally. No cloud calls, no internet dependency, ensuring absolute reliability and data privacy.
* **Real-time Multimodal Translation:** Break down communication barriers with instant, on-the-fly translation between languages. Features speech-to-text, text-to-speech, and phonetic guidance to ensure clear pronunciation.
* **AI-Powered Image Analysis:** Leverage the vision capabilities of Gemma 3n for rapid field assessments. Analyze images to identify the severity of structural damage or perform initial medical triage on injuries.
* **On-Device RAG Knowledge Base:** Access a comprehensive, pre-loaded emergency medical database using natural language queries. The Retrieval-Augmented Generation (RAG) system provides immediate, contextually relevant information from trusted protocols.
* **Adaptive Performance:** Automatically detects device capabilities to deploy the optimal model—the efficient Gemma-3n-E2B (\~5B parameters) for mid-range devices or the high-accuracy Gemma-3n-E4B (\~8B parameters) for high-end hardware, with GPU/NPU acceleration via LiteRT.

## Get Started in Minutes

You can experience Crisis Coach by downloading the latest APK from our releases page.

* **Download the latest version here: [Download Here](https://github.com/ziflhigan/Crisis-Coach/releases/download/v1.0.0/crisis-coach-preview.apk)**

For detailed installation instructions, technical implementation details, and a guide to the app's features, please visit our project wiki. We also have a comprehensive technical write-up on our blog that explains the end-to-end development journey.

* **Project Wiki:** 
* **Technical Blog Post:** 

## Technology Highlights

Crisis Coach stands at the bleeding edge of on-device AI, integrating a suite of powerful technologies to deliver its robust capabilities:

* **Google Gemma 3n:** The core intelligence of the app, utilizing Google's latest open, lightweight, and state-of-the-art model designed for on-device applications.
* **LiteRT (formerly TensorFlow Lite):** The high-performance, cross-platform runtime from Google AI Edge that executes the Gemma model efficiently across CPU, GPU, and NPU hardware.
* **MediaPipe LLM Inference API:** A streamlined, powerful API used to manage and interact with the Gemma model on Android for both text and vision-language tasks.
* **Hugging Face Integration:** The application seamlessly authenticates with and downloads the required Gemma model variants directly from Hugging Face repositories upon initial setup.
* **On-Device RAG with ObjectBox:** Implements a sophisticated Retrieval-Augmented Generation pipeline using the ObjectBox mobile vector database to search and retrieve information from a local knowledge base.
* **Native Text-to-Speech (TTS) Engine:** Utilizes Android's built-in TTS API to provide clear, audible voice output for translations, ensuring accessibility and ease of use in noisy environments. This feature is a core part of our current translation system and will be enhanced in a future release with our integrated Whisper model to handle the speech recognition component, creating a fully self-contained, offline voice-to-voice translation loop.

## Future Improvements

We are constantly exploring new ways to enhance Crisis Coach. Key features planned for our next development phases include:

* **Fully Offline Speech Recognition:** We have begun backend integration of a `Whisper-tiny-en.tflite` model. The next iteration will complete this work to enable a fully voice-driven user experience for commands and transcription, removing any remaining reliance on the Android OS for speech-to-text.
* **Expanded Knowledge Domains:** Ingesting additional specialized knowledge bases, such as hazardous material handling guides, advanced wilderness first aid, and international disaster response codes.
* **Collaborative Response Mode:** A feature allowing multiple responders in a local area to create an ad-hoc, peer-to-peer mesh network to share data, annotations, and tagged points of interest without external infrastructure.
* **Automated Reporting:** Using generative AI to auto-draft incident reports based on a timeline of actions taken within the app (e.g., translations performed, images analyzed, queries made) to reduce administrative burden post-crisis.

## Feedback

This project is part of the Google Gemma 3n Hackathon. We welcome any and all feedback\! 
Please feel free to open an issue in this repository to report bugs, suggest features, or ask questions.

## License

This project is licensed under the [LICENSE](https://github.com/ziflhigan/Crisis-Coach/blob/main/LICENSE).

## Appreciation and Reference Links

This project would not be possible without the incredible open-source tools and models provided by Google and the wider AI community.

* **Competition:** [Google Gemma 3n Hackathon](https://www.kaggle.com/competitions/google-gemma-3n-hackathon/overview)
* **Model:** [Gemma 3n by Google AI](https://ai.google.dev/gemma/docs/gemma-3n)
* **Runtime:** [Google AI Edge](https://ai.google.dev/edge)
-----

*This project was developed by **the cautious 5**. *
