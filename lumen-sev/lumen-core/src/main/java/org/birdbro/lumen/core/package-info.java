/**
 * The exam-workflow kernel (lumen-core): the department-neutral shared library for the
 * exam pipeline booking &rarr; registration &rarr; exam &rarr; report. NON-EXECUTABLE
 * by contract (service-topology Shared Library Boundary): no application entry point,
 * no controllers, no department-specific business rules - the HTTP surface of a
 * department belongs to its service module, and department-specific workflow steps
 * stay there. Consuming services scan {@code org.birdbro} so the kernel's beans
 * register (the OPS {@code LaunchApplication} habit); the kernel never imports a
 * department module, and remote diagnosis embeds the kernel without rewriting it.
 *
 * <p>Registration-side content (lumen-core-registration-kernel): the four-member
 * exam-order aggregate (document / execution / report placeholder / patient reference
 * + jsonb snapshot), the document state machine, exam-side numbering (accession and
 * booking only - the imaging number arrives through the patient-registry port), the
 * HIS two-phase intake (apply cache &rarr; manual confirm), and the ordered
 * registration extension hooks. Kernel DDL ships as the canonical copy-from source
 * under {@code db/kernel/} - consuming services vendor it into their own Flyway
 * ledgers (design D2); this module owns no schema of its own.</p>
 *
 * @author birdbro
 */
package org.birdbro.lumen.core;
