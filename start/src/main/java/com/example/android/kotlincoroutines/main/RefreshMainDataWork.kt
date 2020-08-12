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

import android.content.Context
import android.icu.text.CaseMap
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/*
work manager는 android jetpack과 architecture component의 일부로
opportunistic, guaranteed job을 background에서 실행하기에 좋다.
가령
1) 로그를 업로드하거나
2) 이미지에 필터를 입히고 저장하는 작업
3) 로컬 데이터를 네트워크와 싱크하는 간헐적인 작업
 */

/**
 * Worker job to refresh titles from the network while the app is in the background.
 *
 * WorkManager is a library used to enqueue work that is guaranteed to execute after its constraints
 * are met. It can run work even when the app is in the background, or not running.
 */
class RefreshMainDataWork(context: Context, params: WorkerParameters, private val network: MainNetwork) :
        CoroutineWorker(context, params) {
    //워크매니저는 ListenableWorker를 베이스로 한 다양한 구현 방식을 제공한다.

    /**
     * Refresh the title from the network using [TitleRepository]
     *
     * WorkManager will call this method from a background thread. It may be called even
     * after our app has been terminated by the operating system, in which case [WorkManager] will
     * start just enough to run this [Worker].
     */
    /*
    CorountineWorkder.doWork() 함수가 suspending function이라는 것에 주목하라.
    일반적으로 더 단순한 Worker Class와 달리 이 코드는 당신이 워크매니저 configuration에서 특정한 Executor에서 실행되는 대신
    coroutineContext 멤버에 속한 dispatcher을 사용한다. (디폴트: Dispatchers.Default)
     */
    override suspend fun doWork(): Result {
        val database = getDatabase(applicationContext)
        val repository = TitleRepository(network, database.titleDao)

        return try {
            repository.refreshTitle()
            Result.success()
        } catch (error: TitleRefreshError) {
            Result.failure()
        }
    }

    class Factory(val network: MainNetwork = getNetworkService()) : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
            return RefreshMainDataWork(appContext, workerParameters, network)
        }

    }
}