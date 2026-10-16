param([string]$SourceDirectory = 'C:\Users\Admin\Downloads', [int]$SourceIndex = -1)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$names = @('state digram (4).drawio', 'MediAssistRover_ERD (1) (2).drawio', '04-MediAssistRover_Dac_ta_11_STEP_TYPE-1-.docx', '03-MediAssistRover_10_Workflow_va_Luong_du_lieu_v3.docx', 'FA26SE015_Autonomous Hospital Assistant Robot System for Outpatient Department_NhanDT35(1).docx')
$results = foreach ($name in $names) {
    $path = Join-Path $SourceDirectory $name
    $lines = [System.Collections.Generic.List[string]]::new()
    if ($name.EndsWith('.docx')) {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
        try {
            foreach ($entry in $zip.Entries | Where-Object { $_.FullName -match '^word/(document|footnotes|endnotes|comments|header\d+|footer\d+)\.xml$' }) {
                $reader = [System.IO.StreamReader]::new($entry.Open())
                try { [xml]$xml = $reader.ReadToEnd() } finally { $reader.Dispose() }
                $lines.Add('PART: ' + $entry.FullName)
                foreach ($p in $xml.SelectNodes('//*[local-name()="p"]')) {
                    $text = ($p.SelectNodes('.//*[local-name()="t"]') | ForEach-Object { $_.InnerText }) -join ''
                    if ($text) { $lines.Add($text) }
                }
            }
        } finally { $zip.Dispose() }
    } else {
        [xml]$xml = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        foreach ($diagram in $xml.mxfile.diagram) {
            $lines.Add('DIAGRAM: ' + $diagram.name)
            foreach ($obj in $diagram.SelectNodes('.//object | .//UserObject')) {
                $lines.Add('OBJECT: ' + (($obj.Attributes | ForEach-Object { $_.Name + '=' + $_.Value }) -join '; '))
            }
            $cells = @($diagram.SelectNodes('.//mxCell'))
            if (!$cells.Count) { throw 'Compressed diagram unsupported: export uncompressed XML first.' }
            foreach ($cell in $cells) {
                $label = [System.Net.WebUtility]::HtmlDecode([string]$cell.value)
                $label = $label -replace '<br\s*/?>|</div>|</p>', ' / ' -replace '<[^>]+>', ''
                if ($label -or $cell.edge -eq '1') {
                    $lines.Add(('id={0}; parent={1}; source={2}; target={3}; edge={4}; label={5}' -f $cell.id,$cell.parent,$cell.source,$cell.target,$cell.edge,$label))
                }
            }
        }
    }
    [pscustomobject]@{ name=$name; path=$path; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash; lines=$lines.ToArray() }
}
$pasted = 'C:\Users\Admin\.codex\attachments\7e8e06d3-f039-426e-a36d-c1232b88c500\Pasted text.txt'
$results += [pscustomobject]@{ name='Pasted text.txt'; path=$pasted; sha256=(Get-FileHash -LiteralPath $pasted -Algorithm SHA256).Hash; lines=@(Get-Content -LiteralPath $pasted -Encoding UTF8 | ForEach-Object { [string]$_ }) }
if ($SourceIndex -ge 0) { ConvertTo-Json -InputObject $results[$SourceIndex] -Depth 5 -Compress }
else { ConvertTo-Json -InputObject @($results) -Depth 5 -Compress }
