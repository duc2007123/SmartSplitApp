package com.smartsplit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedBillSettlementTest {
    @Test
    fun combinesAdvancedDebtsAndRoutesExternalBillThroughChosenRepresentative() {
        val project = Project(
            id = "project",
            name = "Trip",
            inviteCode = "TRIP1234",
            memberIds = listOf("a", "b", "c")
        )
        val advancedBill = Bill(
            id = "advanced",
            projectId = project.id,
            title = "Dinner",
            creatorId = "a",
            advanceUserId = "a",
            memberStatuses = mapOf(
                "a" to MemberBillStatus(userId = "a", amountOwed = 0L, isApproved = true),
                "b" to MemberBillStatus(userId = "b", amountOwed = 60L, isApproved = true),
                "c" to MemberBillStatus(userId = "c", amountOwed = 40L, isApproved = true)
            )
        )
        val noAdvanceBill = Bill(
            id = "rent",
            projectId = project.id,
            title = "Trọ tháng 10",
            creatorId = "b",
            advanceUserId = null,
            debtObject = BillDebtObject(ownerUserId = "b"),
            memberStatuses = mapOf(
                "a" to MemberBillStatus(userId = "a", amountOwed = 30L, isApproved = true),
                "b" to MemberBillStatus(userId = "b", amountOwed = 20L, isApproved = true),
                "c" to MemberBillStatus(userId = "c", amountOwed = 50L, isApproved = true)
            )
        )

        val settlement = calculateMergedBillSettlement(
            project = project,
            bills = listOf(advancedBill, noAdvanceBill),
            representativeByBillId = mapOf(noAdvanceBill.id to "a"),
            mergedBillId = "MERGED_123",
            memberNames = mapOf("a" to "Alice", "b" to "Bob", "c" to "Carol")
        )

        val internalTransfers = settlement.transfers.filterNot { it.isExternalPayment }
        val externalTransfer = settlement.transfers.single { it.isExternalPayment }
        assertEquals(2, internalTransfers.size)
        assertTrue(internalTransfers.all { it.toUserId == "a" })
        assertEquals(80L, internalTransfers.single { it.fromUserId == "b" }.amount)
        assertEquals(90L, internalTransfers.single { it.fromUserId == "c" }.amount)
        assertEquals("a", externalTransfer.fromUserId)
        assertNull(externalTransfer.toUserId)
        assertEquals("Chủ bill: Trọ tháng 10", externalTransfer.recipientName)
        assertEquals(100L, externalTransfer.amount)
        assertEquals(noAdvanceBill.id, externalTransfer.sourceBillId)
        assertEquals(0L, settlement.memberNetBalances.values.sum())
    }

    @Test
    fun externalPaymentOnlyIncludesUnpaidApprovedAmounts() {
        val project = Project(
            id = "project",
            name = "Trip",
            inviteCode = "TRIP1234",
            memberIds = listOf("a", "b")
        )
        val bill = Bill(
            id = "rent",
            projectId = project.id,
            title = "Trọ tháng 10",
            creatorId = "a",
            advanceUserId = null,
            debtObject = BillDebtObject(ownerUserId = "a"),
            memberStatuses = mapOf(
                "a" to MemberBillStatus(userId = "a", amountOwed = 30L, isApproved = true),
                "b" to MemberBillStatus(
                    userId = "b",
                    amountOwed = 70L,
                    isApproved = true,
                    isCreatorConfirmed = true
                )
            )
        )

        val settlement = calculateMergedBillSettlement(
            project = project,
            bills = listOf(bill),
            representativeByBillId = mapOf(bill.id to "a"),
            mergedBillId = "MERGED_123",
            memberNames = emptyMap()
        )

        assertEquals(30L, settlement.transfers.single().amount)
        assertEquals("a", settlement.transfers.single().fromUserId)
    }
}
