package com.example.waschedule

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SendService : AccessibilityService() {
    companion object { @Volatile var armedAt = 0L }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        // Clicca solo entro 30s da un alarm nostro, mai a caso.
        if (System.currentTimeMillis() - armedAt > 30_000) return
        val btn = rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")?.firstOrNull() ?: return
        if (btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            armedAt = 0
            Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 1500)
        }
    }

    override fun onInterrupt() {}
}
