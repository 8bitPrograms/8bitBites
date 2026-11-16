// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.*
import java.util.UUID

internal data class ChatGptAccount(val id:String,val clientId:String,val subject:String="",val email:String="",
    val access:String="",val refresh:String="",val idToken:String="",val expires:Long=0,val scopes:String="",
    val model:String="",val models:List<ChatGptModel> = emptyList(),val pending:String="",val receivedAt:Long=0) {
    val connected get()=subject.isNotBlank() && idToken.isNotBlank()
    val sharing get()=connected && "chatgpt.tokens.use.direct" in scopes.split(' ') && "resource.invoke" in scopes.split(' ')
    override fun toString()="ChatGptAccount(id=$id,connected=$connected)"
    fun json():JSONObject=JSONObject().put("id",id).put("clientId",clientId).put("subject",subject).put("email",email)
        .put("access",access).put("refresh",refresh).put("idToken",idToken).put("expires",expires).put("scopes",scopes)
        .put("model",model).put("pending",pending).put("receivedAt",receivedAt)
        .put("models",JSONArray().apply { models.forEach { put(JSONObject().put("slug",it.slug).put("name",it.name)) } })
    companion object {
        fun read(row:JSONObject)=ChatGptAccount(row.getString("id"),row.getString("clientId"),row.optString("subject"),row.optString("email"),
            row.optString("access"),row.optString("refresh"),row.optString("idToken"),row.optLong("expires"),row.optString("scopes"),
            row.optString("model"),row.optJSONArray("models")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { m -> ChatGptModel(m.getString("slug"),m.getString("name")) } } }.orEmpty(),
            row.optString("pending"),row.optLong("receivedAt"))
    }
}
internal data class ChatGptProfile(val id:String,val label:String,val connected:Boolean,val sharing:Boolean)
internal data class ChatGptState(val profiles:List<ChatGptProfile> = emptyList(),val activeId:String="",val email:String="",
    val connected:Boolean=false,val sharing:Boolean=false,val models:List<ChatGptModel> = emptyList(),val model:String="",
    val busy:Boolean=false,val error:String?=null,val welcome:Boolean=false,val stage:String="")

internal class ChatGptConnection(private val context:Context) {
    private val transport=ChatGptTransport()
    private val vault=ChatGptVault(context)
    private val lock=Mutex()
    private val hostPrefs=context.getSharedPreferences("chatgpt-host",Context.MODE_PRIVATE)
    private val accounts=mutableListOf<ChatGptAccount>()
    private val signInScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var signInJob:Job?=null
    @Volatile private var timedOut=false
    private var active=""
    private var provider:JSONObject?=null
    val state=MutableStateFlow(ChatGptState())
    init {
        try { vault.read()?.let { body -> val doc=JSONObject(body);active=doc.optString("active")
            val rows=doc.getJSONArray("accounts");(0 until rows.length()).forEach { accounts.add(ChatGptAccount.read(rows.getJSONObject(it))) } }
            publish()
        } catch(_:Exception) { state.value=ChatGptState(error="credential_storage_unavailable") }
    }
    private fun selected()=accounts.firstOrNull { it.id==active }
    private fun publish(error:String?=null,busy:Boolean=state.value.busy,stage:String=state.value.stage) {
        val a=selected()
        state.value=ChatGptState(accounts.mapIndexed { index,row -> ChatGptProfile(row.id,
            (row.email.ifBlank { "ChatGPT" })+" · "+(index+1),row.connected,row.sharing) },active,a?.email.orEmpty(),
            a?.connected==true,a?.sharing==true,a?.models.orEmpty(),a?.model.orEmpty(),busy,error,
            a?.sharing==true && !hostPrefs.getBoolean("welcome:"+a.id,false),if(busy) stage else "")
    }
    private fun persist() { vault.write(JSONObject().put("active",active).put("accounts",JSONArray().apply { accounts.forEach { put(it.json()) } }).toString()) }
    fun failure(error:Exception) { publish(error=code(error),busy=false) }
    fun acknowledgeWelcome() { selected()?.let { hostPrefs.edit().putBoolean("welcome:"+it.id,true).apply() };publish(error=state.value.error) }
    private fun replace(account:ChatGptAccount) { val index=accounts.indexOfFirst { it.id==account.id };if(index<0) accounts.add(account) else accounts[index]=account;persist();publish() }
    private suspend fun discovery():JSONObject {
        provider?.let { return it }
        val doc=transport.json(Siwc.ISSUER+"/.well-known/openid-configuration")
        if(doc.optString("issuer")!=Siwc.ISSUER) throw SiwcException("invalid_provider")
        for(key in listOf("authorization_endpoint","token_endpoint","jwks_uri","revocation_endpoint")) {
            val uri=URI(doc.getString(key));if(uri.scheme!="https" || uri.host!="auth.openai.com" || uri.port !in setOf(-1,443) || uri.rawUserInfo!=null) throw SiwcException("invalid_provider")
        }
        provider=doc;return doc
    }
    private fun hostId():String=hostPrefs.getString("id",null) ?: ("urn:uuid:"+UUID.randomUUID()).also { check(hostPrefs.edit().putString("id",it).commit()) }

    @Synchronized fun startSignIn(profileId:String?=null,consent:Boolean=false) {
        if(signInJob?.isActive==true || state.value.busy) return
        timedOut=false
        signInJob=signInScope.launch { signIn(profileId,consent) }
    }
    fun cancelSignIn(timedOut:Boolean=false) { this.timedOut=timedOut;signInJob?.cancel() }
    private fun step(stage:String) { publish(stage=stage);Log.i("BiteChatGpt","sign_in stage=$stage") }

    suspend fun signIn(profileId:String?=null,consent:Boolean=false)=lock.withLock {
        publish(busy=true)
        try {
            withContext(Dispatchers.Main) { context.startForegroundService(Intent(context,ChatGptSignInService::class.java)) }
            step("discovery")
            require(profileId!=null || accounts.size<12) { "Too many saved ChatGPT accounts" }
            val previous=accounts.firstOrNull { it.id==profileId }
            val doc=discovery()
            val server=ServerSocket().apply { reuseAddress=false;bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));soTimeout=1000 }
            val attempt=SiwcAttempt("http://127.0.0.1:${server.localPort}/auth/callback",previous?.clientId)
            val callback=try { withTimeout(10*60*1000L) { coroutineScope {
                val listener=async(Dispatchers.IO) { receive(server,attempt) }
                try {
                    val url=Siwc.authorization(doc.getString("authorization_endpoint"),attempt,hostId(),previous?.email.orEmpty(),consent)
                    step("browser")
                    withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    listener.await()
                } finally { server.close() }
            } } } finally { server.close() }
            val registration=previous ?: ChatGptAccount(UUID.randomUUID().toString(),callback.second)
            step("exchange")
            // Keep an issued registration even if its one-time code expires or exchange fails.
            replace(registration)
            val tokens=transport.json(doc.getString("token_endpoint"),mapOf("grant_type" to "authorization_code","client_id" to callback.second,
                "code" to callback.first,"code_verifier" to attempt.verifier,"redirect_uri" to attempt.redirect,"resource" to Siwc.RESOURCE))
            val idToken=tokens.optString("id_token").takeIf { it.isNotBlank() } ?: throw SiwcException("invalid_id_token")
            step("identity")
            val identity=Siwc.verifyIdentity(idToken,callback.second,attempt.nonce,transport.json(doc.getString("jwks_uri")).toString())
            if(previous?.subject?.isNotBlank()==true && identity.subject!=previous.subject) throw SiwcException("account_mismatch")
            val account=credentials(tokens,registration).copy(subject=identity.subject,email=identity.email,idToken=idToken,pending="")
            replace(account);active=account.id;persist();publish()
            if(account.sharing) { step("models");loadModels(account) }
            Log.i("BiteChatGpt","sign_in complete connected=${account.connected} sharing=${account.sharing}")
        } catch(cancelled:CancellationException) { publish(error=if(cancelled is TimeoutCancellationException || timedOut) "login_timeout" else null);if(cancelled !is TimeoutCancellationException) throw cancelled }
        catch(error:Exception) { Log.w("BiteChatGpt","sign_in failed stage=${state.value.stage} code=${code(error)} kind=${error.javaClass.simpleName}");publish(error=code(error)) }
        finally {
            withContext(NonCancellable+Dispatchers.Main) { context.stopService(Intent(context,ChatGptSignInService::class.java)) }
            publish(error=state.value.error,busy=false)
        }
    }
    internal suspend fun receive(server:ServerSocket,attempt:SiwcAttempt,headerTimeoutMs:Int=3000):Pair<String,String> {
        val job=currentCoroutineContext()
        while(true) {
            job.ensureActive()
            val socket=try { server.accept() } catch(_:SocketTimeoutException) { continue }
            socket.use {
                it.soTimeout=headerTimeoutMs
                val reader=it.getInputStream().bufferedReader(Charsets.US_ASCII)
                val head=StringBuilder()
                // Browsers may open an idle speculative connection before the real callback.
                try { while(!head.endsWith("\r\n\r\n") && head.length<16384) { job.ensureActive();val ch=reader.read();if(ch<0) break;head.append(ch.toChar()) } }
                catch(_:IOException) { job.ensureActive();return@use }
                val target=head.toString().lineSequence().first().split(' ').takeIf { parts -> parts.size==3 && parts[0]=="GET" }?.get(1)
                if(target==null || !target.startsWith("/auth/callback?")) { http(it,404,"Not found");return@use }
                val result=try { Siwc.callback(target.substringAfter('?'),attempt) }
                catch(error:SiwcException) { http(it,400,"Sign-in did not complete. Return to 8bitBites.");if(error.code=="invalid_state") return@use else throw error }
                http(it,200,ChatGptCallbackPage.render(context))
                return result
            }
        }
    }
    private fun http(socket:Socket,status:Int,body:String) {
        val bytes=body.toByteArray(Charsets.UTF_8)
        val header="HTTP/1.1 $status ${if(status==200) "OK" else "Bad Request"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        // A browser closing its tab must not discard an already validated callback.
        try { socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII));write(bytes);flush() } }
        catch(_:IOException) { }
    }
    private fun credentials(doc:JSONObject,previous:ChatGptAccount,refreshing:Boolean=false):ChatGptAccount {
        val scopes=if(refreshing && !doc.has("scope")) previous.scopes else doc.optString("scope")
        if(scopes.isBlank()) throw SiwcException("missing_scopes")
        val access=doc.optString("access_token");val refresh=doc.optString("refresh_token")
        val expires=doc.optDouble("expires_in",0.0)
        if(access.isBlank()) {
            if("chatgpt.tokens.use.direct" in scopes || "offline_access" in scopes || refreshing) throw SiwcException("invalid_token_response")
        } else if(doc.optString("token_type").lowercase()!="bearer" || !expires.isFinite() || expires<=0 || expires>366*24*3600 ||
            access.length>40000 || refresh.length>40000 || access.any { it=='\r' || it=='\n' } || ((refreshing || "offline_access" in scopes) && refresh.isBlank())) throw SiwcException("invalid_token_response")
        return previous.copy(access=access,refresh=refresh,idToken=doc.optString("id_token",previous.idToken),
            scopes=scopes,expires=System.currentTimeMillis()+(expires*1000).toLong(),receivedAt=System.currentTimeMillis())
    }
    private suspend fun ready(account:ChatGptAccount):ChatGptAccount {
        val doc=discovery()
        var value=account
        if(value.pending.isNotBlank()) {
            val candidate=ChatGptAccount.read(JSONObject(value.pending))
            val identity=Siwc.verifyIdentity(candidate.idToken,candidate.clientId,null,transport.json(doc.getString("jwks_uri")).toString(),candidate.receivedAt/1000)
            if(identity.subject!=value.subject) throw SiwcException("account_mismatch")
            value=candidate.copy(email=identity.email,pending="");replace(value)
        }
        if(value.expires>System.currentTimeMillis()+60000 && value.access.isNotBlank()) return value
        if(value.refresh.isBlank()) throw SiwcException("sign_in_required")
        val tokens=try { transport.json(doc.getString("token_endpoint"),mapOf("grant_type" to "refresh_token","client_id" to value.clientId,
            "refresh_token" to value.refresh,"resource" to Siwc.RESOURCE)) } catch(error:SiwcException) {
            if(error.code=="invalid_grant") replace(value.copy(access="",refresh="",idToken="",pending=""));throw error
        }
        val candidate=credentials(tokens,value,true)
        if(tokens.has("id_token")) {
            replace(value.copy(pending=candidate.json().toString()))
            val identity=Siwc.verifyIdentity(candidate.idToken,candidate.clientId,null,transport.json(doc.getString("jwks_uri")).toString())
            if(identity.subject!=value.subject) throw SiwcException("account_mismatch")
            value=candidate.copy(email=identity.email,pending="")
        } else value=candidate.copy(pending="")
        replace(value);return value
    }
    private suspend fun loadModels(account:ChatGptAccount) {
        val value=ready(account)
        if(!value.sharing) throw SiwcException("plan_permission_required")
        val models=Siwc.models(transport.json(Siwc.RESOURCE+"/models",token=value.access).toString())
        replace(value.copy(models=models,model=value.model.takeIf { old -> models.any { it.slug==old } } ?: models.firstOrNull()?.slug.orEmpty()))
        if(models.isEmpty()) throw SiwcException("no_models")
    }
    suspend fun reloadModels()=lock.withLock { publish(busy=true);try { loadModels(selected() ?: throw SiwcException("sign_in_required")) }
        catch(error:Exception) { if(error is CancellationException) throw error;publish(error=code(error)) } finally { publish(error=state.value.error,busy=false) } }
    suspend fun select(id:String)=lock.withLock { require(accounts.any { it.id==id });active=id;persist();publish() }
    suspend fun selectModel(slug:String)=lock.withLock { val account=selected() ?: return@withLock;require(account.models.any { it.slug==slug });replace(account.copy(model=slug)) }
    suspend fun signOut()=lock.withLock {
        val account=selected() ?: return@withLock;publish(busy=true)
        var revoked=account.refresh.isBlank()
        try { val token=if(account.pending.isNotBlank()) ChatGptAccount.read(JSONObject(account.pending)).refresh else account.refresh
            if(token.isNotBlank()) {
                val endpoint=discovery().getString("revocation_endpoint")
                val body=mapOf("token" to token,"token_type_hint" to "refresh_token","client_id" to account.clientId)
                try { transport.json(endpoint,body) }
                catch(error:Exception) {
                    if(error is CancellationException) throw error
                    if(error is IOException || (error is SiwcException && error.code in setOf("server_error","temporarily_unavailable","http_500","http_502","http_503","http_504"))) {
                        delay(500);transport.json(endpoint,body)
                    } else throw error
                }
                revoked=true
            }
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) { }
        finally { replace(account.copy(access="",refresh="",idToken="",expires=0,pending="",scopes="",models=emptyList(),model=""));publish(error=if(revoked) null else "revocation_unconfirmed",busy=false) }
    }
    suspend fun chat(turns:List<AiChatTurn>):String {
        val account=lock.withLock { ready(selected() ?: throw SiwcException("sign_in_required")) }
        if(!account.sharing) throw SiwcException("plan_permission_required")
        if(account.model.isBlank() || account.models.none { it.slug==account.model }) throw SiwcException("select_model")
        return transport.response(account.access,account.model,turns)
    }
    private fun code(error:Exception)=if(error is SiwcException && Regex("[a-zA-Z0-9_]{1,100}").matches(error.code)) error.code else "connection_failed"
}
