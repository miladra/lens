# Lens
This application is written by AI.

Lens is a powerful, multi-modal translation app for Android that leverages advanced AI models to translate text, images, and audio in real-time. Built with modern Android development practices, it provides a seamless experience for understanding content across different languages and formats.

## Features

- **Multi-Modal Translation**:
    - **Text**: Instant translation for typed or pasted text.
    - **Image**: Capture photos or select images from your gallery to translate text within them. Includes a built-in crop tool for precise selection.
    - **Audio**: Record system or ambient audio for transcription and translation.
- **Multiple AI Providers**: Support for various LLM providers including:
    - **Google Gemini** (Pro and Flash models)
    - **Groq** (Llama 3, Mixtral)
    - **OpenRouter** (Access to various models like Claude, GPT-4, etc.)
- **Word Explanation**: Deep-dive into specific words or phrases within your translations to understand context, grammar, and nuances.
- **Translation History**: Keep track of your past translations for quick reference.
- **Configurable Settings**: Customize your target language, preferred AI provider, and manage API keys securely.
- **Modern UI**: Built entirely with **Jetpack Compose**, featuring a clean, responsive, and intuitive interface with support for Material Design 3.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **AI Integration**: 
    - Google AI SDK (Generative AI)
    - Retrofit for API interactions (Groq, OpenRouter)
- **Image Processing**: CameraX for capturing and processing images.
- **Audio**: Custom `AudioCaptureService` for high-quality audio recording.
- **Data Persistence**: `SharedPreferences` with Gson for configuration and history storage.
- **Dependency Management**: Gradle (KTS)

## Getting Started

### Prerequisites

- Android Studio Koala or newer.
- Android SDK 24+.
- API Keys for at least one of the supported providers:
    - [Google AI Studio (Gemini)](https://aistudio.google.com/)
    - [Groq Console](https://console.groq.com/)
    - [OpenRouter](https://openrouter.ai/)

### Installation

1. Clone the repository:
2. Open the project in Android Studio.
3. Build and run the app on your device or emulator.
4. Go to the **Settings** icon in the app and enter your API keys to start translating.

## How it Works

Lens acts as a bridge between user input (text, image, or audio) and powerful LLMs. By providing context-aware prompts, it ensures that translations are not just literal but also culturally and contextually accurate. The "Explain Word" feature uses the surrounding translated text as context to provide more meaningful definitions.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
