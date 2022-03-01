package com.example.shop.order.service.saga.order.create

import com.example.shop.shared.persistence.JacksonSerializable


data class OrderCreateSagaState(val id: String, val progress: Progress = Progress.Initialize) : JacksonSerializable {
    fun approvalPending() = copy(progress = Progress.ApprovalPending)
    fun approve() = copy(progress = Progress.Approved)
    fun rejected() = copy(progress = Progress.Rejected)
}

enum class Progress {
    Initialize,
    ApprovalPending,
    Approved,
    Rejected,
}