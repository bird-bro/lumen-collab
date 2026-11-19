package org.birdbro.lumen.setting;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import org.birdbro.lumen.setting.controller.RepTempController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Report-template controller perm-pin proof (exam-setting-api task 5.2; the
 * AuthExamBodyPermPinTests shape; pins relaxed to the either-family admit by
 * rep-temp-scope-perms task 1.3): the mutating methods carry their
 * {@code hasAnyAuthority} button-perm strings (base OR public family - the
 * admit-then-assert split, design D1), the class gate stays the list perm, and read
 * methods stay on the class gate only.
 *
 * @author birdbro
 */
class AuthRepTempPermPinTests {

    @Test
    void tempControllerClassGateIsTheListPerm() {
        PreAuthorize classGate = RepTempController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level list gate");
        assertEquals("hasAuthority('setting:temp:list')", classGate.value(),
                "the rep-temp gate must be setting:temp:list");
    }

    @Test
    void tempMutatingMethodsPinTheirButtonPerms() throws Exception {
        assertPin("create",
                "hasAnyAuthority('setting:temp:create', 'setting:temp:public:create')");
        assertPin("update",
                "hasAnyAuthority('setting:temp:update', 'setting:temp:public:update')");
        assertPin("delete",
                "hasAnyAuthority('setting:temp:delete', 'setting:temp:public:delete')");
    }

    @Test
    void readMethodsStayOnTheClassGateOnly() {
        for (Method method : RepTempController.class.getDeclaredMethods()) {
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
        for (Method method : RepTempController.class.getDeclaredMethods()) {
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
