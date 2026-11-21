"""Isolated, bounded extraction. stdout/stderr never contain source content.

Called by BE, not by the robot. Output contains original text only: no generated
titles, summaries or source prefixes. Runtime: Python 3.11+, Tesseract, LibreOffice.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import signal
import tempfile
import unicodedata
import zipfile

MAX_CHARS = 2_000_000
MAX_CHUNKS = 1000
MAX_TOKENS = 700


def clean(text):
    text = unicodedata.normalize('NFC', text.replace('\ufeff', '').replace('\r\n', '\n'))
    if '\ufffd' in text or '\x00' in text:
        raise ValueError('KNOWLEDGE_TEXT_INVALID')
    return '\n'.join(re.sub(r'[^\S\n]+', ' ', line).strip() for line in text.splitlines()).strip()


def run(args, timeout=30):
    # No shell, no macro execution, no source content in errors.
    try:
        with subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                              start_new_session=os.name != 'nt',
                              creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0)) as process:
            try:
                code = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
                else:
                    os.killpg(process.pid, signal.SIGKILL)
                process.kill()
                raise
            if code:
                raise ValueError('KNOWLEDGE_TOOL_FAILED')
    except FileNotFoundError:
        raise ValueError('KNOWLEDGE_TOOL_MISSING') from None
    except subprocess.TimeoutExpired:
        raise ValueError('KNOWLEDGE_TOOL_TIMEOUT') from None


def ocr(image, folder, required=True):
    from PIL import Image
    image_path = folder / 'ocr.png'
    if isinstance(image, bytes):
        import io
        image = Image.open(io.BytesIO(image))
    if image.width * image.height > 25_000_000:
        raise ValueError('KNOWLEDGE_IMAGE_TOO_LARGE')
    image.convert('RGB').save(image_path)
    target = folder / 'ocr'
    run([os.getenv('KNOWLEDGE_TESSERACT', 'tesseract'), str(image_path), str(target),
         '-l', os.getenv('KNOWLEDGE_OCR_LANGUAGES', 'vie+eng'), 'tsv'])
    import csv
    rows = list(csv.DictReader(target.with_suffix('.tsv').read_text(encoding='utf-8').splitlines(), delimiter='\t'))
    words = [r for r in rows if r.get('text', '').strip() and float(r.get('conf', '-1')) >= 0]
    if not words:
        if required:
            raise ValueError('KNOWLEDGE_OCR_EMPTY')
        return ''
    if sum(float(r['conf']) for r in words) / len(words) < 55:
        raise ValueError('KNOWLEDGE_OCR_LOW_QUALITY')
    lines = {}
    for row in words:
        key = tuple(row[k] for k in ('page_num', 'block_num', 'par_num', 'line_num'))
        lines.setdefault(key, []).append(row['text'])
    return '\n'.join(' '.join(words) for words in lines.values())


def table_text(rows):
    return '\n'.join(' | '.join(clean(str(cell or '')) for cell in row) for row in rows)


def extract_pdf(path, folder):
    import pdfplumber
    blocks = []
    with pdfplumber.open(path) as pdf:
        if not 0 < len(pdf.pages) <= 200:
            raise ValueError('KNOWLEDGE_PAGE_LIMIT')
        for page in pdf.pages:
            # Images are OCRed at page level to preserve reading order without
            # concatenating OCR text with duplicate embedded text on mixed pages.
            if page.images or not (page.extract_text() or '').strip():
                if page.width * page.height * (180 / 72) ** 2 > 25_000_000:
                    raise ValueError('KNOWLEDGE_IMAGE_TOO_LARGE')
                image = page.to_image(resolution=180).original
                blocks.append(('text', ocr(image, folder, required=bool(page.images or page.chars))))
            else:
                tables = page.find_tables()
                def outside_tables(obj):
                    return not any(t.bbox[0] <= obj.get('x0', 0) < t.bbox[2]
                                   and t.bbox[1] <= obj.get('top', 0) < t.bbox[3] for t in tables)
                lines = page.filter(outside_tables).extract_text_lines()
                import statistics
                sizes = [c.get('size', 0) for c in page.chars if c.get('size', 0) > 0]
                typical = statistics.median(sizes) if sizes else 12
                items = []
                for line in lines:
                    text = line['text']
                    # Only unambiguous page counters at the physical page edge;
                    # don't guess that a repeated medical condition is a footer.
                    if (line['top'] < page.height * .06 or line['top'] > page.height * .94) and re.fullmatch(r'(?:Trang|Page)\s+\d+(?:\s*/\s*\d+)?', text.strip(), re.I):
                        continue
                    heading = any(c.get('size', 0) > typical * 1.2 for c in line.get('chars', []))
                    items.append((line['top'], 'heading' if heading else 'text', text))
                items += [(t.bbox[1], 'table', table_text(t.extract())) for t in tables]
                blocks.extend((kind, text) for _, kind, text in sorted(items, key=lambda x: x[0]))
            if sum(len(text) for _, text in blocks) > MAX_CHARS:
                raise ValueError('KNOWLEDGE_TEXT_LIMIT')
    return blocks


def validate_zip(path):
    with zipfile.ZipFile(path) as archive:
        if len(archive.infolist()) > 10000 or sum(i.file_size for i in archive.infolist()) > 100 * 1024 * 1024:
            raise ValueError('KNOWLEDGE_DOCX_TOO_COMPLEX')


def extract_docx(path, folder):
    from docx import Document
    from docx.text.paragraph import Paragraph
    from docx.table import Table
    validate_zip(path)
    doc = Document(path)
    if doc.element.xpath('.//w:footnoteReference | .//w:endnoteReference | .//w:txbxContent | .//w:altChunk'):
        raise ValueError('KNOWLEDGE_COMPLEX_WORD_REQUIRES_PDF')
    blocks = []
    def body(container):
        for item in container.iter_inner_content():
            if isinstance(item, Paragraph):
                kind = 'heading' if item.style and item.style.name.lower().startswith('heading') else 'text'
                # Walk runs to keep pictures adjacent to the surrounding text.
                parts = []
                for span in item.iter_inner_content():
                    for run_item in (span.runs if hasattr(span, 'runs') else [span]):
                        parts.append(run_item.text)
                        for blip in run_item._r.xpath('.//a:blip'):
                            rid = blip.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed')
                            if rid:
                                parts.append('\n' + ocr(item.part.related_parts[rid].blob, folder, required=False) + '\n')
                blocks.append((kind, ''.join(parts)))
            elif isinstance(item, Table):
                # Never silently discard nested tables or images in cells.
                if item._tbl.xpath('.//w:tbl') or item._tbl.xpath('.//a:blip'):
                    raise ValueError('KNOWLEDGE_COMPLEX_TABLE_REQUIRES_PDF')
                blocks.append(('table', table_text([[c.text for c in row.cells] for row in item.rows])))
    body(doc)
    return blocks


def split_blocks(blocks):
    import tiktoken
    encoding = tiktoken.get_encoding('cl100k_base')
    size = lambda s: len(encoding.encode(s, disallowed_special=()))
    chunks, pending = [], ''
    def emit():
        nonlocal pending
        if pending.strip():
            chunks.append(pending.strip())
        pending = ''
    for kind, raw in blocks:
        text = clean(raw)
        if not text:
            continue
        if kind in ('heading', 'table'):
            emit()
        if kind == 'table' and size(text) > MAX_TOKENS:
            rows = text.splitlines()
            header = rows[0]
            current = header
            for row in rows[1:]:
                if size(header + '\n' + row) > MAX_TOKENS:
                    raise ValueError('KNOWLEDGE_TABLE_ROW_TOO_LONG')
                if size(current + '\n' + row) > MAX_TOKENS:
                    chunks.append(current)
                    current = header
                current += '\n' + row
            chunks.append(current)
            continue
        # Keep whole paragraphs/steps together when possible. An oversized single
        # sentence is rejected instead of cutting a word, number or condition.
        units = [text] if size(text) <= MAX_TOKENS else re.split(r'(?<=[.!?])\s+|\n\s*\n', text)
        for unit in units:
            if size(unit) > MAX_TOKENS:
                raise ValueError('KNOWLEDGE_BLOCK_TOO_LONG')
            candidate = pending + ('\n' if pending else '') + unit
            if size(candidate) > MAX_TOKENS:
                emit()
            pending += ('\n' if pending else '') + unit
        if kind == 'table':
            emit()
    emit()
    if not chunks or len(chunks) > MAX_CHUNKS:
        raise ValueError('KNOWLEDGE_CHUNK_LIMIT_OR_EMPTY')
    return [dict(text=text, sha256=hashlib.sha256(text.encode('utf-8')).hexdigest()) for text in chunks]


def process(path, folder):
    suffix = path.suffix.lower()
    if suffix == '.doc':
        if path.read_bytes()[:8] != bytes.fromhex('d0cf11e0a1b11ae1'):
            raise ValueError('KNOWLEDGE_DOC_INVALID')
        target = folder / 'converted'
        target.mkdir()
        profile_path = folder / 'lo-profile'
        (profile_path / 'user').mkdir(parents=True)
        (profile_path / 'user' / 'registrymodifications.xcu').write_text(
            '<?xml version="1.0"?><oor:items xmlns:oor="http://openoffice.org/2001/registry">'
            '<item oor:path="/org.openoffice.Office.Common/Security/Scripting">'
            '<prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item></oor:items>', encoding='utf-8')
        profile = profile_path.as_uri()
        run([os.getenv('KNOWLEDGE_LIBREOFFICE', 'libreoffice'), '-env:UserInstallation=' + profile,
             '--headless', '--convert-to', 'docx', '--outdir', str(target), str(path)], timeout=40)
        path = target / (path.stem + '.docx')
        if not path.is_file():
            raise ValueError('KNOWLEDGE_DOC_CONVERSION_FAILED')
        suffix = '.docx'
    if suffix == '.pdf':
        blocks = extract_pdf(path, folder)
    elif suffix == '.docx':
        blocks = extract_docx(path, folder)
    elif suffix in ('.txt', '.md'):
        text = path.read_text(encoding='utf-8-sig', errors='strict')
        # Markdown tables stay intact, including their existing header row.
        blocks = []
        for block in re.split(r'\n\s*\n', text):
            kind = 'table' if re.search(r'^\s*\|?.+\|.+\n\s*\|?\s*:?-{3}', block) else 'text'
            if block.lstrip().startswith('#'):
                kind = 'heading'
            blocks.append((kind, block))
    else:
        raise ValueError('KNOWLEDGE_FILE_TYPE_UNSUPPORTED')
    if sum(len(text) for _, text in blocks) > MAX_CHARS:
        raise ValueError('KNOWLEDGE_TEXT_LIMIT')
    return dict(version=2, chunks=split_blocks(blocks))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    try:
        with tempfile.TemporaryDirectory(prefix='mar-extract-') as tmp:
            result = process(args.input.resolve(), Path(tmp))
    except Exception as error:
        code = str(error) if isinstance(error, ValueError) and str(error).startswith('KNOWLEDGE_') else 'KNOWLEDGE_EXTRACTION_FAILED'
        result = dict(error=code)
    args.output.write_text(json.dumps(result, ensure_ascii=False), encoding='utf-8')
