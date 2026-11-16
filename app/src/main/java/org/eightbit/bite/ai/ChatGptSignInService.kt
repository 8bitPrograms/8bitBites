// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import org.eightbit.bite.BiteApplication
import org.eightbit.bite.MainActivity
import org.eightbit.bite.R

/** Keeps the loopback receiver runnable while the user is in their browser. */
class ChatGptSignInService:Service() {
    override fun onCreate() {
        super.onCreate()
        val turkish=resources.configuration.locales[0].language=="tr"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("chatgpt-sign-in",if(turkish) "ChatGPT bağlantısı" else "ChatGPT connection",NotificationManager.IMPORTANCE_LOW))
        val back=PendingIntent.getActivity(this,9101,Intent(this,MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("eightbitbites://settings/ai")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel=PendingIntent.getService(this,9102,Intent(this,ChatGptSignInService::class.java).setAction("cancel"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"chatgpt-sign-in").setSmallIcon(R.drawable.ic_ai_chatgpt)
            .setContentTitle(if(turkish) "ChatGPT'ye bağlanılıyor" else "Connecting to ChatGPT")
            .setContentText(if(turkish) "Tarayıcıdaki girişi tamamla" else "Complete sign-in in your browser")
            .setContentIntent(back).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,if(turkish) "İptal" else "Cancel",cancel).build()).build()
        if(Build.VERSION.SDK_INT>=29) startForeground(9101,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(9101,notification)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="cancel") { (application as BiteApplication).chatGpt.cancelSignIn();stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) {
        (application as BiteApplication).chatGpt.cancelSignIn(timedOut=true)
        stopSelf()
    }
    override fun onDestroy() { stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
