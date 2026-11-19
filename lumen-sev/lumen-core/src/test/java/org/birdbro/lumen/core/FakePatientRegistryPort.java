package org.birdbro.lumen.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.patient.PatientRegistryPort;
import org.springframework.stereotype.Service;

/**
 * TEST-SOURCES-ONLY deterministic {@link PatientRegistryPort} for the kernel's test
 * host (task 5.1 - the TestUserDetailsService precedent): the kernel MAIN sources
 * carry no port implementation (consuming services adapt the port to the patient
 * service); every kernel test context needs ONE port bean for the registration
 * service's constructor injection, and this fake stands in deterministically -
 * resolve-or-register maps the request demographics into a fresh snapshot and hands
 out a sequential pat_uid. Real patient-domain behavior is the CONSUMER change's
 * integration concern, never the kernel's.
 *
 * @author birdbro
 */
@Service
public class FakePatientRegistryPort implements PatientRegistryPort {

    /** Sequential pat_uid source - distinct identities per registration. */
    private final AtomicInteger sequence = new AtomicInteger(990000);

    @Override
    public PatientIdentity resolveOrRegister(PatientRegistrationRequest request) {
        PatientSnapshot snapshot = PatientSnapshot.builder()
                .patId("P" + sequence.get())
                .name(request.getName())
                .gender(request.getGender())
                .age(request.getAge())
                .phone(request.getPhone())
                .patientSource(request.getPatientSource())
                .admissionNumber(request.getAdmissionNumber())
                .bedNumber(request.getBedNumber())
                .height(request.getHeight())
                .weight(request.getWeight())
                .build();
        return PatientIdentity.builder()
                .patUid(sequence.incrementAndGet())
                .patId(snapshot.getPatId())
                .resolved(false)
                .snapshot(snapshot)
                .build();
    }
}
