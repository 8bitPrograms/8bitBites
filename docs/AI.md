# AI connections and chat

Choose **Settings > AI and chat** to select **On-device AI**, an HTTPS
**Remote server**, or **ChatGPT**. Connection details and local model management
live in Settings. The bottom navigation and quick widget use the selected
provider's name and symbol: a local chip, remote server or the ChatGPT mark.

The chat screen contains messages, a composer and photo/camera attachments.
Describe a meal, ask questions or clarify an earlier amount. Conversation context
is bounded before submission; switching providers or ChatGPT accounts starts a
new chat. Chat history and unsaved drafts stay in memory and are lost on process
death. **New chat** clears the current conversation.

An assistant can propose a meal, but it cannot write directly to the diary.
Choose **Review meal**, select matching database foods, confirm grams and choose
a meal. You can mark an item as a supplement during review. Only confirmed
food-label nutrition is saved; AI-generated nutrients are not database facts.
Photo portions are estimates, and an unspecified scoop weight stays unknown.

## On-device models

In **Settings > AI and chat > On-device AI**, choose **Browse & download models**
or import a compatible `.litertlm` file.
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

## OpenAI-compatible remote server

Gemma's **Hugging Face download token** is independent of this API mode. It grants
access to the gated model download; after installation the selected model runs
locally. Qwen's listed public file requires no token.

Choose **Remote server** in AI settings. OpenAI and Gemini presets are provided;
you can also enter an HTTPS OpenAI-compatible base URL and model ID. Supply your
own API key if the server requires one. The key remains in memory and must be
entered again in a later session. Endpoint and model ID can be remembered.
Photo recognition requires a vision-capable model. Billing, quotas and data
policies are those of the selected provider.

Recent submitted conversation turns and included resized photos go to the chosen
endpoint. The connection does not follow redirects with the authorization
header. A failed local request is never automatically uploaded.

## Your ChatGPT account

Choose **ChatGPT**, then **Continue with ChatGPT**. OpenAI's browser page handles
the account sign-in and the separate permission to use your ChatGPT plan. Each
person connects their own account. This route does not require an API key or
share the developer's subscription. Account/plan eligibility, provider limits and
availability apply; an identity-only sign-in does not enable plan inference.

After browser consent, use **Return to 8bitBites** if Android has not already
returned to the app. Refresh or select a model from the account's available
models in Settings. The chat composer identifies ChatGPT plan usage and offers
**Manage ChatGPT usage**; the first use also explains how allowance is consumed.
Plan-limit errors link to usage controls. The app does not silently switch to a
paid API or another account.

The implementation follows OpenAI's documented
[open-source sign-in flow](https://developers.openai.com/siwc/token-sharing-open-source/sign-in),
[profile/session rules](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
and [model/inference contract](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference).
It uses authorization-code PKCE, random state/nonce, a temporary loopback
callback, verified ID tokens and account-specific model discovery. Calls use
OpenAI's public Responses API, with streaming and `store:false`. A partial stream
is never accepted as a completed assistant response or meal draft.

OAuth connections are encrypted with Android Keystore in app-private storage
and excluded from backup. Refresh-token rotation is serialized and persisted;
sign-out attempts remote revocation and removes local credentials. If revocation
cannot be confirmed, the app tells you to revoke access in ChatGPT settings.
No browser cookies, ChatGPT conversations or memories are imported. See
[privacy details](PRIVACY.md) and OpenAI's
[plan usage explanation](https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites).

Host tests verify protocol handling and render the screens. **Real-account browser
sign-in, plan entitlement, live inference and the S25 return-to-app flow have not
been verified on a physical phone.** The same applies to device memory use and
local inference. A successful local build does not establish those outcomes.

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

## Local execution and error details

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
