// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

import com.google.gson.*
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.*
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

class SiwcException(val code:String):IllegalStateException("ChatGPT: "+code.filter { it.isLetterOrDigit() || it=='_' }.take(100))
data class SiwcIdentity(val issuer:String,val subject:String,val email:String)
data class ChatGptModel(val slug:String,val name:String)
class SiwcAttempt(val redirect:String,val clientId:String?,val state:String=random(),val nonce:String=random(),val verifier:String=random()) {
    companion object {
        private fun random():String=ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    }
}

object Siwc {
    const val ISSUER="https://auth.openai.com"
    const val RESOURCE="https://api.openai.com/v1"
    const val SCOPE="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    fun form(values:Map<String,String>):String=values.entries.joinToString("&") { URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8") }
    fun authorization(endpoint:String,attempt:SiwcAttempt,hostId:String,email:String="",consent:Boolean=false):String {
        require(URI(endpoint).let { it.scheme=="https" && it.host=="auth.openai.com" && it.port in setOf(-1,443) && it.rawUserInfo==null && it.rawQuery==null })
        require(URI(attempt.redirect).let { it.scheme=="http" && it.host=="127.0.0.1" && it.path=="/auth/callback" && it.port>0 })
        val query=linkedMapOf("client_id" to (attempt.clientId ?: "dynamic_agent_client"),"ext_agent_host_id" to hostId,
            "response_type" to "code","redirect_uri" to attempt.redirect,"scope" to SCOPE,"resource" to RESOURCE,
            "state" to attempt.state,"nonce" to attempt.nonce,"code_challenge_method" to "S256",
            "code_challenge" to Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(attempt.verifier.toByteArray(Charsets.US_ASCII))))
        if(attempt.clientId==null) query["agent_name_hint"]="8bitBites"
        if(email.isNotBlank()) query["login_hint"]=email
        if(consent) query["prompt"]="consent"
        return endpoint+"?"+form(query)
    }
    fun callback(query:String,attempt:SiwcAttempt):Pair<String,String> {
        val values=query.split('&').filter { it.isNotBlank() }.map {
            val pair=it.split('=',limit=2);URLDecoder.decode(pair[0],"UTF-8") to URLDecoder.decode(pair.getOrElse(1){""},"UTF-8")
        }.groupBy({it.first},{it.second})
        val state=values["state"]?.singleOrNull() ?: throw SiwcException("invalid_state")
        if(!MessageDigest.isEqual(state.toByteArray(),attempt.state.toByteArray())) throw SiwcException("invalid_state")
        if(values.containsKey("error")) throw SiwcException(if(values["error"]?.singleOrNull()=="access_denied") "access_denied" else "authorization_failed")
        val code=values["code"]?.singleOrNull()?.takeIf { it.isNotBlank() && it.length<=16000 } ?: throw SiwcException("invalid_callback")
        val issued=values["client_id"]?.singleOrNull() ?: attempt.clientId ?: throw SiwcException("missing_client_id")
        if(values["client_id"].orEmpty().size>1 || issued=="dynamic_agent_client" || !Regex("[A-Za-z0-9_-]{1,250}").matches(issued) ||
            (attempt.clientId!=null && issued!=attempt.clientId)) throw SiwcException("invalid_client_id")
        return code to issued
    }

    fun verifyIdentity(token:String,clientId:String,nonce:String?,jwks:String,nowSeconds:Long=System.currentTimeMillis()/1000):SiwcIdentity {
        try {
            require(token.length<=40000)
            val parts=token.split('.');require(parts.size==3)
            val decoder=Base64.getUrlDecoder()
            val header=JsonParser.parseString(String(decoder.decode(parts[0]),Charsets.UTF_8)).asJsonObject
            val body=JsonParser.parseString(String(decoder.decode(parts[1]),Charsets.UTF_8)).asJsonObject
            require(header.get("alg")?.asString=="RS256")
            val kid=header.get("kid")?.asString ?: error("Missing key")
            val key=JsonParser.parseString(jwks).asJsonObject.getAsJsonArray("keys").map { it.asJsonObject }.single {
                it.get("kid")?.asString==kid && it.get("kty")?.asString=="RSA" && (!it.has("use") || it.get("use").asString=="sig") &&
                    (!it.has("alg") || it.get("alg").asString=="RS256") }
            val modulus=BigInteger(1,decoder.decode(key.get("n").asString));require(modulus.bitLength()>=2048)
            val publicKey=KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus,BigInteger(1,decoder.decode(key.get("e").asString))))
            require(Signature.getInstance("SHA256withRSA").apply { initVerify(publicKey);update((parts[0]+"."+parts[1]).toByteArray(Charsets.US_ASCII)) }.verify(decoder.decode(parts[2])))
            require(body.get("iss")?.asString==ISSUER)
            val aud=body.get("aud") ?: error("Missing audience")
            if(aud.isJsonArray) { require(aud.asJsonArray.any { it.asString==clientId });if(aud.asJsonArray.size()>1) require(body.get("azp")?.asString==clientId) }
            else require(aud.asString==clientId)
            require(body.get("exp").asLong>nowSeconds-60 && body.get("iat").asLong<=nowSeconds+60)
            if(body.has("nbf")) require(body.get("nbf").asLong<=nowSeconds+60)
            if(nonce!=null) require(body.get("nonce")?.asString==nonce)
            val sub=body.get("sub").asString;require(sub.isNotBlank() && sub.length<=500)
            return SiwcIdentity(ISSUER,sub,body.get("email")?.asString?.take(250).orEmpty())
        } catch(_:Exception) { throw SiwcException("invalid_id_token") }
    }
    fun models(body:String):List<ChatGptModel> = JsonParser.parseString(body).asJsonObject.getAsJsonArray("models")
        .map { it.asJsonObject }.filter { it.get("visibility")?.asString=="list" }.map {
            ChatGptModel(it.get("slug").asString,it.get("display_name")?.asString ?: it.get("slug").asString)
        }.filter { it.slug.isNotBlank() && it.slug.length<=200 && it.name.length<=250 }.distinctBy { it.slug }.take(100)
}
