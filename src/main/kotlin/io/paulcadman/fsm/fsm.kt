package io.paulcadman.fsm

import arrow.Kind
import arrow.data.NonEmptyList
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monad
import arrow.typeclasses.Foldable
import arrow.typeclasses.Show
import java.math.BigDecimal

data class BasketItem(var name: String, var price: BigDecimal)

fun calculatePrice(items: Collection<BasketItem>): BigDecimal {
    return items.fold(BigDecimal.ZERO) { acc, next -> acc + next.price}
}

data class Card(var identifier: String) {
    fun charge(price: BigDecimal): IO<Unit> {
        return IO { System.err.println("charging card")}.flatMap { IO.just(Unit) }
    }
}

sealed class CheckoutState {
}

class NoItems: CheckoutState()
data class HasItems(var items: NonEmptyList<BasketItem>): CheckoutState()
data class NoCard(var items: NonEmptyList<BasketItem>): CheckoutState()
data class CardSelected(var items: NonEmptyList<BasketItem>, var card: Card): CheckoutState()
data class CardConfirmed(var items: NonEmptyList<BasketItem>, var card: Card): CheckoutState()
class OrderPlaced: CheckoutState()

sealed class CheckoutEvent {
}

data class SelectItem(var item: BasketItem): CheckoutEvent()
class Checkout: CheckoutEvent()
data class SelectCard(var card: Card): CheckoutEvent()
class Confirm: CheckoutEvent()
class PlaceOrder: CheckoutEvent()
class Cancel: CheckoutEvent()

class FSM<S, E>(val reduce: (S, E) -> IO<S>) {
    fun <F> Foldable<F>.runFSM(initial: S, events: Kind<F, E>): IO<S> {
        return events.foldM(IO.monad(), initial, reduce).fix()
    }
}

fun <S, E> withLogging(wrapping: FSM<S, E>, SE: Show<E> = Show.fromToString(), SS: Show<S> = Show.fromToString()): FSM<S, E> {
    SE.run {
        SS.run {
            return FSM { s, e ->
                wrapping.reduce(s, e).flatMap { state ->
                    IO {
                        System.err.println(s.show())
                        System.err.println(e.show())
                    }.flatMap { IO.just(state) }
                }
            }
        }
    }
}


val checkout: FSM<CheckoutState, CheckoutEvent> = FSM { state, event ->
    val cancelEvent: (CheckoutState) -> IO<CheckoutState> = { state ->
        when {
            state is NoCard -> IO.just(HasItems(state.items))
            state is CardSelected -> IO.just(HasItems(state.items))
            state is CardConfirmed -> IO.just(HasItems(state.items))
            else -> IO.just(state)
        }
    }

    when {
        state is NoItems && event is SelectItem -> IO.just(HasItems(NonEmptyList.just(event.item)))
        state is HasItems && event is SelectItem -> IO.just(HasItems(state.items.plus(event.item)))
        state is HasItems && event is Checkout -> IO.just(NoCard(state.items))
        state is NoCard && event is SelectCard -> IO.just(CardSelected(state.items, event.card))
        state is CardSelected && event is Confirm -> IO.just(CardConfirmed(state.items, state.card))
        event is Cancel -> cancelEvent(state)
        state is CardConfirmed && event is PlaceOrder -> state.card.charge(calculatePrice(state.items.all)).map { OrderPlaced() }
        else -> IO.just(state)
    }
}



