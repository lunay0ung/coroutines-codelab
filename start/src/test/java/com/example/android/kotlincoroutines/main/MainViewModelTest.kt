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
import com.example.android.kotlincoroutines.fakes.MainNetworkFake
import com.example.android.kotlincoroutines.fakes.TitleDaoFake
import com.example.android.kotlincoroutines.main.utils.MainCoroutineScopeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule //Rule은 JUnit을 이용한 테스트에서 코드를 어떻게 실행할지 정하는 방법
    val coroutineScope = MainCoroutineScopeRule()
    /*
     MainCoroutineScopeRule
     a custom rule in this codebase that configures Dispatchers.Main to use a TestCoroutineDispatcher
     from kotlinx-coroutines-test. This allows tests to advance a virtual-clock for testing,
     and allows code to use Dispatchers.Main in unit tests.
     */

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    // InstantTaskExecutorRule은 JUnit 룰 중에 하나로,
    // 각 태스크를 어떻게 동기적으로 실행할지 LiveData를 설정하는 룰

    lateinit var subject: MainViewModel

    @Before
    fun setup() {
        subject = MainViewModel(
                TitleRepository(
                        MainNetworkFake("OK"),
                        TitleDaoFake("initial")
                ))
    }

    @Test
    fun whenMainClicked_updatesTaps() {
        // TODO: Write this
    }
}