// 역할: 탭별 뷰모델을 최초 진입 시 지연 생성하고 탭 전환 시 소멸되지 않도록 유지·관리하는 저장소 소유자입니다.
package com.example.gemgemgen.ui.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Per-tab store so leaving a tab can destroy or trim that tab's ViewModels, with lazy creation. */
class TabViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
    private var initialized = false

    fun <T : ViewModel> getOrCreate(
        modelClass: Class<T>,
        factory: ViewModelProvider.Factory
    ): T {
        initialized = true
        return ViewModelProvider(this, factory)[modelClass]
    }

    fun <T : ViewModel> getIfInitialized(
        modelClass: Class<T>,
        factory: ViewModelProvider.Factory
    ): T? {
        if (!initialized) return null
        return ViewModelProvider(this, factory)[modelClass]
    }

    fun clear() {
        initialized = false
        viewModelStore.clear()
    }
}
