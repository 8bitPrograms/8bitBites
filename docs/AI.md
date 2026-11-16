# AI meal entry

Open **AI** in bottom navigation. Type a meal description, choose a photo, or
take one with the embedded camera. Recognition returns a review draft. For each
item, select a matching database food, confirm grams and choose a meal. You can
mark an item as a supplement during review. Only confirmed food-label nutrition
is saved; AI-generated nutrient values are not used as database facts.

## On-device models

Choose **Browse & download models** or import a compatible `.litertlm` file.
The bundled catalogue describes three pinned model conversions:

| Model | Approximate download | Input | Access |
| --- | ---: | --- | --- |
| Gemma 3 1B INT4 | 584 MB | Text | Accept Gemma terms and supply an authorised HF read token |
| Qwen 2.5 1.5B INT8 | 1.60 GB | Text | Public download |
| Gemma 3n E2B INT4 | 3.66 GB | Text and photos | Accept Gemma terms and supply an authorised HF read token |

These are the entries pinned in `app/src/main/assets/models.json`, not a promise
that every device can run them. Follow each model's linked card and license.
The current backend uses LiteRT-LM on CPU and supports arm64-v8a/x86_64. Arbitrary
GGUF, checkpoint and `.task` files are not compatible imports. No model weights
are included in the repository or silently installed with the APK.

Downloads default to an unmetered connection. Android 14+ uses user-initiated
data-transfer jobs; older supported Android versions use WorkManager with a
foreground notification. Progress and pause/resume are available. Files must
match their pinned revision, byte count and SHA-256 before use. Android can
delay work because of network, battery, memory or thermal restrictions. Reopen
the model manager after a reboot or explicit task stop to resume if needed.

## Your API key

Gemma's **Hugging Face download token** is independent of this API mode. It grants
access to the gated model download; after installation the selected model runs
locally. Qwen's listed public file requires no token.

Choose OpenAI, Gemini or an HTTPS OpenAI-compatible base URL. Enter your own
API key and model ID; photo recognition requires a vision-capable model. The
key remains in memory and must be entered again in a later session. The chosen
endpoint and model ID can be remembered.

ChatGPT subscriptions and free ChatGPT accounts are not used as API credentials.
Provider billing, quotas and data policies apply separately. The app does not
offer ChatGPT account login or borrow another person's subscription.

Only submitted text and a selected resized photo go to the endpoint. API mode
does not follow redirects with the authorization header, and local mode does
not automatically upload input after an inference failure.

Photo portions are estimates. A scoop with no known weight stays unspecified.
Review drafts are kept in memory and can be lost after process death. Inference,
device memory use and live provider calls still require physical-device testing.

## Download recovery

The public Qwen model appears first. Gemma entries require Hugging Face access
and an authorised read token; a free account alone does not grant gated access.
Connecting, waiting for Wi-Fi, transferring and verifying are shown separately.
Android's system deferral is separate from a missing eligible network. The first
received bytes appear in B/KB so a small transfer is visible before it reaches
one percent or one MB.
Use Retry now for a queued/interrupted transfer, or explicitly allow mobile data
for that download. Turning off Wi-Fi-only does not silently change other jobs.

Paused connections are interrupted and keep their partial file. Valid partial
HTTP ranges continue immediately; ignored ranges restart safely. Android 14+
transfers use the network assigned to the job. Repeated connection failures stop
after five consecutive attempts without progress, with a manual resume option.
Size and SHA-256 verification remain mandatory before a model becomes usable.

Resumable jobs declare a 64 KB minimum network chunk, capped by the remaining
file size. Android can therefore budget a small resumable transfer instead of
treating the entire remaining GB-sized model as one indivisible transfer. This
uses Android's [minimum network chunk contract](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setMinimumNetworkChunkBytes(long)).
The manager also reads the [pending job reason](https://developer.android.com/reference/android/app/job/JobScheduler#getPendingJobReason(int))
when Android delays a queued job. Neither change overrides the user's Wi-Fi-only
choice or the operating system's restrictions.

If a download remains at its starting amount, the visible state distinguishes
network waiting, Android deferral and connecting to the server. Retry now keeps
the partial file; Allow mobile data explicitly changes that download's network
choice. Public Qwen does not need a token. Gemma needs an accepted model license
and a read token authorised for that model.

The desktop checked small real Qwen download/resume requests and host transfer
tests. Complete download, background behaviour and inference still need phone
validation.

## Local execution and error details in 0.6.2

Local inference runs in a private `:local_ai` bound-service process. The main
process owns the diary and model-library lock. Only the selected local model path,
meal text and a private temporary photo path cross the app's internal connection.
The worker does not start diary, Health Connect or widget observers.

One synchronous JNI request runs on the worker thread. The interface shows model
loading, preparation and recognition separately. A Kotlin runtime error is returned
as a controlled error; an unexpected worker death fails the request. Cancellation
and the three-minute timeout end the dedicated worker instead of closing a native
conversation that may still be processing. Neither event changes diary entries
or removes the installed model. There is no automatic cloud fallback or retry.

**Copy error details** includes the app/runtime version, device model, Android
version, ABI, available/total memory at start, model identifier and last stage.
Android's process-exit reason/status is included when available. The app stores
one local diagnostic record; it excludes meal text, photos, generated responses,
credentials and raw native exception text. Copying does not upload or send it.
The temporary photo copy is deleted after the request or on recovery from an
interrupted attempt.

The upstream [Qwen template report](https://github.com/google-ai-edge/gallery/issues/1317)
was resolved for its reporter with an updated model. Comparing the Hugging Face
revision recommended there (`5e6e7a58d03777898b31330a15d2e0a5a9107de3`) with our
pinned revision showed the same 1,597,922,856-byte file and SHA-256
`c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e`.
That report therefore does not establish the cause of the reported Samsung S25
failure. The model pin and download are unchanged. Host tests simulate runtime
errors, worker death and cancellation; full S25 inference remains unverified.
