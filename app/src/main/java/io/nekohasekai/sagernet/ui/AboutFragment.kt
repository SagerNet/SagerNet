package io.nekohasekai.sagernet.ui

import android.content.Context
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import io.nekohasekai.sagernet.R

class AboutFragment : MaterialAboutFragment() {

    override fun getMaterialAboutList(activityContext: Context?): MaterialAboutList {

        return MaterialAboutList.Builder()
            .addCard(
                MaterialAboutCard.Builder()
                    .title(R.string.app_name)
                    .outline(false)
                    .build()
            )
            .build()

    }

}