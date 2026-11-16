package org.eightbit.bite.core

import com.google.gson.*
import java.net.URI
import java.net.URLDecoder
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.*

class SiwcTest {
    private val now=1_800_000_000L
    private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private fun b64(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun jwt(change:(JsonObject)->Unit={},headerAlg:String="RS256"):String {
        val header=b64("""{"alg":"$headerAlg","kid":"test"}""".toByteArray())
        val body=JsonObject().apply { addProperty("iss",Siwc.ISSUER);addProperty("sub","subject");addProperty("aud","oaiapp_test")
            addProperty("iat",now-10);addProperty("exp",now+3600);addProperty("nonce","nonce");addProperty("email","test@example.com");change(this) }
        val message=header+"."+b64(body.toString().toByteArray())
        return message+"."+b64(Signature.getInstance("SHA256withRSA").apply { initSign(pair.private);update(message.toByteArray()) }.sign())
    }
    private fun jwks():String {
        val key=pair.public as RSAPublicKey
        fun unsigned(bytes:ByteArray)=if(bytes.first()==0.toByte()) bytes.copyOfRange(1,bytes.size) else bytes
        return """{"keys":[{"kty":"RSA","kid":"test","use":"sig","alg":"RS256","n":"${b64(unsigned(key.modulus.toByteArray()))}","e":"${b64(unsigned(key.publicExponent.toByteArray()))}"}]}"""
    }
    @Test fun browserAuthorizationUsesPkceHostIdentityAndSeparatePlanPermission() {
        val attempt=SiwcAttempt("http://127.0.0.1:54321/auth/callback",null,"state","nonce","verifier")
        val uri=URI(Siwc.authorization(Siwc.ISSUER+"/api/accounts/authorize",attempt,"urn:uuid:host"))
        val query=uri.rawQuery.split('&').associate { it.split('=',limit=2).let { row -> row[0] to URLDecoder.decode(row[1],"UTF-8") } }
        assertEquals("dynamic_agent_client",query["client_id"]);assertEquals("8bitBites",query["agent_name_hint"])
        assertEquals("urn:uuid:host",query["ext_agent_host_id"]);assertEquals(attempt.redirect,query["redirect_uri"])
        assertTrue(query.getValue("scope").split(' ').contains("chatgpt.tokens.use.direct"))
        assertEquals(b64(MessageDigest.getInstance("SHA-256").digest("verifier".toByteArray())),query["code_challenge"])
        val returning=Siwc.authorization(Siwc.ISSUER+"/api/accounts/authorize",SiwcAttempt(attempt.redirect,"oaiapp_test"),"urn:uuid:host")
        assertFalse(returning.contains("agent_name_hint"));assertFalse(returning.contains("id_token_hint"))
    }
    @Test fun callbackRejectsStateMismatchDuplicatesAndChangedReturningRegistration() {
        val attempt=SiwcAttempt("http://127.0.0.1:43210/auth/callback",null,"state")
        assertEquals("code" to "oaiapp_test",Siwc.callback("code=code&state=state&client_id=oaiapp_test",attempt))
        for(query in listOf("code=code&state=wrong&client_id=oaiapp_test","code=one&code=two&state=state&client_id=oaiapp_test",
            "code=code&state=state&state=state&client_id=oaiapp_test","code=code&state=state&client_id=dynamic_agent_client"))
            assertFailsWith<SiwcException> { Siwc.callback(query,attempt) }
        val returning=SiwcAttempt(attempt.redirect,"oaiapp_test","state")
        assertEquals("code" to "oaiapp_test",Siwc.callback("code=code&state=state",returning))
        assertFailsWith<SiwcException> { Siwc.callback("code=code&state=state&client_id=oaiapp_other",returning) }
        assertEquals("access_denied",assertFailsWith<SiwcException> { Siwc.callback("error=access_denied&state=state",attempt) }.code)
    }
    @Test fun idTokenMustHaveAValidSignatureIssuerAudienceExpiryAndNonce() {
        assertEquals("subject",Siwc.verifyIdentity(jwt(),"oaiapp_test","nonce",jwks(),now).subject)
        assertFailsWith<SiwcException> { Siwc.verifyIdentity(jwt(),"oaiapp_other","nonce",jwks(),now) }
        assertFailsWith<SiwcException> { Siwc.verifyIdentity(jwt(),"oaiapp_test","other",jwks(),now) }
        assertFailsWith<SiwcException> { Siwc.verifyIdentity(jwt({it.addProperty("iss","https://example.com")}),"oaiapp_test","nonce",jwks(),now) }
        assertFailsWith<SiwcException> { Siwc.verifyIdentity(jwt({it.addProperty("exp",now-1000)}),"oaiapp_test","nonce",jwks(),now) }
        assertFailsWith<SiwcException> { Siwc.verifyIdentity(jwt(headerAlg="none"),"oaiapp_test","nonce",jwks(),now) }
    }
    @Test fun multipleAudiencesNeedTheMatchingAuthorizedParty() {
        val token=jwt({ it.add("aud",JsonArray().apply { add("oaiapp_test");add("other") }) })
        assertFailsWith<SiwcException> { Siwc.verifyIdentity(token,"oaiapp_test","nonce",jwks(),now) }
        val valid=jwt({ it.add("aud",JsonArray().apply { add("oaiapp_test");add("other") });it.addProperty("azp","oaiapp_test") })
        assertEquals("subject",Siwc.verifyIdentity(valid,"oaiapp_test","nonce",jwks(),now).subject)
    }
    @Test fun onlyVisibleAccountModelsAppearInTheirOriginalOrder() {
        val models=Siwc.models("""{"models":[{"slug":"second","display_name":"Second","visibility":"list"},{"slug":"hidden","visibility":"hide"},{"slug":"first","display_name":"First","visibility":"list"}]}""")
        assertEquals(listOf("second","first"),models.map { it.slug })
    }
}
