package com.flarelane.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.flarelane.FlareLane
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val context: Context = this
    private var isSetTags: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        askNotificationPermission();
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.d("FlareLane", "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }
                val token = task.result
                Log.d("FlareLane", "Example FCM Token: $token")
            })

        findViewById<Button>(R.id.setUserIdButton).setOnClickListener(object :
            View.OnClickListener {
            var userId: String? = null
            override fun onClick(v: View) {
                FlareLane.setUserId(context, userId)
                userId = if (userId == null) "myuser@flarelane.com" else null
            }
        })

        findViewById<Button>(R.id.toggleTagsButton).setOnClickListener {
            try {
                if (isSetTags) {
                    val data = JSONObject()
                    data.put("age", JSONObject.NULL)
                    data.put("gender", JSONObject.NULL)
                    FlareLane.setTags(context, data)
                    isSetTags = false
                } else {
                    val data = JSONObject()
                    data.put("age", 27)
                    data.put("gender", "men")
                    FlareLane.setTags(context, data)
                    isSetTags = true
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        findViewById<Button>(R.id.trackEventButton).setOnClickListener {
            try {
                val data = JSONObject()
                data.put("num", 10)
                data.put("str", "hello world")
                FlareLane.trackEvent(context, "test_event", data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        findViewById<Button>(R.id.isSubscribedButton).setOnClickListener {
            val isSubscribed = FlareLane.isSubscribed(context)
            Log.d("FlareLane", "isSubscribed(): $isSubscribed")
        }

        findViewById<Button>(R.id.subscribeButton).setOnClickListener {
            FlareLane.subscribe(
                context,
                true
            ) { isSubscribed -> Log.d("FlareLane", "subscribe(): $isSubscribed") }
        }

        findViewById<Button>(R.id.unsubscribeButton).setOnClickListener {
            FlareLane.unsubscribe(context) { isSubscribed ->
                Log.d(
                    "FlareLane",
                    "unsubscribe(): $isSubscribed"
                )
            }
        }

        findViewById<Button>(R.id.btn_webView_bridge_test).setOnClickListener {
            startActivity(
                Intent(it.context, WebViewBridgeTestActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }

        findViewById<Button>(R.id.btn_url_notification).setOnClickListener {
            val testUrl = "https://www.google.com"
            val intent = Intent("com.google.android.c2dm.intent.RECEIVE")
            intent.putExtra("notificationId", "0")
            intent.putExtra("isFlareLane", true)
            intent.putExtra("title", "Click to open WebView")
            intent.putExtra("body", "url=${testUrl}")
            intent.putExtra("url", testUrl)
            intent.putExtra("data", "{}")
            intent.putExtra("from", "0")
            sendBroadcast(intent)
        }

        findViewById<Button>(R.id.btn_in_app_message).setOnClickListener {
            FlareLane.displayInApp(this, "home")
        }
    }

    // FOR FIREBASE: https://firebase.google.com/docs/cloud-messaging/android/client
    // Declare the launcher at the top of your Activity/Fragment:
    private val requestPermissionLauncher =
        registerForActivityResult<String, Boolean>(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // TODO: Inform user that that your app will not show notifications.
            }
        }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
