# 0011 — Browser audio capture with independent transcription

Status: Proposed

## Context

Microphone or speaker audio should become a prompt without changing device routing. Every existing chat model and
the knowledge pipeline should remain usable, including models without native audio support. Browser capture
permissions and system-audio availability vary by browser and OS.

## Decision

- Capture explicit, bounded turns in the browser using `getUserMedia` or `getDisplayMedia`, with
  `suppressLocalAudioPlayback: false`. Record only audio tracks with `MediaRecorder`; retain complete files rather
  than submitting undecodable timeslice fragments. Stop and release all owned tracks on navigation or cancellation.
- Transcribe through an authenticated backend endpoint. `AudioTranscriptionService` depends on the
  `AudioTranscriber` outbound port, implemented by a multipart OpenAI-compatible adapter in
  `infrastructure/llm/compatible`. Endpoint, credentials, model, timeout and upload limits come from configuration,
  independently of answering-model selection. Transcription POSTs are never retried and upstream errors are sanitized.
- Review transcripts in the composer by default. Automatic submission is an explicit per-chat preference.
  Submitted transcripts enter the existing text chat, `KnowledgeAugmenter`, context, SSE and citation paths.
- Persist conversation type (`TEXT` or `AUDIO`) and audio preferences using a new Flyway migration. Default existing
  chats to text. Keep device IDs local to the browser. Apply saved output-token overrides on send and regeneration.
- Keep raw audio transient: do not attach it to messages or index it in the knowledge base. Enforce authentication,
  bounded uploads, rate limits and one active transcription per user. Do not return keys or service URLs to clients.

## Consequences

All chat providers work with audio-derived prompts; adapters and RAG do not need vendor-specific audio logic.
Speech-to-text requires a separately configured service. Recording and transcription add latency before answer
streaming. Full system audio is not available in every browser: the UI explains sharing requirements and reports
missing tracks. A browser cannot guarantee how another application's exclusive device access behaves, but this
implementation never takes exclusive control, changes device routing, or suppresses local playback.

Captures end at a configured duration or explicit stop; this is not continuous background monitoring. Cancelling an
upload cannot recall audio already processed by a service. No audio retention, audio replay or speech synthesis is
added. The nginx permissions policy permits microphone and display capture for the same origin.

## Alternatives considered

- Browser speech recognition is not portable and cannot reliably transcribe a selected speaker stream.
- Sending audio straight to an answering model would couple chat to audio-capable vendors and bypass text retrieval.
- A native loopback agent could capture more OS sources, but needs a separate installation, lifecycle and trust model.
