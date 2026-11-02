# Optional reminder synchronization protocol

A server is configured by the user. No production host or credential is shipped. Use HTTPS with no embedded user information, query string or fragment. A bare origin uses `/api/chatbot`; an explicitly supplied API base path is retained. Redirects are disabled so bearer credentials cannot move to another host.

| Method | Relative path | Contract |
| --- | --- | --- |
| POST | /device/connect | `{roomKey,password}` → `{token,roomKey,label}` |
| GET | /device/sync | Bearer authentication → `{version,savedAt,list}` |
| PUT | /device/sync | `{expectedVersion,mutationId,list}` → committed snapshot |
| POST | /device/sync/ack | `{version}` after durable local application |
| DELETE | /device/connection | Revoke the device credential |

`list` contains at most 30 definitions and `summary: {enabled,time,sendEmpty}`. A definition contains an opaque ID, name, category, time, weekdays (1–7), optional calendar anchor, intervalMonths, noticeDays, enabled, revision and leadMinutes. Time is HH:mm in Asia/Seoul. Categories weekly/monthly and legacy aliases are supported.

Writes compare the expected version atomically and increment the version. A mutation ID (16–80 URL-safe characters) is retained with its request fingerprint and original response. An identical retry returns the original snapshot; reuse with different contents is rejected. An old base returns HTTP 409 with `{message,current:{version,savedAt,list}}`; neither side may silently discard its pending edits.

HTTP 401 requires reauthentication, 429 allows retry later, and transport failures retain pending changes. Successful acknowledgement is separate from messenger delivery. A device connection, room identity, and bot/sending switches are not synchronized. Servers must scope credentials to a room, validate inputs, bound payloads to 64 KiB, hash credentials, enforce TLS, and protect browser writes against cross-origin requests.

The app's durable queue and snapshot are updated in local database transactions. Responses are checked against the current endpoint/credential and current pending mutation. A response for an older in-flight edit cannot replace a newer local edit. Changing connections does not reuse another endpoint's version history.
