// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.*
import android.os.*
import androidx.annotation.RequiresApi
import androidx.work.*
import kotlinx.coroutines.*
import org.eightbit.bite.*
import org.eightbit.bite.R
import org.eightbit.bite.core.ModelSpec
import java.util.UUID

object ModelDownloadJobs {
    private fun jobId(app:BiteApplication,id:String)=820000+app.models.catalogue.indexOfFirst { it.id==id }.also { require(it>=0) }
    private fun workName(id:String)="bite-model-$id"
    @RequiresApi(34)
    internal fun transferJob(context:Context,jobId:Int,spec:ModelSpec,generation:String,remainingBytes:Long,unmetered:Boolean):JobInfo {
        val network=NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).apply {
            if(unmetered) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }.build()
        return JobInfo.Builder(jobId,ComponentName(context,ModelDownloadJob::class.java))
            .setUserInitiated(true).setRequiredNetwork(network)
            .setEstimatedNetworkBytes(remainingBytes.coerceAtLeast(0),0)
            // Every written chunk can be resumed; the OS need not budget the whole GB-sized file at once.
            .setMinimumNetworkChunkBytes(remainingBytes.coerceIn(0,64L*1024))
            .setExtras(PersistableBundle().apply { putString("model",spec.id);putString("generation",generation) }).build()
    }
    suspend fun start(app:BiteApplication,id:String,token:String,unmetered:Boolean)=withContext(Dispatchers.IO) {
        val library=app.models;val spec=library.spec(id)
        require(!library.current(id).wanted) { "This model is already queued" }
        library.saveToken(id,token)
        require(!spec.gated || library.token(id).isNotBlank()) { "Enter a Hugging Face read token for this model" }
        val generation=UUID.randomUUID().toString()
        library.status(id) { it.copy(phase="queued",wanted=true,error="",generation=generation,unmetered=unmetered,attempts=0) }
        try {
            if(Build.VERSION.SDK_INT>=34) {
                val job=transferJob(app,jobId(app,id),spec,generation,spec.bytes-library.partial(id).length(),unmetered)
                check(app.getSystemService(JobScheduler::class.java).schedule(job)==JobScheduler.RESULT_SUCCESS) { "Android could not schedule this download. Try again while the app is open." }
            } else {
                val work=OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                    .setInputData(workDataOf("model" to id,"generation" to generation))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(if(unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED).build()).build()
                WorkManager.getInstance(app).enqueueUniqueWork(workName(id),ExistingWorkPolicy.REPLACE,work)
            }
        } catch(error:Exception) { library.status(id) { it.copy(phase="failed",wanted=false,error=error.message ?: "Could not start the download") };throw error }
    }
    fun pause(app:BiteApplication,id:String) {
        app.models.status(id) { it.copy(phase="paused",wanted=false,downloaded=app.models.partial(id).length()) }
        app.models.interrupt(id)
        if(Build.VERSION.SDK_INT>=34) app.getSystemService(JobScheduler::class.java).cancel(jobId(app,id))
        else WorkManager.getInstance(app).cancelUniqueWork(workName(id))
    }
    fun reconcile(app:BiteApplication)=reconcile(app,app.models)
    internal fun reconcile(context:Context,library:ModelLibrary) {
        val connectivity=context.getSystemService(ConnectivityManager::class.java)
        val capabilities=connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        for(spec in library.catalogue) {
            val state=library.current(spec.id)
            if(!state.wanted) continue
            val scheduler=if(Build.VERSION.SDK_INT>=34) context.getSystemService(JobScheduler::class.java) else null
            val scheduledId=820000+library.catalogue.indexOf(spec)
            if(scheduler!=null && scheduler.getPendingJob(scheduledId)==null) {
                library.status(spec.id) { if(it.generation==state.generation && it.wanted)
                    it.copy(phase="paused",wanted=false,downloaded=library.partial(spec.id).length()) else it }
                continue
            }
            if(state.phase in setOf("queued","waiting_network","waiting_system")) {
                val ready=capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true &&
                    (!state.unmetered || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                val reason=if(Build.VERSION.SDK_INT>=34) scheduler?.getPendingJobReason(scheduledId) else null
                val phase=when {
                    reason==JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY || (reason==null && !ready) -> "waiting_network"
                    reason!=null && reason>JobScheduler.PENDING_JOB_REASON_UNDEFINED -> "waiting_system"
                    else -> "queued"
                }
                if(phase!=state.phase) library.status(spec.id) { if(it.generation==state.generation && it.wanted && it.phase==state.phase)
                    it.copy(phase=phase) else it }
            }
        }
    }
    const val CHANNEL="model-downloads"
    fun notification(app:BiteApplication,id:String,done:Long=0,verifying:Boolean=false):Notification {
        val manager=app.getSystemService(NotificationManager::class.java)
        val turkish=app.getSharedPreferences("locale",Context.MODE_PRIVATE).getString("selected","en")=="tr"
        manager.createNotificationChannel(NotificationChannel(CHANNEL,if(turkish) "Model indirmeleri" else "Model downloads",NotificationManager.IMPORTANCE_LOW))
        val spec=app.models.spec(id)
        val open=PendingIntent.getActivity(app,jobId(app,id),Intent(app,MainActivity::class.java).putExtra("screen","models").setAction("org.eightbit.bite.models"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause=PendingIntent.getBroadcast(app,jobId(app,id),Intent(app,PauseModelDownload::class.java).putExtra("model",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_model_download).setContentTitle(spec.name)
            .setContentText(if(verifying) {if(turkish) "Model doğrulanıyor…" else "Verifying model…"} else modelDownloadAmount(done,spec.bytes))
            .setProgress(100,(100*done/spec.bytes).toInt(),verifying).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(open).addAction(Notification.Action.Builder(null,if(turkish) "Duraklat" else "Pause",pause).build()).build()
    }
    fun notificationId(app:BiteApplication,id:String)=jobId(app,id)
    fun progress(app:BiteApplication,id:String,done:Long,verifying:Boolean) {
        if(Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(app,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
            app.getSystemService(NotificationManager::class.java).notify(notificationId(app,id),notification(app,id,done,verifying))
    }
}

@RequiresApi(34)
class ModelDownloadJob:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val jobs=mutableMapOf<Int,Pair<String,Job>>()
    override fun onStartJob(params:JobParameters):Boolean {
        val app=application as BiteApplication
        val id=params.extras.getString("model") ?: return false
        val generation=params.extras.getString("generation") ?: return false
        if(!app.models.desired(id,generation)) return false
        val notificationId=ModelDownloadJobs.notificationId(app,id)
        setNotification(params,notificationId,ModelDownloadJobs.notification(app,id,app.models.partial(id).length()),JOB_END_NOTIFICATION_POLICY_REMOVE)
        val running=scope.launch {
            try {
                val network=params.network
                val result=if(network==null) {
                    app.models.status(id) { if(it.generation==generation && it.wanted) it.copy(phase="waiting_network") else it }
                    DownloadOutcome.RETRY
                } else ModelDownloadEngine(app,app.models,network).run(id,generation) { done,verifying ->
                    ModelDownloadJobs.progress(app,id,done,verifying)
                }
                jobFinished(params,result==DownloadOutcome.RETRY)
            } finally {
                if(jobs[params.jobId]?.first==generation) jobs.remove(params.jobId)
            }
        }
        jobs[params.jobId]=generation to running
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {
        val generation=params.extras.getString("generation").orEmpty()
        if(jobs[params.jobId]?.first==generation) jobs.remove(params.jobId)?.second?.cancel()
        val app=application as BiteApplication
        val id=params.extras.getString("model") ?: return false
        if(app.models.desired(id,generation)) app.models.interrupt(id)
        app.models.status(id) { if(it.wanted && it.generation==params.extras.getString("generation"))
            it.copy(phase="waiting",error="Android paused the transfer. You can retry now; the partial model is kept.") else it }
        return app.models.desired(id,params.extras.getString("generation").orEmpty())
    }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
class ModelDownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as BiteApplication
        val id=inputData.getString("model") ?: return Result.failure()
        val generation=inputData.getString("generation") ?: return Result.failure()
        if(!app.models.desired(id,generation)) return Result.success()
        val notificationId=ModelDownloadJobs.notificationId(app,id)
        val notification=ModelDownloadJobs.notification(app,id,app.models.partial(id).length())
        try {
            setForeground(if(Build.VERSION.SDK_INT>=29) ForegroundInfo(notificationId,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(notificationId,notification))
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) {
            app.models.status(id) { if(it.generation==generation && it.wanted) it.copy(phase="failed",wanted=false,
                error="Android could not start the download. Reopen the app and tap Resume.") else it }
            return Result.failure()
        }
        val outcome=ModelDownloadEngine(app,app.models).run(id,generation) { done,verifying ->
            ModelDownloadJobs.progress(app,id,done,verifying)
        }
        return if(outcome==DownloadOutcome.RETRY) Result.retry() else Result.success()
    }
}
class PauseModelDownload:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val app=context.applicationContext as BiteApplication
        val id=intent.getStringExtra("model") ?: return
        if(app.models.catalogue.any { it.id==id }) ModelDownloadJobs.pause(app,id)
    }
}
