package com.valghora.shrimpl.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/TEMPLATE_SPEC.md T6.5: ask, explain first, or say a permission is off. */
class PermissionPlanTest {
    private fun plan(missing: List<String>, asked: Set<String> = emptySet(), rationale: Set<String> = emptySet()) =
        PermissionPlan.of(missing, asked::contains, rationale::contains)

    @Test
    fun firstRequestAsksAndroidDirectly() =
        assertEquals(PermissionPlan(listOf(CAMERA, MIC), explainFirst = false, blocked = emptyList()), plan(listOf(CAMERA, MIC)))

    @Test
    fun afterOneRefusalItExplainsFirst() =
        assertEquals(
            PermissionPlan(listOf(CAMERA, MIC), explainFirst = true, blocked = emptyList()),
            plan(listOf(CAMERA, MIC), asked = setOf(CAMERA, MIC), rationale = setOf(CAMERA, MIC)),
        )

    @Test
    fun afterASecondRefusalItSaysTheyAreOff() =
        assertEquals(PermissionPlan(emptyList(), explainFirst = false, blocked = listOf(CAMERA, MIC)), plan(listOf(CAMERA, MIC), asked = setOf(CAMERA, MIC)))

    @Test
    fun mixedStatesAskForWhatAndroidStillAsksAbout() =
        // The camera was turned down twice; the microphone was never asked for.
        assertEquals(PermissionPlan(listOf(MIC), explainFirst = false, blocked = listOf(CAMERA)), plan(listOf(CAMERA, MIC), asked = setOf(CAMERA)))

    @Test
    fun oneRefusalAmongSeveralStillExplains() =
        assertEquals(
            PermissionPlan(listOf(CAMERA, MIC), explainFirst = true, blocked = emptyList()),
            plan(listOf(CAMERA, MIC), asked = setOf(CAMERA), rationale = setOf(CAMERA)),
        )

    private companion object {
        const val CAMERA = "android.permission.CAMERA"
        const val MIC = "android.permission.RECORD_AUDIO"
    }
}
