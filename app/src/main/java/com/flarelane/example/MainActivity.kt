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
    private var isSetUserAttributes: Boolean = false
    private var isSubscribedState: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Seed toggle states from the persisted SDK values so the button labels
        // reflect reality on launch, instead of a stale `false` default.
        isSubscribedState = FlareLane.isSubscribed(context)

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

        // Toggle label convention: (set) = next tap will set, (del) = next tap will delete.
        val userIdButton = findViewById<Button>(R.id.setUserIdButton)
        userIdButton.text = "Toggle UserId (set)"
        userIdButton.setOnClickListener(object : View.OnClickListener {
            var userId: String? = null
            override fun onClick(v: View) {
                // Compute the next value first so the SDK call and the label both reflect
                // the action the user just took. Calling `setUserId(userId)` *before*
                // computing the next value would invert the meaning of the first tap
                // (label says "del" but no userId had been set).
                val nextUserId = if (userId == null) "myuser@flarelane.com" else null
                FlareLane.setUserId(context, nextUserId)
                userId = nextUserId
                userIdButton.text = "Toggle UserId (${if (userId != null) "del" else "set"})"
            }
        })

        val tagsButton = findViewById<Button>(R.id.toggleTagsButton)
        tagsButton.text = "Toggle Tags (set)"
        tagsButton.setOnClickListener {
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
                tagsButton.text = "Toggle Tags (${if (isSetTags) "del" else "set"})"
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

        val userAttributesButton = findViewById<Button>(R.id.setUserAttributesButton)
        userAttributesButton.text = "Toggle User Attributes (set)"
        userAttributesButton.setOnClickListener {
            try {
                val attributes = JSONObject()
                if (isSetUserAttributes) {
                    // Clear all attributes by setting them to null.
                    attributes.put("name", JSONObject.NULL)
                    attributes.put("email", JSONObject.NULL)
                    attributes.put("phoneNumber", JSONObject.NULL)
                    attributes.put("dob", JSONObject.NULL)
                    attributes.put("timeZone", JSONObject.NULL)
                    attributes.put("country", JSONObject.NULL)
                    attributes.put("language", JSONObject.NULL)
                    isSetUserAttributes = false
                } else {
                    attributes.put("name", "Test User")
                    attributes.put("email", "test@example.com")
                    attributes.put("phoneNumber", "+821012345678")
                    attributes.put("dob", "1990-01-01")
                    attributes.put("timeZone", "Asia/Seoul")
                    attributes.put("country", "KR")
                    attributes.put("language", "ko")
                    isSetUserAttributes = true
                }
                FlareLane.setUserAttributes(context, attributes)
                userAttributesButton.text =
                    "Toggle User Attributes (${if (isSetUserAttributes) "del" else "set"})"
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        findViewById<Button>(R.id.isSubscribedButton).setOnClickListener {
            val isSubscribed = FlareLane.isSubscribed(context)
            Log.d("FlareLane", "isSubscribed(): $isSubscribed")
        }

        val subscribeButton = findViewById<Button>(R.id.subscribeButton)
        subscribeButton.text = "Toggle Subscribe (${if (isSubscribedState) "del" else "set"})"
        subscribeButton.setOnClickListener {
            if (!isSubscribedState) {
                FlareLane.subscribe(context, true) { subscribed ->
                    Log.d("FlareLane", "subscribe(): $subscribed")
                    isSubscribedState = subscribed
                    runOnUiThread {
                        subscribeButton.text =
                            "Toggle Subscribe (${if (isSubscribedState) "del" else "set"})"
                    }
                }
            } else {
                FlareLane.unsubscribe(context) { subscribed ->
                    Log.d("FlareLane", "unsubscribe(): $subscribed")
                    isSubscribedState = subscribed
                    runOnUiThread {
                        subscribeButton.text =
                            "Toggle Subscribe (${if (isSubscribedState) "del" else "set"})"
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_webView_bridge_test).setOnClickListener {
            startActivity(
                Intent(it.context, WebViewBridgeTestActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }

        findViewById<Button>(R.id.btn_webView_test).setOnClickListener {
            startActivity(
                Intent(it.context, WebViewTestActivity::class.java).apply {
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
            val data = JSONObject()
            data.put("d1", 27)
            data.put("d2", "men")
            data.put("d3", JSONObject.NULL)

            FlareLane.displayInApp(this, "home", data)
        }

        FlareLane.displayInApp(this, "home");
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
