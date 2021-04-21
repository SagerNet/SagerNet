package io.nekohasekai.sagernet.ktx

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R

fun Fragment.alert(text: String): AlertDialog {
    return AlertDialog.Builder(requireContext())
        .setTitle(R.string.error_title)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok,null)
        .create()
}