package com.example.shop.order.service.saga.order.create

import com.example.shop.billing.api.billing.commands.ApproveOrder
import com.example.shop.order.api.order.models.OrderDetail
import com.example.shop.shared.persistence.JacksonSerializable


data class OrderCreateSagaState(
    val orderId: String,
    val orderDetail: OrderDetail?,
    val progress: Progress = Progress.Initialize
) : JacksonSerializable {
    fun securingPending() = copy(progress = Progress.SecuringPending)
    fun securingFailed() = copy(progress = Progress.SecuringFailed)
    fun approvalPending() = copy(progress = Progress.ApprovalPending)
    fun approve() = copy(progress = Progress.Approved)
    fun rejected() = copy(progress = Progress.Rejected)

    fun makeApproveBillingCommand(): ApproveOrder {
        if (orderDetail == null) {
            throw IllegalStateException()
        }

        return ApproveOrder(
            orderId,
            orderDetail.consumerId
        )
    }
}

enum class Progress {
    Initialize,
    SecuringPending,
    SecuringFailed,
    ApprovalPending,
    Approved,
    Rejected,
}