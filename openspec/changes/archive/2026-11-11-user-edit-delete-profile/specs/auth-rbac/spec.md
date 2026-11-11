# Spec Delta: auth-rbac

## MODIFIED Requirements

### Requirement: User Management

The service SHALL provide user CRUD on `user_info` (username unique, password stored
hashed, enable flag, and the profile fields: phone, email, gender, title, department
anchor, exam-type visibility, report-audit authority tier, signature image path).
Creating or modifying a user SHALL require an authenticated administrator, and the
audit fields SHALL be filled from the security context. The seeded default
administrator SHALL carry a must-change-password flag on first login. Deleting a user
SHALL be a logical delete that unbinds the user's role bindings; the seed
administrator (id 1) and the caller's own account SHALL NOT be deletable.

#### Scenario: Administrator creates a user

- **WHEN** an authenticated administrator creates a user
- **THEN** the user persists with hashed password and `create_by` filled

#### Scenario: Duplicate username rejected

- **WHEN** a user is created with an existing username
- **THEN** the service rejects it with an `A`-class code

#### Scenario: Administrator edits a user's profile

- **WHEN** an authenticated administrator updates a user's profile fields (name, phone,
  email, gender, title, department, exam-type visibility, audit tier, signature, role
  bindings)
- **THEN** the row's mutable fields update with `update_by` filled and the username
  stays untouched

#### Scenario: Role rebinding is a full replace

- **WHEN** an update carries a role-id set
- **THEN** the user's `user_role` bindings become exactly that set (previous bindings
  logically unbound, new ones inserted) in the same transaction as the profile update

#### Scenario: Administrator deletes a user

- **WHEN** an authenticated administrator deletes a user that is neither the seed admin
  nor the caller's own account
- **THEN** the row is logically deleted and its role bindings unbound in one transaction

#### Scenario: Protected accounts cannot be deleted

- **WHEN** a delete targets the caller's own account or user id 1
- **THEN** the service rejects it with an `A`-class code and nothing changes

<!-- synced from: user-edit-delete-profile-api 2026-11-10 -->
