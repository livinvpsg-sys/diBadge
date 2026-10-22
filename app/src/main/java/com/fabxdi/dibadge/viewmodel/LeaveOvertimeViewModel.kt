package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.ClaimEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LeaveOvertimeViewModel(application: Application) : AndroidViewModel(application) {
    private val leaveOvertimeDao = AppDatabase.getDatabase(application).leaveOvertimeDao()

    val allLeaves: Flow<List<LeaveEntity>> = leaveOvertimeDao.getAllLeaves()
    val allOvertime: Flow<List<OvertimeEntity>> = leaveOvertimeDao.getAllOvertime()
    val allClaims: Flow<List<ClaimEntity>> = leaveOvertimeDao.getAllClaims()

    init {
        viewModelScope.launch {
            leaveOvertimeDao.deletePastPendingLeaves(LocalDate.now())
        }
    }

    fun saveOvertime(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        description: String,
        id: Int = 0,
        status: String = "Pending",
        appliedDate: LocalDate = LocalDate.now(),
        appliedDateTime: LocalDateTime = LocalDateTime.now(),
        approvedDate: LocalDate? = null
    ) {
        viewModelScope.launch {
            val overtime = OvertimeEntity(
                id = id,
                date = date,
                startTime = startTime,
                endTime = endTime,
                description = description,
                status = status,
                appliedDate = appliedDate,
                appliedDateTime = appliedDateTime,
                approvedDate = approvedDate
            )
            if (id == 0) {
                leaveOvertimeDao.insertOvertime(overtime)
            } else {
                leaveOvertimeDao.updateOvertime(overtime)
            }
        }
    }

    fun updateOvertime(overtime: OvertimeEntity) {
        viewModelScope.launch {
            leaveOvertimeDao.updateOvertime(overtime)
        }
    }

    fun deleteOvertime(overtime: OvertimeEntity) {
        viewModelScope.launch {
            leaveOvertimeDao.deleteOvertime(overtime)
        }
    }

    fun saveLeave(
        startDate: LocalDate,
        endDate: LocalDate?,
        leaveType: String,
        reason: String,
        attachments: List<String>,
        id: Int = 0,
        status: String = "Pending",
        appliedDate: LocalDate = LocalDate.now(),
        appliedDateTime: LocalDateTime = LocalDateTime.now(),
        approvedDate: LocalDate? = null
    ) {
        viewModelScope.launch {
            val leave = LeaveEntity(
                id = id,
                startDate = startDate,
                endDate = endDate,
                leaveType = leaveType,
                reason = reason,
                attachments = attachments,
                status = status,
                appliedDate = appliedDate,
                appliedDateTime = appliedDateTime,
                approvedDate = approvedDate
            )
            if (id == 0) {
                leaveOvertimeDao.insertLeave(leave)
            } else {
                leaveOvertimeDao.updateLeave(leave)
            }
        }
    }

    fun updateLeave(leave: LeaveEntity) {
        viewModelScope.launch {
            leaveOvertimeDao.updateLeave(leave)
        }
    }

    fun deleteLeave(leave: LeaveEntity) {
        viewModelScope.launch {
            leaveOvertimeDao.deleteLeave(leave)
        }
    }

    fun saveClaim(
        date: LocalDate,
        category: String,
        amount: Double,
        description: String,
        id: Int = 0,
        status: String = "Pending",
        receiptUrl: String? = null
    ) {
        viewModelScope.launch {
            val claim = ClaimEntity(
                id = id,
                date = date,
                category = category,
                amount = amount,
                description = description,
                status = status,
                receiptUrl = receiptUrl
            )
            if (id == 0) {
                leaveOvertimeDao.insertClaim(claim)
            } else {
                leaveOvertimeDao.updateClaim(claim)
            }
        }
    }

    fun updateClaim(claim: ClaimEntity) {
        viewModelScope.launch {
            leaveOvertimeDao.updateClaim(claim)
        }
    }

    fun deleteClaim(claim: ClaimEntity) {
        viewModelScope.launch {
            leaveOvertimeDao.deleteClaim(claim)
        }
    }
}
