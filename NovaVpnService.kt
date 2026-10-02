package com.novashield.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class NovaVpnService : VpnService() {
    companion object { @Volatile var running=false }
    private var iface: ParcelFileDescriptor?=null
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int {
        if (iface==null) {
            // Safe local TUN shell. A real encrypted tunnel requires a compatible,
            // authorized VPN server/backend; this does not impersonate Avast Mimic.
            iface=Builder().setSession("NovaShield").addAddress("10.8.0.2",32).addRoute("0.0.0.0",0).establish()
            running=true
        }
        return START_STICKY
    }
    override fun onDestroy(){ iface?.close(); iface=null; running=false; super.onDestroy() }
}
