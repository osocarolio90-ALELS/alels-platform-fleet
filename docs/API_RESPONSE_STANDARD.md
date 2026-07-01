# API Response Standard

## Compatibility first

Existing endpoints and payloads remain governed by `API_COMPATIBILITY_RULES.md`. This standard guides new or explicitly versioned API work and does not authorize wrapping or reshaping legacy responses.

## Success responses

- Return the narrow DTO required by the consumer; never expose persistence or security objects.
- Use stable field names, types, nullability, units, timezone semantics, and identifier formats.
- Choose the established HTTP success status for the operation.
- Collections use a consistent, documented ordering. Pagination metadata must be explicit where pagination exists.
- Avoid redundant success envelopes unless the established endpoint family already uses one.

## Error responses

Use the established `StandardErrorResponse` shape where applicable:

```json
{
  "success": false,
  "code": "STABLE_MACHINE_CODE",
  "message": "Safe human-readable message",
  "timestamp": "ISO-8601 timestamp"
}
```

Do not expose stack traces, SQL, filesystem paths, credentials, internal hostnames, or sensitive authorization details. Error codes are stable contracts; messages may be localized or refined only when compatibility permits.

## DTO/request/response rules

- `Request` types represent inbound transport data and receive boundary validation.
- `Response` types represent outbound contracts and contain only authorized fields.
- Do not reuse request DTOs as persistence models or responses for convenience.
- Additive fields require consumer compatibility review; removals, renames, type changes, new required fields, and semantic changes require versioning/migration.

## Future Refactor Recommendation

Inventory endpoint response families before proposing a universal envelope or machine-readable API specification.
