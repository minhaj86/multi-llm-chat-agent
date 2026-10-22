# Frontend — AGENTS.md

React 19 + TypeScript + Vite single-page app. Read the [root AGENTS.md](../AGENTS.md) first.

## Layout

```text
src/
├── main.tsx, App.tsx       Bootstrap, QueryClient, router (Admin, Knowledge and Compare pages are lazy-loaded)
├── api/
│   ├── http.ts             fetch wrapper: base URL, JWT header, 401 handling, ApiError (RFC 9457 problem details),
│   │                       JSON or FormData bodies
│   ├── client.ts           Typed REST clients: authApi, providerApi, conversationApi, promptApi, chatApi, compareApi,
│   │                       adminApi, knowledgeApi (admin knowledge base + citations)
│   ├── stream.ts           postEventStream(): POST + streamed SSE response (EventSource cannot POST or send headers)
│   ├── sse.ts              Incremental SSE parser (SseParser, readSse)
│   └── types.ts            API types (mirror of the backend DTOs)
├── store/                  zustand stores
│   ├── auth.ts             Token + user, persisted as "ai-workspace-auth"
│   ├── preferences.ts      Theme, selected provider/model, system prompt, knowledge setting for new chats,
│   │                       persisted as "ai-workspace-preferences"
│   └── chatStream.ts       In-flight exchanges per conversation slot (incl. citations) + 40 ms token buffer
├── hooks/                  useProviders (+ resolveProvider/resolveModel), useChatSender (send/regenerate/stop),
│                           useConversations, useKnowledge (collections, stats, sources with polling), useTheme,
│                           useCopyToClipboard
├── pages/                  ChatPage, ComparePage, AdminPage, KnowledgePage, KnowledgeSourcePage, KnowledgeTestPage,
│                           AuthPages (login/register), Guards (RequireAuth/RequireAdmin)
├── components/
│   ├── layout/             AppLayout, Sidebar, ConversationItem, TopBar
│   ├── providers/          ProviderModelSelector, ProviderStatusList
│   ├── chat/               MessageList, MessageItem, Composer, ReasoningBlock, ErrorNotice, SystemPromptPicker,
│   │                       KnowledgeSelector, Citations, WelcomeScreen
│   ├── compare/            TargetPicker, ComparePanel
│   ├── admin/              AdminNav, UsersTab, ProvidersTab, UsageTab (recharts), PromptsTab
│   ├── knowledge/          Knowledge admin building blocks: dialogs (add document, text, edit, delete, collections),
│   │                       DuplicateNotice, ChunkViewer, KnowledgeStatsPanel, StatusBadge, MultiSelect, TagInput
│   ├── markdown/           Markdown (react-markdown + remark-gfm + rehype-highlight), CodeBlock (copy button)
│   ├── settings/           SettingsDialog
│   └── ui/                 Button, Form, Listbox, Modal, Spinner, StatusDot
└── test/                   setup.ts (Vitest + Testing Library, jsdom); knowledge.tsx (fixtures, mockFetch, renderRoutes)
```

Routes: `/login`, `/register`, `/` (new chat), `/c/:conversationId`, `/compare`, `/admin`, `/admin/knowledge`,
`/admin/knowledge/test`, `/admin/knowledge/:sourceId` (admin routes for administrators only), `/audio` (new audio chat).

Audio chats use `AudioControls`, `useAudioCapture` and `lib/audioCapture.ts`. Capture is explicit and bounded; always
release all tracks and abort uploads on unmount, including a permission grant that arrives after cancellation.
Record only audio from display streams and request `suppressLocalAudioPlayback: false`. Transcription goes through
`audioApi` to the backend, then through the existing text chat/RAG flow. Persist audio preferences on conversations;
microphone device IDs stay in the browser. Never submit isolated MediaRecorder timeslice fragments as standalone files.

## Rules

- **Providers and models are data.** Build every provider/model list from `useProviders()` and `useModels()`
  (`GET /api/providers`, `GET /api/providers/{id}/models`). Offline providers are shown as "Name — Offline" and
  disabled. Never hardcode provider ids, names or models, and never branch on a specific provider.
- **The backend is the only gateway.** The browser never talks to an LLM and never sees API keys. All HTTP goes
  through `src/api` (`http.ts` for JSON, `stream.ts` for SSE). Do not call `fetch` from components.
- **Streaming**: `useChatSender` posts to `/api/chat/stream` or `/api/chat/regenerate`, and dispatches the SSE events
  `start`, `citations`, `delta`, `reasoning`, `done`, `error` and `title`. Stop uses an `AbortController`, plus the
  explicit cancel endpoint so that the backend also stops the generation.
- **Knowledge base**: collections, sources and statistics come from the API (`knowledgeApi`); never hardcode
  collections. Citations are rendered from the structured `citations` data, never parsed from the answer text.
  Indexing is asynchronous: `useKnowledge` hooks poll while a source is processing; invalidate the `['knowledge']`
  queries after every mutation.
- **Server state** (conversations, messages, providers, admin data) lives in TanStack Query. Invalidate queries after
  mutations; do not copy server data into zustand. zustand holds only client state (auth token, preferences,
  in-flight streams).
- **Errors**: show `ApiError.message` (backend problem details are user-readable). Provider failures render
  `ErrorNotice`, which offers the backend-suggested alternative providers.
- **Styling**: Tailwind CSS 4 utility classes, dark mode through the `dark` class (`useThemeSync`, and
  `public/theme-init.js` prevents a flash on load). Layouts must work from 360 px wide (mobile drawer sidebar).
- **Accessibility**: interactive elements are buttons or links with labels. Dialogs use `ui/Modal` (moves focus into
  the dialog and restores it afterwards; Escape and backdrop clicks close it), selects use `ui/Listbox`.

## TypeScript conventions

- Strict mode with `verbatimModuleSyntax` (use `import type` for types) and `erasableSyntaxOnly` (no `enum`, no
  constructor parameter properties; use union types and `as const` objects).
- 2-space indent, single quotes, no semicolons, named exports (`App` is the only default export).
- Relative imports (no path aliases).

## Commands

```bash
npm ci                     # install exact dependencies
npm run dev                # Vite on http://localhost:5173, proxies /api to VITE_PROXY_TARGET (default http://localhost:8088)
npm run typecheck          # tsc -b --noEmit
npm run lint               # oxlint src
npm test                   # vitest run
npm run build              # type check + production build into dist/
../scripts/test.sh frontend   # typecheck + lint + tests
```

The backend must be running for `npm run dev`: use `../scripts/run-dev.sh start` (whole dev stack) or
`../scripts/deploy.sh` (Docker).

## Testing

- Vitest + React Testing Library, colocated as `*.test.ts(x)`.
- Wrap components that use queries in a fresh `QueryClientProvider` per test (see `ProviderModelSelector.test.tsx`).
- Stub the network with `vi.stubGlobal('fetch', vi.fn(...))` and return `Response` objects (see
  `AuthPages.test.tsx`, `ProviderModelSelector.test.tsx`). Never call a real backend from tests. Knowledge-base tests
  use the helpers in `src/test/knowledge.tsx`: `mockFetch` (routes by method and path, records calls with parsed
  bodies), `renderRoutes` (router + QueryClient) and typed fixtures.
- `cn()` is plain `clsx` (no tailwind-merge): do not pass a conflicting utility (for example `w-36` to an `Input`
  that already has `w-full`); wrap the control in a sized element instead.
- Test behaviour through roles and labels (`getByRole`, `getByLabelText`), not CSS classes.
