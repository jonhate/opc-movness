from pathlib import Path
import xml.etree.ElementTree as ET,html,re,zipfile,json
R=Path(__file__).resolve().parent;B=R.parent/'r49-brand';D=R/'decoded';S=R/'overlay/app/src/main'
A='http://schemas.android.com/apk/res/android';ET.register_namespace('android',A)
manifest=ET.parse(D/'AndroidManifest.xml');app=manifest.getroot().find('application')
for activity in app.findall('activity'):
    if activity.get('{'+A+'}name')=='com.ai.assistance.operit.ui.main.MainActivity':activity.set('{'+A+'}windowSoftInputMode','adjustResize')
service=ET.SubElement(app,'service',{'{'+A+'}name':'io.movness.ui.LearningService','{'+A+'}label':'Movness 手机操作与学习','{'+A+'}permission':'android.permission.BIND_ACCESSIBILITY_SERVICE','{'+A+'}exported':'true'})
intent=ET.SubElement(service,'intent-filter');ET.SubElement(intent,'action',{'{'+A+'}name':'android.accessibilityservice.AccessibilityService'})
ET.SubElement(service,'meta-data',{'{'+A+'}name':'android.accessibilityservice','{'+A+'}resource':'@xml/movness_learning_accessibility'})
manifest.write(D/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
# Source manifest retains its placeholders and Gradle merger annotations.
text=(B/'source/app/src/main/AndroidManifest.xml').read_text().replace('android:windowSoftInputMode="adjustPan"','android:windowSoftInputMode="adjustResize"')
entry='''        <service android:name="io.movness.ui.LearningService" android:label="@string/movness_learning_service_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true">
            <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/movness_learning_accessibility" />
        </service>
'''
(S/'AndroidManifest.xml').write_text(text.replace('    </application>',entry+'    </application>'))
xml='''<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
 android:accessibilityEventTypes="typeViewClicked|typeViewLongClicked|typeViewTextChanged|typeViewScrolled"
 android:accessibilityFeedbackType="feedbackGeneric" android:notificationTimeout="100"
 android:canRetrieveWindowContent="true"
 android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
 android:description="@string/movness_learning_service_description" />
'''
for p in [D/'res/xml/movness_learning_accessibility.xml',S/'res/xml/movness_learning_accessibility.xml']:p.parent.mkdir(parents=True,exist_ok=True);p.write_text(xml)
zh='''Movness 是你的随身行动助手。说出目标后，它会使用已配置且已授权的手机工具，也可以把工作交给已连接的电脑。

首次使用只需接入模型，并按需要开启手机操作与连续执行权限。电脑配对从右上角「连接 DSH」进入，识别二维码、在电脑确认，再选择会话。

「对话记录」保存聊天；「自动任务」展示已保存的时间或条件触发任务。电脑文件实际取回后，才会出现在「连接 DSH」的成果列表；旧版文件会保留。

示范学习只在你点击「开始示范」后记录点击、滚动和非密码文字输入，记录保存在本机。学习名称等摘要可由工具交给你配置的模型；读取当前界面的工具会按任务向模型提供可见、非密码控件。你可以结束、丢弃或删除学习记录。回放只对明确且唯一的控件执行，界面变化时会停止。

聊天、配置、学习记录与取回的成果保存在应用数据中。云模型和联网工具会向你选择的服务发送任务所需的消息、工具结果、文件或情境信息；并非所有数据都只留在手机。位置等访问取决于实际工具与系统授权，不会因为打开此页面而持续收集。

手机停止按钮结束当前响应并请求停止本机示范回放；电脑任务需要另行取消并确认状态。模型回复、动作被接受与实际目标完成是不同状态。本预览版不保证全天候后台运行。反馈请联系向你提供安装包的 Movness 项目维护者。'''
en='''Movness is your personal action assistant. State a goal and it can use configured, authorized phone tools or work with a paired computer.

Connect your model and grant the phone and continuous-execution permissions you need. Use Connect DSH in the header to scan the pairing code, approve on the computer, and select a session.

Conversations remain in Chat history. Automatic tasks are saved time- or condition-triggered workflows. Retrieved computer files appear in Connect DSH results only after retrieval; older retrieved versions remain available.

Demonstrations record clicks, scrolling and non-password text only after you explicitly start recording. They are stored locally and can be stopped, discarded or deleted. Skill names and summaries may be sent to your configured model by tools. Screen tools provide visible non-password controls for your task. Replay requires a unique target and stops when the interface changes.

Cloud models and network tools receive the messages, tool results, files or context needed for the task. Data does not necessarily stay only on the phone. Location access depends on enabled tools and system permission, rather than continuous collection by default.

Phone Stop ends the response and requests cancellation of local demonstration replay. Computer work requires separate cancellation and confirmation. A reply, an accepted action and a verified goal are different states. Continuous background operation is not guaranteed. Contact the Movness maintainer who supplied this preview for feedback.'''
extra_zh='<p><b>示范学习与手机界面工具</b><br>示范只在用户主动开始后记录点击、滚动及非密码文字输入，保存于本机，直到用户删除记录、清除应用数据或卸载。学习列表工具仅返回名称、编号、步骤数等摘要；若模型调用界面工具，当前可见、非密码控件将作为工具结果交给所配置的模型。回放执行已保存的明确控件动作，动作被系统接受不等于业务目标已完成。用户可随时停止；找不到唯一目标、权限不可用或服务中断时应停止并说明原因。启用系统无障碍服务不代表对任意目的操作的授权。</p>'
extra_en='<p><b>Demonstrations and phone tools</b><br>Recording begins only when explicitly started and stores clicks, scrolling and non-password text locally until deletion, app-data removal or uninstall. The learning-list tool returns names, identifiers and step counts. Screen tools send visible non-password controls to your configured model for the task. Replay performs saved exact-target actions; an accepted action is not proof of business success. The user can stop it. Missing or ambiguous targets, revoked permission or interrupted service must stop execution. Android accessibility permission is not authorization for arbitrary purposes.</p>'
pattern=re.compile(r'(<string\s+(?![^>]*?/>)[^>]*\bname="([^"]+)"[^>]*>)(.*?)(</string>)',re.S)
def encode(s):return html.escape(s,quote=False).replace("'","\\'").replace('"','\\"').replace('\n','\\n')
for p in (D/'res').glob('values*/strings.xml'):
    text=p.read_text();is_zh=p.parent.name=='values' or p.parent.name.startswith('values-zh');human=zh if is_zh else en;notice=extra_zh if is_zh else extra_en
    def update(m):
        if m[2]=='agreement_human_readable_content':return m[1]+encode(human)+m[4]
        if m[2]=='agreement_serious_content':
            old=ET.fromstring(m[0]).text or '';old=old.replace('2026-11-06-movness','2026-11-06-movness-r50')
            # Decode Android quotes once before encoding the replacement.
            old=old.replace('\\"','"').replace("\\'","'").replace('\\n','\n')
            return m[1]+encode(old+'\n'+notice)+m[4]
        return m[0]
    out=pattern.sub(update,text)
    if p.parent.name=='values':out=out.replace('</resources>','    <string name="movness_learning_service_name">Movness 手机操作与学习</string>\n    <string name="movness_learning_service_description">仅在你开始示范或发起手机操作时读取界面。示范记录保存在本机，可随时停止和删除；不会记录密码框内容。</string>\n</resources>')
    p.write_text(out)
    source=B/'source/app/src/main/res'/p.parent.name/'strings.xml'
    if source.exists():
        target=S/'res'/p.parent.name/'strings.xml';target.parent.mkdir(parents=True,exist_ok=True)
        src=pattern.sub(update,source.read_text())
        if p.parent.name=='values':src=src.replace('</resources>','    <string name="movness_learning_service_name">Movness 手机操作与学习</string>\n    <string name="movness_learning_service_description">仅在你开始示范或发起手机操作时读取界面。示范记录保存在本机，可随时停止和删除；不会记录密码框内容。</string>\n</resources>')
        target.write_text(src)
with zipfile.ZipFile(R/'phone-package/movness_phone_bundle.toolpkg','w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for n in ['manifest.json','main.js','phone.js']:z.write(R/'phone-package'/n,n)
for target in [D/'assets/packages/movness_phone_bundle.toolpkg',S/'assets/packages/movness_phone_bundle.toolpkg']:
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((R/'phone-package/movness_phone_bundle.toolpkg').read_bytes())
with zipfile.ZipFile(B/'private-inputs/Movness_r46_source_and_handoff.zip') as archive:
    notices=['Movness 0.5.2\nPhone UI and demonstrations use Android platform APIs.\nUpstream components retain their original rights and licences.\n']
    for n in archive.namelist():
        if n.endswith('/upstream/OPERIT_LICENSE') or n.endswith('/upstream/TERMINAL_LICENSE'):notices.append('\n'+n.rsplit('/',1)[-1]+'\n'+archive.read(n).decode())
notices.append('\nCommonMark Java 0.24.0 — BSD-2-Clause\n'+(R/'licenses/CommonMark-LICENSE.txt').read_text())
notices.append('\nGNU GPL v3 — complete text supplementing the upstream notices\n'+(R/'licenses/COPYING').read_text())
for target in [D/'assets/movness-notices.txt',S/'assets/movness-notices.txt']:target.parent.mkdir(parents=True,exist_ok=True);target.write_text('\n'.join(notices))
for p in D.glob('res/**/*.xml'):ET.parse(p)
print('Prepared versioned notices, real service declaration and built-in phone ToolPkg')
