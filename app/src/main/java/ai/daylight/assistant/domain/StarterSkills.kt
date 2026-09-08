package ai.daylight.assistant.domain

/**
 * Built-in starter skills. Seeded once into Room with stable ids so the user can
 * disable or delete them. Instructions stay short — matching skills are injected
 * in full; the rest appear as name + purpose only.
 */
data class StarterSkill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val examplePrompts: List<String>
)

object StarterSkills {
    const val ID_PREFIX = "starter."

    val catalog: List<StarterSkill> = listOf(
        StarterSkill(
            id = "starter.research-brief",
            name = "Research brief",
            description = "Sourced briefing: search first, then a tight synthesis with citations.",
            instructions = """
                You are writing a research brief. Call parallel_search before stating time-sensitive or contested facts. Structure the answer as: 1) one-sentence bottom line, 2) key findings with [n] citations, 3) disagreements or uncertainty, 4) useful next questions. Never invent sources. Prefer recent primary pages over blogs.
            """.trimIndent(),
            examplePrompts = listOf(
                "Brief me on the latest MiniMax model releases",
                "What changed in the IB CS syllabus this year?"
            )
        ),
        StarterSkill(
            id = "starter.study-notes",
            name = "Study notes",
            description = "IB-style notes: headings, definitions, examples, and a quick check.",
            instructions = """
                Produce study notes a student can revise from. Use short headings, a precise definition, one worked example, and 3 check questions with answers after a divider. Flag anything that depends on a specific syllabus or mark scheme and search if the user named a course, paper, or year. Keep tone clear, not cute.
            """.trimIndent(),
            examplePrompts = listOf(
                "Notes on binary search for HL CS",
                "French A: how to write a commentaire composé intro"
            )
        ),
        StarterSkill(
            id = "starter.essay-outline",
            name = "Essay outline",
            description = "Thesis, arguments, counter-argument, and a closer — ready to draft.",
            instructions = """
                Build an essay outline, not the full essay unless asked. Give: working thesis, 3 body claims with evidence bullets, the strongest counter-argument and a reply, and a closing move. If the prompt is a past paper or stimulus, stay inside that question. Search only when the essay needs current facts.
            """.trimIndent(),
            examplePrompts = listOf(
                "Outline an English B paper 2 on identity",
                "BNM: outline an argument on central bank independence"
            )
        ),
        StarterSkill(
            id = "starter.email-draft",
            name = "Ready-to-paste email",
            description = "A complete email the user can copy — subject, greeting, body, sign-off.",
            instructions = """
                Return a ready-to-paste email only: Subject line, then the body. Match the requested register (formal Swiss/French school, professional, short). Do not send anything and do not claim you sent it. If key facts are missing (recipient, ask, deadline), make one reasonable assumption and mark it in a single italic note under the draft, not inside the email.
            """.trimIndent(),
            examplePrompts = listOf(
                "Email my teacher asking for a deadline extension",
                "Formal enquiry about a university open day"
            )
        ),
        StarterSkill(
            id = "starter.decision-memo",
            name = "Decision memo",
            description = "Compare options against criteria and recommend one path.",
            instructions = """
                Write a decision memo: context in 2 sentences, criteria, a compact comparison of the options, risks, and a single recommendation with why. Search when prices, specs, or dates matter. Do not hedge with three equally good answers — pick one and say what would change your mind.
            """.trimIndent(),
            examplePrompts = listOf(
                "Should I take SL Math IA topic A or B?",
                "Compare two used JDM cars on upkeep and resale"
            )
        ),
        StarterSkill(
            id = "starter.fact-check",
            name = "Fact check",
            description = "Verify a claim with search and a clear verdict.",
            instructions = """
                Treat the user's statement as a claim to check. Call parallel_search (and fetch_url if they pasted a link). Verdict first: True / Mixed / False / Unclear. Then 3 evidence bullets with citations. Separate sourced facts from inference. If sources disagree, say so instead of averaging them.
            """.trimIndent(),
            examplePrompts = listOf(
                "Fact-check: MiniMax M3 has a 1M context window",
                "Is this article's inflation number current?"
            )
        ),
        StarterSkill(
            id = "starter.daily-planner",
            name = "Daily planner",
            description = "Turn a pile of work into a timed day plan.",
            instructions = """
                Call get_current_time first so the plan uses the real local date. Ask for nothing extra if the user already listed tasks. Produce a timed block plan with one focus per block, short breaks, and a shutdown item. Put hard deadlines first. Keep it realistic for an after-school window unless they said otherwise.
            """.trimIndent(),
            examplePrompts = listOf(
                "Plan tonight: CS IA, French reading, 40 min osu",
                "I have 3 hours after school — schedule Physics and an essay intro"
            )
        ),
        StarterSkill(
            id = "starter.code-walkthrough",
            name = "Code walkthrough",
            description = "Explain, review, or sketch code with a concrete next edit.",
            instructions = """
                For code questions: state the goal, walk through the current behaviour, name the bug or design issue, then give a minimal patch or sketch. If they asked for a full project, call create_code_project instead of dumping files in chat. Never include real secrets. Prefer the language they are using.
            """.trimIndent(),
            examplePrompts = listOf(
                "Review this Kotlin function for off-by-one errors",
                "Scaffold a tiny Compose list + detail app as a zip"
            )
        )
    )

    fun isStarterId(id: String): Boolean = id.startsWith(ID_PREFIX)
}
