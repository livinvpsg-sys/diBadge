package com.fabxdi.dibadge.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps sending the phone's position into live-location chat messages until each share ends
 * (time is up, the person taps Stop, or the notification's Stop button is used).
 * Runs as a foreground service of type "location", so it keeps going with the app in the background.
 */
class LiveLocationService : Service() {

    private data class Share(val groupId: String, val messageId: String, val expiresAt: Long)

    private val shares = mutableListOf<Share>()
    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var updating = false
    private var lastWrite = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            push(location)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            endExpired()
            if (shares.isNotEmpty()) handler.postDelayed(this, 30_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (shares.isEmpty()) loadShares()

        when (intent?.action) {
            ACTION_START -> {
                val g = intent.getStringExtra(EXTRA_GROUP_ID)
                val m = intent.getStringExtra(EXTRA_MESSAGE_ID)
                val e = intent.getLongExtra(EXTRA_EXPIRES_AT, 0L)
                if (g != null && m != null) {
                    shares.removeAll { it.messageId == m }
                    shares.add(Share(g, m, if (e <= 0L) Long.MAX_VALUE else e))
                    saveShares()
                }
            }
            ACTION_STOP -> {
                val m = intent.getStringExtra(EXTRA_MESSAGE_ID)
                shares.filter { it.messageId == m }.forEach { end(it) }
            }
            ACTION_STOP_ALL -> shares.toList().forEach { end(it) }
            else -> Unit
        }
        endExpired()

        if (shares.isEmpty()) {
            // A start request must still show the notification once before the service stops
            if (intent?.action == ACTION_START) showForeground()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        showForeground()
        if (!hasPermission()) {
            shares.toList().forEach { end(it) }
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startUpdates()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 30_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        stopUpdates()
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- location

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (updating) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            try {
                lm.requestLocationUpdates(p, 10_000L, 10f, listener, Looper.getMainLooper())
                updating = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Send a first fix straight away if one is known
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { push(it) }
    }

    private fun stopUpdates() {
        try { locationManager?.removeUpdates(listener) } catch (_: Exception) {}
        updating = false
    }

    private fun push(loc: Location) {
        val now = System.currentTimeMillis()
        if (now - lastWrite < 8_000L) return
        lastWrite = now
        val data = mapOf<String, Any>(
            "locationLat" to loc.latitude,
            "locationLng" to loc.longitude,
            "locationAccuracy" to (if (loc.hasAccuracy()) loc.accuracy.toInt().coerceAtLeast(1) else 0),
            "liveUpdatedAt" to now
        )
        shares.filter { it.expiresAt > now }.forEach { s ->
            try {
                FirebaseFirestore.getInstance().collection("personalGroups").document(s.groupId)
                    .collection("messages").document(s.messageId)
                    .update(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---------------------------------------------------------------- ending shares

    private fun endExpired() {
        val now = System.currentTimeMillis()
        shares.filter { it.expiresAt <= now }.forEach { end(it) }
    }

    private fun end(s: Share) {
        try {
            FirebaseFirestore.getInstance().collection("personalGroups").document(s.groupId)
                .collection("messages").document(s.messageId)
                .update("isLiveEnded", true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        shares.remove(s)
        saveShares()
        if (shares.isEmpty()) {
            stopUpdates()
            handler.removeCallbacks(tick)
            stopForegroundCompat()
            stopSelf()
        }
    }

    // ---------------------------------------------------------------- saved shares (survive a restart)

    private fun prefs() = getSharedPreferences("live_location_shares", Context.MODE_PRIVATE)

    private fun saveShares() {
        val arr = JSONArray()
        shares.forEach { arr.put(JSONObject().put("g", it.groupId).put("m", it.messageId).put("e", it.expiresAt)) }
        prefs().edit().putString("shares", arr.toString()).apply()
    }

    private fun loadShares() {
        try {
            val arr = JSONArray(prefs().getString("shares", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                shares.add(Share(o.getString("g"), o.getString("m"), o.getLong("e")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- notification

    private fun showForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live location", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP_ALL),
            flags
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, flags)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Sharing your live location")
            .setContentText("Tap Stop to end sharing")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val ACTION_START = "com.fabxdi.dibadge.LIVE_LOCATION_START"
        private const val ACTION_STOP = "com.fabxdi.dibadge.LIVE_LOCATION_STOP"
        private const val ACTION_STOP_ALL = "com.fabxdi.dibadge.LIVE_LOCATION_STOP_ALL"
        private const val EXTRA_GROUP_ID = "groupId"
        private const val EXTRA_MESSAGE_ID = "messageId"
        private const val EXTRA_EXPIRES_AT = "expiresAt"
        private const val CHANNEL_ID = "live_location"
        private const val NOTIFICATION_ID = 7401

        /** Starts sharing for one message. [expiresAt] of Long.MAX_VALUE (or 0) means no time limit. */
        fun start(context: Context, groupId: String, messageId: String, expiresAt: Long) {
            val intent = Intent(context, LiveLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .putExtra(EXTRA_EXPIRES_AT, expiresAt)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops sharing for one message. */
        fun stop(context: Context, messageId: String) {
            val intent = Intent(context, LiveLocationService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}