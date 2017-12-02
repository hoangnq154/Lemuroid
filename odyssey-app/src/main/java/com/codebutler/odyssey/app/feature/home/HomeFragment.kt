/*
 * BrowseFragment.kt
 *
 * Copyright (C) 2017 Odyssey Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.codebutler.odyssey.app.feature.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v17.leanback.app.BrowseFragment
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v17.leanback.widget.Presenter
import android.support.v17.leanback.widget.Row
import android.support.v17.leanback.widget.RowPresenter
import android.support.v4.app.ActivityOptionsCompat
import com.codebutler.odyssey.R
import com.codebutler.odyssey.app.feature.game.GameActivity
import com.codebutler.odyssey.app.feature.home.HomeAdapterFactory.AboutItem
import com.codebutler.odyssey.app.feature.home.HomeAdapterFactory.AllGamesItem
import com.codebutler.odyssey.app.feature.home.HomeAdapterFactory.GameSystemItem
import com.codebutler.odyssey.app.feature.home.HomeAdapterFactory.RescanItem
import com.codebutler.odyssey.app.feature.home.HomeAdapterFactory.SettingsItem
import com.codebutler.odyssey.app.feature.search.GamesSearchFragment
import com.codebutler.odyssey.app.feature.settings.SettingsActivity
import com.codebutler.odyssey.lib.library.GameLibrary
import com.codebutler.odyssey.lib.library.db.OdysseyDatabase
import com.codebutler.odyssey.lib.library.db.entity.Game
import com.codebutler.odyssey.lib.ui.SimpleErrorFragment
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import com.uber.autodispose.kotlin.autoDisposeWith
import dagger.Provides
import dagger.android.support.AndroidSupportInjection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class HomeFragment : BrowseSupportFragment(), OnItemViewClickedListener {

    @Inject lateinit var adapterFactory: HomeAdapterFactory
    @Inject lateinit var gameLibrary: GameLibrary
    @Inject lateinit var odysseyDb: OdysseyDatabase

    override fun onAttach(context: Context?) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        headersState = BrowseFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        title = getString(R.string.app_name)
        onItemViewClickedListener = this
    }

    override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row) {
        when (item) {
            is Game -> startActivity(GameActivity.newIntent(context, item))
            is GameSystemItem -> fragmentManager.beginTransaction()
                    .replace(R.id.content, GamesGridFragment.create(GamesGridFragment.Mode.SYSTEM, item.system.id))
                    .addToBackStack(null)
                    .commit()
            is AllGamesItem -> fragmentManager.beginTransaction()
                    .replace(R.id.content, GamesGridFragment.create(GamesGridFragment.Mode.ALL))
                    .addToBackStack(null)
                    .commit()
            is RescanItem -> {
                progressBarManager.show()
                gameLibrary.indexGames()
                        .observeOn(AndroidSchedulers.mainThread())
                        .autoDisposeWith(AndroidLifecycleScopeProvider.from(this))
                        .subscribe(
                                {
                                    loadContents()
                                    progressBarManager.hide()
                                },
                                { error ->
                                    progressBarManager.hide()
                                    val errorFragment = SimpleErrorFragment.create(error.toString())
                                    fragmentManager.beginTransaction()
                                            .replace(R.id.content, errorFragment)
                                            .addToBackStack(null)
                                            .commit()
                                })
            }
            is SettingsItem -> {
                val intent = Intent(activity, SettingsActivity::class.java)
                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity)
                        .toBundle()
                startActivity(intent, bundle)
            }
            is AboutItem -> {}
        }

        loadContents()
    }

    private fun loadContents() {
        odysseyDb.gameDao().selectCounts()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposeWith(AndroidLifecycleScopeProvider.from(this))
                .subscribe { counts ->
                    val categoryRowAdapter = ArrayObjectAdapter(ListRowPresenter())
                    if (counts.favoritesCount > 0) {
                        categoryRowAdapter.add(ListRow(
                                HeaderItem(getString(R.string.favorites)),
                                adapterFactory.buildFavoritesAdapter()))
                    }
                    if (counts.recentsCount > 0) {
                        categoryRowAdapter.add(ListRow(
                                HeaderItem(getString(R.string.recently_played)),
                                adapterFactory.buildRecentsAdapter()))
                    }
                    categoryRowAdapter.add(ListRow(
                            HeaderItem(getString(R.string.library)),
                            adapterFactory.buildSystemsAdapter(counts)))
                    categoryRowAdapter.add(ListRow(
                            HeaderItem(getString(R.string.settings)),
                            adapterFactory.buildSettingsAdapter()))
                    adapter = categoryRowAdapter

                    if (counts.totalCount > 0) {
                        setOnSearchClickedListener {
                            fragmentManager.beginTransaction()
                                    .replace(R.id.content, GamesSearchFragment.create())
                                    .addToBackStack(null)
                                    .commit()
                        }
                    } else {
                        setOnSearchClickedListener(null)
                    }
                }
    }

    @dagger.Module
    class Module {

        @Provides
        fun adapterFactory(fragment: HomeFragment, odysseyDb: OdysseyDatabase) = HomeAdapterFactory(fragment, odysseyDb)
    }
}