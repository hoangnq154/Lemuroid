package com.swordfish.lemuroid.lib.core.assetsmanager

import com.swordfish.lemuroid.lib.core.CoreManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import io.reactivex.Completable

class NoAssetsManager : CoreManager.AssetsManager {

    override fun clearAssets(directoriesManager: DirectoriesManager) = Completable.complete()

    override fun retrieveAssets(coreManagerApi: CoreManager.CoreManagerApi, directoriesManager: DirectoriesManager) =
            Completable.complete()
}
