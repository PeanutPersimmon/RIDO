package io.github.peanutpersimmon.rido.domain.voice

/** Conservative lexical evidence for checking a model-selected creation tool. */
object VoiceSemanticCue {
    private val sleepWords = listOf("睡觉", "睡了", "睡眠", "午睡", "小睡", "小憩", "眯了", "入睡", "起床")
    private val factWords = listOf("刚刚", "刚才", "已经", "做了", "去了", "吃了", "看了", "回来了", "结束了", "开会了", "洗漱了", "买东西了")
    private val arrangementWords = listOf("安排了", "约好了", "预约了", "计划了", "定好了", "明天", "后天", "下周")

    fun isSleep(text: String): Boolean = sleepWords.any(text::contains)
    fun isReportedFact(text: String): Boolean = factWords.any(text::contains) &&
        arrangementWords.none(text::contains)
    fun isExplicitNap(text: String): Boolean = listOf("午睡", "小睡", "小憩", "眯了").any(text::contains)
    fun isExplicitNightSleep(text: String): Boolean = listOf("昨晚", "夜间", "入睡", "起床").any(text::contains)
}
