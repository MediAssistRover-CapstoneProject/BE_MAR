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


def topic_id(kind, text, scope=''):
    """Conservative identity: same question/field/table row, never vector proximity."""
    def normalized(value):
        value = unicodedata.normalize('NFD', value.casefold().replace('đ', 'd'))
        value = ''.join(c for c in value if unicodedata.category(c) != 'Mn')
        return re.sub(r'[^a-z0-9]+', ' ', value).strip()

    def catalog_identity(subject):
        category, separator, item = subject.strip().partition(' - ')
        if not separator:
            item = category
        if item.endswith('?'):
            subject = normalized(item)
            if re.search(r'\b(phong nay|o day|tai day|cho nay)\b', subject):
                subject = normalized(category) + ' ' + subject
            return 'question:' + subject
        if normalized(item) == 'chuc nang' and separator:
            return 'room-purpose:' + normalized(category)
        return 'field::' + normalized(item)

    question = re.match(r'^Hỏi\s*:\s*(.+?)(?:\n|$)', text, re.I)
    if question and re.search(r'\nĐáp\s*:', text, re.I):
        subject = normalized(question.group(1))
        if re.search(r'\b(phong nay|o day|tai day|cho nay)\b', subject):
            subject = normalized(scope) + ' ' + subject
        identity = 'question:' + subject
    elif kind == 'catalog':
        match = re.search(r'Mục\s*/\s*câu hỏi\s*:\s*(.*?)\s*Thông tin\s*/\s*trả lời\s*:',
                          text, re.I | re.S)
        if match:
            identity = catalog_identity(match.group(1))
        else:
            identity = 'exact:' + hashlib.sha256(text.encode('utf-8')).hexdigest()
    elif kind == 'table-row':
        header, row = text.split('\n', 1)
        cells = row.split('|')
        first = cells[0].strip()
        # The demo catalog has an ordinal in column one; its actual subject is
        # the "Mục / câu hỏi" field in column two.
        if first.isdecimal() and len(cells) > 1:
            match = re.search(r'Mục\s*/\s*câu hỏi\s*:\s*(.*?)\s*Thông tin\s*/\s*trả lời\s*:',
                              cells[1], re.I | re.S)
            identity = catalog_identity(match.group(1)) if match else 'exact:' + hashlib.sha256(text.encode('utf-8')).hexdigest()
        else:
            identity = ('table:' + normalized(header) + ':' + normalized(first)) if first else 'exact:' + hashlib.sha256(text.encode('utf-8')).hexdigest()
    elif kind == 'field':
        label = text.split(':', 1)[0]
        # A room-specific label needs room scope; hospital-wide headings can
        # change wording between documents without changing the fact identity.
        room_scope = normalized(scope) if re.search(r'\b(phong|khoa)\b', normalized(scope)) else ''
        if normalized(label) in {'dap', 'noi dung', 'ghi chu', 'thong tin tra loi', 'muc cau hoi'}:
            identity = 'exact:' + hashlib.sha256(text.encode('utf-8')).hexdigest()
        else:
            identity = 'field:' + room_scope + ':' + normalized(label)
    else:
        # Exact repeated prose can be removed; similar prose is never guessed equal.
        identity = 'exact:' + hashlib.sha256(text.encode('utf-8')).hexdigest()
    return hashlib.sha256(identity.encode('utf-8')).hexdigest()


def atomic_blocks(blocks):
    """Keep every FAQ answer and table row in one independently replaceable item."""
    units, question, catalog, plain, scope = [], [], [], [], ''

    def flush_plain():
        nonlocal plain
        if plain:
            text = clean('\n'.join(plain))
            if text:
                units.append(('text', text, scope))
        plain = []

    def flush_question():
        nonlocal question
        if question:
            text = clean('\n'.join(question))
            if text:
                units.append(('faq' if re.search(r'\nĐáp\s*:', text, re.I) else 'text', text, scope))
        question = []

    def flush_catalog():
        nonlocal catalog
        if catalog:
            text = clean('\n'.join(catalog))
            if text:
                units.append(('catalog', text, scope))
        catalog = []

    for kind, raw in blocks:
        text = clean(raw)
        if not text:
            flush_question()
            flush_catalog()
            flush_plain()
            continue
        if kind == 'heading':
            flush_question()
            flush_catalog()
            flush_plain()
            scope = text
            units.append((kind, text, scope))
            continue
        if kind == 'table':
            flush_question()
            flush_catalog()
            flush_plain()
            rows = text.splitlines()
            if len(rows) > 1:
                header = rows[0]
                logical_rows = []
                for row in rows[1:]:
                    if re.fullmatch(r'[\s|:\-]+', row):
                        continue
                    if '|' not in row and logical_rows:
                        logical_rows[-1] += ' ' + row
                    else:
                        logical_rows.append(row)
                for row in logical_rows:
                    units.append(('table-row', header + '\n' + row, scope))
            else:
                units.append(('text', text, scope))
            continue
        for line in text.splitlines():
            if re.match(r'^STT\s+Mục\s*/\s*câu hỏi\s+và\s+thông tin\s*/\s*câu trả lời', line, re.I):
                flush_question()
                flush_catalog()
                flush_plain()
                continue
            if re.match(r'^(?:MediAssist Rover|MediAssistRover)\s*\|.*\bTrang\s+\d+\s*$', line, re.I):
                flush_question()
                flush_catalog()
                flush_plain()
                continue
            if re.match(r'^\d+\s+Mục\s*/\s*câu hỏi\s*:', line, re.I):
                flush_question()
                flush_catalog()
                flush_plain()
                catalog = [line]
            elif catalog and (re.match(r'^STT\s+Mục\s*/\s*câu hỏi', line, re.I) or
                              re.match(r'^(?:MediAssist Rover|MediAssistRover)\b', line, re.I)):
                flush_catalog()
            elif catalog:
                catalog.append(line)
            elif re.match(r'^Hỏi\s*:', line, re.I):
                flush_question()
                flush_plain()
                question = [line]
            elif question and (re.match(r'^\d+\.\s+\S', line) or
                               re.match(r'^(?:MediAssist Rover|MediAssistRover|HẾT TÀI LIỆU)\b', line, re.I)):
                flush_question()
                plain.append(line)
            elif question:
                question.append(line)
            elif re.match(r'^.{2,80}:\s*\S', line) and not line.startswith('Đáp:'):
                flush_plain()
                units.append(('field', line, scope))
            else:
                plain.append(line)
        # A normal paragraph boundary is an independent exact-match unit.
        if not question:
            flush_plain()
    flush_question()
    flush_catalog()
    flush_plain()
    return units


def split_blocks(blocks):
    import tiktoken
    encoding = tiktoken.get_encoding('cl100k_base')
    size = lambda s: len(encoding.encode(s, disallowed_special=()))
    chunks = []
    for kind, text, scope in atomic_blocks(blocks):
        if size(text) > MAX_TOKENS:
            if kind == 'table-row':
                raise ValueError('KNOWLEDGE_TABLE_ROW_TOO_LONG')
            if kind in ('faq', 'field', 'catalog'):
                raise ValueError('KNOWLEDGE_BLOCK_TOO_LONG')
            # Only free prose may be split; never break an answer or table row.
            sentences = re.split(r'(?<=[.!?])\s+|\n\s*\n', text)
            current = ''
            for sentence in sentences:
                if size(sentence) > MAX_TOKENS:
                    raise ValueError('KNOWLEDGE_BLOCK_TOO_LONG')
                candidate = current + (' ' if current else '') + sentence
                if size(candidate) > MAX_TOKENS:
                    chunks.append(current)
                    current = sentence
                else:
                    current = candidate
            if current:
                chunks.append(current)
        else:
            chunks.append((kind, text, scope))
    if not chunks or len(chunks) > MAX_CHUNKS:
        raise ValueError('KNOWLEDGE_CHUNK_LIMIT_OR_EMPTY')
    result = []
    for item in chunks:
        kind, text, scope = item if isinstance(item, tuple) else ('text', item, '')
        result.append(dict(text=text, sha256=hashlib.sha256(text.encode('utf-8')).hexdigest(),
                           topicKey=topic_id(kind, text, scope)))
    return result


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
    return dict(version=3, chunks=split_blocks(blocks))


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
