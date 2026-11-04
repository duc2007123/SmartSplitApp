package com.smartsplit.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BillUnpaidAmountTest {
    @Test
    fun unapprovedMemberDoesNotHaveDebtOnBillWithoutAdvance() {
        val bill = Bill(
            projectId = "project",
            title = "Shared bill",
            creatorId = "creator",
            advanceUserId = null,
            debtObject = BillDebtObject(ownerUserId = "creator"),
            memberStatuses = mapOf(
                "creator" to MemberBillStatus(
                    userId = "creator",
                    amountOwed = 30_000L,
                    isApproved = true
                ),
                "member" to MemberBillStatus(
                    userId = "member",
                    amountOwed = 40_000L,
                    isApproved = false
                )
            )
        )

        assertEquals(0L, bill.unpaidAmountFor("member"))
        assertEquals(30_000L, bill.unpaidAmountFor("creator"))
    }

    @Test
    fun approvedMemberDebtIsCountedOnBillWithoutAdvance() {
        val bill = Bill(
            projectId = "project",
            title = "Shared bill",
            creatorId = "creator",
            advanceUserId = null,
            debtObject = BillDebtObject(ownerUserId = "creator"),
            memberStatuses = mapOf(
                "member" to MemberBillStatus(
                    userId = "member",
                    amountOwed = 40_000L,
                    isApproved = true
                )
            )
        )

        assertEquals(40_000L, bill.unpaidAmountFor("member"))
    }
}
