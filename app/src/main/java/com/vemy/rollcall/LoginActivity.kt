package com.vemy.rollcall

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.Interceptor.*
import org.json.JSONObject
import java.math.BigInteger


class RedirectLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request: Request = chain.request()
        println("Sending request to: ${request.url}")

        var response: Response = chain.proceed(request)

        while (response.isRedirect) {
            val location = response.header("Location")
            println("Redirected to: $location")

            request = request.newBuilder()
                .url(location!!)
                .build()
            response.close()
            response = chain.proceed(request)
        }

        return response
    }
}

object HttpClientManager {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(SimpleCookieJar())
            //.addInterceptor(RedirectLoggingInterceptor())
            //.callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            //.protocols(Arrays.asList(Protocol.HTTP_1_1))
            .build()
    }
}

class LoginActivity : AppCompatActivity() {

    private val sharedPreferences by lazy {
        getSharedPreferences("login_info", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameEditText = findViewById<EditText>(R.id.username)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val loadingSpinner = findViewById<ProgressBar>(R.id.loadingSpinner)

        // 尝试从 SharedPreferences 中读取用户名和密码
        val savedUsername = sharedPreferences.getString("username", null)
        val savedPassword = sharedPreferences.getString("password", null)

        if (savedUsername != null && savedPassword != null) {
            usernameEditText.setText(savedUsername)
            passwordEditText.setText(savedPassword)
        }

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()
            loginButton.isEnabled = false
            loginButton.text = "正在登录"
            loadingSpinner.visibility = View.VISIBLE

            login(username, password, loginButton, loadingSpinner)
        }
    }

    private fun login(
        username: String,
        password: String,
        loginButton: Button,
        loadingSpinner: ProgressBar
    ) {
        Thread {
            try {
                // 保存用户名和密码到 SharedPreferences
                with(sharedPreferences.edit()) {
                    putString("username", username)
                    putString("password", password)
                    apply()
                }

                // 第一步：获取重定向的 URL
                val rollcallUrl = "https://courses.zju.edu.cn/api/radar/rollcalls"
                val request = Request.Builder()
                    .get()
                    .url(rollcallUrl)
                    .build()

                var response = HttpClientManager.client.newCall(request).execute()

                // 获取最终的重定向 URL
                val loginUrl = response.request.url.toString()

                // 第二步：使用重定向 URL 作为 service 登录
                /*val loginUrl = HttpUrl.Builder()
                    .scheme("https")
                    .host("zjuam.zju.edu.cn")
                    .addPathSegments("cas/login")
                    .addQueryParameter("service", finalUrl)
                    .build()*/

                response = HttpClientManager.client.newCall(Request.Builder().url(loginUrl).build())
                    .execute()

                val pubkeyUrl = "https://zjuam.zju.edu.cn/cas/v2/getPubKey"
                val pubKeyResponse =
                    HttpClientManager.client.newCall(Request.Builder().url(pubkeyUrl).build())
                        .execute()
                val pubKeyJson = pubKeyResponse.body?.string() ?: "{}"
                val pubKey = JSONObject(pubKeyJson)
                val modulus = pubKey.getString("modulus")
                val exponent = pubKey.getString("exponent")

                // 加密密码
                val encryptedPassword = rsaEncrypt(password, exponent, modulus)

                // 获取 execution 参数
                val execution = getExecution(response.body?.string() ?: "")

                val formBody = FormBody.Builder()
                    .add("username", username)
                    .add("password", encryptedPassword)
                    .add("_eventId", "submit")
                    .add("execution", execution)
                    .add("authcode", "")
                    .build()

                val loginRequest = Request.Builder()
                    .url(loginUrl)
                    .post(formBody)
                    .build()

                val loginResponse = HttpClientManager.client.newCall(loginRequest).execute()

                // 登录成功后，保存会话
                if (loginResponse.isSuccessful) {
                    if (!loginResponse.body?.string()
                            ?.contains("用户名或密码错误")!!
                    ) {
                        //test
                        val testResponse =
                            HttpClientManager.client.newCall(
                                Request.Builder()
                                    .url(rollcallUrl)
                                    .get()
                                    .build()
                            ).execute()
                        if (testResponse.isSuccessful) {
                            runOnUiThread {
                                Toast.makeText(this, "登陆成功", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            throw Exception("访问API失败！")
                        }
                        // 进入 MainActivity
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()  // 结束 LoginActivity，防止返回时重新进入登录界面
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "用户名或密码错误", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        val statusCode = loginResponse.code  // 获取HTTP状态码
                        Toast.makeText(this, "登录失败，请重试！状态码：$statusCode", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "登录失败，请重试！错误信息： ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // 恢复按钮状态，隐藏加载动画
                runOnUiThread {
                    loginButton.isEnabled = true
                    loginButton.text = "登录"
                    loadingSpinner.visibility = View.GONE
                }
            }
        }.start()
    }

    // 获取 execution 参数
    private fun getExecution(responseBody: String): String {
        val regex = """(?<=name="execution" value=").*?(?=")""".toRegex()
        return regex.find(responseBody)?.value ?: ""
    }

    // 加密密码 (RSA)
    private fun rsaEncrypt(passwd: String, eHex: String, nHex: String): String {
        var pwd = BigInteger.ZERO
        passwd.forEach { c ->
            pwd = pwd.multiply(BigInteger.valueOf(256)).add(BigInteger.valueOf(c.code.toLong()))
        }

        val e = BigInteger(eHex, 16)
        val n = BigInteger(nHex, 16)

        val encrypted = pwd.modPow(e, n)

        return encrypted.toString(16)
    }
}
