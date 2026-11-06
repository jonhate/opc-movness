/* METADATA
{
  "name": "movness_computer",
  "display_name": {
    "zh": "电脑协作",
    "en": "Computer collaboration"
  },
  "description": {
    "zh": "调用已配对的电脑 DSH 会话，提交工作并把实际成果文件带回手机。",
    "en": "Work with a paired DSH session on your computer."
  },
  "enabledByDefault": true,
  "category": "System",
  "tools": [
    {
      "name": "usage_advice",
      "advice": true,
      "description": {
        "zh": "先用 computer_status 查看连接；首次需在电脑连接界面扫码、电脑批准并选择会话。已有授权的常规任务直接提交，不逐步要求用户点击执行。computer_submit 的 accepted 只表示进入电脑队列，必须继续 computer_wait，直至 reply_ready/no_reply/needs_input/stopped。reply_ready 只是收到电脑输出记录，检查其中错误与实际产物证据后再作结论。网络结果不确定时用 computer_recover_submission 查找原任务接收记录，稍后可再次核对；不会自动重发，不能重新提交为新任务。停止电脑会话用 computer_cancel，之后继续查询确认停止。电脑会话可能包含用户其他任务，须按用户意图操作。电脑生成文档后，使用 computer_fetch_file 读取项目相对路径并保存到手机成果；返回 saved_to_phone 后才可说明已保存。可用 computer_results 查找本机已有成果。不要把输出里的电脑路径当成手机已收到文件。文件传输上限5MiB；文件变化会拒绝发布成果。",
        "en": "Submit only authorized work. Acceptance is not completion. Wait for results and inspect execution evidence. Recover uncertain submissions by checking admission records; do not resend."
      },
      "parameters": []
    },
    {
      "name": "computer_status",
      "description": {
        "zh": "查看已配对电脑和当前电脑任务的状态，不返回设备凭据。",
        "en": "Read paired-computer and task state without credentials."
      },
      "parameters": []
    },
    {
      "name": "computer_sessions",
      "description": {
        "zh": "列出电脑上已有的会话，便于理解当前协作上下文。更换绑定目标需在电脑连接界面选择。",
        "en": "List existing computer sessions."
      },
      "parameters": []
    },
    {
      "name": "computer_submit",
      "description": {
        "zh": "把明确任务提交给已绑定电脑会话。返回 accepted 后继续查询结果。",
        "en": "Submit work to the selected computer session."
      },
      "parameters": [
        {
          "name": "message",
          "type": "string",
          "required": true,
          "description": {
            "zh": "要让电脑完成的具体工作",
            "en": "Specific work for the computer"
          }
        }
      ]
    },
    {
      "name": "computer_recover_submission",
      "description": {
        "zh": "原提交的网络结果不确定时，核对电脑队列及历史中的原请求编号；不会再次发送任务。",
        "en": "Check original admission records after an uncertain submission, without resending."
      },
      "parameters": []
    },
    {
      "name": "computer_wait",
      "description": {
        "zh": "等待电脑结果，返回运行中、需要用户、收到输出、无回复或停止状态。运行中可继续等待。",
        "en": "Wait for computer output or an actionable state."
      },
      "parameters": []
    },
    {
      "name": "computer_cancel",
      "description": {
        "zh": "请求停止当前绑定电脑会话的运行。会影响该会话当前任务；返回后查询实际停止状态。",
        "en": "Request cancellation of the selected computer session."
      },
      "parameters": []
    },
    {
      "name": "computer_fetch_file",
      "description": {
        "zh": "把电脑会话项目内的成果文件完整带回手机，校验版本和字节数后发布到本机成果。",
        "en": "Fetch a result file from the selected computer project into local phone results."
      },
      "parameters": [
        {
          "name": "path",
          "type": "string",
          "required": true,
          "description": {
            "zh": "项目内的相对文件路径，例如 storyboard.md；不能填写URL或上级路径",
            "en": "A project-relative file path such as storyboard.md."
          }
        }
      ]
    },
    {
      "name": "computer_results",
      "description": {
        "zh": "列出已完整保存到手机的电脑成果，含本机路径与SHA256；不需要电脑在线。",
        "en": "List downloaded local results, including local paths and SHA256."
      },
      "parameters": []
    }
  ]
}
*/
import { getClient, publicError } from "../android";

async function run<T>(action: () => Promise<T>): Promise<void> {
  try { complete(await action()); }
  catch (error) { complete({ success: false, ...publicError(error) }); }
}

export const computer_status = () => run(async () => (await getClient()).publicState());
export const computer_sessions = () => run(async () => ({ sessions: await (await getClient()).sessions() }));
export const computer_submit = (params: { message: string }) => run(async () => (await getClient()).submit(params.message));
export const computer_recover_submission = () => run(async () => (await getClient()).recoverSubmission());
export const computer_wait = () => run(async () => (await getClient()).waitForReply());
export const computer_cancel = () => run(async () => (await getClient()).cancel());


export const computer_fetch_file = (params: { path: string }) => run(async () => (await getClient()).fetchFile(params.path));
export const computer_results = () => run(async () => ({ files: await (await getClient()).results() }));
