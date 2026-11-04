package com.smartsplit.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillMemberPaymentStatusTest {
    @Test
    fun noAdvanceBillUpdatesMemberStatusAfterPaid() {
        val bill = Bill(
            projectId = "project",
            title = "Shared bill",
            creatorId = "creator",
            advanceUserId = null,
            debtObject = BillDebtObject(ownerUserId = "creator"),
            memberStatuses = mapOf(
                "member" to MemberBillStatus(
                    userId = "member",
                    amountOwed = 30_000L,
                    isApproved = true
                ),
                "other" to MemberBillStatus(
                    userId = "other",
                    amountOwed = 20_000L,
                    isApproved = true
                )
            )
        )

        assertFalse(bill.isMemberPaymentComplete("member"))
        assertFalse(bill.isAllPaid)
        assertTrue(
            bill.copy(
                memberStatuses = bill.memberStatuses + (
                    "member" to bill.memberStatuses.getValue("member").copy(
                        isPaid = true,
                        isCreatorConfirmed = true
                    )
                )
            ).isMemberPaymentComplete("member")
        )
    }

    @Test
    fun paymentWithCreditorShowsPendingUntilConfirmed() {
        val bill = Bill(
            projectId = "project",
            title = "Shared bill",
            creatorId = "creator",
            advanceUserId = "creator",
            memberStatuses = mapOf(
                "member" to MemberBillStatus(
                    userId = "member",
                    amountOwed = 30_000L,
                    isApproved = true,
                    hasExplicitApproval = true,
                    isPaid = true
                )
            )
        )

        assertFalse(bill.isMemberPaymentComplete("member"))
        assertTrue(
            bill.copy(
                memberStatuses = bill.memberStatuses + (
                    "member" to bill.memberStatuses.getValue("member").copy(
                        isCreatorConfirmed = true
                    )
                )
            ).isMemberPaymentComplete("member")
        )
    }
}
