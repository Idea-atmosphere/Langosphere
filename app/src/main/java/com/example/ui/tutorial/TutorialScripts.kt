package com.example.ui.tutorial

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import com.example.ui.theme.AppTab
import com.example.ui.theme.icon

/**
 * One step of a tutorial: the control it points at (a [tutorialTarget] key,
 * or null for a step that is only a card in the middle of the screen) and
 * the texts, which live in strings_tutorial.xml in both languages.
 */
data class TutorialStep(
    val id: String,
    val target: String?,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

/**
 * The tutorials that can be started on their own from Settings ▸ Interactive
 * tutorial: the basics of the screen, and one per tab. «Full tour» plays the
 * basics followed by every tab in the user's own tab order.
 */
enum class TutorialTopic(
    val tab: AppTab?,
    @StringRes val title: Int,
    @StringRes val description: Int,
) {
    BASICS(null, R.string.tutorial_topic_basics, R.string.tutorial_topic_basics_desc),
    READER(AppTab.READER, R.string.tutorial_topic_reader, R.string.tutorial_topic_reader_desc),
    VIDEO(AppTab.VIDEO, R.string.tutorial_topic_video, R.string.tutorial_topic_video_desc),
    ONLINE(AppTab.ONLINE, R.string.tutorial_topic_online, R.string.tutorial_topic_online_desc),
    AGENT(AppTab.AGENT, R.string.tutorial_topic_agent, R.string.tutorial_topic_agent_desc),
    LEITNER(AppTab.LEITNER, R.string.tutorial_topic_leitner, R.string.tutorial_topic_leitner_desc);

    val icon: ImageVector get() = tab?.icon() ?: Icons.Filled.TouchApp

    val steps: List<TutorialStep> get() = TutorialScripts.stepsFor(this)

    companion object {
        fun forTab(tab: AppTab): TutorialTopic = entries.first { it.tab == tab }

        /** The basics, then every tab in the order the user arranged them. */
        fun fullTour(tabOrder: List<AppTab>): List<TutorialTopic> =
            listOf(BASICS) + tabOrder.map { forTab(it) }
    }
}

/** The scripts. Step ids double as target keys (see the `tutorialTarget` calls in the screens). */
object TutorialScripts {

    private fun step(id: String, @StringRes title: Int, @StringRes body: Int, target: String? = id) =
        TutorialStep(id = id, target = target, title = title, body = body)

    private val basics = listOf(
        step("basics.welcome", R.string.tut_basics_1_title, R.string.tut_basics_1_body, target = null),
        step("header.settings", R.string.tut_basics_2_title, R.string.tut_basics_2_body),
        step("nav.bar", R.string.tut_basics_3_title, R.string.tut_basics_3_body),
        step("basics.language", R.string.tut_basics_4_title, R.string.tut_basics_4_body, target = null),
        step("basics.popups", R.string.tut_basics_5_title, R.string.tut_basics_5_body, target = null),
    )

    private val reader = listOf(
        step("reader.dictionary", R.string.tut_reader_1_title, R.string.tut_reader_1_body),
        step("reader.openFile", R.string.tut_reader_2_title, R.string.tut_reader_2_body),
        step("reader.actions", R.string.tut_reader_3_title, R.string.tut_reader_3_body),
        step("reader.toolbar", R.string.tut_reader_4_title, R.string.tut_reader_4_body),
        step("reader.fullscreen", R.string.tut_reader_5_title, R.string.tut_reader_5_body),
        step("reader.paper", R.string.tut_reader_6_title, R.string.tut_reader_6_body),
    )

    private val video = listOf(
        step("video.importHeader", R.string.tut_video_1_title, R.string.tut_video_1_body),
        step("video.mediaTile", R.string.tut_video_2_title, R.string.tut_video_2_body),
        step("video.subEn", R.string.tut_video_3_title, R.string.tut_video_3_body),
        step("video.subFa", R.string.tut_video_4_title, R.string.tut_video_4_body),
        step("video.subJson", R.string.tut_video_5_title, R.string.tut_video_5_body),
        step("video.stage", R.string.tut_video_6_title, R.string.tut_video_6_body),
        step("video.splitHandle", R.string.tut_video_7_title, R.string.tut_video_7_body),
        step("video.transcript", R.string.tut_video_8_title, R.string.tut_video_8_body),
        step("video.toolCluster", R.string.tut_video_9_title, R.string.tut_video_9_body),
    )

    private val online = listOf(
        step("online.search", R.string.tut_online_1_title, R.string.tut_online_1_body),
        step("online.hubTabs", R.string.tut_online_2_title, R.string.tut_online_2_body),
        step("online.channelTray", R.string.tut_online_3_title, R.string.tut_online_3_body),
        step("online.instances", R.string.tut_online_4_title, R.string.tut_online_4_body),
        step("online.watchCache", R.string.tut_online_5_title, R.string.tut_online_5_body),
        step("online.stage", R.string.tut_online_6_title, R.string.tut_online_6_body),
        step("online.drawer", R.string.tut_online_7_title, R.string.tut_online_7_body),
        step("online.toolCluster", R.string.tut_online_8_title, R.string.tut_online_8_body),
    )

    private val agent = listOf(
        step("agent.settings", R.string.tut_agent_1_title, R.string.tut_agent_1_body),
        step("agent.settingsCard", R.string.tut_agent_2_title, R.string.tut_agent_2_body),
        step("agent.memory", R.string.tut_agent_3_title, R.string.tut_agent_3_body),
        step("agent.memoryPanel", R.string.tut_agent_4_title, R.string.tut_agent_4_body),
        step("agent.history", R.string.tut_agent_5_title, R.string.tut_agent_5_body),
        step("agent.chat", R.string.tut_agent_6_title, R.string.tut_agent_6_body),
        step("agent.composer", R.string.tut_agent_7_title, R.string.tut_agent_7_body),
    )

    private val leitner = listOf(
        step("leitner.toolLeitner", R.string.tut_leitner_1_title, R.string.tut_leitner_1_body),
        step("leitner.toolQuiz", R.string.tut_leitner_2_title, R.string.tut_leitner_2_body),
        step("leitner.header", R.string.tut_leitner_3_title, R.string.tut_leitner_3_body),
        step("leitner.export", R.string.tut_leitner_4_title, R.string.tut_leitner_4_body),
        step("leitner.pills", R.string.tut_leitner_5_title, R.string.tut_leitner_5_body),
        step("leitner.deck", R.string.tut_leitner_6_title, R.string.tut_leitner_6_body),
    )

    fun stepsFor(topic: TutorialTopic): List<TutorialStep> = when (topic) {
        TutorialTopic.BASICS -> basics
        TutorialTopic.READER -> reader
        TutorialTopic.VIDEO -> video
        TutorialTopic.ONLINE -> online
        TutorialTopic.AGENT -> agent
        TutorialTopic.LEITNER -> leitner
    }
}
