# EchoSense - Jetpack Compose Edition

Modern Android conversation recording app built with Jetpack Compose, featuring offline speaker diarization and automatic note extraction.

## Features

### 🎤 Real-time Recording
- Live audio capture with waveform visualization
- Up to 4 speaker identification
- Real-time transcript display
- Bookmark important moments

### 🧠 Intelligent Processing
- Offline speaker diarization using MFCC
- Automatic note extraction
- Action item detection
- Decision tracking
- Question identification

### 📱 Modern UI
- Built with Jetpack Compose
- Material Design 3
- Smooth animations
- Dark theme optimized
- Gradient backgrounds matching design

### 🔒 Privacy First
- 100% offline processing
- No internet required
- No data collection
- Local storage only

### 📤 Export Options
- Text format
- PDF generation
- JSON export
- Share individual sessions
- Bulk export all notes

## Tech Stack

- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with ViewModels
- **Database**: Room (SQLite)
- **Navigation**: Jetpack Navigation Compose
- **Coroutines**: Kotlin Coroutines & Flow
- **Audio**: AudioRecord API
- **ML**: Custom MFCC implementation
- **PDF**: iText library
- **Permissions**: Accompanist Permissions

## Project Structure
```
com.echosense/
├── models/              # Data models
├── db/                  # Room database
├── audio/               # Audio processing
├── ml/                  # ML algorithms
├── ui/
│   ├── theme/          # Compose theme
│   ├── screens/        # Screen composables
│   └── navigation/     # Navigation setup
├── viewmodels/         # ViewModels
└── utils/              # Utilities
```

## Installation

1. **Prerequisites**
   - Android Studio Hedgehog or newer
   - Gradle 8.13
   - JDK 17
   - Android SDK 24+

2. **Clone and Build**
```bash
   git clone <repository>
   cd EchoSense
   ./gradlew build
```

3. **Run**
   - Connect Android device or emulator
   - Click Run in Android Studio
   - Grant microphone permissions

## Usage

### Start Recording
1. Tap the large mic button on home screen
2. Grant microphone permission
3. Speak naturally - app detects speakers automatically

### During Recording
- Watch live waveform visualization
- See active speaker indicators
- View real-time transcript
- Pause/resume as needed
- Add bookmarks

### After Recording
- View extracted notes by category
- See speaker breakdown with statistics
- Read full transcript
- Share in multiple formats
- Delete unwanted sessions

## Building for Production
```bash
./gradlew assembleRelease
```

Generate signed APK in Android Studio:
1. Build → Generate Signed Bundle/APK
2. Select APK
3. Create or select keystore
4. Choose release variant

## Permissions

- `RECORD_AUDIO`: Required for recording conversations
- `READ_MEDIA_AUDIO` (Android 13+): Access audio files
- `WRITE_EXTERNAL_STORAGE` (Android 12-): Save audio files

## Key Components

### Screens
- **HomeScreen**: Main landing with mic button
- **LiveCaptureScreen**: Active recording interface
- **SessionSummaryScreen**: Post-recording summary
- **NotesScreen**: All notes browsing
- **HistoryScreen**: Past sessions
- **SettingsScreen**: App configuration

### ViewModels
- **LiveCaptureViewModel**: Recording state management
- **SessionSummaryViewModel**: Session data handling
- **NotesViewModel**: Notes collection management
- **HistoryViewModel**: Session history
- **SettingsViewModel**: App settings

## Performance

- **Memory**: ~50-100MB during recording
- **Storage**: ~10MB per hour (16kHz)
- **Battery**: Optimized for long sessions
- **Processing**: Real-time on mid-range devices

## Customization

### Change Theme Colors
Edit `ui/theme/Theme.kt`:
```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6DD5ED),
    // Modify colors here
)
```

### Adjust Speaker Count
In `LiveCaptureViewModel.kt`:
```kotlin
private val speakerDiarizer = SpeakerDiarizer(maxSpeakers = 4)
```

### Change Recording Quality
In `AudioRecorder.kt`:
```kotlin
private val sampleRate = 16000  // Change to 8000 or 44100
```

## Troubleshooting

### Build Errors
- Clean project: `./gradlew clean`
- Invalidate caches: File → Invalidate Caches
- Update Gradle: File → Project Structure → Project

### Runtime Issues
- Check permissions in Settings
- Ensure device has microphone
- Check storage availability
- Review Logcat for errors

## Contributing

Areas for improvement:
- Real STT integration
- Enhanced speaker recognition
- Multi-language support
- Cloud sync (optional)
- Wear OS support

## License

Educational and personal use

## Credits

- Material Design 3
- Jetpack Compose
- Room Persistence
- iText PDF
- Accompanist

---

**EchoSense** - Modern offline conversation companion built with Jetpack Compose