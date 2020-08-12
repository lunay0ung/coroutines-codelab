/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.kotlincoroutines.main

import com.example.android.kotlincoroutines.util.SkipNetworkInterceptor
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

private val service: MainNetwork by lazy {
    val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(SkipNetworkInterceptor())
            .build()

    val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    retrofit.create(MainNetwork::class.java)
}

fun getNetworkService() = service

/**
 * Main network interface which will fetch a new welcome title for us
 */

/*
룸과 레트로핏은 suspending function을 main-safe하도록 한다. 해당 함수들이 network, db작업을 하더라도 Dispatchers.Main에서 호출해도 된다.
룸과 레트로핏 모두 Dispatchers.IO를 사용하는 대신 커스컴 Dispatcher를 사용한다.
Room will run coroutines using the default query and transaction Executor that's configured.
Retrofit will create a new Call object under the hood, and call enqueue on it to send the request asynchronously.
 */
interface MainNetwork {
    @GET("next_title.json")
    suspend fun fetchNextTitle(): String
    //suspend 변경제어자를 추가 -> 레트로핏은 자동적으로 suspend function을 main-safe하게 만들어서 Dispatchers.Main에서 직접 호출할 수 있도록 해준다.
    //Call wrapper를 제거: Call<String> -> String 으로 변경하였지만 json-backed타입과 같은 복잡한 값을 리턴받을 수 있다.
    //If you still wanted to provide access to Retrofit's full Result,
    //you can return Result<String> instead of String from the suspend function.
}


