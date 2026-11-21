from pathlib import Path
import importlib.util
import json
import subprocess
import sys
import pytest

spec = importlib.util.spec_from_file_location('processor', Path(__file__).with_name('process_document.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


def test_clean_preserves_numbers_conditions_and_does_not_add_metadata():
    source = 'Giờ khám: 07:00–17:00.\n\n  Phí: 150.000 đồng; miễn phí trẻ dưới 6 tuổi. '
    chunks = p.split_blocks([('text', source)])
    assert len(chunks) == 1
    assert '150.000' in chunks[0]['text'] and 'dưới 6 tuổi' in chunks[0]['text']
    assert 'Nguồn:' not in chunks[0]['text'] and 'Tài liệu:' not in chunks[0]['text']
    assert len(chunks[0]['sha256']) == 64


def test_heading_boundary_and_long_table_preserve_each_row():
    rows = ['Dịch vụ | Giá'] + [f'Khám chuyên khoa {i} | {i}.000 đồng' for i in range(150)]
    chunks = p.split_blocks([('text', 'Quầy mở cửa lúc 7 giờ.'), ('heading', 'Bảng giá'), ('table', '\n'.join(rows))])
    assert chunks[0]['text'] == 'Quầy mở cửa lúc 7 giờ.'
    table_chunks = [c['text'] for c in chunks if 'Dịch vụ | Giá' in c['text']]
    assert len(table_chunks) > 1
    for row in rows[1:]:
        assert sum(row in c.splitlines() for c in table_chunks) == 1
    assert all(c.startswith(rows[0]) for c in table_chunks)


def test_oversized_sentence_fails_instead_of_silent_truncation():
    with pytest.raises(ValueError, match='KNOWLEDGE_BLOCK_TOO_LONG'):
        p.split_blocks([('text', 'điều kiện ' * 3000)])


def test_docx_keeps_table_at_original_position(tmp_path):
    from docx import Document
    doc = Document()
    doc.add_paragraph('Trước bảng: chỉ áp dụng trong giờ hành chính.')
    table = doc.add_table(rows=2, cols=2)
    for row, cells in zip(table.rows, [('Dịch vụ', 'Giá'), ('Khám', '150.000')]):
        for cell, text in zip(row.cells, cells): cell.text = text
    doc.add_paragraph('Sau bảng: chưa bao gồm xét nghiệm.')
    path = tmp_path / 'test.docx'
    doc.save(path)
    text = '\n'.join(c['text'] for c in p.process(path, tmp_path)['chunks'])
    assert text.index('Trước bảng') < text.index('150.000') < text.index('Sau bảng')


def test_actual_pdf_and_blank_pdf(tmp_path):
    # Build a minimal text PDF with pypdf; no provider mock involved.
    from pypdf import PdfWriter
    from pypdf.generic import DictionaryObject, NameObject, DecodedStreamObject
    writer = PdfWriter()
    page = writer.add_blank_page(width=612, height=792)
    font = DictionaryObject({NameObject('/Type'): NameObject('/Font'), NameObject('/Subtype'): NameObject('/Type1'), NameObject('/BaseFont'): NameObject('/Helvetica')})
    page[NameObject('/Resources')] = DictionaryObject({NameObject('/Font'): DictionaryObject({NameObject('/F1'): writer._add_object(font)})})
    content = DecodedStreamObject()
    content.set_data(b'BT /F1 12 Tf 50 700 Td (Opening hours: 07:00-17:00. Closed Sundays.) Tj ET')
    page[NameObject('/Contents')] = writer._add_object(content)
    path = tmp_path / 'text.pdf'
    writer.write(path)
    result = p.process(path, tmp_path)
    assert 'Closed Sundays.' in result['chunks'][0]['text']


def test_cli_returns_sanitized_error_and_no_content_on_stdout(tmp_path):
    path = tmp_path / 'bad.txt'
    path.write_bytes(b'private\xff')
    output = tmp_path / 'result.json'
    result = subprocess.run([sys.executable, str(Path(p.__file__)), str(path), str(output)], capture_output=True)
    assert result.returncode == 0 and not result.stdout and not result.stderr
    assert json.loads(output.read_text()) == {'error': 'KNOWLEDGE_EXTRACTION_FAILED'}


def test_txt_md_and_invalid_doc(tmp_path):
    for extension in ('txt', 'md'):
        path = tmp_path / ('input.' + extension)
        path.write_text('Giờ mở cửa\n\n7 giờ đến 17 giờ. Nghỉ Chủ nhật.', encoding='utf-8')
        assert 'Nghỉ Chủ nhật' in str(p.process(path, tmp_path))
    path = tmp_path / 'input.doc'
    path.write_bytes(b'not a Word document')
    with pytest.raises(ValueError, match='KNOWLEDGE_DOC_INVALID'): p.process(path, tmp_path)
