package com.valghora.shrimpl.core

/**
 * What a web app does when a page wants Android permissions it doesn't hold (docs/TEMPLATE_SPEC.md
 * T6.5): ask Android for [ask], after explaining why if [explainFirst], and say that [blocked] are
 * off, with a way to Settings, since Android no longer asks for them.
 */
data class PermissionPlan(val ask: List<String>, val explainFirst: Boolean, val blocked: List<String>) {
    companion object {
        /**
         * The plan for [missing] permissions. Android can't tell "never asked" from "Don't allow" chosen
         * twice: [asked] says whether the app asked for a permission before, [rationale] is Android's
         * `shouldShowRequestPermissionRationale` (true after one refusal).
         */
        fun of(missing: List<String>, asked: (String) -> Boolean, rationale: (String) -> Boolean): PermissionPlan {
            val (ask, blocked) = missing.partition { !asked(it) || rationale(it) }
            return PermissionPlan(ask, explainFirst = ask.any(rationale), blocked = blocked)
        }
    }
}
