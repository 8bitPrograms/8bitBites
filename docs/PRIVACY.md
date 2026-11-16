# Privacy and data flow

8bitBites has no app account, analytics service or hosted personal-diary backend.
Profiles, food and water logs, favourites, supplement records, targets and avatar
images are stored in the app's private Android storage. Android backup is
disabled in the manifest. User-controlled backup/restore is not yet implemented.

## Optional connections

| Action | Data sent | Destination |
| --- | --- | --- |
| App/catalogue version check | HTTP request for public manifests; no diary or profile payload | GitHub raw content |
| Install a catalogue update | Request for the selected public SQLite snapshot | GitHub raw content |
| External food search | Entered query/barcode; USDA key when using USDA | Chosen food-data provider |
| Download a local model | Pinned model-file request and, if required, an HF access token | Hugging Face and its file delivery hosts |
| Remote AI chat | Submitted recent conversation and included resized photos, plus provider credentials | Chosen AI endpoint |
| ChatGPT sign-in | OAuth registration/host identifier, browser consent, basic account identity and protected credentials | OpenAI authentication service |
| ChatGPT plan chat | Submitted recent conversation and included photos, using the selected account's OAuth credential | OpenAI public Responses API |
| Health Connect sync | Confirmed food nutrition records and their updates/deletions | Android Health Connect |

Network services can observe ordinary connection metadata such as the requester's
IP address. Their own access, retention and licensing terms apply.

## AI, keys and photos

Local inference runs on the device and does not upload meal input. It never
silently falls back to a remote provider. API mode sends input only when the user
submits recognition. Provider API keys and the USDA key stay in session memory;
endpoint/model selections can persist without the key.

ChatGPT account connections use OpenAI's browser authorization. Account/client
mapping, identity, permissions, rotating tokens and expiry are encrypted in
private Android storage using Keystore. The app can keep separate account
registrations; switching account or AI provider starts a new conversation.
Signing in alone does not grant plan usage. This connection does not read the
user's ChatGPT conversations or memories, and no browser cookies are imported.
Sign-out attempts to revoke the renewable session and clears local tokens. If
remote revocation cannot be confirmed, the app says so and directs the user to
ChatGPT settings. The installation's opaque host ID and account mapping remain.
Connection diagnostics contain only the current sign-in stage, HTTP status and
bounded error category/class. They exclude authorization URLs, codes, tokens,
account addresses, request bodies, chat text and photos. A temporary foreground
notification keeps browser sign-in active until completion or cancellation.

Sent chat messages, submitted resized photos, pending meal summaries and their
provider/model/account binding are stored in the app's private local files.
New chat retains the old conversation in history. Deleting a conversation removes
its local record and photos; confirmed diary entries remain separate. Unsent
photo attachments are temporary. API keys and OAuth tokens are not part of the
chat-history files. Android backup remains disabled.

Bounded recent context is sent only when the user submits to the selected
provider. Restoring history is a local operation. ChatGPT requests use
`store:false`; this is an API storage setting, not a claim of zero provider
retention or a setting that disables the app's own local history. The app does
not import conversations from the user's ChatGPT account.

AI-estimated portions and nutrients are saved to the diary only after explicit
approval, under a distinct AI-estimate source. Existing food labels and the
public catalogue are not overwritten by generated values.

The local model runs in a private worker process. One diagnostic record keeps
device/runtime, available memory, model identifier, last stage and error category;
it excludes meal text, photos, model output and credentials. It stays on the device.
Copy error details places this information on the clipboard only when requested;
the app does not automatically send it. A private resized photo copy used by the
worker is deleted after completion/cancellation or interrupted-request recovery.

Hugging Face tokens needed for background downloads are encrypted with Android
Keystore, retained while a download is paused, and removed after completion or
deletion. Tokens are not forwarded to file-delivery hosts. Model files are
verified before they become selectable.

Camera permission is requested when taking a meal photo. Selected or captured
images are resized and re-encoded before use. Camera captures use private cache;
capture files are removed after processing. Profile images are re-encoded in
private storage without location metadata. No selected image is automatically
uploaded by choosing or capturing it.

## Health Connect and public data

Health Connect nutrition permission and automatic sync are optional. The app
writes its own food entries; it does not read other apps' health records.
Water and unlinked supplement logs remain local in this version. Revoking the
permission prevents further sync; it does not itself delete previously written
Health Connect records.

The repository's public `database/` contains food-catalogue data only. The private
Room diary is a separate database. Publishing a catalogue must never include
personal logs, photos, profiles, credentials or signing material.

To report a problem, use synthetic examples and remove personal information
from screenshots and logs. Physical-device behaviour is still being validated.
