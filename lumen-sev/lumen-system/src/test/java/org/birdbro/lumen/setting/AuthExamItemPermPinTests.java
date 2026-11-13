package org.birdbro.lumen.setting;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import org.birdbro.lumen.setting.controller.ExamItemController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item controller perm-pin proof (exam-setting-api task 4.2; the
 * AuthExamBodyPermPinTests shape): the mutating methods carry their exact button-perm
 * {@code @PreAuthorize} strings, the class gate is the list perm, and read methods
 * stay on the class gate only.
 *
 * @author birdbro
 */
class AuthExamItemPermPinTests {

    @Test
    void itemControllerClassGateIsTheListPerm() {
        PreAuthorize classGate = ExamItemController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level list gate");
        assertEquals("hasAuthority('setting:item:list')", classGate.value(),
                "the item gate must be setting:item:list");
    }

    @Test
    void itemMutatingMethodsPinTheirButtonPerms() throws Exception {
        assertPin("create", "hasAuthority('setting:item:create')");
        assertPin("update", "hasAuthority('setting:item:update')");
        assertPin("delete", "hasAuthority('setting:item:delete')");
    }

    @Test
    void readMethodsStayOnTheClassGateOnly() {
        for (Method method : ExamItemController.class.getDeclaredMethods()) {
            PreAuthorize pin = method.getAnnotation(PreAuthorize.class);
            if (pin != null) {
                assertTrue(pin.value().contains(":create") || pin.value().contains(":update")
                        || pin.value().contains(":delete"),
                        method.getName() + " must not pin a read perm - the class gate governs");
            }
        }
    }

    // ===================== helpers =====================

    private void assertPin(String methodName, String expectedPin) throws Exception {
        for (Method method : ExamItemController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                PreAuthorize pin = method.getAnnotation(PreAuthorize.class);
                assertNotNull(pin, "the mutating method " + methodName + " must pin a perm");
                assertEquals(expectedPin, pin.value(),
                        "the method " + methodName + " must pin the exact button perm");
                return;
            }
        }
        throw new AssertionError("method " + methodName + " not found");
    }
}
