-- The editable text produced by extraction/OCR is distinct from the uploaded binary.
ALTER TABLE KnowledgeDocument
    ADD COLUMN extracted_text MEDIUMTEXT NULL,
    ADD COLUMN ocr_edited BOOLEAN NOT NULL DEFAULT FALSE;
