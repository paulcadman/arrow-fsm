package io.paulcadman.fsm

import arrow.data.ListK
import arrow.data.foldable
import org.junit.Test
import org.junit.Assert.fail

import java.math.BigDecimal

class ExampleUnitTest {
    @Test
    fun run_a_statemachine() {
        withLogging(checkout).run {
            val l = ListK(listOf(
                    SelectItem(BasketItem("eggs", BigDecimal(1.00))),
                    SelectItem(BasketItem("fish", BigDecimal(168.50))),
                    Checkout(),
                    SelectCard(Card("0000-0000-0000-0000")),
                    Confirm(),
                    PlaceOrder()
            ))
            ListK.foldable().runFSM(NoItems(), l)
        }.unsafeRunSync()
    }
}

