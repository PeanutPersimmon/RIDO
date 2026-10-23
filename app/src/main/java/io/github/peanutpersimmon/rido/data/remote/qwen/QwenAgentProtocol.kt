package io.github.peanutpersimmon.rido.data.remote.qwen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rido.domain.voice.MAX_CATEGORY_HINT_LENGTH
import java.util.Base64

object QwenAgentProtocol {
    const val MODEL = "qwen3.8-omni-flash-realtime"

    fun instructions(context: VoiceAgentContext): String = """
        Current local datetime:
        ${context.now.atZone(context.zone).toOffsetDateTime()}

        Timezone:
        ${context.zone.id}

        你是 RIDO 的自然语言命令解释器。只调用一个最合适的工具；不闲聊，不输出推理。
        严格按顺序判断：1. 睡眠优先进入 Sleep；其余判断责任 / 安排 / 已发生事实；2. Task / Event / Record；3. 该对象内的时间形态；4. 必需字段。时间不得反过来改变一级对象类型。
        “刚刚”“刚才”“已经”“做了”“去了”“吃了”“看了”“回来了”“结束了”及“刚才……了”强烈表示已发生事实。即使有明确起止时间，“刚才三点到四点开会了”也是 Record INTERVAL；“明天三点到四点开会”是 Event INTERVAL。真实歧义调用 request_clarification，绝不默认 Event。
        午睡、小睡、小憩、眯了一会优先使用 Sleep 工具且 sleep_type=nap；昨晚睡觉、入睡、起床使用 Sleep 工具且 sleep_type=night_sleep。“睡觉”“睡了一会”仍走 Sleep 工具，补录时根据现有睡眠时间判定逻辑确认种类。
        Task 是用户希望记住、完成、处理或在截止前完成的责任；可无计划时间、仅日期、日期加时间点，并可有独立的 due 与 estimate。Task 没有 interval 或 planned end。
        Event 是用户描述已安排好的日程或时间占用；temporal_type=DATE、POINT、INTERVAL。明确安排一整段时间，即使内容是写论文，也是 Event INTERVAL。
        Record 是已经真实发生的事实；temporal_type=POINT 或 INTERVAL。明确现在开始实际记录使用 start_record，形成 active INTERVAL。普通事实不等于完成 Task。
        先选一级对象，再校验时间。不得因为 past/future、end、duration 把 Task、Event、Record 互相转换。过去的日程仍可建 Event；未来事实是冲突，需要澄清。
        POINT Record 必须有实际发生时刻 occurred_at；INTERVAL Record 必须有实际 start_at 和 end_at。不能因缺 end_at 猜成 active；不能因仅有 duration 猜测结束于 now。
        昨天吃药了等过去只有日期的事实不支持直接保存：request_clarification(reason=RECORD_MISSING_TIME, known_date=昨天的日期)，保留日期并询问具体时刻，禁止补 00:00、12:00 或 now。
        明天三点打电话、明天下午写论文等责任与安排均合理的输入：request_clarification(reason=OBJECT_TYPE_AMBIGUOUS)，给出 Task 与 Event 的实际备选字段。
        明天3到5点写论文 → create_event(INTERVAL)。周五前写完论文 → create_task(due_date=周五)。刚刚吃药了 → create_record(POINT, occurred_at=刚刚的实际时刻)。
        “今天完成报销”若是待办命令 → create_task(due_date=今天或planned_date=今天，按表达选择)；“报销已经完成了”若明确指已有任务 → complete_task，找不到任务则提示，绝不新建 DONE Task。
        “下午三点组会”若上下文已有明确日期与安排 → create_event(POINT)；缺日期或语义不清 → request_clarification，不能补日期。
        “下午三点提醒我组会”按任务意图 → create_task(planned_at=已明确的日期和15:00)；若日期未明确，先询问日期。一次不得同时建 Event。明确说“提前半小时提醒我”时填写 reminder_offset_minutes=30，覆盖全局默认；不得声称通知已经送达。
        一次最多处理一个主要对象。即使用户明确要求同时建 Task 和 Event，也调用 request_clarification(reason=MULTIPLE_PRIMARY_OBJECTS)，请用户先选一项。本版本不提供批量执行。
        明确要求完成已有 Task 才用 complete_task；找不到目标不能新建 DONE Task。停止 Record 只用 stop_record；明确要求同时完成关联 Task 才用 stop_record_and_complete_task。
        已发生 Record 的补录只有在用户明确要求同时完成一个已有 Task 时才用 create_record_and_complete_task，必须填写 task_query 和真实发生时间。普通 create_record 即使关联 Task 也不能完成 Task。复合动作只创建一个 Record，并更新已有 Task；找不到、多个匹配或 Task 已完成时必须澄清，绝不新建 DONE Task 或覆盖完成时间。历史 Record 可关联已完成 Task；现在开始的 active Record 不能关联已完成 Task。
        已发生的睡眠用 create_sleep_record；明确现在开始睡眠用 start_sleep。睡眠始终是 INTERVAL；未来睡眠事实必须澄清，不能按时间改成 Event。Sleep 工具不得携带 category_hint。
        create_record_from_event 只表示从既有 Event 预填并保留来源关系；两边后续修改互不自动同步。POINT 必须有用户明确的 actual_at；INTERVAL 必须有用户明确的 actual_start_at 与 actual_end_at，且结束晚于开始。缺少实际时间就澄清或交由编辑器确认；绝不能把 Event 计划时间当作已发生事实直接保存。
        category_hint 仅为用户已配置的分类与标签规则提供短语，不判断 Task / Event / Record；不学习用户历史。update_* 与 Sleep 工具不得填写 category_hint。
        结合当前设备日期、时间和时区解析相对时间；具体时刻输出带时区偏移的 ISO-8601 datetime。只有日期时输出日期，不编造时刻。数据库 ID 不由你生成；查询目标只给文字。
        title 只保留做什么，地点放入 location。音频输入把听到的原句写入 source_text；source_text 只用于诊断。
        示例（当前时间 2026-09-26T15:00+08:00）：
        “明天三点写论文” → create_task(title=写论文, planned_at=2026-09-27T15:00:00+08:00)
        “明天下午三点组会” → create_event(title=组会, temporal_type=POINT, start_at=2026-09-27T15:00:00+08:00)
        “明天下午三点到五点写论文” → create_event(title=写论文, temporal_type=INTERVAL, start_at=2026-09-27T15:00:00+08:00, end_at=2026-09-27T17:00:00+08:00)
        “早上九点半买早饭了” → create_record(title=买早饭, temporal_type=POINT, occurred_at=2026-09-26T09:30:00+08:00)
        “今天早上九到十二点找蜡块” → create_record(title=找蜡块, temporal_type=INTERVAL, start_at=2026-09-26T09:00:00+08:00, end_at=2026-09-26T12:00:00+08:00)
        “九点到十二点找蜡块，并完成找蜡块任务” → create_record_and_complete_task(title=找蜡块, temporal_type=INTERVAL, start_at=2026-09-26T09:00:00+08:00, end_at=2026-09-26T12:00:00+08:00, task_query=找蜡块)
        “开始计时找蜡块” → start_record(title=找蜡块)
    """.trimIndent()

    fun sessionUpdate(context: VoiceAgentContext, audioInput: Boolean = false): JsonObject {
        val session = obj(
            "modalities" to array("text"),
            "instructions" to instructions(context),
            "turn_detection" to null,
            "enable_search" to false,
            "tools" to tools(),
            "temperature" to 0.2,
            "max_tokens" to 2048,
        )
        if (audioInput) session.add("audio", obj("input" to obj("format" to obj(
            "type" to "pcm", "sample_rate" to 16000))))
        return obj("type" to "session.update", "session" to session)
    }

    fun userText(text: String): JsonObject = obj(
        "type" to "conversation.item.create",
        "item" to obj(
            "type" to "message",
            "role" to "user",
            "content" to array(obj("type" to "input_text", "text" to text)),
        ),
    )

    fun audioAppend(pcm: ByteArray): JsonObject = obj(
        "type" to "input_audio_buffer.append",
        "audio" to Base64.getEncoder().encodeToString(pcm),
    )
    fun audioCommit(): JsonObject = obj("type" to "input_audio_buffer.commit")
    fun audioClear(): JsonObject = obj("type" to "input_audio_buffer.clear")

    fun responseCreate(): JsonObject = obj("type" to "response.create")

    fun functionOutput(callId: String, output: String): JsonObject = obj(
        "type" to "conversation.item.create",
        "item" to obj("type" to "function_call_output", "call_id" to callId, "output" to output),
    )

    fun tools(): JsonArray = array(
        tool("create_task", "创建责任或待办；计划点、截止与预计投入彼此独立，没有结束时刻", listOf("title"),
            "title" to string(), "note" to string(), "planned_date" to date(), "planned_at" to iso(),
            "due_date" to date(), "due_at" to iso(), "estimated_duration_minutes" to integer(),
            "location" to string(), "parent_query" to string(), "category_hint" to categoryHint(), "reminder_offset_minutes" to integer()),
        tool("update_task", "按文字查询并更新任务；未出现字段保持不变", listOf("target_query"),
            "target_query" to string(), "title" to string(), "note" to string(), "planned_date" to date(),
            "planned_at" to iso(), "due_date" to date(), "due_at" to iso(),
            "estimated_duration_minutes" to integer(), "location" to string(), "parent_query" to string(),
            "clear_fields" to enumArray("start", "due", "estimate", "note", "location", "parent")),
        tool("complete_task", "仅用于用户明确要求完成已有任务；completed_at 是任务完成时刻", listOf("target_query"),
            "target_query" to string(), "completed_at" to iso()),
        tool("reopen_task", "重新打开一个已完成任务", listOf("target_query"), "target_query" to string()),
        tool("create_event", "创建安排；DATE 仅日期，POINT 仅开始时刻，INTERVAL 必须有起止时刻", listOf("title", "temporal_type"),
            "title" to string(), "temporal_type" to enumString("DATE", "POINT", "INTERVAL"), "date" to date(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "category_hint" to categoryHint(), "reminder_offset_minutes" to integer()),
        tool("update_event", "按标题和原时间查询并更新日程", listOf("target_query"),
            "target_query" to string(), "target_date" to date(), "target_start_at" to iso(),
            "title" to string(), "date" to date(), "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "clear_fields" to enumArray("start", "end", "location", "note")),
        tool("create_record", "创建已发生事实；POINT 只用 occurred_at，INTERVAL 必须用 start_at 和 end_at", listOf("title", "temporal_type"),
            "title" to string(), "temporal_type" to enumString("POINT", "INTERVAL"), "occurred_at" to iso(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "task_query" to string(), "category_hint" to categoryHint()),
        tool("create_record_and_complete_task", "明确补录一个已发生 Record 并完成指定已有任务；POINT 使用 occurred_at，INTERVAL 使用实际 start_at/end_at", listOf("title", "temporal_type", "task_query"),
            "title" to string(), "temporal_type" to enumString("POINT", "INTERVAL"), "occurred_at" to iso(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "task_query" to string(), "category_hint" to categoryHint()),
        tool("create_sleep_record", "补记已经发生的睡眠；缺少精确边界时不得编造时间", listOf("sleep_type"),
            "sleep_type" to enumString("nap", "night_sleep"), "title" to string(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string()),
        tool("update_record", "按文字查询并更新记录；temporal_type 仅在明确改变形态时提供", listOf("target_query"),
            "target_query" to string(), "target_start_at" to iso(), "title" to string(),
            "temporal_type" to enumString("POINT", "INTERVAL"), "occurred_at" to iso(), "start_at" to iso(), "end_at" to iso(),
            "location" to string(), "note" to string(),
            "task_query" to string(), "clear_fields" to enumArray("end", "location", "note", "task")),
        tool("start_record", "仅在用户明确说开始记录或开始计时时立即开始真实记录", listOf("title"),
            "title" to string(), "task_query" to string(), "location" to string(), "note" to string(),
            "category_hint" to categoryHint()),
        tool("start_sleep", "仅在用户明确说现在开始睡眠时启动睡眠记录", listOf("sleep_type"),
            "sleep_type" to enumString("nap", "night_sleep"), "title" to string(), "start_at" to iso()),
        tool("stop_record", "只停止当前或指定的进行中记录，不完成任务", emptyList(), "target_query" to string()),
        tool("stop_record_and_complete_task", "仅在用户明确要求两个操作时停止记录并完成其关联任务", emptyList(), "target_query" to string()),
        tool("create_record_from_event", "从已有日程预填记录并保留来源；仅明确实际时间可直接保存，缺少时交编辑器确认，绝不借用计划时间直接保存", listOf("event_query", "temporal_type"),
            "event_query" to string(), "event_date" to date(), "temporal_type" to enumString("POINT", "INTERVAL"),
            "actual_at" to iso(), "actual_start_at" to iso(), "actual_end_at" to iso()),
        tool("request_clarification", "缺少关键时间、语义冲突或多对象请求时询问用户，不创建数据", listOf("reason"),
            "reason" to enumString("OBJECT_TYPE_AMBIGUOUS", "RECORD_MISSING_TIME", "INTERVAL_MISSING_BOUNDARY", "TARGET_NOT_UNIQUE", "MULTIPLE_PRIMARY_OBJECTS", "TIME_SEMANTIC_CONFLICT"),
            "question" to string(), "known_date" to date(), "record_title" to string(),
            "task_option" to clarificationOption(), "event_option" to clarificationOption()),
    )

    private fun tool(name: String, description: String, required: List<String>,
        vararg properties: Pair<String, JsonObject>): JsonObject {
        val props = JsonObject().apply {
            properties.forEach { (key, value) -> add(key, value) }
            add("source_text", string())
        }
        return obj("type" to "function", "function" to obj(
            "name" to name, "description" to description,
            "parameters" to obj("type" to "object", "properties" to props,
                "required" to JsonArray().apply { required.forEach { add(it) } }, "additionalProperties" to false),
        ))
    }

    private fun string() = obj("type" to "string")
    private fun categoryHint() = obj(
        "type" to "string",
        "maxLength" to MAX_CATEGORY_HINT_LENGTH,
        "description" to "分类词或短语，不是分类 ID",
    )
    private fun boolean() = obj("type" to "boolean")
    private fun integer() = obj("type" to "integer", "minimum" to 1)
    private fun date() = obj("type" to "string", "description" to "YYYY-MM-DD")
    private fun iso() = obj("type" to "string", "description" to "ISO-8601 datetime with UTC offset")
    private fun enumString(vararg values: String) = obj("type" to "string", "enum" to array(*values))
    private fun enumArray(vararg values: String) = obj("type" to "array",
        "items" to obj("type" to "string", "enum" to array(*values)))
    private fun clarificationOption() = obj("type" to "object", "properties" to obj(
        "title" to string(), "planned_date" to date(), "planned_at" to iso(), "due_date" to date(), "due_at" to iso(),
        "temporal_type" to enumString("DATE", "POINT", "INTERVAL"), "date" to date(), "start_at" to iso(), "end_at" to iso(),
    ), "additionalProperties" to false)

    fun obj(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
        values.forEach { (key, value) -> when (value) {
            null -> add(key, null)
            is String -> addProperty(key, value)
            is Number -> addProperty(key, value)
            is Boolean -> addProperty(key, value)
            is JsonObject -> add(key, value)
            is JsonArray -> add(key, value)
            else -> error("Unsupported JSON value")
        } }
    }

    fun array(vararg values: Any): JsonArray = JsonArray().apply {
        values.forEach { value -> when (value) {
            is String -> add(value)
            is JsonObject -> add(value)
            else -> error("Unsupported JSON array value")
        } }
    }
}
