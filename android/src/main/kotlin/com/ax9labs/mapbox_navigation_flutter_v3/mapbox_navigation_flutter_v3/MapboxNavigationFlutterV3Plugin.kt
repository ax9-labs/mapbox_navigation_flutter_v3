package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.app.Activity
import android.content.Intent
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

/** MapboxNavigationFlutterV3Plugin */
class MapboxNavigationFlutterV3Plugin :
    FlutterPlugin,
    MethodCallHandler,
    ActivityAware,
    ActivityResultListener {
    // The MethodChannel that will the communication between Flutter and native Android
    //
    // This local reference serves to register the plugin with the Flutter Engine and unregister it
    // when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var pendingResult: Result? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "mapbox_navigation_flutter_v3")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: Result
    ) {
        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
            "initialize" -> {
                val accessToken = call.argument<String>("accessToken")
                if (accessToken.isNullOrBlank()) {
                    result.error("NO_TOKEN", "accessToken is required", null)
                    return
                }
                MapboxAccessToken.value = accessToken
                result.success(null)
            }
            "startNavigation" -> startNavigation(call, result)
            else -> result.notImplemented()
        }
    }

    private fun startNavigation(
        call: MethodCall,
        result: Result
    ) {
        val currentActivity = activity
        if (currentActivity == null) {
            result.error("NO_ACTIVITY", "startNavigation called with no attached Activity", null)
            return
        }
        if (MapboxAccessToken.value == null) {
            result.error("NOT_INITIALIZED", "Call initialize() before startNavigation()", null)
            return
        }
        if (pendingResult != null) {
            result.error("ALREADY_NAVIGATING", "A navigation session is already in progress", null)
            return
        }

        @Suppress("UNCHECKED_CAST")
        val waypoints = call.argument<List<Map<String, Any?>>>("waypoints")
        @Suppress("UNCHECKED_CAST")
        val options = call.argument<Map<String, Any?>>("options") ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val rawMarkers = call.argument<List<Map<String, Any?>>>("markers") ?: emptyList()

        if (waypoints.isNullOrEmpty()) {
            result.error("NO_WAYPOINTS", "At least one waypoint is required", null)
            return
        }

        // Decoded here (not in NavigationActivity) and handed off via
        // PendingNavigationMarkers rather than the launch Intent - base64
        // icon bytes in an Intent extra risk TransactionTooLargeException
        // once real icons are involved (Android's Binder transaction
        // buffer is ~1MB total). See PendingNavigationMarkers for the full
        // rationale.
        PendingNavigationMarkers.value = MarkerDecoder.decodeMarkers(rawMarkers)

        pendingResult = result
        val intent =
            NavigationActivity.newIntent(
                context = currentActivity,
                waypoints = waypoints,
                options = options
            )
        currentActivity.startActivityForResult(intent, NAVIGATION_REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode != NAVIGATION_REQUEST_CODE) return false

        val outcome = data?.getStringExtra(NavigationActivity.EXTRA_RESULT) ?: "error"
        Log.d(TAG, "Navigation session finished: $outcome")
        pendingResult?.success(outcome)
        pendingResult = null
        return true
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityBinding = binding
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        detachActivity()
    }

    private fun detachActivity() {
        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
        activity = null
    }

    companion object {
        private const val TAG = "MapboxNavFlutterV3Plugin"
        private const val NAVIGATION_REQUEST_CODE = 0x4E4156 // "NAV"
    }
}
