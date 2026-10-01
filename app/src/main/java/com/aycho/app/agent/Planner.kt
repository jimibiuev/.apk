package com.aycho.app.agent

/**
 * 规划段 - 负责制定计划与进度管理
 */
class Planner {

    /**
     * 生成规划 Prompt
     */
    fun getPrompt(blackboard: Blackboard): String {
        return if (blackboard.plan.isEmpty()) {
            getInitialPlanPrompt(blackboard)
        } else {
            getUpdatePlanPrompt(blackboard)
        }
    }

    private fun getInitialPlanPrompt(blackboard: Blackboard): String = buildString {
        append("You are an agent who can operate an Android phone on behalf of a user. ")
        append("Your goal is to track progress and devise high-level plans to achieve the user's requests.\n\n")

        append("### User Request ###\n")
        append("${blackboard.instruction}\n\n")

        append("---\n")
        append("Make a high-level plan to achieve the user's request. ")
        append("If the request is complex, break it down into subgoals. ")
        append("The screenshot displays the starting state of the phone.\n\n")

        append("IMPORTANT: For requests that explicitly require an answer, ")
        append("always add 'perform the `answer` action' as the last step to the plan!\n\n")

        // Skill 上下文
        if (blackboard.skillContext.isNotEmpty()) {
            append("### Available Skills ###\n")
            append("${blackboard.skillContext}\n\n")
        }

        append("### Guidelines ###\n")
        append("1. Do NOT press the Home button to go back to the home screen, and never go to the launcher to look for app icons. To switch to a different app, ALWAYS use the `open_app` action - it launches any installed app directly.\n")
        append("2. `Back` is DIFFERENT from `Home`: pressing Back only goes one step back within the current app and is safe plus encouraged whenever the current page is not the one you need (wrong page, popup / sub-page blocking you, or the same click keeps failing). Plan an explicit recovery step - press Back once, then re-examine the screen - instead of repeating the same failed click.\n")
        append("3. Use search to quickly find a file or entry with a specific name.\n")
        append("4. If there are relevant skills listed above, follow their suggested steps for better efficiency.\n")
        if (blackboard.additionalKnowledge.isNotEmpty()) {
            append("5. ${blackboard.additionalKnowledge}\n")
        }
        append("\n")

        append("Provide your output in the following format:\n\n")
        append("### Thought ###\n")
        append("A detailed explanation of your rationale for the plan.\n\n")
        append("### Plan ###\n")
        append("1. first subgoal\n")
        append("2. second subgoal\n")
        append("...\n")
    }

    private fun getUpdatePlanPrompt(blackboard: Blackboard): String = buildString {
        append("You are an agent who can operate an Android phone on behalf of a user. ")
        append("Your goal is to track progress and update plans.\n\n")

        append("### User Request ###\n")
        append("${blackboard.instruction}\n\n")

        if (blackboard.completedPlan.isNotEmpty() && blackboard.completedPlan != "No completed subgoal.") {
            append("### Historical Operations ###\n")
            append("${blackboard.completedPlan}\n\n")
        }

        append("### Current Plan ###\n")
        append("${blackboard.plan}\n\n")

        append("### Last Action ###\n")
        append("${blackboard.lastAction}\n\n")

        append("### Last Action Description ###\n")
        append("${blackboard.lastSummary}\n\n")

        // 最近的动作结果
        if (blackboard.actionOutcomes.isNotEmpty()) {
            val recentOutcomes = blackboard.actionOutcomes.takeLast(3)
            val failCount = recentOutcomes.count { it in listOf("B", "C") }
            if (failCount > 0) {
                append("### Recent Action Results ###\n")
                append("Last ${recentOutcomes.size} actions: ${recentOutcomes.joinToString(", ")} ")
                append("(A=success, B=partial, C=failed)\n")
                append("Failed attempts: $failCount\n\n")
            }
        }

        if (blackboard.importantNotes.isNotEmpty()) {
            append("### Important Notes ###\n")
            append("${blackboard.importantNotes}\n\n")
        }

        // 错误升级
        if (blackboard.errorFlagPlan) {
            append("### STUCK - Multiple Failed Attempts! ###\n")
            append("You have encountered several consecutive failed attempts:\n")
            val k = blackboard.errToManagerThresh
            val recentActions = blackboard.actionHistory.takeLast(k)
            val recentSummaries = blackboard.summaryHistory.takeLast(k)
            val recentErrors = blackboard.errorDescriptions.takeLast(k)

            recentActions.forEachIndexed { i, act ->
                append("- Action: $act | Description: ${recentSummaries.getOrNull(i)} | Failed: ${recentErrors.getOrNull(i)}\n")
            }
            append("\nIMPORTANT: DO NOT mark as \"Finished\" when there are failed attempts! ")
            append("Try a different approach:\n")
            append("- Wait for page to load (the UI might be loading)\n")
            append("- Try clicking at different coordinates\n")
            append("- Scroll to find the correct button\n")
            append("- Press Back and try again\n\n")
        }

        append("---\n")
        append("Assess the current status.\n\n")

        append("### SECURITY: Sensitive Pages - MUST STOP ###\n")
        append("ONLY output \"STOP_SENSITIVE\" when the screen is ACTIVELY REQUESTING one of these:\n")
        append("- A payment confirmation button that will charge money (确认支付, 立即付款, 确认付款)\n")
        append("- A password input field that is focused and waiting for input\n")
        append("- Face ID or fingerprint verification dialog\n")
        append("DO NOT stop for: price displays, payment method selection, cart pages, or general app navigation.\n\n")

        append("### CRITICAL: When to mark \"Finished\" ###\n")
        append("ONLY mark as \"Finished\" when ALL of these are true:\n")
        append("1. The user's request has been FULLY completed (not partially)\n")
        append("2. You can SEE the final success state in the screenshot (e.g., order confirmed, message sent, setting changed)\n")
        append("3. The last action was SUCCESSFUL (outcome A), not failed (outcome C)\n")
        append("4. There are NO recent consecutive failures\n\n")
        append("If any action failed or the task is incomplete, DO NOT say \"Finished\". Instead, update the plan with a new approach.\n\n")

        append("Provide your output in the following format:\n\n")
        append("### Thought ###\n")
        append("Your rationale for the updated plan.\n\n")
        append("### Historical Operations ###\n")
        append("Add newly completed subgoals on top of existing ones.\n\n")
        append("### Plan ###\n")
        append("Updated plan or \"Finished\" if truly done.\n")
    }

    /**
     * 解析规划响应
     */
    fun parseResponse(response: String): PlanResult {
        val thought = response
            .substringAfter("### Thought", "")
            .substringBefore("### Historical Operations")
            .substringBefore("### Plan")
            .replace("###", "")
            .trim()

        val completedSubgoal = if (response.contains("### Historical Operations")) {
            response
                .substringAfter("### Historical Operations")
                .substringBefore("### Plan")
                .replace("###", "")
                .trim()
        } else {
            "No completed subgoal."
        }

        val plan = response
            .substringAfter("### Plan")
            .replace("###", "")
            .trim()

        return PlanResult(thought, completedSubgoal, plan)
    }
}

data class PlanResult(
    val thought: String,
    val completedSubgoal: String,
    val plan: String
)
