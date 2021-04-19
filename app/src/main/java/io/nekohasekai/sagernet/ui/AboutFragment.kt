package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import io.nekohasekai.sagernet.R

class AboutFragment : ToolbarFragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.menu_about)
    }

}