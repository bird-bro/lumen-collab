# web-system-console Delta: org-logo-upload-web

## ADDED Requirements

### Requirement: Organization Logo Upload Control

The organization profile dialogs (hospital + campus) SHALL bind the logo field to an
image upload control backed by `POST /api/v1/system/common/upload/image` (api spec §
Common Image Upload Endpoint): an AntD picture-card Upload, one image, validated
client-side (png/jpg/jpeg/webp, ≤2MB) BEFORE any request, with the successful
response's `data.path` stored as the form's `logoUrl` value (the org-save contract is
unchanged — still the `logoUrl` field of the org create/update body). The control
SHOULD render the current value as the tile image; a legacy absolute `http(s)`
value displays as-is, a stored relative `/profile/...` value resolves through the
static read-back segment. The former `Logo 地址` URL text input SHALL NOT remain.

#### Scenario: Upload round-trip fills the form value

- **WHEN** an administrator picks a valid 1MB png in the logo tile and the upload
  succeeds
- **THEN** `uploadImage` was called exactly once and the form's `logoUrl` equals the
  returned `data.path`
- **AND** saving the dialog submits that path in the org body (existing endpoint)

#### Scenario: Client-side validation rejects before any request

- **WHEN** the picked file is a `.gif` (or >2MB)
- **THEN** the rejection message surfaces and no network request is made

#### Scenario: Existing logo renders as the tile image

- **WHEN** the dialog opens for an org whose `logoUrl` is `/profile/avatar/.../x.png`
- **THEN** the tile shows that image (resolved same-origin via the static segment)
- **AND** an absolute `https://...` legacy value still renders as-is

#### Scenario: Upload failure keeps the dialog usable

- **WHEN** the upload request fails (backend `A0400` or transport `B0001`)
- **THEN** the error message surfaces on the page
- **AND** the previously stored `logoUrl` value is unchanged and the dialog stays
  open for retry
