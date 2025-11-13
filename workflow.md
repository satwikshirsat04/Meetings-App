## 🎯 **Core Features**

### 1. **Offline Audio Recording & Processing**
- Records conversations using device microphone
- No internet required - all processing happens locally
- Real-time audio processing and analysis

### 2. **Automatic Speaker Diarization**
- Identifies and separates different speakers in conversations
- Supports up to 4 speakers simultaneously
- Assigns unique colors and labels to each speaker

### 3. **Real-time Transcription**
- Converts speech to text in real-time
- Tags each transcript entry with speaker identification
- Maintains conversation flow with timestamps

### 4. **Smart Note Extraction**
- Automatically extracts different types of notes:
  - **Key Points** - Important statements and long explanations
  - **Action Items** - Tasks and commitments (detects action verbs)
  - **Decisions** - Conclusions and agreements
  - **Questions** - Queries and clarifications
  - **Bookmarks** - User-added markers during recording

### 5. **Conversation Analytics**
- Speaker talking time percentages
- Duration tracking
- Keyword extraction from conversations
- Session summaries with organized notes

## 🔄 **Workflow & How It Works**

### **1. Recording Phase** (`LiveCaptureScreen`)
```
User starts recording → AudioRecorder captures audio → Real-time processing begins
```

**Technical Process:**
- `AudioRecorder` captures audio data at 16kHz
- Audio is processed in chunks by `AudioProcessor`
- `SpeakerDiarizer` analyzes audio features using MFCC (Mel-frequency cepstral coefficients)
- Real-time waveform visualization shows audio amplitude

![UI](/assets/UI1.jpg)
![UI](/assets/UI2.jpg)

### **2. Speaker Classification Process**

The app uses **audio fingerprinting** to distinguish speakers:

```kotlin
// Core Speaker Identification Logic (SpeakerDiarizer.kt)

fun processAudioSegment(audioData: ShortArray, sampleRate: Int): Int? {
    val features = audioProcessor.extractMFCC(audioData, sampleRate) // Extract audio features
    return identifySpeaker(features, timestamp) // Match against known speaker profiles
}
```

**How Speaker Classification Works:**
1. **Feature Extraction**: `AudioProcessor` converts audio to MFCC features (unique audio fingerprints)
2. **Similarity Comparison**: Compares current audio segment with existing speaker profiles using cosine similarity
3. **Speaker Assignment**:
   - If similarity > threshold (0.7): Assign to existing speaker
   - If new voice pattern: Create new speaker profile (up to 4 speakers)
   - Uses sliding window of recent features for adaptive learning

### **3. Real-time Processing Pipeline**
```
Audio Input → Feature Extraction → Speaker ID → Transcription → Note Extraction
     ↓              ↓               ↓           ↓              ↓
 AudioRecorder → AudioProcessor → SpeakerDiarizer → Transcript → NoteExtractor
```

### **4. Post-Recording Analysis**

When recording ends:
```kotlin
// NoteExtractor.kt - Automatic note categorization
fun extractNotes(transcriptEntries: List<TranscriptEntry>): List<Note> {
    // Analyzes transcript for patterns:
    // - Action verbs → Action Items
    // - Decision words → Decisions  
    // - Question words → Questions
    // - Important phrases → Key Points
}
```

**Keyword Extraction**: `KeywordExtractor` identifies important terms and generates conversation titles.

### **5. Data Storage & Management**

**Room Database Entities:**
- `Session` - Recording sessions metadata
- `Speaker` - Speaker profiles and statistics  
- `TranscriptEntry` - Timestamped conversation text
- `Note` - Categorized notes and insights

### **6. Navigation & UI Flow**
```
HomeScreen → LiveCapture → SessionSummary ←→ Notes/History/Settings
     ↓           ↓              ↓
 Start Recording → Real-time Processing → Summary & Analytics
```

## 🧠 **Technical Architecture**

### **Core Components:**

1. **Audio Layer** (`AudioRecorder`, `AudioProcessor`)
   - Handles audio capture and preprocessing
   - Extracts MFCC features for speaker identification

2. **ML Processing Layer** (`SpeakerDiarizer`, `NoteExtractor`, `KeywordExtractor`)
   - Real-time speaker diarization
   - Natural language processing for note extraction
   - Keyword analysis and title generation

3. **Data Layer** (Room Database)
   - Local storage of all sessions, transcripts, and notes
   - Relationship: Session → Speakers → TranscriptEntries → Notes

4. **UI Layer** (Compose Screens)
   - Real-time visualization of conversation
   - Organized summary views
   - Navigation between features

## 🎨 **User Experience**

1. **Start**: Tap microphone on home screen
2. **Record**: Watch real-time speaker identification and transcription
3. **Review**: Get automated summary with categorized notes
4. **Manage**: Access history, notes, and settings
5. **Share**: Export sessions in multiple formats (text, PDF, JSON)

## 🔧 **Key Technical Innovations**

- **Offline-first design** - No cloud dependencies
- **Adaptive speaker recognition** - Learns speaker patterns during conversation
- **Real-time processing** - Immediate feedback during recording
- **Intelligent categorization** - Context-aware note extraction
- **Modular architecture** - Separated concerns for maintainability