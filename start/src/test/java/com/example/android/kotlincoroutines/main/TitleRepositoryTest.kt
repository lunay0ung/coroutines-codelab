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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.android.kotlincoroutines.fakes.MainNetworkCompletableFake
import com.example.android.kotlincoroutines.fakes.MainNetworkFake
import com.example.android.kotlincoroutines.fakes.TitleDaoFake
import com.google.common.truth.Truth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test

class TitleRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun whenRefreshTitleSuccess_insertsRows() {
       val subject = TitleRepository(
               MainNetworkFake("OK"),
               TitleDaoFake("title")
       )
        //테스트 러너는 코루틴에 대해 아무것도 모르기 때문에 테스트를 suspend func으로 만들 순 없다.
        //subject.refreshTitle()
        // launch로 실행된 코루틴들은 비동기적이다. 미래 어느 시점에 완료될 것이라는 뜻이다.
        // 따라서 비동기적인 코드를 테스트하려면, 테스트가 코루틴이 완료될 때까지 기다려야 한다는 것을 알려야 한다.
        // launch는 비동기적인 호출이기 때문에 -
        // that means it returns right away and can continue to run a coroutine after the function returns -
        // 테스트에서 쓰일 순 없다.

        //launch는 코루틴을 실행한 즉시 되돌아온다
        GlobalScope.launch {
            // since this is asynchronous code, this may be called *after* the test completes
            //아래 코드는 비동기적이기 때문에 테스트가 완료된 '이후' 호출될 것이다.
            subject.refreshTitle()
        }
        //-> 즉, 이 테스트는 '때때로' 실패할 것이다.
    }

    /*
    kotlinx-coroutines-test라이브러리는 suspend function을 호출하는 동안 block하는 runBlockingTest function을 제공한다.

    **important
    runBlockingTest 함수는 일반적인 call 함수와 같이 언제나 caller를 block한다.
    코루틴은 같은 스레드에서 동기적으로 실행된다.
    코드 상에서 runBlocking과 runBlockingTest 함수 대신 바로 return하는 launch 함수를 써야한다.
    runBlocking은 코루틴에서 blocking interface를 제공하기 위해 쓰일 수 있는 반면
    runBlockingTest는 코루틴을 테스트에서 컨트롤되는 방식으로 실행하기 때문에 테스트에서만 쓰여야 한다.
    The function runBlockingTest will always block the caller, just like a regular function call.
     */

    /*
    아래의 테스트는 refreshTitle 함수에 의해 데이터베이스에 "OK"가 삽입되었음을 체크하는 fake를 사용한다.
    테스트가 runBlockingTest를 호출하면, runBlockingTest에 의해 시작된 코루틴이 완료될 때까지 block한다.
    그다음 내부에서는, 우리가 refreshTitle 함수를 호출할 때
    데이터베이스에 row가 추가될 때까지 일반적인 suspend, resume 메커니즘을 사용한다.
    테스트 코루틴이 완료된 후 runBlockingTest로 되돌아온다.
     */
    @ExperimentalCoroutinesApi
    @Test
    fun whenRefreshTitleSuccess_insertRows() = runBlockingTest {
        val titleDao = TitleDaoFake("OK")
        val subject = TitleRepository (
                MainNetworkFake("OK"),
                titleDao
        )
        subject.refreshTitle()
        Truth.assertThat(titleDao.nextInsertedOrNull()).isEqualTo("OK")
    }

    /*
    아래의 테스트는 MainNetworkCompletableFake를 사용하는데,
    이것은 테스트가 callers를 재개할 때까지 caller를 suspend하도록 디자인된 network fake이다.

    그다음 테스트는 refreshTitle을 호출하기 위해 별개의 코루틴을 시작한다.
    여기가 timeouts 테스트의 중요한 부분인데, timeout은 runBlockingTest를 생성한 코루틴이 아닌 다른 코루틴에서 일어나야 한다.
    이렇게 함으로써 advenceTimeBy라는 다음 코드를 호출할 수 있다.

    이 테스트를 실행하면 다음과 같은 에러가 발생한다.
    Caused by: kotlinx.coroutines.test.UncompletedCoroutinesError: Test finished with active jobs: ["...]

    runBlockingTest의 한 가지 feature는 테스트가 완료된 후 코루틴이 leak되도록 하지 않는다는 것이다.
    만약 끝나지 않은 코루틴이 있다면-이 경우에는 launch 코루틴- 테스트는 fail한다.
     */
    @Test(expected = TitleRefreshError::class)
    fun whenRefreshTitleTimeout_throws() = runBlockingTest {
        val network = MainNetworkCompletableFake()
        val subject = TitleRepository (
                network,
                TitleDaoFake("title")
        )

        launch {
            subject.refreshTitle()
        }

        advanceTimeBy(5_000)
    }
}