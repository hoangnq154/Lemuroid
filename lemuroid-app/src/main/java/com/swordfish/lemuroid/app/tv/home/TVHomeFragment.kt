package com.swordfish.lemuroid.app.tv.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.systems.SystemInfo
import com.swordfish.lemuroid.app.shared.library.LibraryIndexWork
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.tv.folderpicker.TVFolderPickerLauncher
import com.swordfish.lemuroid.app.tv.settings.TVSettingsActivity
import com.swordfish.lemuroid.app.tv.shared.GamePresenter
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.util.subscribeBy
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDispose
import dagger.android.support.AndroidSupportInjection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import javax.inject.Inject

class TVHomeFragment : BrowseSupportFragment() {

    @Inject lateinit var retrogradeDb: RetrogradeDatabase
    @Inject lateinit var gameInteractor: GameInteractor

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Game -> gameInteractor.onGamePlay(item)
                is SystemInfo -> {
                    val systemId = item.system.id.dbname
                    val action = TVHomeFragmentDirections.actionNavigationSystemsToNavigationGames(systemId)
                    findNavController().navigate(action)
                }
                is TVSetting -> {
                    when (item) {
                        TVSetting.RESCAN -> LibraryIndexWork.enqueueUniqueWork(context!!.applicationContext)
                        TVSetting.CHOOSE_DIRECTORY -> launchFolderPicker()
                        TVSetting.SETTINGS -> launchTVSettings()
                    }
                }
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recreateAdapter(false, false)
        setOnSearchClickedListener {
            findNavController().navigate(R.id.navigation_search)
        }
    }

    override fun onResume() {
        super.onResume()

        val factory = TVHomeViewModel.Factory(retrogradeDb)
        val homeViewModel = ViewModelProviders.of(this, factory).get(TVHomeViewModel::class.java)

        Observables.combineLatest(homeViewModel.recentGames, homeViewModel.availableSystems)
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(scope())
            .subscribeBy { (recentGames, systems) -> update(recentGames, systems) }
    }

    private fun update(recentGames: List<Game>, systems: List<SystemInfo>) {
        val adapterHasGames = findAdapterById(GAMES_ADAPTER) != null
        val adapterHasSystems = findAdapterById(SYSTEM_ADAPTER) != null

        if (adapterHasGames != recentGames.isNotEmpty() || adapterHasSystems != systems.isNotEmpty()) {
            recreateAdapter(recentGames.isNotEmpty(), systems.isNotEmpty())
        }

        findAdapterById(GAMES_ADAPTER)?.setItems(recentGames, LEANBACK_GAME_DIFF_CALLBACK)
        findAdapterById(SYSTEM_ADAPTER)?.setItems(systems, LEANBACK_SYSTEM_DIFF_CALLBACK)
    }

    private fun findAdapterById(id: Long): ArrayObjectAdapter? {
        for (i: Int in 0 until adapter.size()) {
            val listRow = adapter.get(i) as ListRow
            if (listRow.headerItem.id == id) {
                return listRow.adapter as ArrayObjectAdapter
            }
        }
        return null
    }

    private fun recreateAdapter(includeRecentGames: Boolean, includeSystems: Boolean) {
        val result = ArrayObjectAdapter(ListRowPresenter())

        if (includeRecentGames) {
            val recentItems = ArrayObjectAdapter(GamePresenter(resources.getDimensionPixelSize(R.dimen.card_size)))
            result.add(
                ListRow(HeaderItem(GAMES_ADAPTER, resources.getString(R.string.tv_home_section_recents)), recentItems)
            )
        }

        if (includeSystems) {
            val systemItems = ArrayObjectAdapter(
                SystemPresenter(
                    resources.getDimensionPixelSize(R.dimen.card_size),
                    resources.getDimensionPixelSize(R.dimen.card_padding)
                )
            )
            result.add(
                ListRow(
                    HeaderItem(SYSTEM_ADAPTER, resources.getString(R.string.tv_home_section_systems)), systemItems
                )
            )
        }

        val settingsItems = ArrayObjectAdapter(
            SettingPresenter(
                resources.getDimensionPixelSize(R.dimen.card_size),
                resources.getDimensionPixelSize(R.dimen.card_padding)
            )
        )
        settingsItems.add(0, TVSetting.RESCAN)
        settingsItems.add(1, TVSetting.CHOOSE_DIRECTORY)
        settingsItems.add(2, TVSetting.SETTINGS)
        result.add(
            ListRow(
                HeaderItem(SETTINGS_ADAPTER, resources.getString(R.string.tv_home_section_settings)), settingsItems
            )
        )

        adapter = result
    }

    private fun launchFolderPicker() {
        if (TVHelper.isSAFSupported(requireContext())) {
            StorageFrameworkPickerLauncher.pickFolder(requireContext())
        } else {
            TVFolderPickerLauncher.pickFolder(requireContext())
        }
    }

    private fun launchTVSettings() {
        startActivity(Intent(requireContext(), TVSettingsActivity::class.java))
    }

    companion object {
        const val GAMES_ADAPTER = 1L
        const val SYSTEM_ADAPTER = 2L
        const val SETTINGS_ADAPTER = 3L

        val LEANBACK_GAME_DIFF_CALLBACK = object : DiffCallback<Game>() {
            override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean {
                return oldItem == newItem
            }

            override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean {
                return oldItem.id == newItem.id
            }
        }

        val LEANBACK_SYSTEM_DIFF_CALLBACK = object : DiffCallback<SystemInfo>() {
            override fun areContentsTheSame(oldInfo: SystemInfo, newInfo: SystemInfo): Boolean {
                return oldInfo == newInfo
            }

            override fun areItemsTheSame(oldInfo: SystemInfo, newInfo: SystemInfo): Boolean {
                return oldInfo.system.id == newInfo.system.id
            }
        }
    }

    @dagger.Module
    class Module
}
