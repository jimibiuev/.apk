package com.aycho.app.agent

/**
 * 执行段 - 决定具体执行什么动作
 */
class Actuator {

    companion object {
        val GUIDELINES = """
General:
- For any pop-up window, close it (e.g., by clicking 'Don't Allow' or 'Accept') before proceeding.
- For requests that are questions, remember to use the `answer` action to reply before finish!
- If the desired state is already achieved, you can just complete the task.

Action Related:
- Use `open_app` to open an app, do not use the app drawer.
- Back vs Home - these are DIFFERENT, never confuse them:
  * `Back` (system_button "Back") only goes ONE step back inside the current app. It is SAFE and SHOULD be used whenever the current page is not the page you need: a wrong page was opened, a sub-page / popup / full-screen ad is in the way, or the same click keeps failing. After pressing Back, look at the new screen and re-decide.
  * `Home` returns to the launcher. Do NOT press Home to find apps; use `open_app` to switch apps. Only press Home if the user explicitly asks to go to the home screen.
  * If your previous 2 attempts to click the same target failed, prefer pressing Back ONCE to return to the previous page and re-examine it, instead of clicking the same wrong spot again.
- Consider using `swipe` to reveal additional content.
- If swiping doesn't change the page, it may have reached the bottom.

Text Related:
- To input text: first click the input box, make sure keyboard is visible, then use `type` action.
- To clear text: long press the backspace button in the keyboard.
        """.trimIndent()
    }

    /**
     * 生成执行 Prompt
     */
    fun getPrompt(blackboard: Blackboard): String = buildString {
        append("You are an agent who can operate an Android phone. ")
        append("Decide the next action based on the current state.\n\n")

        append("### User Request ###\n")
        append("${blackboard.instruction}\n\n")

        append("### Overall Plan ###\n")
        append("${blackboard.plan}\n\n")

        append("### Current Subgoal ###\n")
        val subgoals = blackboard.plan.split(Regex("(?<=\\d)\\. ")).take(3)
        append("${subgoals.joinToString(". ")}\n\n")

        append("### Progress Status ###\n")
        if (blackboard.progressStatus.isNotEmpty()) {
            append("${blackboard.progressStatus}\n\n")
        } else {
            append("No progress yet.\n\n")
        }

        append("### Guidelines ###\n")
        append("$GUIDELINES\n\n")

        append("---\n")
        append("Examine all information and decide on the next action.\n\n")

        append("#### Atomic Actions ####\n")
        append("### Coordinate System (CRITICAL) ###\n")
        append("All coordinates MUST be NORMALIZED integers in [0, 999], relative to the screenshot image you are looking at.\n")
        append("x: 0 = left edge, 999 = right edge. y: 0 = top edge, 999 = bottom edge.\n")
        append("Estimate the position of the target inside the whole screenshot; NEVER output raw pixel values.\n")
        append("Example: center of screen -> [500, 500]; top-right corner -> [950, 60]; bottom-middle button -> [500, 930].\n\n")
        append("- click(coordinate): Click at (x, y). Example: {\"action\": \"click\", \"coordinate\": [500, 500]}\n")
        append("- double_tap(coordinate): Double tap at (x, y) for zoom or like. Example: {\"action\": \"double_tap\", \"coordinate\": [x, y]}\n")
        append("- long_press(coordinate): Long press at (x, y). Example: {\"action\": \"long_press\", \"coordinate\": [x, y]}\n")
        append("- type(text): Type text into activated input box. Example: {\"action\": \"type\", \"text\": \"hello\"}\n")
        append("- swipe(coordinate, coordinate2): Swipe from point1 to point2. Example: {\"action\": \"swipe\", \"coordinate\": [x1, y1], \"coordinate2\": [x2, y2]}\n")
        append("- system_button(button): Press Back/Home/Enter. Example: {\"action\": \"system_button\", \"button\": \"Back\"}\n")
        append("- open_app(text): Open an app by name. ALWAYS use this instead of looking for app icons on screen! Example: {\"action\": \"open_app\", \"text\": \"设置\"}\n")
        if (blackboard.installedApps.isNotEmpty()) {
            append("  Available apps: ${blackboard.installedApps}\n")
        }
        append("- wait(duration): Wait for page loading. Duration in seconds (1-10). Example: {\"action\": \"wait\", \"duration\": 3}\n")
        append("- take_over(message): Request user to manually complete login/captcha/verification. Example: {\"action\": \"take_over\", \"message\": \"请完成登录验证\"}\n")
        append("- answer(text): Answer user's question. Example: {\"action\": \"answer\", \"text\": \"The answer is...\"}\n")
        append("\n")

        append("#### Sensitive Operations ####\n")
        append("For payment, password, or privacy-related actions, add 'message' field to request user confirmation:\n")
        append("Example: {\"action\": \"click\", \"coordinate\": [500, 900], \"message\": \"确认支付 ¥100\"}\n")
        append("The user will see a confirmation dialog and can choose to confirm or cancel.\n")
        append("\n")

        append("### Latest Action History ###\n")
        if (blackboard.actionHistory.isNotEmpty()) {
            val numActions = minOf(5, blackboard.actionHistory.size)
            val latestActions = blackboard.actionHistory.takeLast(numActions)
            val latestSummaries = blackboard.summaryHistory.takeLast(numActions)
            val latestOutcomes = blackboard.actionOutcomes.takeLast(numActions)
            val latestErrors = blackboard.errorDescriptions.takeLast(numActions)

            latestActions.forEachIndexed { i, act ->
                val outcome = latestOutcomes.getOrNull(i) ?: "?"
                if (outcome == "A") {
                    append("- Action: $act | Description: ${latestSummaries.getOrNull(i)} | Outcome: Successful\n")
                } else {
                    append("- Action: $act | Description: ${latestSummaries.getOrNull(i)} | Outcome: Failed | Error: ${latestErrors.getOrNull(i)}\n")
                }
            }
        } else {
            append("No actions have been taken yet.\n")
        }
        append("\n")

        append("---\n")
        append("IMPORTANT:\n")
        append("1. Do NOT repeat previously failed actions. Try a different approach.\n")
        append("2. Prioritize the current subgoal.\n")
        append("3. Always analyze the screen BEFORE deciding on an action.\n\n")

        append("Provide your output in the following format:\n\n")
        append("### Thought ###\n")
        append("1. **Screen**: What app/page is currently shown? Is it the expected page for the current subgoal?\n")
        append("2. **Blocker**: Any popup, dialog, keyboard, or loading state that needs to be handled first?\n")
        append("3. **Target**: Is the target element visible? If not, should I scroll or navigate?\n")
        append("4. **Decision**: Based on the above analysis, what action should I take and why?\n\n")
        append("### Action ###\n")
        append("A valid JSON specifying the action. Example: {\"action\":\"click\", \"coordinate\": [500, 500]}\n")
        append("REMINDER: coordinates are normalized [0, 999], NOT pixels.\n\n")
        append("### Description ###\n")
        append("A brief description of the chosen action.\n")
    }

    /**
     * 解析执行响应
     */
    fun parseResponse(response: String): ActuatorResult {
        val thought = response
            .substringAfter("### Thought", "")
            .substringBefore("### Action")
            .replace("###", "")
            .trim()

        val actionStr = response
            .substringAfter("### Action", "")
            .substringBefore("### Description")
            .replace("###", "")
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val description = response
            .substringAfter("### Description", "")
            .replace("###", "")
            .trim()

        val action = Action.fromJson(actionStr)

        return ActuatorResult(thought, action, actionStr, description)
    }
}

data class ActuatorResult(
    val thought: String,
    val action: Action?,
    val actionStr: String,
    val description: String
)
