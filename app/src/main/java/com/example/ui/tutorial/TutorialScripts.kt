package com.example.ui.tutorial

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import com.example.ui.theme.AppTab
import com.example.ui.theme.icon

/**
 * One step of a tutorial: the control it points at (a [tutorialTarget] key,
 * or null for a step that is only a card in the middle of the screen) and
 * the texts, which live in strings_tutorial.xml in both languages.
 *
 * The spotlighted control stays live — touches inside the hole reach it —
 * so a step can invite an action: [actionHint] is the «tap it to …» line on
 * the card, and [advanceWhen] names a target whose appearance (a control
 * that was not on the screen when the step began, e.g. the online player
 * once a clip is opened) moves the tour on by itself. [skipIfMet] skips the
 * step outright when that control is already there.
 *
 * [host] says which window shows the step: the main screen, or the Prompts
 * dialog (Settings ▸ Prompts), which carries its own overlay because a
 * dialog window floats above everything drawn in the main one.
 */
data class TutorialStep(
    val id: String,
    val target: String?,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val actionHint: Int? = null,
    val advanceWhen: String? = null,
    val skipIfMet: Boolean = false,
    val host: TutorialHost = TutorialHost.MAIN,
)

/** The window a step is shown in; each window has one [TutorialOverlay] that renders only its own steps. */
enum class TutorialHost { MAIN, PROMPTS }

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
    LEITNER(AppTab.LEITNER, R.string.tutorial_topic_leitner, R.string.tutorial_topic_leitner_desc),

    /**
     * The JSON learning package chapter on its own: the Prompts window, part
     * by part. It is also played in the middle of the Video tutorial (right
     * after the JSON slot), so it is listed in the picker but not added to
     * the full tour a second time. Its tab is Video so the slot that takes
     * the package is on the screen when the window closes.
     */
    PROMPTS(AppTab.VIDEO, R.string.tutorial_topic_prompts, R.string.tutorial_topic_prompts_desc);

    val icon: ImageVector get() = when (this) {
        PROMPTS -> Icons.Filled.DataObject
        else -> tab?.icon() ?: Icons.Filled.TouchApp
    }

    val steps: List<TutorialStep> get() = TutorialScripts.stepsFor(this)

    companion object {
        /** The tab's own tutorial (the first topic declared for that tab). */
        fun forTab(tab: AppTab): TutorialTopic = entries.first { it.tab == tab }

        /** The basics, then every tab in the order the user arranged them. */
        fun fullTour(tabOrder: List<AppTab>): List<TutorialTopic> =
            listOf(BASICS) + tabOrder.map { forTab(it) }

        /** What the picker lists one by one: the tour's topics plus the stand-alone chapters. */
        fun pickerList(tabOrder: List<AppTab>): List<TutorialTopic> = fullTour(tabOrder) + PROMPTS
    }
}

/** The scripts. Step ids double as target keys (see the `tutorialTarget` calls in the screens). */
object TutorialScripts {

    private fun step(
        id: String,
        @StringRes title: Int,
        @StringRes body: Int,
        target: String? = id,
        @StringRes action: Int? = null,
        advanceWhen: String? = null,
        skipIfMet: Boolean = false,
        host: TutorialHost = TutorialHost.MAIN,
    ) = TutorialStep(
        id = id,
        target = target,
        title = title,
        body = body,
        actionHint = action,
        advanceWhen = advanceWhen,
        skipIfMet = skipIfMet,
        host = host,
    )

    /** A step inside the Prompts window (Settings ▸ Prompts). */
    private fun promptsStep(id: String, @StringRes title: Int, @StringRes body: Int, @StringRes action: Int? = null) =
        step(id, title, body, action = action, host = TutorialHost.PROMPTS)

    private val basics = listOf(
        step("basics.welcome", R.string.tut_basics_1_title, R.string.tut_basics_1_body, target = null),
        step("header.settings", R.string.tut_basics_2_title, R.string.tut_basics_2_body, action = R.string.tut_basics_2_action),
        step("nav.bar", R.string.tut_basics_3_title, R.string.tut_basics_3_body, action = R.string.tut_basics_3_action),
        step("basics.language", R.string.tut_basics_4_title, R.string.tut_basics_4_body, target = null),
        step("basics.popups", R.string.tut_basics_5_title, R.string.tut_basics_5_body, target = null),
    )

    private val reader = listOf(
        step("reader.dictionary", R.string.tut_reader_1_title, R.string.tut_reader_1_body, action = R.string.tut_reader_1_action),
        // Opening a file is a real action: the system picker opens, and the
        // tour moves on by itself once the reading toolbar (= a document)
        // appears. With a document already open the step just explains the
        // title pill.
        step("reader.openFile", R.string.tut_reader_2_title, R.string.tut_reader_2_body, action = R.string.tut_reader_2_action, advanceWhen = "reader.toolbar"),
        step("reader.actions", R.string.tut_reader_3_title, R.string.tut_reader_3_body, action = R.string.tut_reader_3_action),
        step("reader.toolbar", R.string.tut_reader_4_title, R.string.tut_reader_4_body, action = R.string.tut_reader_4_action),
        step("reader.fullscreen", R.string.tut_reader_5_title, R.string.tut_reader_5_body),
        step("reader.paper", R.string.tut_reader_6_title, R.string.tut_reader_6_body, action = R.string.tut_reader_6_action),
    )

    /**
     * The Prompts window (Settings ▸ Prompts), where the JSON learning package
     * is made: every section in reading order, then the generator's parts,
     * then the way back to the player. Shown by the overlay inside the
     * dialog; the main screen opens the window when the chapter starts and
     * closes it when the chapter is left (see MainScreen).
     */
    private val prompts = listOf(
        promptsStep("prompts.family", R.string.tut_prompts_1_title, R.string.tut_prompts_1_body, R.string.tut_prompts_1_action),
        promptsStep("prompts.level", R.string.tut_prompts_2_title, R.string.tut_prompts_2_body, R.string.tut_prompts_2_action),
        promptsStep("prompts.dictionary", R.string.tut_prompts_3_title, R.string.tut_prompts_3_body, R.string.tut_prompts_3_action),
        promptsStep("prompts.guide", R.string.tut_prompts_4_title, R.string.tut_prompts_4_body),
        promptsStep("prompts.modes", R.string.tut_prompts_5_title, R.string.tut_prompts_5_body, R.string.tut_prompts_5_action),
        promptsStep("prompts.languagePair", R.string.tut_prompts_6_title, R.string.tut_prompts_6_body, R.string.tut_prompts_6_action),
        promptsStep("prompts.pills", R.string.tut_prompts_7_title, R.string.tut_prompts_7_body),
        promptsStep("prompts.promptLevel", R.string.tut_prompts_8_title, R.string.tut_prompts_8_body, R.string.tut_prompts_8_action),
        promptsStep("prompts.chunk", R.string.tut_prompts_9_title, R.string.tut_prompts_9_body, R.string.tut_prompts_9_action),
        promptsStep("prompts.copy", R.string.tut_prompts_10_title, R.string.tut_prompts_10_body, R.string.tut_prompts_10_action),
        promptsStep("prompts.preview", R.string.tut_prompts_11_title, R.string.tut_prompts_11_body, R.string.tut_prompts_11_action),
        promptsStep("prompts.close", R.string.tut_prompts_12_title, R.string.tut_prompts_12_body, R.string.tut_prompts_12_action),
    )

    private val video = listOf(
        step("video.importHeader", R.string.tut_video_1_title, R.string.tut_video_1_body, action = R.string.tut_video_1_action),
        step("video.mediaTile", R.string.tut_video_2_title, R.string.tut_video_2_body, action = R.string.tut_video_2_action),
        step("video.subEn", R.string.tut_video_3_title, R.string.tut_video_3_body, action = R.string.tut_video_3_action),
        step("video.subFa", R.string.tut_video_4_title, R.string.tut_video_4_body),
        step("video.subJson", R.string.tut_video_5_title, R.string.tut_video_5_body, action = R.string.tut_video_5_action),
    ) + prompts + listOf(
        step("video.stage", R.string.tut_video_6_title, R.string.tut_video_6_body, action = R.string.tut_video_6_action),
        step("video.splitHandle", R.string.tut_video_7_title, R.string.tut_video_7_body, action = R.string.tut_video_7_action),
        step("video.transcript", R.string.tut_video_8_title, R.string.tut_video_8_body, action = R.string.tut_video_8_action),
        step("video.toolCluster", R.string.tut_video_9_title, R.string.tut_video_9_body, action = R.string.tut_video_9_action),
    )

    private val online = listOf(
        step("online.search", R.string.tut_online_1_title, R.string.tut_online_1_body, action = R.string.tut_online_1_action),
        step("online.hubTabs", R.string.tut_online_2_title, R.string.tut_online_2_body, action = R.string.tut_online_2_action),
        step("online.channelTray", R.string.tut_online_3_title, R.string.tut_online_3_body, action = R.string.tut_online_3_action),
        step("online.instances", R.string.tut_online_4_title, R.string.tut_online_4_body, action = R.string.tut_online_4_action),
        step("online.watchCache", R.string.tut_online_5_title, R.string.tut_online_5_body, action = R.string.tut_online_5_action),
        // The hands-on step of this tab: tap a card, the clip opens, and the
        // player steps that follow point at the real player. Skipped when a
        // clip is already playing.
        step("online.videoGrid", R.string.tut_online_6_title, R.string.tut_online_6_body, action = R.string.tut_online_6_action, advanceWhen = "online.stage", skipIfMet = true),
        step("online.stage", R.string.tut_online_7_title, R.string.tut_online_7_body, action = R.string.tut_online_7_action),
        step("online.drawer", R.string.tut_online_8_title, R.string.tut_online_8_body, action = R.string.tut_online_8_action),
        step("online.toolCluster", R.string.tut_online_9_title, R.string.tut_online_9_body, action = R.string.tut_online_9_action),
    )

    private val agent = listOf(
        step("agent.settings", R.string.tut_agent_1_title, R.string.tut_agent_1_body, action = R.string.tut_agent_1_action, advanceWhen = "agent.settingsCard"),
        step("agent.settingsCard", R.string.tut_agent_2_title, R.string.tut_agent_2_body),
        step("agent.memory", R.string.tut_agent_3_title, R.string.tut_agent_3_body, action = R.string.tut_agent_3_action, advanceWhen = "agent.memoryPanel"),
        step("agent.memoryPanel", R.string.tut_agent_4_title, R.string.tut_agent_4_body, action = R.string.tut_agent_4_action),
        step("agent.history", R.string.tut_agent_5_title, R.string.tut_agent_5_body, action = R.string.tut_agent_5_action),
        step("agent.chat", R.string.tut_agent_6_title, R.string.tut_agent_6_body),
        step("agent.composer", R.string.tut_agent_7_title, R.string.tut_agent_7_body, action = R.string.tut_agent_7_action),
    )

    private val leitner = listOf(
        step("leitner.toolLeitner", R.string.tut_leitner_1_title, R.string.tut_leitner_1_body),
        step("leitner.toolQuiz", R.string.tut_leitner_2_title, R.string.tut_leitner_2_body, action = R.string.tut_leitner_2_action),
        step("leitner.header", R.string.tut_leitner_3_title, R.string.tut_leitner_3_body),
        step("leitner.export", R.string.tut_leitner_4_title, R.string.tut_leitner_4_body),
        step("leitner.pills", R.string.tut_leitner_5_title, R.string.tut_leitner_5_body, action = R.string.tut_leitner_5_action),
        step("leitner.deck", R.string.tut_leitner_6_title, R.string.tut_leitner_6_body, action = R.string.tut_leitner_6_action),
    )

    fun stepsFor(topic: TutorialTopic): List<TutorialStep> = when (topic) {
        TutorialTopic.BASICS -> basics
        TutorialTopic.READER -> reader
        TutorialTopic.VIDEO -> video
        TutorialTopic.ONLINE -> online
        TutorialTopic.AGENT -> agent
        TutorialTopic.LEITNER -> leitner
        TutorialTopic.PROMPTS -> prompts
    }
}
