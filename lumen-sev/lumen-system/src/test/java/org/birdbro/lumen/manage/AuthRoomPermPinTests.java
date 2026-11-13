package org.birdbro.lumen.manage;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import org.birdbro.lumen.manage.controller.OrgRoomController;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Room controller perm-pin proof (org-device-room-api task 3.3): the mutating methods
 * carry their exact button-perm {@code @PreAuthorize} strings (incl. the D6 status
 * toggle's own {@code system:room:status}) and the class gate is the page perm -
 * asserted by reflection so a perm-string drift fails fast (the behavioral denial
 * matrix lives in AuthPermEnforcementTests; this pins the CONTRACT).
 *
 * @author birdbro
 */
class AuthRoomPermPinTests {

    @Test
    void roomControllerClassGateIsThePagePerm() {
        PreAuthorize classGate = OrgRoomController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level page gate");
        assertEquals("hasAuthority('system:room:list')", classGate.value(),
                "the room page gate must be system:room:list");
    }

    @Test
    void roomMutatingMethodsPinTheirButtonPerms() {
        assertMethodPin("create", "hasAuthority('system:room:create')");
        assertMethodPin("update", "hasAuthority('system:room:update')");
        assertMethodPin("setStatus", "hasAuthority('system:room:status')");
        assertMethodPin("delete", "hasAuthority('system:room:delete')");
    }

    @Test
    void roomReadMethodsStayOnTheClassGateOnly() {
        // The read methods must NOT pin a narrower perm - the class gate governs them.
        for (String readMethod : new String[] {"getById", "list", "page"}) {
            Method method = findMethod(readMethod);
            assertNotNull(method, "the read method " + readMethod + " must exist");
            assertTrue(method.getAnnotation(PreAuthorize.class) == null,
                    "the read method " + readMethod
                            + " must rely on the class gate, not pin its own perm");
        }
    }

    // ===================== helpers =====================

    private void assertMethodPin(String methodName, String expectedPin) {
        Method method = findMethod(methodName);
        assertNotNull(method, "the method " + methodName + " must exist");
        PreAuthorize pin = method.getAnnotation(PreAuthorize.class);
        assertNotNull(pin, "the mutating method " + methodName + " must pin a button perm");
        assertEquals(expectedPin, pin.value(),
                "the method " + methodName + " must pin the exact button perm");
    }

    private Method findMethod(String name) {
        for (Method method : OrgRoomController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}
