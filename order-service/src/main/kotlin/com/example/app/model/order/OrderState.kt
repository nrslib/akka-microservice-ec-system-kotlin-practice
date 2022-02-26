package com.example.app.model.order

import com.example.shared.persistence.JacksonSerializable

data class OrderState(val orderState: State = State.None) : JacksonSerializable {
    fun activate() =
        if (canActivate()) copy(orderState = State.Created) else throw IllegalStateChangeException()

    fun approve() = if (canApprove()) copy(orderState = State.Approved) else throw IllegalStateChangeException()

    fun reject() =
        if (canReject()) copy(orderState = State.Rejected) else throw IllegalStateChangeException()

    fun cancel() = if (canCancel()) {
        copy(orderState = State.Cancel)
    } else throw IllegalStateChangeException()

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

class IllegalStateChangeException : Exception()
