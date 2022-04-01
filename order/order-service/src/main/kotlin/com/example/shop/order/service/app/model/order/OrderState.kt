package com.example.shop.order.service.app.model.order

import com.example.shop.shared.persistence.CborSerializable

data class OrderState(val orderState: State = State.None) : CborSerializable {
    fun activate() =
        if (canActivate()) copy(orderState = State.Created) else throw IllegalStateChangeException(orderState)

    fun approve() = if (canApprove()) copy(orderState = State.Approved) else throw IllegalStateChangeException(orderState)

    fun reject() =
        if (canReject()) copy(orderState = State.Rejected) else throw IllegalStateChangeException(orderState)

    fun cancel() = if (canCancel()) {
        copy(orderState = State.Cancel)
    } else throw IllegalStateChangeException(orderState)

    fun canActivate() = orderState != State.Cancel && orderState != State.Approved && orderState != State.Rejected
    fun canApprove() = orderState != State.Cancel && orderState != State.Rejected
    fun canReject() = orderState != State.Cancel && orderState != State.Approved

    fun canCancel() = when (orderState) {
        State.None,
        State.Created -> true
        else -> false
    }
}

enum class State {
    None,
    Created,
    Approved,
    Rejected,
    Cancel,
}

class IllegalStateChangeException(val currentState: State, override val message: String = "current state: $currentState") : Exception()
