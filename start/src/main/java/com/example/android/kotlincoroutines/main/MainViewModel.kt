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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.kotlincoroutines.util.BACKGROUND
import com.example.android.kotlincoroutines.util.singleArgViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainViewModel designed to store and manage UI-related data in a lifecycle conscious way. This
 * allows data to survive configuration changes such as screen rotations. In addition, background
 * work such as fetching network results can continue through configuration changes and deliver
 * results after the new Fragment or Activity is available.
 *
 * @param repository the data source this ViewModel will fetch results from.
 */
class MainViewModel(private val repository: TitleRepository) : ViewModel() {

    companion object {
        /**
         * Factory for creating [MainViewModel]
         *
         * @param arg the repository to pass to [MainViewModel]
         */
        val FACTORY = singleArgViewModelFactory(::MainViewModel)
    }

    /**
     * Request a snackbar to display a string.
     *
     * This variable is private because we don't want to expose MutableLiveData
     *
     * MutableLiveData allows anyone to set a value, and MainViewModel is the only
     * class that should be setting values.
     */
    private val _snackBar = MutableLiveData<String?>()

    /**
     * Request a snackbar to display a string.
     */
    val snackbar: LiveData<String?>
        get() = _snackBar

    /**
     * Update title text via this LiveData
     */
    val title = repository.title

    private val _spinner = MutableLiveData<Boolean>(false)

    /**
     * Show a loading spinner if true
     */
    val spinner: LiveData<Boolean>
        get() = _spinner

    /**
     * Count of taps on the screen
     */
    private var tapCount = 0

    /**
     * LiveData with formatted tap count.
     */
    private val _taps = MutableLiveData<String>("$tapCount taps")

    /**
     * Public view of tap live data.
     */
    val taps: LiveData<String>
        get() = _taps

    /**
     * Respond to onClick events by refreshing the title.
     *
     * The loading spinner will display until a result is returned, and errors will trigger
     * a snackbar.
     */
    fun onMainViewClicked() {
        refreshTitle()
        updateTaps()
    }

    /**
     * Wait one second then update the tap count.
     */
   /* private fun updateTaps() {
        // TODO: Convert updateTaps to use coroutines
        tapCount++
        BACKGROUND.submit {
            Thread.sleep(1_000)
            _taps.postValue("${tapCount} taps")
        }
    }
    */

    fun updateTaps() {
        viewModelScope.launch { //ViewModelScope에서 코루틴을 시작 / viewModelScope가 디폴트로 Dispatcher.Main을 갖기 때문에 코루틴은 메인스레드에서 시작
            tapCount++
            delay(1_000) //해당 코루틴을 1초간 멈춘다
            //delay는 suspend fun임 -> 이 코루틴이 메인스레드에서 돌아도 delay는 스레드를 1초도 블락하지 않음
            _taps.postValue("$tapCount taps") //메인스레드에서 resume 된다
        }
    }



    /**
     * Called immediately after the UI shows the snackbar.
     */
    fun onSnackbarShown() {
        _snackBar.value = null
    }

    fun refreshTitle(){

        /*
        Even though refreshTitle will make a network request and database query it can use coroutines to expose a main-safe interface.
        이 함수가 네트워크 리퀘스트/데이터베이스쿼리를 만들어내지만 main-safe한 interface를 위해 코루틴을 사용할 수 있다
        This means it'll be safe to call it from the main thread. 메인스레드에서 함수를 호출해도 안전하다는 뜻이다
        Because we're using viewModelScope, 뷰모델 스코프를 사용하기 때문에
        when the user moves away from this screen the work started by this coroutine will automatically be cancelled.
        유저가 해당 스크린에서 벗어나면 이 코루틴에서 시작된 작업은 자동으로 취소된다
        That means it won't make extra network requests or database queries.
        추가적인 네트워크 리퀘스트와 데이터베이스 쿼리를 실행하지 않는다는 뜻이다
         */
        viewModelScope.launch { //코루틴을 non-코루틴에서 생성하려면 launch로 시작해라. 이렇게 하면 uncaught exception이 발생해도 자동으로 처리된다 (otherwise 앱 크래시됨)
            //async로 시작하는 코루틴은 await을 호출할 때까지 exception을 throw하지 않는데, await은 suspend fun이기 때문에 코루틴내부에서만 사용할 수 있다

            //이미 코루틴 내부에 있다면 차일드 코루틴을 생성하기 위해 launch, await을 모두 쓸 수 있다
            //launch는 결과값을 리턴받지 않을 때, await은 결과값을 리턴받을 때 쓴다
            try {
                _spinner.value = true

                //refreshTitle이 suspned 함수이기 때문에 콜백을 패스하지 않아도 된다
                //아래 코드는 일반적인 blocking function call로 보이지만,
                //실제로는 network and database query가 완료될 때까지 기다린다 -> 이렇게 함으로써 메인스레드를 블라킹하지 않을 수 있다
                repository.refreshTitle()
            } catch (error: TitleRefreshError) {
                //suspend function에서 exception들은 일반적 function에서의 에러와 같이 작동한다
                //suspend function에서 에러를 throw하면 에러는 caller에게 throw된다
                //코루틴 바깥에서 exception을 throw하면, 코루틴은 기본적으로 부모 함수를 취소한다
                //즉 연관된 태스크를 함께 취소하기 쉽다
                _snackBar.value = error.message
            } finally {
                _spinner.value = false //쿼리가 실행되면 언제나 스피너는 off상태가 된다
            }
        }
    }

    /**
     * Refresh the title, showing a loading spinner while it refreshes and errors via snackbar.
     */

   /* fun refreshTitle() { //이 함수는 유저가 화면을 클릭할 때마다 호출되며, 레포지토리가 제목을 새로고침하고 새로운 제목을 데이터베이스에 저장하게 한다
        _spinner.value = true //쿼리를 시작하기 전 스피너를 로딩시킴

        //*object : TitleRefreshCallback -> TitleRefreshCallback를 구현함으로써 새로운 객체 생성
        //  -> 코틀린에서 익명클래스를 사용하는 방법
        //**익명클래스:
        //  익명 클래스란 다른 내부클래스와는 다르게 이름이 없는 클래스
        //  클래스의 선언과 객체의 생성을 동시에 하기 때문에 단 한번만 사용될 수 있고, 오직 하나의 객체만을 생성할 수 있는 일회용 클래스
        repository.refreshTitleWithCallbacks(object : TitleRefreshCallback {
            override fun onCompleted() { //이 함수는 타이틀을 전달하지 않음.
                            // 모든 타이틀은 룸 데이터베이스에 쓰기 때문에 ui는 룸에 의해 업데이트되는 라이브데이터를 observe하다가 업데이트 됨
                _spinner.postValue(false) //쿼리 결과를 가져오면 로딩 스피너를 클리어한다
            }

            override fun onError(cause: Throwable) { //쿼리 실패 시 스낵바를 띄우고 로딩 스피너를 클리어한다
                _snackBar.postValue(cause.message)
                _spinner.postValue(false)
            }
        })
    }*/

    */
    */


}
