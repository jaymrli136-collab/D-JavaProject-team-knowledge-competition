# Changelog

## 2026-11-13 - Build and verification recovery

- Fixed Spring configuration binding for nested LLM and retrieval properties.
- Restored missing service imports and project-role repository operations.
- Restricted knowledge-gap abstention anchors to meaningful query terms.
- Added query rewrite traces for session follow-ups.
- Moved Surefire temporary files to the D-drive build directory.
- Verified the full backend test suite: 38 tests passed.

## 2026-11-12 - Local development snapshot

- Multi-project knowledge store, ACL and active document retrieval.
- L0/L1/L2 indexing, BM25 + hash-vector retrieval and reranking.
- Explicit Agent Harness with scope, rewrite, memory, tool, retrieve, answer, verify and gap stages.
- SSE streaming, cancellation checks and run time budget.
- Citation validation, coverage measurement and original-section drill-down.
- Governed project memory: propose, confirm, retire and active-memory soft context.
- Knowledge gap capture, repeat accumulation, candidate-document review, resolve and ignore.
- Document lineage and automatic ACTIVE/SUPERSEDED lifecycle.
- Fixed evaluation suite, history snapshots and knowledge health overview.
- Answer feedback, run history and feedback summary.
- Prompt-injection guard for untrusted evidence.
- Product, architecture, data model, API and demo documentation.
