package com.ai.assistance.operit.data.preferences

import android.content.Context

/**
 * 默认角色卡提示词的双语数据
 */
object CharacterCardBilingualData {

    /**
     * 获取默认角色卡描述
     */
    fun getDefaultDescription(context: Context): String {
        return if (isChineseLocale(context)) {
            "系统默认的角色卡配置"
        } else {
            "System default character card configuration"
        }
    }

    /**
     * 获取默认角色设定
     */
    fun getDefaultCharacterSetting(context: Context): String {
        return if (isChineseLocale(context)) {
            "你是 Movness，用户的随身行动助手。根据用户目标使用当前已配置且已授权的手机工具；电脑工作仅通过已连接的电脑工具执行。缺少模型、权限或连接时准确说明具体缺口。区分计划、任务已接收、模型已回复和成果已保存；不能把未执行的动作说成完成，也不能编造先前任务。对外发送、删除和发布等行为遵守用户授权。优先完成明确目标，避免让用户为每个普通步骤重复确认。"
        } else {
            "You are Movness, the user's personal action assistant. Use configured, authorized phone tools for the user's goal and connected computer tools for computer work. State missing configuration, permission or connection precisely. Distinguish plans, accepted tasks, model replies and saved results. Never claim an unperformed action completed or invent earlier work. Respect authorization for sending, deleting and publishing. Carry out clear goals without repeated confirmation for ordinary authorized steps."
        }
    }

    /**
     * 获取默认其他内容（聊天）
     */
    fun getDefaultOtherContentChat(context: Context): String {
        return if (isChineseLocale(context)) {
            "保持有帮助的语气，并清楚地传达限制。"
        } else {
            "Maintain a helpful tone and clearly communicate limitations."
        }
    }

    /**
     * 获取默认其他内容（语音）
     */
    fun getDefaultOtherContentVoice(context: Context): String {
        return if (isChineseLocale(context)) {
            """你是 Movness，用户的随身行动助手。语音回答简短、自然，先说明当前结果或需要用户做的关键一步。沿用当前任务和授权，不要求用户逐步点击普通工具动作。对未执行、执行中、失败和已验证完成作明确区分；不能假装已经操作设备。用户问到身份时如实说明是 AI 助手。"""
        } else {
            """You are Movness, a personal action assistant. Keep spoken replies short and natural; lead with the result or the one required user action. Continue within the current task and authorization without requiring clicks for ordinary tool steps. Distinguish not started, running, failed and verified complete. Do not pretend to have operated a device. Identify yourself truthfully as an AI assistant when asked."""
        }
    }

    /**
     * 获取角色描述标签
     */
    fun getCharacterDescriptionLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "角色描述："
        } else {
            "Character Description:"
        }
    }

    /**
     * 获取性格特征标签
     */
    fun getPersonalityLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "性格特征："
        } else {
            "Personality:"
        }
    }

    /**
     * 获取场景设定标签
     */
    fun getScenarioLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "场景设定："
        } else {
            "Scenario Setting:"
        }
    }

    /**
     * 获取对话示例标签
     */
    fun getDialogueExampleLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "对话示例："
        } else {
            "Dialogue Examples:"
        }
    }

    /**
     * 获取系统提示词标签
     */
    fun getSystemPromptLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "系统提示词："
        } else {
            "System Prompt:"
        }
    }

    /**
     * 获取历史指令标签
     */
    fun getPostHistoryInstructionsLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "历史指令："
        } else {
            "Post-History Instructions:"
        }
    }

    /**
     * 获取备用问候语标签
     */
    fun getAlternateGreetingsLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "备用问候语："
        } else {
            "Alternate Greetings:"
        }
    }

    /**
     * 获取深度提示词标签
     */
    fun getDepthPromptLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "深度提示词："
        } else {
            "Depth Prompt:"
        }
    }

    /**
     * 获取世界书标签名称模板
     */
    fun getWorldBookTagName(context: Context, characterName: String): String {
        return if (isChineseLocale(context)) {
            "世界书: $characterName"
        } else {
            "World Book: $characterName"
        }
    }

    /**
     * 获取世界书标签描述模板
     */
    fun getWorldBookTagDescription(context: Context, characterName: String): String {
        return if (isChineseLocale(context)) {
            "为角色'$characterName'自动生成的世界书。"
        } else {
            "World book auto-generated for character '$characterName'."
        }
    }

    /**
     * 获取来源标签
     */
    fun getSourceLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "来源：酒馆角色卡\n"
        } else {
            "Source: Tavern Character Card\n"
        }
    }

    /**
     * 获取作者标签
     */
    fun getAuthorLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "作者："
        } else {
            "Author:"
        }
    }

    /**
     * 获取作者备注标签
     */
    fun getAuthorNotesLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "作者备注：\n\n"
        } else {
            "Author Notes:\n\n"
        }
    }

    /**
     * 获取版本标签
     */
    fun getVersionLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "版本："
        } else {
            "Version:"
        }
    }

    /**
     * 获取原始标签标签
     */
    fun getOriginalTagsLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "原始标签："
        } else {
            "Original Tags:"
        }
    }

    /**
     * 获取格式标签
     */
    fun getFormatLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "格式："
        } else {
            "Format:"
        }
    }

    /**
     * 获取标签标签
     */
    fun getTagsLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "标签："
        } else {
            "Tags:"
        }
    }

    /**
     * 获取等标签
     */
    fun getEtAlLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "等"
        } else {
            " et al."
        }
    }

    /**
     * 获取未找到标签
     */
    fun getNotFoundLabel(context: Context): String {
        return if (isChineseLocale(context)) {
            "未找到"
        } else {
            "not found"
        }
    }

    /**
     * 检查是否为中文语言环境
     */
    private fun isChineseLocale(context: Context): Boolean {
        val locale = context.resources.configuration.locales.get(0)
        return locale.language == "zh" || locale.language == "zho"
    }
}
