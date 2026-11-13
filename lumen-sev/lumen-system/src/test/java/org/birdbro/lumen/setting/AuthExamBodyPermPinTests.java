package org.birdbro.lumen.setting;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import org.birdbro.lumen.setting.controller.ExamBodyController;
import org.birdbro.lumen.setting.controller.ExamBodyGroupController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Body group/body controller perm-pin proof (exam-setting-api task 2.3; the
 * AuthDevicePermPinTests shape): the mutating methods carry their exact button-perm
 * {@code @PreAuthorize} strings and the class gate is the list perm - asserted by
 * reflection so a perm-string drift fails fast (the behavioral denial matrix rides the
 * API tests' no-perm token; this pins the CONTRACT).
 *
 * @author birdbro
 */
class AuthExamBodyPermPinTests {

    @Test
    void groupControllerClassGateIsTheListPerm() {
        PreAuthorize classGate = ExamBodyGroupController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level list gate");
        assertEquals("hasAuthority('setting:body:list')", classGate.value(),
                "the body-group gate must be setting:body:list (one resource family)");
    }

    @Test
    void groupMutatingMethodsPinTheirButtonPerms() throws Exception {
        assertPin(ExamBodyGroupController.class, "create",
                "hasAuthority('setting:body:create')");
        assertPin(ExamBodyGroupController.class, "update",
                "hasAuthority('setting:body:update')");
        assertPin(ExamBodyGroupController.class, "delete",
                "hasAuthority('setting:body:delete')");
    }

    @Test
    void bodyControllerClassGateIsTheListPerm() {
        PreAuthorize classGate = ExamBodyController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level list gate");
        assertEquals("hasAuthority('setting:body:list')", classGate.value(),
                "the body gate must be setting:body:list");
    }

    @Test
    void bodyMutatingMethodsPinTheirButtonPerms() throws Exception {
        assertPin(ExamBodyController.class, "create", "hasAuthority('setting:body:create')");
        assertPin(ExamBodyController.class, "update", "hasAuthority('setting:body:update')");
        assertPin(ExamBodyController.class, "delete", "hasAuthority('setting:body:delete')");
    }

    @Test
    void readMethodsStayOnTheClassGateOnly() {
        for (Class<?> controller : new Class<?>[] {ExamBodyGroupController.class,
                ExamBodyController.class}) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize pin = method.getAnnotation(PreAuthorize.class);
                if (pin != null) {
                    assertTrue(pin.value().contains(":create") || pin.value().contains(":update")
                            || pin.value().contains(":delete"),
                            controller.getSimpleName() + "." + method.getName()
                                    + " must not pin a read perm - the class gate governs reads");
                }
            }
        }
    }

    // ===================== helpers =====================

    private void assertPin(Class<?> controller, String methodName, String expectedPin)
            throws Exception {
        Method method = controller.getDeclaredMethod(methodName,
                resolveSignature(controller, methodName));
        PreAuthorize pin = method.getAnnotation(PreAuthorize.class);
        assertNotNull(pin, "the mutating method " + methodName + " must pin a button perm");
        assertEquals(expectedPin, pin.value(),
                "the method " + methodName + " must pin the exact button perm");
    }

    private Class<?>[] resolveSignature(Class<?> controller, String methodName) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method.getParameterTypes();
            }
        }
        throw new AssertionError("method " + methodName + " not found on " + controller);
    }
}
