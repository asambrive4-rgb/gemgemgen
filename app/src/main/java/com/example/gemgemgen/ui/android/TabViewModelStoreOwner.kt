// 역할: 탭 전환 시 뷰모델이 소멸되지 않고 유지되도록 관리하는 저장소 소유자입니다.
package com.example.gemgemgen.ui.android

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Per-tab store so leaving a tab can destroy that tab's ViewModels. */
class TabViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}
