package com.sergsave.pocat.screens.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.sergsave.pocat.BuildConfig
import com.sergsave.pocat.Constants
import com.sergsave.pocat.MyApplication
import com.sergsave.pocat.R
import com.sergsave.pocat.apprate.AppRateManager
import com.sergsave.pocat.helpers.EventObserver
import com.sergsave.pocat.helpers.FirstTimeLaunchBugWorkaround
import com.sergsave.pocat.helpers.setToolbarAsActionBar
import kotlinx.android.synthetic.main.activity_main.*

// TODO: Names of constants (XX_BUNDLE_KEY or BUNDLE_KEY_XX)

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        (application as MyApplication).appContainer.provideMainViewModelFactory()
    }

    private val navigation by viewModels<NavigationViewModel>()
    private val appRater by lazy { (application as MyApplication).appContainer.appRateManager }

    override fun onStop() {
        appRater.dismissRateDialog()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirstTimeLaunchBugWorkaround.needFinishOnCreate(this)){
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        
        if (savedInstanceState == null) {
            viewModel.onFirstActivityStarted()
            checkInputSharingIntent()
        }

        appRater.onRootActivityStart()

        val lifecycleOwner = this

        viewModel.apply {
            requestPageChangeEvent.observe(lifecycleOwner, EventObserver {
                pager.setCurrentItem(it, false)
            })

            shouldShowAppRate.observe(lifecycleOwner, Observer { shouldShow ->
                if (shouldShow) {
                    val showed = appRater.showAppRateDialogIfPossible(lifecycleOwner, {
                        onAppRateFinished(it)
                    })
                    if (showed) viewModel.onAppRateShowed()
                }
            })
        }

        navigation.apply {
            openCatEvent.observe(lifecycleOwner, EventObserver {
                lifecycleOwner.launchCatCard(CAT_CARD_ACTIVITY_REQUEST_CODE, it.card, it.transition)
            })

            addNewCatEvent.observe(lifecycleOwner, EventObserver {
                lifecycleOwner.launchCatCard(CAT_CARD_ACTIVITY_REQUEST_CODE)
            })
        }

        setupPager()

        setToolbarAsActionBar(toolbar, showBackButton = false)
        supportActionBar?.elevation = 0f
    }

    private fun onAppRateFinished(action: AppRateManager.ActionType) {
        when (action) {
            AppRateManager.ActionType.DECLINE -> viewModel.onAppRateDeclined()
            AppRateManager.ActionType.ACCEPT -> viewModel.onAppRateAccepted()
            AppRateManager.ActionType.SHOW_LATER -> viewModel.onAppRateShowLater()
        }
    }

    private fun setupPager() {
        pager.adapter = PagerAdapter(this)
        pager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.onPageChanged(position)
                navigation.onPageChanged(position)
            }
        })

        TabLayoutMediator(tab_layout, pager) { tab, position ->
            val tabInfo = viewModel.tabInfoForPosition(position)
            tabInfo?.let { tab.text = getString(it.titleStringId) }
        }.attach()
    }

    private fun checkInputSharingIntent() {
        val isForwarded = intent?.getBooleanExtra(Constants.IS_FORWARDED_INTENT_KEY, false) ?: false
        if (!isForwarded)
            return

        viewModel.onForwardIntent()

        // Forward further
        launchCatCard(CAT_CARD_ACTIVITY_REQUEST_CODE, this.intent)
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu?.findItem(R.id.action_testing)?.isVisible = !BuildConfig.HIDE_TEST_ACTION

        if (menu is MenuBuilder)
            menu.setOptionalIconsVisible(true)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        viewModel.onOptionsItemSelected(item.itemId)
        when (item.itemId) {
            R.id.action_settings -> launchSettings()
            R.id.action_about -> launchAbout()
            R.id.action_donate -> launchDonate()
            R.id.action_testing -> launchTesting()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || requestCode != CAT_CARD_ACTIVITY_REQUEST_CODE)
            return

        val wasCatPetted = data?.getBooleanExtra(Constants.WAS_CAT_PETTED_INTENT_KEY, false) == true
        if (wasCatPetted)
            viewModel.onCatWasPetted()
    }

    companion object {
        private const val CAT_CARD_ACTIVITY_REQUEST_CODE = 1000
    }
}