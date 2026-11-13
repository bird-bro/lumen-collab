package org.birdbro.lumen.manage;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import org.birdbro.lumen.manage.controller.OrgDeviceController;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Device controller perm-pin proof (org-device-room-api task 2.3): the mutating
 * methods carry their exact button-perm {@code @PreAuthorize} strings and the class
 * gate is the page perm - the strings the V27 menu seed binds, asserted by reflection
 * so a perm-string drift fails fast (the behavioral denial matrix lives in
 * AuthPermEnforcementTests; this pins the CONTRACT, not one enforcement path).
 *
 * @author birdbro
 */
class AuthDevicePermPinTests {

    @Test
    void deviceControllerClassGateIsThePagePerm() {
        PreAuthorize classGate = OrgDeviceController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level page gate");
        assertEquals("hasAuthority('system:device:list')", classGate.value(),
                "the device page gate must be system:device:list");
    }

    @Test
    void deviceMutatingMethodsPinTheirButtonPerms() throws Exception {
        assertMethodPin("create", "hasAuthority('system:device:create')");
        assertMethodPin("update", "hasAuthority('system:device:update')");
        assertMethodPin("delete", "hasAuthority('system:device:delete')");
    }

    @Test
    void deviceReadMethodsStayOnTheClassGateOnly() {
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
        for (Method method : OrgDeviceController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}
