// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

import java.net.URI

data class ModelSpec(val id:String,val name:String,val repository:String,val revision:String,
    val filename:String,val bytes:Long,val sha256:String,val vision:Boolean,val gated:Boolean,val license:String,val memoryGb:Int) {
    init {
        require(Regex("[a-z0-9-]{1,60}").matches(id))
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository) && ".." !in repository)
        require(Regex("[a-f0-9]{40}").matches(revision))
        require(Regex("[A-Za-z0-9_.-]+\\.litertlm").matches(filename) && ".." !in filename)
        require(bytes in 1024..8L*1024*1024*1024 && Regex("[a-f0-9]{64}").matches(sha256))
    }
    val url get()="https://huggingface.co/$repository/resolve/$revision/$filename"
    val page get()="https://huggingface.co/$repository"
}

enum class ResumeResponse { APPEND, RESTART, VERIFY }
object ModelDownloadPolicy {
    fun response(code:Int,range:String?,offset:Long,total:Long):ResumeResponse {
        require(offset in 0..total)
        if(code==416 && offset==total) return ResumeResponse.VERIFY
        if(code==200) return ResumeResponse.RESTART
        require(code==206) { "Model server returned HTTP $code" }
        val parts=Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(range.orEmpty())?.groupValues
            ?: error("Invalid download range")
        require(parts[1].toLong()==offset && parts[2].toLong() in offset until total && parts[3].toLong()==total) { "The server returned a different model range" }
        return ResumeResponse.APPEND
    }
    fun allowedUrl(uri:URI):Boolean {
        val host=uri.host?.lowercase() ?: return false
        return uri.scheme=="https" && uri.rawUserInfo==null && uri.port in setOf(-1,443) &&
            (host=="huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co"))
    }
    fun sendToken(uri:URI)=uri.scheme=="https" && uri.host=="huggingface.co" && uri.rawUserInfo==null
}

class VersionTapUnlock(private val required:Int=7,private val intervalMs:Long=3000) {
    private var count=0
    private var last:Long?=null
    fun tap(now:Long):Boolean {
        val previous=last
        count=if(previous==null || now<previous || now-previous>intervalMs) 1 else count+1
        last=now
        if(count<required) return false
        count=0;last=null;return true
    }
}
