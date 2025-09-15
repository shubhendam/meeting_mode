# Meeting Mode Gemma

## Project Overview

Meeting Mode Gemma is an Android application that provides intelligent meeting transcription and summarization capabilities using Google's Gemma 3n AI model. The app records audio during meetings, processes it into manageable chunks, transcribes the content using on-device AI, and generates comprehensive meeting summaries - all while maintaining complete privacy by processing everything locally on the device.

## Technology Stack

### Core Technologies
- **Android SDK**: Native Android development with Kotlin
- **Jetpack Compose**: Modern UI framework for declarative interfaces
- **Google MediaPipe**: Framework for running Gemma 3n model on-device
- **Gemma 3n E2B**: Google's lightweight multimodal AI model optimized for mobile devices
- **Kotlin Coroutines**: Asynchronous programming for background processing

### Audio Processing
- **Android AudioRecord API**: Low-level audio recording with PCM format
- **WAV File Format**: 16kHz mono audio compatible with Gemma 3n requirements
- **Custom Audio Chunking**: 30-second segments with 5-second overlap for seamless processing

### AI Model Integration
- **MediaPipe LLM Inference**: On-device model execution framework
- **Gemma 3n E2B (2B parameters)**: Efficient model variant for audio transcription and text summarization
- **Model Hot-swapping**: Dynamic instruction loading for different AI tasks

## Project Structure

```
meeting_mode_gemma/
├── app/
│   ├── build.gradle.kts                 # App-level dependencies and configuration
│   └── src/main/
│       ├── AndroidManifest.xml          # App permissions and component declarations
│       ├── java/com/example/meeting_mode_gemma/
│       │   ├── MeetingModeGemmaApplication.kt    # Application entry point
│       │   ├── MainActivity.kt                   # Main activity with permission handling
│       │   ├── audio/
│       │   │   └── AudioRecorder.kt             # Audio recording implementation
│       │   ├── data/
│       │   │   └── MeetingSession.kt            # Data models for meeting sessions
│       │   ├── processing/
│       │   │   ├── AudioProcessor.kt            # Audio chunking and file management
│       │   │   └── GemmaAudioEngine.kt          # AI model integration and inference
│       │   └── ui/
│       │       ├── MeetingScreen.kt             # Main UI with recording controls
│       │       ├── MeetingViewModel.kt          # UI state and business logic
│       │       └── theme/                       # Compose UI theming
│       └── res/
│           ├── values/                          # App resources and themes
│           └── xml/                             # Configuration files
└── build.gradle.kts                     # Project-level configuration
```

## Core Components

### 1. Audio Recording (`audio/AudioRecorder.kt`)
**Purpose**: Captures high-quality audio suitable for AI processing
- **Format**: 16kHz mono PCM, 16-bit depth
- **Output**: WAV files with proper headers
- **Features**:
    - Real-time audio capture using Android AudioRecord API
    - Automatic file management and directory creation
    - Memory-efficient streaming recording without preset duration limits

### 2. Audio Processing (`processing/AudioProcessor.kt`)
**Purpose**: Prepares recorded audio for AI model consumption
- **Chunking Strategy**: Splits long recordings into 30-second segments
- **Overlap Management**: 5-second overlap between chunks to prevent content loss
- **File Management**: Creates temporary chunk files and handles cleanup
- **Features**:
    - Smart audio segmentation algorithm
    - WAV header manipulation for chunk creation
    - Automated temporary file lifecycle management

### 3. AI Model Integration (`processing/GemmaAudioEngine.kt`)
**Purpose**: Interfaces with Gemma 3n model for transcription and summarization
- **Model Location**: `/data/local/tmp/gemma-3n-E2B-it-int4.task`
- **Dual Functionality**:
    - Audio-to-text transcription with low temperature (0.1) for accuracy
    - Text-to-summary generation with higher temperature (0.7) for creativity
- **Features**:
    - Dynamic model session creation with different parameters
    - Async response handling with timeout mechanisms
    - Memory-efficient model loading and cleanup

### 4. Data Management (`data/MeetingSession.kt`)
**Purpose**: Represents meeting session metadata and file organization
- **File Structure**: Each meeting creates a timestamped folder containing:
    - `full_recording.wav`: Original audio file
    - `transcription.txt`: Complete transcription from all chunks
    - `summary.txt`: AI-generated meeting summary
    - `temp/`: Temporary audio chunks (deleted after processing)
- **Features**:
    - Automatic timestamp-based naming
    - Status tracking throughout processing pipeline
    - User-friendly display formatting

### 5. User Interface (`ui/`)

#### MeetingScreen.kt
**Purpose**: Main user interface with minimal, functional design
- **Core Controls**: Large red record button, stop functionality
- **Status Display**: Real-time processing status and messages
- **Meeting History**: Hamburger menu with list of completed meetings
- **Features**:
    - Clean, distraction-free interface
    - Visual feedback for recording state
    - Modal bottom sheet for meeting history

#### MeetingViewModel.kt
**Purpose**: Business logic and state management
- **State Management**: Reactive UI state using Kotlin Flows
- **Background Processing**: Coroutine-based async operations
- **File Operations**: Meeting directory management and file I/O
- **Features**:
    - Complete meeting lifecycle management
    - Error handling and user feedback
    - Permission management integration

## Processing Pipeline

### 1. Recording Phase
```
User Press Record → AudioRecorder starts → Continuous WAV writing → User stops → File saved
```

### 2. Processing Phase
```
Audio File → AudioProcessor chunks (30s/5s overlap) → Temp files created → Ready for AI
```

### 3. AI Processing Phase
```
For each chunk:
  Load Gemma3n (transcription mode) → Process chunk → Collect transcription → Unload model

Combine all transcriptions → Load Gemma3n (summary mode) → Generate summary → Save files
```

### 4. Completion Phase
```
Cleanup temp files → Update meeting status → Notify user → Add to history
```

## Technical Architecture Decisions

### On-Device Processing
- **Privacy First**: No audio or transcriptions leave the device
- **Offline Capability**: Works without internet connection
- **Low Latency**: Direct hardware access for real-time processing

### File Management Strategy
- **External Storage**: Uses app-specific directory for meeting files
- **Organized Structure**: Timestamped folders for easy navigation
- **Cleanup Automation**: Temporary files automatically removed after processing

### AI Model Strategy
- **Model Reloading**: Different instructions for transcription vs summarization tasks
- **Memory Optimization**: Load model only when needed, cleanup immediately after
- **Error Resilience**: Fallback mechanisms for model failures

### Background Processing
- **Non-Blocking UI**: All AI processing happens on background threads
- **Progress Updates**: Real-time status updates during long operations
- **Resource Management**: Proper coroutine scoping to prevent memory leaks

## Target Use Cases

1. **Business Meetings**: Record discussions and generate action item summaries
2. **Academic Lectures**: Transcribe educational content for later review
3. **Personal Notes**: Convert voice memos into organized text summaries
4. **Interview Documentation**: Professional interview transcription and analysis

## Performance Characteristics

- **Model Size**: Gemma 3n E2B (~2GB effective memory footprint)
- **Processing Speed**: ~6 tokens per second for audio processing
- **Audio Limits**: Up to 30 seconds per chunk (can handle unlimited total duration)
- **Storage**: Minimal storage footprint with automatic cleanup
- **Battery Impact**: Optimized for mobile battery constraints

## Privacy and Security

- **Complete Local Processing**: No cloud connectivity required
- **No Data Collection**: No telemetry or usage analytics
- **File Encryption**: Files stored in app-specific protected directories
- **Permission Minimal**: Only requests essential audio recording permissions

## Future Enhancement Opportunities

1. **Multi-language Support**: Leverage Gemma 3n's multilingual capabilities
2. **Speaker Identification**: Distinguish between different meeting participants
3. **Integration APIs**: Export capabilities for other productivity apps
4. **Advanced Chunking**: More sophisticated audio segmentation algorithms
5. **Custom Prompts**: User-configurable summary generation instructions