# Document preprocessing and topic replacement (pipeline v3)

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
   FAQ question/answer pairs, labelled fields and table rows into independently
   replaceable items (maximum 700 cl100k tokens each). Free prose is split at
   existing boundaries. No generated title, filename or source prefix is added.
4. Persist chunks and checksums. Upload each as UTF-8 text to the same store.
   A lease and durable per-chunk state avoid repeating an upload after a known
   success. Unknown upload outcomes become `RECONCILIATION_REQUIRED`, not an
   automatic second upload.
5. Only after **every** chunk is indexed does the parent become `COMPLETED`.
   In the same database transaction, a newer item with the same topic key
   becomes published and the older item becomes unpublished. Completion order
   does not override upload order. The cleanup outbox detaches superseded files
   from the vector store; it does not delete the original source document.
   AI Voice searches up to 20 candidates, then asks BE which files are currently
   published and obtains complete chunk text. It uses at most 5 whole chunks.
   BE failure means no document answer, not a fallback to unverified text.
6. Publication is checked again after generation and on replay of a cached turn.
   An answer from a withdrawn document is discarded. Greeting/SOS/navigation
   routes continue to use their existing paths.

Existing API statuses remain compatible with FE. `processing_stage` internally
tracks EXTRACTING, INDEXING and READY. A v3 parent has no single `openaiFileId`;
remote IDs belong to its children. Metadata is stored separately from chunk text.

## Latest upload wins for matching items

The stable topic key comes from a normalized FAQ question, a labelled field,
the subject of a catalog row, or a table row identifier. The combined demo PDF
is handled both when PDF table detection succeeds and when the PDF renders as
ordinary lines with `Mục / câu hỏi` and `Thông tin / trả lời`. Exact repeated
prose also shares a key. Room-specific generic labels include the room heading
so two rooms do not overwrite each other.

This is deliberately conservative. Paraphrases with different questions and
unstructured prose that only *seems* similar are not silently merged. A vector
similarity score alone cannot prove that two facts have the same scope. Each
labelled item or answer must contain the complete authoritative value; updating
only half of a compound item replaces the whole old item. Use `GET /{id}/chunks`
to inspect the extracted items and their `active` flag.

Retained active v2 originals are automatically queued for one-time v3 reindex.
Until they have been withdrawn, a plain new upload returns
`KNOWLEDGE_REINDEX_PENDING` so old grouped chunks cannot conflict with new
items. The explicit replacement API remains available. Legacy v1 documents
or v2 documents without retained originals cannot be reprocessed automatically;
plain upload returns `KNOWLEDGE_LEGACY_REPLACE_REQUIRED` until they are replaced
or deleted. A failed v3 reindex leaves the old document published, and the
failed v3 record can be retried after fixing the cause.

## APIs (Swagger)

All `/api/v1/knowledge/documents` endpoints require ADMIN:

| Method/path | Purpose |
|---|---|
| `POST /api/v1/knowledge/documents` | Upload; every call creates a new publication attempt, including identical bytes |
| `GET /api/v1/knowledge/documents/{id}` | Status and sanitized error |
| `GET /api/v1/knowledge/documents/{id}/chunks` | Ordered extracted content and chunk states |
| `GET /api/v1/knowledge/documents/{id}/extracted-text` | Read the editable extracted/OCR text (plain text) |
| `GET /api/v1/knowledge/documents/search?q=...&page=0&size=20` | Search extracted/OCR text across ready documents; returns document metadata and a 240-character excerpt |
| `GET /api/v1/knowledge/chunks/search?q=...&documentId=...&page=0&size=20` | Search ready documents' chunks; `documentId` is optional, and each result includes content and `active` |
| `POST /api/v1/knowledge/documents/{id}/replace` | Multipart replacement of an active COMPLETED document |
| `PUT /api/v1/knowledge/documents/{id}` | Edit all extracted/OCR text with JSON `{"content":"..."}`; regenerate all chunks and return a new document ID |
| `PUT /api/v1/knowledge/documents/{id}/chunks/{chunkId}` | Edit one ready v3 chunk with JSON `{"content":"..."}`; returns new document ID |
| `POST /api/v1/knowledge/documents/{id}/retry` | Reprocess a FAILED v2/v3 document after fixing its cause |
| `DELETE /api/v1/knowledge/documents/{id}` | Hard-delete the document, its chunks, and pending replacements from BE; queue remote detach |
| `DELETE /api/v1/knowledge/documents/{id}/chunks/{chunkId}` | Hard-delete one chunk of a COMPLETED v2/v3 document; queue remote detach |

Whole-document replacement is explicit: uploading another file with the same
name does not silently remove every item in the old file. Matching item keys
are superseded automatically after the new document finishes indexing. During
explicit replacement the old document stays active;
publication of the replacement and withdrawal of the predecessor share one DB
transaction. Failed replacements leave the old document usable. Retry does not
accept CANCELLED or RECONCILIATION_REQUIRED documents.
Once a replacement is ready, the previous document and its chunks are removed
from BE in that transaction. Its remote files are queued for detach. Clients
should poll the new document ID returned by the update endpoint. The whole-text
PUT does not accept a file upload: it feeds the edited extraction through the
same splitting logic, then indexes the resulting chunks. The separate
`POST /{id}/replace` endpoint is for uploading a different source file.
Chunk editing snapshots all current chunks into a new revision, changes the
selected chunk, and stores the resulting extracted/OCR text. The uploaded PDF,
DOC or other binary remains available as an immutable reference, so the OCR
text can differ from the original pixels or layout. The edited revision is
indexed before the old document is removed. Only active chunks may be edited;
inactive chunks superseded by another document stay inactive in the revision.
The edited chunk keeps its existing topic identity, so this endpoint is for
correcting the value of an existing fact or answer. Upload a revised file if
the question, subject or structure itself changes.
Editing is limited to 20,000 characters per chunk. A failed edited revision
can be retried without re-extracting the original and losing the edit.

`POST /api/v1/internal/knowledge/resolve` is for AI Voice only. It requires a
dedicated `X-Knowledge-Token` header (at least 32 characters), and accepts
`{"vectorStoreId":"vs_...","fileIds":["file-..."]}` (maximum 20). It never
accepts a caller's own replacement text. It returns only active, complete files
in the configured store. Do not expose the token to FE or Pi.

The two ADMIN search APIs perform case-insensitive literal text matching in BE,
not semantic/vector search. `q` is required and must be 2–200 characters;
`page` starts at 0 and `size` is 1–50. Results include only active, COMPLETED
documents in the configured store. Chunk search includes both published and
superseded chunks of those documents; use `active` to tell them apart. While a
replacement is indexing, the old ready revision remains searchable. Once it
publishes, the old revision is removed and only the new one appears.

Deletion removes BE document/chunk rows and the original payload in one DB
transaction after recording known remote file IDs in a durable cleanup outbox.
The publication gate immediately rejects those files, while the worker retries
detaching them from this store. Detach does not delete a potentially shared
OpenAI File object. An active worker lease or unknown upload outcome returns
HTTP 409 without deleting rows; retry after processing or reconcile the unknown
OpenAI upload. Deleting a chunk leaves the source PDF payload in its document,
and uploading the source again can create that chunk again. A superseded older
chunk is not automatically restored when its newer owner is deleted.

## Deploy together

Pause document uploads/knowledge Q&A during the coordinated rollout. Do not run
the old AI Voice against a v3 document: it does not accept pipeline version 3.

1. Set the **same** `OPENAI_VECTOR_STORE_ID` in BE and AI Voice. Their OpenAI keys
   must have access to that store. Configure the same random
   `KNOWLEDGE_SERVICE_TOKEN` (32+ characters) in both services.
2. Configure AI Voice `BACKEND_URL` as the HTTPS BE origin (HTTP is allowed only
   for localhost development). AI Voice needs the internal resolve route through
   the reverse proxy. Do not use the robot/device credential for this route.
3. Deploy BE including Flyway V66 and the processor runtime; deploy updated
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

The integration test applies V60, V65 and V66 to a **fresh disposable MySQL database**.
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
