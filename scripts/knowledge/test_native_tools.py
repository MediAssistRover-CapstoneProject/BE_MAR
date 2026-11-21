"""Real OCR/conversion smoke tests. Container has the required native tools."""
import shutil
from pathlib import Path
import pytest
from test_process_document import p


@pytest.mark.skipif(not shutil.which('tesseract'), reason='Tesseract not installed on this host')
def test_real_vietnamese_scanned_pdf(tmp_path):
    from PIL import Image, ImageDraw, ImageFont
    font_path = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
    if not font_path.exists(): pytest.skip('Test font unavailable')
    image = Image.new('RGB', (1600, 700), 'white')
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(font_path), 48)
    draw.text((70, 90), 'Giờ khám: 07:00 đến 17:00.', font=font, fill='black')
    draw.text((70, 200), 'Không tiếp nhận vào Chủ nhật.', font=font, fill='black')
    path = tmp_path / 'scan.pdf'
    image.save(path, 'PDF', resolution=150)
    text = '\n'.join(c['text'] for c in p.process(path, tmp_path)['chunks'])
    assert '07:00' in text and '17:00' in text and 'Chủ nhật' in text


@pytest.mark.skipif(not shutil.which('libreoffice'), reason='LibreOffice not installed on this host')
def test_real_doc_conversion_preserves_text_and_table(tmp_path):
    from docx import Document
    doc = Document()
    doc.add_paragraph('Giờ khám: 07:00 đến 17:00. Nghỉ Chủ nhật.')
    table = doc.add_table(rows=2, cols=2)
    for row, values in zip(table.rows, [('Dịch vụ', 'Giá'), ('Khám', '150.000 đồng')]):
        for cell, value in zip(row.cells, values): cell.text=value
    source=tmp_path / 'source.docx'
    doc.save(source)
    converted=tmp_path / 'legacy'
    converted.mkdir()
    p.run(['libreoffice','-env:UserInstallation='+(tmp_path/'test-profile').as_uri(),
           '--headless','--convert-to','doc:MS Word 97','--outdir',str(converted),str(source)], timeout=40)
    path=converted/'source.doc'
    assert path.exists()
    text='\n'.join(c['text'] for c in p.process(path,tmp_path)['chunks'])
    assert '07:00' in text and 'Chủ nhật' in text and '150.000' in text


@pytest.mark.skipif(not shutil.which('tesseract'), reason='Tesseract not installed on this host')
def test_mixed_pdf_keeps_image_text_and_does_not_duplicate_embedded_text(tmp_path):
    from PIL import Image, ImageDraw, ImageFont
    from reportlab.pdfgen import canvas
    from reportlab.lib.utils import ImageReader
    image = Image.new('RGB', (1200, 180), 'white')
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 44)
    ImageDraw.Draw(image).text((30, 40), 'Closed Sundays.', font=font, fill='black')
    path = tmp_path / 'mixed.pdf'
    pdf = canvas.Canvas(str(path))
    pdf.setFont('Helvetica', 20)
    pdf.drawString(40, 740, 'Opening hours: 07:00 to 17:00.')
    pdf.drawImage(ImageReader(image), 40, 600, width=480, height=72)
    pdf.save()
    text = '\n'.join(c['text'] for c in p.process(path, tmp_path)['chunks'])
    assert text.count('07:00') == 1 and 'Closed Sundays.' in text
