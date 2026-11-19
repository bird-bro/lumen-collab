# exam-setting Delta

## MODIFIED Requirements

### Requirement: Report Template Endpoints

The service SHALL expose `/api/v1/setting/rep-temps` with `GET /tree` (modality +
type required — full folder/template tree for the scope), `GET /page` (templates
under one parent node, name filter, paged), `POST`, `PUT /{id}`, `DELETE /{id}` —
gated by perms `setting:temp:{list,create,update,delete}`. The create/update
request bodies and the template row payloads (page response; content is not part of
the tree node shape) SHALL carry the optional `content` object; tree and page
behavior is otherwise unchanged. The three mutating endpoints' static pins SHALL
admit EITHER family (`hasAnyAuthority` of the base and the public button perms) —
the scope-precise family is enforced in the service layer per the Scope Enforcement
requirement (the static pins stay as defense in depth; the class gate
`setting:temp:list` is unchanged).

<!-- synced from: rep-temp-scope-perms 2026-11-19 -->

#### Scenario: Tree returns both scopes independently

- **WHEN** `GET /tree?modality=DR&type=1` and `GET /tree?modality=DR&type=2` are
  requested by the same user
- **THEN** the first returns the modality's public tree and the second only the
  user's own private tree

#### Scenario: Template page under a folder

- **WHEN** `GET /page?modality=DR&type=2&upId=7&name=结节` is requested
- **THEN** the response lists the user's own non-deleted template rows whose parent
  is node 7 whose name contains 结节, paged

#### Scenario: Template payloads carry the content object

- **WHEN** a structured template is created or updated with a valid content object
- **THEN** the stored row and subsequent page responses return the same content
- **AND** a prose template's payloads carry `content: null`

#### Scenario: Static pins admit either family

- **WHEN** a user holding ONLY `setting:temp:public:create` (not the base create)
  passes the create endpoint's static pin
- **THEN** the pin admits the request and the service layer decides by the
  request's scope

### Requirement: Report Template Scope Enforcement

The service SHALL enforce write ownership on private templates: create with type=2
binds owner_uid to the requesting user; update/delete of a type=2 row SHALL be
refused unless the requester is the owner. Scope-keyed families gate the surface:
private (type=2) writes SHALL require the base button perms
(`setting:temp:create/update/delete`); public (type=1) writes SHALL require the
public family (`setting:temp:public:create/update/delete`); public reads
(`GET /tree` and `GET /page` with type=1) SHALL require
`setting:temp:public:list`. The precise family is asserted in the service layer —
create branches on the request's `type`, update/delete branch on the row's stored
type after the ownership load; a missing family SHALL be refused content-free with
the A0301 permission-denied envelope. A user SHALL never see another user's private
rows in any listing.

<!-- synced from: rep-temp-scope-perms 2026-11-19 -->

#### Scenario: Non-owner private write refused

- **WHEN** user B requests update or delete of user A's private template row
- **THEN** the service refuses the write and does not leak the row's content

#### Scenario: Private create binds the owner

- **WHEN** a user creates a template with type=2
- **THEN** the stored row's owner_uid is the requesting user, regardless of the
  request body

#### Scenario: Public write without the public family refused

- **WHEN** a user holding only the base family (no `setting:temp:public:update`)
  updates a public (type=1) template row
- **THEN** the service refuses the write with the A0301 envelope, content-free

#### Scenario: Public write with the public family succeeds

- **WHEN** a user holding `setting:temp:public:create` creates a template with
  type=1
- **THEN** the public row is created (owner_uid NULL) without any base write perm

#### Scenario: Public read without public:list refused

- **WHEN** a user holding only `setting:temp:list` (no `setting:temp:public:list`)
  requests the public tree (`type=1`)
- **THEN** the service refuses the read with the A0301 envelope, content-free
