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
| API meal recognition | Submitted meal text and selected resized photo, plus provider credentials | Chosen AI endpoint |
| Health Connect sync | Confirmed food nutrition records and their updates/deletions | Android Health Connect |

Network services can observe ordinary connection metadata such as the requester's
IP address. Their own access, retention and licensing terms apply.

## AI, keys and photos

Local inference runs on the device and does not upload meal input. It never
silently falls back to a remote provider. API mode sends input only when the user
submits recognition. Provider API keys and the USDA key stay in session memory;
endpoint/model selections can persist without the key.

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
