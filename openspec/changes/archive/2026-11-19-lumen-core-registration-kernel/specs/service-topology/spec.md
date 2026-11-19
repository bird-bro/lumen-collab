# Spec Delta

## MODIFIED Requirements

### Requirement: Shared Library Boundary

`framework`, `common`, and `lumen-core` SHALL be non-executable library jars. They MUST
NOT contain a Spring Boot application entry point, and they MUST NOT contain business
rules belonging to a single department service. `lumen-core` additionally carries the
department-neutral exam-workflow kernel (order aggregate, document state machine,
number-issuer and patient-registry port seams, HIS two-phase intake, extension seams) —
department-specific workflow steps stay in the department services, and cross-service
machinery (number-rule storage and issuance) stays behind the ports. The HTTP surface
of a service belongs to that service: controllers live in the service module, never in
a shared library.

#### Scenario: Library is not runnable

- **WHEN** the build packages `framework`, `common`, or `lumen-core`
- **THEN** the artifact is a plain jar with no `Main-Class` boot entry point
- **AND** no Spring Boot repackage goal has produced an executable jar

#### Scenario: HTTP surface ownership

- **WHEN** the copied base modules and the service shells are inspected
- **THEN** neither `framework`, `common`, nor `lumen-core` declares a Spring MVC
  controller (`@RestControllerAdvice` advice classes are response/error plumbing, not
  endpoints)
- **AND** shared libraries expose service beans and extension points, not controllers

#### Scenario: Service-specific rule proposed for a shared library

- **WHEN** a rule applies to only one department's workflow
- **THEN** it belongs in that service, not in the shared modules
