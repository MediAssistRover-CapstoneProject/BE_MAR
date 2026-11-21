# Document preprocessing (pipeline v2)

The existing ADMIN upload API is unchanged. FE and navigation/map/endpoint logic
are not changed. There is no content approval step. Each newly uploaded document
is extracted and split before indexing in the existing OpenAI vector store.

## Flow and publication

1. `POST /api/v1/knowledge/documents`, multipart `file`, maximum 20 MiB.
2. BE retains the original payload. An isolated Python process extracts PDF,
   DOCX, DOC, UTF-8 TXT or MD. DOC conversion uses a private LibreOffice profile
   with macro security set to very high. Scan/mixed PDF pages use Tesseract
   `vie+eng`; DOCX text pictures use OCR at their position in the body.
3. Normalize whitespace/Unicode without paraphrasing or changing numbers. Split
   at existing headings, paragraphs and table rows, maximum 700 cl100k tokens
   per chunk. Existing table headers repeat when a large table is split. No
   generated title, filename or source prefix is added to chunk text.
4. Persist chunks and checksums. Upload each as UTF-8 text to the same store.
   A lease and durable per-chunk state avoid repeating an upload after a known
   success. Unknown upload outcomes become `RECONCILIATION_REQUIRED`, not an
   automatic second upload.
5. Only after **every** chunk is indexed does the parent become `COMPLETED`.
   AI Voice searches up to 20 candidates, then asks BE which files are currently
   published and obtains complete chunk text. It uses at most 5 whole chunks.
   BE failure means no document answer, not a fallback to unverified text.
6. Publication is checked again after generation and on replay of a cached turn.
   An answer from a withdrawn document is discarded. Greeting/SOS/navigation
   routes continue to use their existing paths.

Existing API statuses remain compatible with FE. `processing_stage` internally
tracks EXTRACTING, INDEXING and READY. A v2 parent has no single `openaiFileId`;
remote IDs belong to its children. Metadata is stored separately from chunk text.

## APIs (Swagger)

All `/api/v1/knowledge/documents` endpoints require ADMIN:

| Method/path | Purpose |
|---|---|
| `POST /api/v1/knowledge/documents` | Upload; identical bytes return the existing v2 record |
| `GET /api/v1/knowledge/documents/{id}` | Status and sanitized error |
| `GET /api/v1/knowledge/documents/{id}/chunks` | Ordered extracted content and chunk states |
| `POST /api/v1/knowledge/documents/{id}/replace` | Multipart replacement of an active COMPLETED document |
| `POST /api/v1/knowledge/documents/{id}/retry` | Reprocess a FAILED v2 document after fixing its cause |
| `DELETE /api/v1/knowledge/documents/{id}` | Immediately withdraw it and pending replacements |

Replacement is explicit: uploading another file with the same name does not
silently remove the old file. During replacement the old document stays active;
publication of the replacement and withdrawal of the predecessor share one DB
transaction. Failed replacements leave the old document usable. Retry does not
accept CANCELLED or RECONCILIATION_REQUIRED documents.

`POST /api/v1/internal/knowledge/resolve` is for AI Voice only. It requires a
dedicated `X-Knowledge-Token` header (at least 32 characters), and accepts
`{"vectorStoreId":"vs_...","fileIds":["file-..."]}` (maximum 20). It never
accepts a caller's own replacement text. It returns only active, complete files
in the configured store. Do not expose the token to FE or Pi.

Withdrawal is immediate at BE's publication gate. A durable outbox retries
detaching remote files, including files created during cancellation. It detaches
from this store; it does not delete a potentially shared OpenAI file object or
erase the original BE payload. Original payload retention/purge remains an
operator decision. An unknown upload ID needs operator reconciliation in OpenAI.

## Deploy together

Pause document uploads/knowledge Q&A during the coordinated rollout. Do not run
the old AI Voice against a partially indexed v2 document: it lacks the BE gate.

1. Set the **same** `OPENAI_VECTOR_STORE_ID` in BE and AI Voice. Their OpenAI keys
   must have access to that store. Configure the same random
   `KNOWLEDGE_SERVICE_TOKEN` (32+ characters) in both services.
2. Configure AI Voice `BACKEND_URL` as the HTTPS BE origin (HTTP is allowed only
   for localhost development). AI Voice needs the internal resolve route through
   the reverse proxy. Do not use the robot/device credential for this route.
3. Deploy BE including Flyway V65 and the processor runtime; deploy updated
   AI Voice. The Dockerfile includes Python, Tesseract Vietnamese/English and
   LibreOffice, and preloads the tokenizer so extraction needs no tokenizer
   download at runtime. Resume after a real upload/chat smoke test.
4. For a native Java deployment, install the Python requirements in a dedicated
   venv and install Tesseract + `vie`/`eng` language data and LibreOffice. Set:

   ```text
   KNOWLEDGE_PYTHON=/absolute/path/to/venv/bin/python
   KNOWLEDGE_PROCESSOR=/absolute/path/to/BE_MAR/scripts/knowledge/process_document.py
   KNOWLEDGE_TESSERACT=/absolute/path/to/tesseract
   KNOWLEDGE_LIBREOFFICE=/absolute/path/to/libreoffice
   ```

   Shipping only the JAR is insufficient. Temporary document files are deleted
   after processing; the Java wrapper stops processing after 120 seconds.

V60 documents remain searchable if BE tracks them as COMPLETED and active.
Provider files uploaded outside BE are not trusted by the resolver. To clean an
old document, call its replacement API with the original file. Legacy original
payloads may already have been discarded by the previous worker; they cannot be
reconstructed from the DB automatically.

## Limits and validation

- Maximum 200 PDF pages, 2 million extracted characters, 1000 chunks, 100 MiB
  expanded DOCX and 25 million pixels per OCR image. Exceeding a limit fails the
  document rather than truncating it.
- A single sentence/table row exceeding the chunk budget is rejected rather than
  cut mid-condition. Word nested tables, images inside tables, text boxes,
  footnotes/endnotes and altChunk content require exporting to PDF first; these
  are explicitly rejected instead of silently losing content.
- OCR rejects empty required text and low mean word confidence (<55). This is a
  quality heuristic, **not a guarantee** of correctness. Complex columns, scans
  and tables still need validation against real hospital documents.
- Cleanup only removes unambiguous page counters at PDF page edges; repeated
  medical conditions are not guessed away as headers/footers.
- Twenty search candidates are post-filtered by BE. Many unpublished/stale
  candidates may cause no answer rather than unsafe fallback. Remote cleanup
  reduces this condition. A full pre-search publication catalog is not used.
- Cleaner contexts do not guarantee lower latency; BE publication checks add
  two short HTTP calls. Measure retrieval/generation timings on the same question
  set before/after deployment. Existing model rules are preserved, not a proof
  that every generated statement is factually entailed by its sources.

## Repeatable checks

```powershell
# Native preprocessing tests; native OCR/Word tests skip if tools are absent.
python -m pytest scripts/knowledge -q
# Focused Java suite (legacy unrelated tests currently have constructor drift).
mvn -Pknowledge-tests '-Dtest=Knowledge*Test' test
```

The integration test applies V60 and V65 to a **fresh disposable MySQL database**.
Set `KNOWLEDGE_TEST_JDBC_URL` pointing to a database named `mar_knowledge_test`,
root password `knowledge-local-test`. Never point this at application data.
Set `KNOWLEDGE_TEST_PYTHON` to test the actual Java → Python subprocess contract.

```powershell
docker build -f scripts/knowledge/Dockerfile.test -t mar-knowledge-processor-test:local scripts/knowledge
docker run --rm --network none --memory 1g --cpus 2 mar-knowledge-processor-test:local
```

AI Voice checks: `python -m pytest tests/test_openai_knowledge.py
tests/test_knowledge_resolver.py tests/test_rag.py tests/test_robot_api_latency.py -q`.
Provider calls in these tests are mocked. Acceptance still needs a real OpenAI
store upload, hospital question set and robot speech playback; no production
data or paid provider calls are used by the automated suite.
