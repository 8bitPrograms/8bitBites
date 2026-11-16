package org.eightbit.bite.core
import java.net.URI
import kotlin.test.*

class ModelDownloadsTest {
    @Test fun resumesOnlyTheExactPinnedRange() {
        assertEquals(ResumeResponse.APPEND,ModelDownloadPolicy.response(206,"bytes 10-99/100",10,100))
        assertEquals(ResumeResponse.RESTART,ModelDownloadPolicy.response(200,null,10,100))
        assertEquals(ResumeResponse.VERIFY,ModelDownloadPolicy.response(416,null,100,100))
        assertEquals(ResumeResponse.APPEND,ModelDownloadPolicy.response(206,"bytes 10-90/100",10,100))
        for(range in listOf("bytes 0-99/100","bytes 10-9/100","bytes 10-100/101",null))
            assertFails { ModelDownloadPolicy.response(206,range,10,100) }
        assertFails { ModelDownloadPolicy.response(416,null,99,100) }
    }
    @Test fun tokenNeverFollowsCdnRedirects() {
        assertTrue(ModelDownloadPolicy.allowedUrl(URI("https://us.aws.cdn.hf.co/file?signature=test")))
        assertFalse(ModelDownloadPolicy.sendToken(URI("https://us.aws.cdn.hf.co/file")))
        assertTrue(ModelDownloadPolicy.sendToken(URI("https://huggingface.co/repo")))
        for(url in listOf("http://huggingface.co/file","https://huggingface.co.evil.test/file","https://secret@huggingface.co/file","https://127.0.0.1/file","https://huggingface.co:8080/file"))
            assertFalse(ModelDownloadPolicy.allowedUrl(URI(url)))
    }
    @Test fun modelPathsAndPinsAreValidated() {
        fun spec(file:String="model.litertlm",revision:String="a".repeat(40))=ModelSpec("model","Model","owner/repo",revision,file,1024,"b".repeat(64),false,false,"apache-2.0",4)
        assertContains(spec().url,"/resolve/"+"a".repeat(40)+"/")
        for(name in listOf("../model.litertlm","model.gguf","%2e%2e.litertlm","foo/bar.litertlm")) assertFails { spec(name) }
        assertFails { spec(revision="main") }
    }
    @Test fun sevenQuickVersionTapsUnlockAndSlowTapsReset() {
        val unlock=VersionTapUnlock()
        repeat(6) { assertFalse(unlock.tap(it*100L)) };assertTrue(unlock.tap(600))
        assertFalse(unlock.tap(700));assertFalse(unlock.tap(5000))
        repeat(5) { assertFalse(unlock.tap(5100+it*100L)) };assertTrue(unlock.tap(5600))
    }
}
