"""Review artifact derived from the production Compose DSL fixture; not a phone screenshot."""
import argparse, html, json, re, base64
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
fixture=json.loads((root/'evidence/ui-compose-fixture.json').read_text())
def inline(text):
    return re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',html.escape(text))
def markdown(text):
    lines=text.splitlines();parts=[];i=0
    while i<len(lines):
        line=lines[i]
        if line.startswith('|'):
            rows=[]
            while i<len(lines) and lines[i].startswith('|'):
                cells=[cell.strip() for cell in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?',cell) for cell in cells):rows.append(cells)
                i+=1
            parts.append('<div class="table-scroll"><table>'+''.join('<tr>'+''.join(('<th>' if r==0 else '<td>')+inline(cell)+('</th>' if r==0 else '</td>') for cell in row)+'</tr>' for r,row in enumerate(rows))+'</table></div>');continue
        if line.startswith('# '):parts.append('<h1>'+inline(line[2:])+'</h1>')
        elif line.startswith('## '):parts.append('<h2>'+inline(line[3:])+'</h2>')
        elif line.startswith('> '):parts.append('<blockquote>'+inline(line[2:])+'</blockquote>')
        elif line.startswith('- '):parts.append('<p class="bullet">'+inline(line[2:])+'</p>')
        elif line:parts.append('<p>'+inline(line)+'</p>')
        i+=1
    return ''.join(parts)
def node(value):
    kind=value['type'];props=value['props'];children=''.join(node(child) for child in value['children'])
    if kind in ('Text','Markdown'):
        text=props.get('text','')
        if kind=='Markdown':return '<article class="markdown">'+markdown(text)+'</article>'
        return '<div class="text '+html.escape(props.get('style','bodyMedium'))+'">'+html.escape(text)+'</div>'
    if kind in ('TextButton','Button'):
        label=props.get('text','') or ''.join(str(child['props'].get('text','')) for child in value['children'])
        action='preview' if label=='预览' else 'results' if label=='返回成果' else 'system' if label in ['分享','其他应用打开'] else 'device'
        return '<button class="'+('primary' if kind=='Button' else 'text-button')+'" data-action="'+action+'">'+html.escape(label)+'</button>'
    if kind=='CircularProgressIndicator':return '<span class="spinner"></span>'
    css='row' if kind=='Row' else 'card' if kind=='Card' else 'page' if kind=='LazyColumn' else 'column'
    style=''
    for name,key in [('padding','padding'),('spacing','gap')]:
        if isinstance(props.get(name),(int,float)):style+=key+':'+str(props[name])+'px;'
    return '<div class="'+css+'" style="'+style+'">'+children+'</div>'
body='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Movness · 成果交互预览</title><style>
*{box-sizing:border-box}body{margin:0;background:#f6f6fa;color:#262636;font-family:Inter,"Noto Sans CJK SC","PingFang SC","Microsoft Yahei",sans-serif;-webkit-font-smoothing:antialiased}.review-bar{padding:18px 24px;max-width:800px;margin:auto;display:flex;gap:16px;align-items:center;justify-content:space-between;color:#747483;font-size:12px}.review-bar strong{font-size:14px;color:#353546}.notice{max-width:752px;margin:0 auto 18px;padding:0 16px;color:#777887;font-size:12px;line-height:1.8}.surface{max-width:752px;margin:0 auto 48px;background:#fff;border-radius:28px;min-height:700px;padding:10px 8px;box-shadow:0 12px 48px #25254b06}.page,.column{display:flex;flex-direction:column}.row{display:flex;align-items:center;flex-wrap:wrap}.card{background:#f5f5fb;border-radius:22px}.text{line-height:1.55}.headlineMedium{font-size:28px;font-weight:650;letter-spacing:-.6px}.headlineSmall{font-size:24px;font-weight:650}.titleLarge{font-size:22px;font-weight:550}.titleMedium{font-size:16px;font-weight:600}.bodyMedium{font-size:14px;color:#656574}.bodySmall{font-size:12px;color:#858592}button{cursor:pointer;font:inherit;border:0}.text-button{color:#6159d8;background:transparent;border-radius:20px;font-size:14px;padding:9px 12px;width:fit-content}.text-button:hover{background:#eeeefa}.primary{background:#655ce5;color:#fff;padding:13px 24px;border-radius:99px;font-size:15px;width:fit-content}.markdown{font-size:14px;line-height:1.9;color:#424253}.markdown h1{font-size:30px;letter-spacing:-.6px;margin:12px 0}.markdown h2{font-size:20px;margin:28px 0 12px}.markdown p{margin:10px 0}.table-scroll{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px;line-height:1.8}th,td{text-align:left;vertical-align:top;padding:12px 10px;border-bottom:1px solid #ececf3}th{font-weight:600;color:#777489}th:first-child,td:first-child{white-space:nowrap;padding-left:0}.bullet:before{content:'·';margin-right:9px;color:#7a74cc}blockquote{margin:26px 0 8px;padding:14px 16px;border-left:3px solid #dcd8f8;background:#f7f7fc;color:#858593;font-size:12px}#status{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:#343343;color:#fff;border-radius:14px;padding:12px 18px;font-size:13px;max-width:90vw}#status:empty{display:none}[hidden]{display:none!important}@media(max-width:780px){body{background:#fff}.surface{border-radius:0;box-shadow:none;min-height:100vh;margin-bottom:0;padding:0}.review-bar{padding:16px 24px}.notice{padding:0 24px}th,td{padding:10px 6px;font-size:12px}.markdown h1{font-size:28px}}
</style><div class="review-bar"><strong>Movness · 成果返回</strong><span>r48 / 界面结构夹具</span></div><p class="notice">来自实际 Compose DSL 函数的交互快照。内容为协议测试样稿；此页用于检查结构与排版，不是手机截图。预览与返回可点击，系统分享仍需手机验收。</p><main class="surface" id="results">RESULTS</main><main class="surface" id="preview" hidden>PREVIEW</main><main class="surface" id="older" hidden>OLDER</main><div id="status" role="status"></div><script>
document.addEventListener('click',event=>{const button=event.target.closest('button');if(!button)return;const action=button.dataset.action;if(['preview','results','older'].includes(action)){for(const id of ['results','preview','older'])document.getElementById(id).hidden=id!==action;window.scrollTo(0,0)}else{document.getElementById('status').textContent=action==='system'?'系统打开与分享需要在手机验收':'设备连接操作需要在手机验收';setTimeout(()=>document.getElementById('status').textContent='',2600)}});
</script></html>'''.replace('RESULTS',node(fixture['results']).replace('data-action="preview"','data-action="preview-first"',1).replace('data-action="preview"','data-action="older"').replace('data-action="preview-first"','data-action="preview"')).replace('PREVIEW',node(fixture['preview'])).replace('OLDER',node(fixture['olderPreview']))
font=base64.b64encode((root/'assets/preview-font.woff2').read_bytes()).decode()
body=body.replace('<style>', '<style>@font-face{font-family:MovnessPreviewSans;src:url(data:font/woff2;base64,'+font+') format("woff2");font-weight:100 900;font-style:normal}')
body=body.replace('font-family:Inter,', 'font-family:MovnessPreviewSans,Inter,').replace('</style>', '.row>button{width:auto;flex:0 0 auto}</style>')
body=body.replace('</html>', '<script type="text/plain" id="font-license">'+(root/'licenses/preview-font-OFL.txt').read_text()+'</script></html>')
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(body)
print(a.output.resolve())
