package io.horizontalsystems.bankwallet.modules.market.favorites_new

import io.horizontalsystems.bankwallet.core.subscribeIO
import io.horizontalsystems.bankwallet.entities.DataState
import io.horizontalsystems.bankwallet.modules.market.MarketItem
import io.horizontalsystems.bankwallet.modules.market.SortingField
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.ICurrencyManager
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject

class MarketFavoritesService(
    private val repository: MarketFavoritesRepository,
    private val menuService: MarketFavoritesMenuService,
    private val currencyManager: ICurrencyManager,
    private val backgroundManager: BackgroundManager
) : BackgroundManager.Listener {
    private var favoritesDisposable: Disposable? = null
    private var repositoryDisposable: Disposable? = null

    private val marketItemsSubject = PublishSubject.create<DataState<List<MarketItem>>>()
    val marketItemsObservable: Observable<DataState<List<MarketItem>>>
        get() = marketItemsSubject

    val sortingFieldTypes = SortingField.values().toList()
    var sortingField: SortingField = menuService.sortingField
        set(value) {
            field = value
            menuService.sortingField = value
            rebuildItems()
        }

    private fun fetch(forceRefresh: Boolean) {
        favoritesDisposable?.dispose()

        repository.get(sortingField, currencyManager.baseCurrency, forceRefresh)
            .doOnSubscribe {
                marketItemsSubject.onNext(DataState.Loading)
            }
            .subscribeIO({ marketItems ->
                marketItemsSubject.onNext(DataState.Success(marketItems))
            }, { error ->
                marketItemsSubject.onNext(DataState.Error(error))
            }).let {
                favoritesDisposable = it
            }
    }

    private fun rebuildItems() {
        fetch(false)
    }

    private fun forceRefresh() {
        fetch(true)
    }

    fun refresh() {
        forceRefresh()
    }

    fun start() {
        backgroundManager.registerListener(this)

        repository.dataUpdatedObservable
            .subscribeIO { forceRefresh() }
            .let { repositoryDisposable = it }

        forceRefresh()
    }

    fun stop() {
        backgroundManager.unregisterListener(this)
        favoritesDisposable?.dispose()
    }

    override fun willEnterForeground() {
        forceRefresh()
    }
}
