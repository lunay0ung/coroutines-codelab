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

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.android.kotlincoroutines.util.BACKGROUND
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher

/**
 * TitleRepository provides an interface to fetch a title or request a new one be generated.
 *
 * Repository modules handle data operations. They provide a clean API so that the rest of the app
 * can retrieve this data easily. They know where to get the data from and what API calls to make
 * when data is updated. You can consider repositories to be mediators between different data
 * sources, in our case it mediates between a network API and an offline database cache.
 */
class TitleRepository(val network: MainNetwork, val titleDao: TitleDao) {

    /**
     * [LiveData] to load title.
     *
     * This is the main interface for loading a title. The title will be loaded from the offline
     * cache.
     *
     * Observing this will not cause the title to be refreshed, use [TitleRepository.refreshTitleWithCallbacks]
     * to refresh the title.
     */
    val title: LiveData<String?> = titleDao.titleLiveData.map { it?.title }


    // Without introducing coroutines to the network or database,
    // we can make this code main-safe using coroutines.
    // This will let us get rid of the callback and allow us to pass the result back to the thread that initially called it.
    //You can use this pattern anytime you need to do blocking or CPU intensive work
    // from inside a coroutine such as sorting and filtering a large list or reading from disk.
    //코루틴 버전의 refreshTitle 함수
    //코루틴을 사용하면 콜백이 없이도 + 해당 콜백을 호출한 스레드로 바로 결과값을 전달할 수 있다
    //이 패턴은 blocking한 작업 혹은 CPU intensie한 작업에 언제나 사용할 수 있다
    //가령 큰 사이즈의 리스트를 sorting, filtering하거나 디스크에서 읽어오는 경우이다.
    suspend fun refreshTitle() {
        //dispatcher 간 switch를 위해서 코루틴은 withContext를 사용한다.
        //withContext를 호출하면, 다른 dispatcher로 switch된 후 해당 disptcher를 호출한 dispatcher로 lambda의 결과를 갖고 되돌아온다

        //기본적으로 코루틴은 세 가지 Dispatchers를 제공한다: Main, IO, 그리고 Default다.
        //IO dispatcher는 network나 disk로 부터 읽는 IO작업에, Default dispatcher는 CPU intensive한 작업에 최적화되어있다.

        //이 코드는 여전히 blocking call을 사용한다: execute(), insertTitles() 모두 코루틴이 실행 중인 스레드를 블락할 것이다.
        //하지만 withContext를 통해 Dispatchers.IO로 switch되면서, 우리는 IO Dispatcher 내부의 스레드 하나를 블락킹하게 된다.
        //이 작업을 호출한 코루틴은 아마도 Dispatchers.Main 상에서 실행되고 있을 것이며, withContext lambda가 완료될 때까지 suspend될 것이다.
        //콜백 버전과 비교하면, 두 가지 중요한 차이점이 있다.
        //1) withContext는 결과값을 withContext를 부른 Dispatcher로 반환한다(이 경우에는 .Main)
        // -> 콜백버전의 경우에는 BACKGROUND executor service 내부 스레드 상의 콜백을 호출했다
        //2) caller는 이 함수에 callback을 전달할 필요가 없다. 결과값이나 에러를 받기 위해 suspend / resume될 뿐이다.

        withContext(Dispatchers.IO) {
           val result = try {
                network.fetchNextTitle().execute()
           } catch (cause: Throwable) {
               throw TitleRefreshError("unable to refresh title", cause)
           }

           if (result.isSuccessful) {
               // Save it to database
               titleDao.insertTitle(Title(result.body()!!))
           } else {
               // If it's not successful, inform the callback of the error
               throw TitleRefreshError("Unable to refresh title", null)
           }
       }
    }


    /**
     * Refresh the current title and save the results to the offline cache.
     *
     * This method does not return the new title. Use [TitleRepository.title] to observe
     * the current tile.
     */
    fun refreshTitleWithCallbacks(titleRefreshCallback: TitleRefreshCallback) {
        // This request will be run on a background thread by retrofit
        BACKGROUND.submit { //작업을 위해 BACKGROUND 스레드로 switch한다
            //callback에 기초한 이 함수는 메인스레드를 블락하지 않기 때문에 main-safe하다
            //하지만 콜러에게 작업이 완료되었음을 알리기 위해 콜백을 사용해야 하며,
            //BACKGROUND thread 상의 콜백을 호출해야 한다
            try {
                // Make network request using a blocking call
                val result = network.fetchNextTitle().execute() //execute-> blocking method
                if (result.isSuccessful) {
                    // Save it to database
                    titleDao.insertTitle(Title(result.body()!!))
                    // Inform the caller the refresh is completed
                    titleRefreshCallback.onCompleted()
                } else {
                    // If it's not successful, inform the callback of the error
                    titleRefreshCallback.onError(
                            TitleRefreshError("Unable to refresh title", null))
                }
            } catch (cause: Throwable) {
                // If anything throws an exception, inform the caller
                titleRefreshCallback.onError(
                        TitleRefreshError("Unable to refresh title", cause))
            }
        }
    }
}

/**
 * Thrown when there was a error fetching a new title
 *
 * @property message user ready error message
 * @property cause the original cause of this exception
 */
class TitleRefreshError(message: String, cause: Throwable?) : Throwable(message, cause)

interface TitleRefreshCallback {
    fun onCompleted()
    fun onError(cause: Throwable)
}
