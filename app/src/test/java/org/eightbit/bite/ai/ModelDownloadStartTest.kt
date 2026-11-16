package org.eightbit.bite.ai

import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowJobScheduler
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,shadows=[DownloadSchedulerShadow::class])
class ModelDownloadStartTest {
    private val context get()=RuntimeEnvironment.getApplication() as Context
    private lateinit var library:ModelLibrary
    private lateinit var scheduler:JobScheduler
    private val id get()=library.catalogue.first().id
    private val jobId=820000

    @Before fun setup() {
        context.getSharedPreferences("model-library",Context.MODE_PRIVATE).edit().clear().commit()
        library=ModelLibrary(context)
        scheduler=context.getSystemService(JobScheduler::class.java)
        shadowOf(context.getSystemService(ConnectivityManager::class.java)).clearAllNetworks()
    }
    private fun queue() {
        library.status(id) { ModelStatus(phase="queued",wanted=true,generation="test") }
        scheduler.schedule(ModelDownloadJobs.transferJob(context,jobId,library.spec(id),"test",library.spec(id).bytes,true))
    }
    @Suppress("DEPRECATION")
    private fun network(unmetered:Boolean) {
        val connectivity=shadowOf(context.getSystemService(ConnectivityManager::class.java))
        val network=ShadowNetwork.newInstance(if(unmetered) 123 else 124)
        connectivity.addNetwork(network,ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            if(unmetered) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE,0,true,true))
        val capabilities=ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if(unmetered) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        connectivity.setNetworkCapabilities(network,capabilities)
    }

    @Test fun resumableGigabyteJobsDeclareASmallChunkAndPreserveNetworkChoice() {
        val spec=library.spec(id)
        val job=ModelDownloadJobs.transferJob(context,jobId,spec,"test",spec.bytes,true)
        assertTrue(job.isUserInitiated)
        assertEquals(spec.bytes,job.estimatedNetworkDownloadBytes)
        assertEquals(65536L,job.minimumNetworkChunkBytes)
        assertTrue(job.requiredNetwork!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        assertEquals(id,job.extras.getString("model"))
        val resumed=ModelDownloadJobs.transferJob(context,jobId,spec,"new",1234,false)
        assertEquals(1234L,resumed.minimumNetworkChunkBytes)
        assertFalse(resumed.requiredNetwork!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    @Test fun networkWaitEndsWhenAndroidReportsConstraintsSatisfied() {
        queue()
        val shadow=Shadow.extract<DownloadSchedulerShadow>(scheduler)
        shadow.reason=JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("waiting_network",library.current(id).phase)
        network(false)
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("waiting_network",library.current(id).phase)
        network(true)
        shadow.reason=JobScheduler.PENDING_JOB_REASON_UNDEFINED
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("queued",library.current(id).phase)
        assertTrue(library.current(id).wanted)
    }

    @Test fun androidDeferralAndNetworkDeferralAreDistinctAndDoNotOverwriteTransfers() {
        queue();network(true)
        val shadow=Shadow.extract<DownloadSchedulerShadow>(scheduler)
        shadow.reason=JobScheduler.PENDING_JOB_REASON_DEVICE_STATE
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("waiting_system",library.current(id).phase)
        shadow.reason=JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("waiting_network",library.current(id).phase)
        library.status(id) { it.copy(phase="downloading",downloaded=65536) }
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("downloading",library.current(id).phase)
        assertEquals(65536L,library.current(id).downloaded)
    }

    @Test fun missingAndroidJobBecomesResumablePauseWithPartialBytesKept() {
        queue()
        library.partial(id).apply { parentFile!!.mkdirs();writeBytes(ByteArray(1234)) }
        scheduler.cancel(jobId)
        ModelDownloadJobs.reconcile(context,library)
        assertEquals("paused",library.current(id).phase)
        assertFalse(library.current(id).wanted)
        assertEquals(1234L,library.current(id).downloaded)
        assertEquals(1234L,library.partial(id).length())
    }

    @Test fun progressShowsFirstBytesEvenWhenAPercentWouldStillBeZero() {
        val total=1597922856L
        assertTrue(modelDownloadAmount(0,total).startsWith("0 KB /"))
        assertTrue(modelDownloadAmount(1,total).startsWith("1 B /"))
        assertTrue(modelDownloadAmount(65536,total).startsWith("64.0 KB /"))
        assertTrue(modelDownloadAmount(2L*1024*1024,total).startsWith("2.0 MB /"))
    }
}

/** Robolectric has no pending-reason implementation; model the scheduler's documented response. */
@Implements(className="android.app.JobSchedulerImpl",isInAndroidSdk=false,minSdk=34)
class DownloadSchedulerShadow:ShadowJobScheduler.ShadowJobSchedulerImpl() {
    var reason=JobScheduler.PENDING_JOB_REASON_UNDEFINED
    @Implementation fun getPendingJobReason(jobId:Int)=
        if(getPendingJob(jobId)==null) JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID else reason
}
