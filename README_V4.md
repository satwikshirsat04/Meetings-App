# EchoSense - AI-Powered Conversation Analysis App

EchoSense is an Android application that provides real-time conversation analysis with automatic speaker diarization and speech-to-text capabilities. The app intelligently identifies different speakers in a conversation and generates comprehensive transcripts with speaker attribution.

## 🎯 Core Features

- **Real-time Speaker Diarization** - Automatically identifies and tracks multiple speakers
- **Offline Speech Recognition** - Uses Vosk engine for privacy-focused processing
- **Live Audio Visualization** - Beautiful waveform and speaker activity indicators
- **Smart Note Extraction** - Automatically generates summaries and action items
- **Multi-speaker Support** - Configurable for 2-6 speakers in a conversation

## 🔧 Technical Architecture

### Audio Processing Pipeline

The app employs a sophisticated audio processing workflow:

```
Audio Input → Pre-processing → Feature Extraction → Speaker Identification → Speech Recognition
```

### Key Components

#### 1. AudioProcessor.kt
**Enhanced MFCC Feature Extraction**

The `AudioProcessor` class implements a comprehensive feature extraction pipeline:

- **Pre-emphasis Filter**: Enhances higher frequencies using `out[i] = data[i] - alpha * data[i-1]`
- **Framing**: Segments audio into 400-sample frames with 50% overlap (160-sample hop)
- **Windowing**: Applies Hamming window to reduce spectral leakage
- **FFT Processing**: Custom radix-2 FFT implementation for power spectrum calculation
- **Mel Filterbank**: Converts to mel scale using 26 triangular filters
- **MFCC Computation**: Extracts 13 cepstral coefficients using DCT
- **Enhanced Features**: Combines MFCC + Delta + Delta-Delta (39 total dimensions)

```kotlin
// Feature extraction workflow
val emphasized = preEmphasis(bufferArray)
val frames = frameSignal(emphasized, FRAME_SIZE, HOP_SIZE)
val windowed = applyHamming(padded)
val power = computePowerSpectrum(windowed)
val mfcc = computeMFCC(power, sampleRate, NUM_MFCC)
```

#### 2. SpeakerDiarizer.kt
**Real-time Speaker Identification**

The diarization system uses cosine similarity on MFCC features:

- **Feature Normalization**: L2 normalization ensures consistent similarity comparisons
- **Speaker Profiles**: Maintains rolling window of embeddings for each speaker
- **Similarity Thresholding**: Uses configurable threshold (0.72) for speaker matching
- **Centroid Comparison**: Compares new features against averaged speaker embeddings
- **Smart Switching**: Implements minimum switch delay (800ms) to prevent rapid changes

```kotlin
// Speaker identification logic
val normalized = l2norm(features)
val centroid = computeCentroid(p.embeddings)
val sim = cosine(centroid, normalized)

if (sim >= similarityThreshold) {
    // Match existing speaker
    updateSpeaker(bestId, normalized)
} else if (profiles.size < maxSpeakers) {
    // Create new speaker profile
    createSpeaker(normalized)
}
```

#### 3. AudioRecorder.kt
**Efficient Audio Capture**

- **Fixed Frame Size**: 320 samples (20ms @ 16kHz) for consistent processing
- **Buffer Management**: Handles audio data in manageable chunks
- **Amplitude Monitoring**: Provides real-time volume levels for visualization
- **File Output**: Optional PCM recording for session archival

#### 4. LiveCaptureViewModel.kt
**Orchestration Layer**

- **Pipeline Coordination**: Manages audio processing, diarization, and recognition
- **Database Integration**: Stores transcripts, speaker data, and session information
- **State Management**: Maintains UI state and processing status
- **Resource Management**: Handles Vosk model lifecycle and memory management

## 🎙️ Real-time Processing Workflow

1. **Audio Capture**
   - 16kHz mono audio captured in 20ms frames
   - Real-time amplitude calculation for visualization

2. **Feature Extraction**
   - MFCC features extracted from each audio frame
   - 39-dimensional feature vector (13 MFCC + delta + delta-delta)

3. **Speaker Diarization**
   - Features compared against existing speaker profiles
   - Cosine similarity determines speaker identity
   - New speakers created when similarity below threshold

4. **Speech Recognition**
   - Audio frames accumulated and sent to Vosk engine
   - Partial results for live feedback
   - Final results stored with speaker attribution

5. **Database Storage**
   - Speakers created dynamically as detected
   - Transcript entries linked to speaker IDs
   - Session metadata and timing information

## 🎨 User Interface

The app features a modern, dark-themed interface with:

- **Live Speaker Circles**: Animated indicators showing active speakers
- **Waveform Visualization**: Real-time audio amplitude display
- **Transcript Panel**: Color-coded speaker attribution
- **Control Buttons**: Pause, stop, and bookmark functionality
- **Session Management**: Automatic note extraction and summary generation

## 🔧 Configuration Options

- **Speaker Count**: Configurable from 2-6 speakers
- **Similarity Threshold**: Adjustable speaker matching sensitivity
- **Processing Parameters**: Customizable frame sizes and overlap
- **Model Selection**: Offline Vosk model for privacy

## 🚀 Performance Optimizations

- **Efficient FFT**: Custom radix-2 implementation optimized for mobile
- **Buffer Management**: Fixed-size buffers prevent memory leaks
- **Background Processing**: Coroutine-based async processing
- **Model Caching**: Vosk model loaded once per session

## 📊 Output Features

- **Speaker-Attributed Transcripts**: Who said what and when
- **Session Summaries**: Automated meeting minutes
- **Action Item Extraction**: Identifies decisions and tasks
- **Multiple Export Formats**: Text, PDF, and JSON options

EchoSense provides enterprise-grade conversation analysis with privacy-focused offline processing, making it ideal for meetings, interviews, and any multi-speaker scenarios where accurate speaker attribution is essential.