package com.ruckus.agent.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NitroProjectAdmissionTest {
    private fun spec(
        name: String = "Nitro Demo",
        packageName: String = "com.example.nitro",
        features: List<String> = listOf("Login", "Dashboard"),
    ) = ProjectSpec(
        name = name,
        packageName = packageName,
        kind = BuildKind.ANDROID_APP,
        description = "  Safe   project description  ",
        features = features,
    )

    @Test fun normalizesAcceptedProject() {
        val admitted = NitroProjectAdmission.admit(spec(name = "  Nitro   Demo  "))
        assertEquals("Nitro Demo", admitted.name)
        assertEquals("Safe project description", admitted.description)
        assertEquals(listOf("Login", "Dashboard"), admitted.features)
    }

    @Test fun builderUsesAdmittedValues() {
        val plan = BuilderEngine().plan(spec(name = "  Nitro   Demo  ", features = listOf("  Login   flow  ")))
        assertEquals("Create ANDROID_APP project: Nitro Demo", plan.first())
        assertEquals("Implement feature: Login flow", plan[3])
    }

    @Test fun rejectsTraversalLikePackage() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(packageName = "com.example../escape"))
        }
    }

    @Test fun rejectsShellLikePackage() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(packageName = "com.example.app;rm"))
        }
    }

    @Test fun rejectsUppercasePackageSegments() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(packageName = "com.Example.app"))
        }
    }

    @Test fun rejectsControlCharacters() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(name = "Nitro\nInjected"))
        }
    }

    @Test fun rejectsDuplicateFeaturesAfterNormalization() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(features = listOf("Login", " login ")))
        }
    }

    @Test fun rejectsExcessFeatureCount() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(features = (1..65).map { "Feature $it" }))
        }
    }

    @Test fun rejectsEmptyFeatureAfterNormalization() {
        assertThrows(ProjectAdmissionException::class.java) {
            NitroProjectAdmission.admit(spec(features = listOf("   ")))
        }
    }
}
