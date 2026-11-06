/* METADATA
{
  "name": "movness_phone",
  "display_name": {
    "zh": "手机行动与示范学习",
    "en": "Phone actions and demonstrations"
  },
  "description": {
    "zh": "Movness本机工具。手机界面操作使用phone_apps、phone_launch、phone_screen、phone_act。常规授权任务连续执行，不让用户逐步点击。用户已教过的操作先learning_list核对，再按授权learning_run并查状态。自动任务创建继续使用底座的workflow工具，不把聊天历史当作定时任务。",
    "en": "Native phone actions and saved user demonstrations. Inspect actual results."
  },
  "tools": [
    {
      "name": "phone_apps",
      "description": {
        "zh": "列出可启动的手机应用名称与包名。",
        "en": "列出可启动的手机应用名称与包名。"
      },
      "parameters": []
    },
    {
      "name": "phone_launch",
      "description": {
        "zh": "打开指定手机应用。launch_requested只表示已发出打开请求，之后读取界面核对。",
        "en": "打开指定手机应用。launch_requested只表示已发出打开请求，之后读取界面核对。"
      },
      "parameters": [
        {
          "name": "package_name",
          "type": "string",
          "required": true,
          "description": {
            "zh": "phone_apps中的实际包名",
            "en": "phone_apps中的实际包名"
          }
        }
      ]
    },
    {
      "name": "phone_screen",
      "description": {
        "zh": "读取当前手机界面中的可见非密码控件。返回的package、viewId、label、description、class可用于精确操作；truncated表示结果未全部展示。",
        "en": "读取当前手机界面中的可见非密码控件。返回的package、viewId、label、description、class可用于精确操作；truncated表示结果未全部展示。"
      },
      "parameters": []
    },
    {
      "name": "phone_act",
      "description": {
        "zh": "执行手机界面操作。仅接受唯一、可见、非密码目标；不会猜坐标或随意选第一个。action_accepted不代表业务结果成功，操作后读取界面核对。",
        "en": "执行手机界面操作。仅接受唯一、可见、非密码目标；不会猜坐标或随意选第一个。action_accepted不代表业务结果成功，操作后读取界面核对。"
      },
      "parameters": [
        {
          "name": "action",
          "type": "string",
          "required": true,
          "description": {
            "zh": "click、longClick、text、scrollForward、scrollBackward、back或home",
            "en": "click、longClick、text、scrollForward、scrollBackward、back或home"
          }
        },
        {
          "name": "package",
          "type": "string",
          "required": false,
          "description": {
            "zh": "目标应用包名",
            "en": "目标应用包名"
          }
        },
        {
          "name": "viewId",
          "type": "string",
          "required": false,
          "description": {
            "zh": "来自phone_screen的控件资源ID",
            "en": "来自phone_screen的控件资源ID"
          }
        },
        {
          "name": "label",
          "type": "string",
          "required": false,
          "description": {
            "zh": "来自phone_screen的精确文字",
            "en": "来自phone_screen的精确文字"
          }
        },
        {
          "name": "description",
          "type": "string",
          "required": false,
          "description": {
            "zh": "来自phone_screen的精确描述",
            "en": "来自phone_screen的精确描述"
          }
        },
        {
          "name": "class",
          "type": "string",
          "required": false,
          "description": {
            "zh": "来自phone_screen的控件类型",
            "en": "来自phone_screen的控件类型"
          }
        },
        {
          "name": "text",
          "type": "string",
          "required": false,
          "description": {
            "zh": "仅text动作：要输入的非密码文字",
            "en": "仅text动作：要输入的非密码文字"
          }
        }
      ]
    },
    {
      "name": "learning_list",
      "description": {
        "zh": "列出用户保存的示范名称、编号与步骤数，不返回录制文字内容。",
        "en": "列出用户保存的示范名称、编号与步骤数，不返回录制文字内容。"
      },
      "parameters": []
    },
    {
      "name": "learning_run",
      "description": {
        "zh": "执行用户授权的已保存示范。返回running和runId后继续learning_status，直至completed、failed、cancelled或interrupted。completed仅表示记录的操作步骤已被执行，业务结果应另行核对。",
        "en": "执行用户授权的已保存示范。返回running和runId后继续learning_status，直至completed、failed、cancelled或interrupted。completed仅表示记录的操作步骤已被执行，业务结果应另行核对。"
      },
      "parameters": [
        {
          "name": "skill_id",
          "type": "string",
          "required": true,
          "description": {
            "zh": "learning_list返回的真实学习记录ID",
            "en": "learning_list返回的真实学习记录ID"
          }
        }
      ]
    },
    {
      "name": "learning_status",
      "description": {
        "zh": "核对此次示范回放的实际状态、已完成步骤与停止原因。",
        "en": "核对此次示范回放的实际状态、已完成步骤与停止原因。"
      },
      "parameters": [
        {
          "name": "run_id",
          "type": "string",
          "required": true,
          "description": {
            "zh": "learning_run返回的runId",
            "en": "learning_run返回的runId"
          }
        }
      ]
    },
    {
      "name": "learning_cancel",
      "description": {
        "zh": "请求停止指定回放，不会取消别的执行编号。返回cancellation_requested后查询状态确认。",
        "en": "请求停止指定回放，不会取消别的执行编号。返回cancellation_requested后查询状态确认。"
      },
      "parameters": [
        {
          "name": "run_id",
          "type": "string",
          "required": true,
          "description": {
            "zh": "需要停止的实际runId",
            "en": "需要停止的实际runId"
          }
        }
      ]
    }
  ]
}
*/
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);
var phone_exports = {};
__export(phone_exports, {
  learning_cancel: () => learning_cancel,
  learning_list: () => learning_list,
  learning_run: () => learning_run,
  learning_status: () => learning_status,
  phone_act: () => phone_act,
  phone_apps: () => phone_apps,
  phone_launch: () => phone_launch,
  phone_screen: () => phone_screen
});
module.exports = __toCommonJS(phone_exports);
const context = () => Java.getApplicationContext();
function invoke(type, method, ...args) {
  try {
    complete(JSON.parse(Java.callStatic(type, method, ...args)));
  } catch (error) {
    console.error("Movness phone tool failed", error instanceof Error ? error.message : "native_error");
    complete({ success: false, error: error instanceof Error ? error.message : "\u624B\u673A\u5DE5\u5177\u6267\u884C\u5931\u8D25" });
  }
}
const phone_apps = () => invoke("io.movness.ui.PhoneActions", "apps", context());
const phone_launch = (p) => invoke("io.movness.ui.PhoneActions", "launch", context(), p.package_name);
const phone_screen = () => invoke("io.movness.ui.PhoneActions", "screen");
const phone_act = (p) => invoke("io.movness.ui.PhoneActions", "act", JSON.stringify(p));
const learning_list = () => invoke("io.movness.ui.LearningStore", "list", context());
const learning_run = (p) => invoke("io.movness.ui.LearningStore", "run", context(), p.skill_id);
const learning_status = (p) => invoke("io.movness.ui.LearningStore", "status", context(), p.run_id);
const learning_cancel = (p) => invoke("io.movness.ui.LearningStore", "cancel", context(), p.run_id);
