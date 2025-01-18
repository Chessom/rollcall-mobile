package com.vemy.rollcall

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl


class SimpleCookieJar : CookieJar {
    private val cookieStore = HashMap<String, List<Cookie>>()
    override fun saveFromResponse(httpUrl: HttpUrl, cookies: List<Cookie>) {
        val currentCookies = cookieStore[httpUrl.host]?.toMutableList() ?: mutableListOf()

        for (cookie in cookies) {
            // 检查当前 cookie 是否已经存在于已有的 cookies 中
            val existingCookieIndex = currentCookies.indexOfFirst { it.name == cookie.name }

            if (existingCookieIndex >= 0) {
                // 如果存在相同的 cookie 名称，则替换掉现有的 cookie
                currentCookies[existingCookieIndex] = cookie
            } else {
                // 如果不存在相同名称的 cookie，则添加新的 cookie
                currentCookies.add(cookie)
            }
        }

        // 保存更新后的 cookies 到 cookieStore
        cookieStore[httpUrl.host] = currentCookies
    }


    override fun loadForRequest(httpUrl: HttpUrl): List<Cookie> {
        val cookies: List<Cookie>? = cookieStore[httpUrl.host]
        return cookies ?: ArrayList()
    }
}
