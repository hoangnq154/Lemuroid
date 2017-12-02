/*
 * HomeAdapterFactory.kt
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

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.Observer
import android.arch.paging.PagedList
import android.support.v17.leanback.widget.ArrayObjectAdapter
import com.codebutler.odyssey.R
import com.codebutler.odyssey.lib.library.GameSystem
import com.codebutler.odyssey.lib.library.db.OdysseyDatabase
import com.codebutler.odyssey.lib.library.db.dao.GameLibraryCounts
import com.codebutler.odyssey.lib.library.db.entity.Game
import com.codebutler.odyssey.lib.ui.PagedListObjectAdapter
import com.codebutler.odyssey.lib.ui.SimpleItem
import com.codebutler.odyssey.lib.ui.SimpleItemPresenter
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import com.uber.autodispose.kotlin.autoDisposeWith
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class HomeAdapterFactory(
        private var lifecycleOwner: LifecycleOwner,
        private var odysseyDb: OdysseyDatabase) {

    data class GameSystemItem(val system: GameSystem) : SimpleItem(system.titleResId)
    object AboutItem : SimpleItem(R.string.about)
    object RescanItem : SimpleItem(R.string.rescan)
    object AllGamesItem : SimpleItem(R.string.all_games)
    object SettingsItem : SimpleItem(R.string.settings)
    object NoGamesItem : SimpleItem(R.string.no_games)

    fun buildFavoritesAdapter(): PagedListObjectAdapter<Game> {
        val favoritesAdapter = PagedListObjectAdapter(GamePresenter(), Game.DIFF_CALLBACK)
        odysseyDb.gameDao().selectFavorites()
                .create(0, PagedList.Config.Builder()
                        .setPageSize(50)
                        .setPrefetchDistance(50)
                        .build())
                .observe(lifecycleOwner, Observer {
                    pagedList -> favoritesAdapter.pagedList = pagedList
                })
        return favoritesAdapter
    }

    fun buildRecentsAdapter(): PagedListObjectAdapter<Game> {
        val recentsAdapter = PagedListObjectAdapter(GamePresenter(), Game.DIFF_CALLBACK)
        odysseyDb.gameDao().selectRecentlyPlayed()
                .create(0, PagedList.Config.Builder()
                        .setPageSize(50)
                        .setPrefetchDistance(50)
                        .build())
                .observe(lifecycleOwner, Observer {
                    pagedList -> recentsAdapter.pagedList = pagedList
                })
        return recentsAdapter
    }

    fun buildSystemsAdapter(counts: GameLibraryCounts): ArrayObjectAdapter {
        val systemsAdapter = ArrayObjectAdapter(SimpleItemPresenter())
        if (counts.totalCount == 0L) {
            systemsAdapter.add(NoGamesItem)
        } else {
            odysseyDb.gameDao().selectSystems()
                    .toObservable()
                    .flatMapIterable { it }
                    .map { GameSystem.findById(it)!! }
                    .toList()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDisposeWith(AndroidLifecycleScopeProvider.from(lifecycleOwner))
                    .subscribe { systems ->
                        systemsAdapter.clear()
                        systemsAdapter.addAll(0, systems.map { system -> GameSystemItem(system) })
                        systemsAdapter.add(AllGamesItem)
                    }
        }
        return systemsAdapter
    }

    fun buildSettingsAdapter(): ArrayObjectAdapter {
        val settingsAdapter = ArrayObjectAdapter(SimpleItemPresenter())
        settingsAdapter.add(SettingsItem)
        settingsAdapter.add(RescanItem)
        settingsAdapter.add(AboutItem)
        return settingsAdapter
    }
}
