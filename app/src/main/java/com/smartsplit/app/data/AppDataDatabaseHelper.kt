package com.smartsplit.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppDataDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE projects (
                id TEXT PRIMARY KEY NOT NULL,
                invite_code TEXT NOT NULL,
                data TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE project_members (
                user_id TEXT NOT NULL,
                project_id TEXT NOT NULL,
                PRIMARY KEY (user_id, project_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bills (
                id TEXT PRIMARY KEY NOT NULL,
                project_id TEXT NOT NULL,
                creator_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                data TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX bills_project_id_idx ON bills(project_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun saveSnapshot(projects: List<Project>, bills: List<Bill>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            projects.forEach { project ->
                val projectValues = ContentValues().apply {
                    put("id", project.id)
                    put("invite_code", project.inviteCode)
                    put("data", projectToJson(project).toString())
                }
                db.insertWithOnConflict("projects", null, projectValues, SQLiteDatabase.CONFLICT_REPLACE)

                db.delete("project_members", "project_id = ?", arrayOf(project.id))
                project.memberIds.distinct().forEach { userId ->
                    val memberValues = ContentValues().apply {
                        put("user_id", userId)
                        put("project_id", project.id)
                    }
                    db.insertOrThrow("project_members", null, memberValues)
                }
            }

            bills.forEach { bill ->
                saveBill(db, bill)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveBills(bills: List<Bill>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            bills.forEach { bill -> saveBill(db, bill) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveBill(db: SQLiteDatabase, bill: Bill) {
        val values = ContentValues().apply {
            put("id", bill.id)
            put("project_id", bill.projectId)
            put("creator_id", bill.creatorId)
            put("created_at", bill.createdAt)
            put("data", billToJson(bill).toString())
        }
        check(
            db.insertWithOnConflict("bills", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
        ) { "Failed to persist bill ${bill.id}" }
    }

    fun projectsForUser(userId: String): List<Project> {
        val db = readableDatabase
        return db.rawQuery(
            """
            SELECT projects.data
            FROM projects
            INNER JOIN project_members ON project_members.project_id = projects.id
            WHERE project_members.user_id = ?
            ORDER BY projects.rowid DESC
            """.trimIndent(),
            arrayOf(userId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(projectFromJson(JSONObject(cursor.getString(0))))
            }
        }
    }

    fun billsForProjects(projectIds: List<String>): List<Bill> {
        if (projectIds.isEmpty()) return emptyList()
        val placeholders = projectIds.joinToString(",") { "?" }
        return readableDatabase.rawQuery(
            "SELECT data FROM bills WHERE project_id IN ($placeholders) ORDER BY created_at DESC",
            projectIds.toTypedArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(billFromJson(JSONObject(cursor.getString(0))))
            }
        }
    }

    fun billsForUser(userId: String): List<Bill> =
        readableDatabase.rawQuery(
            """
            SELECT DISTINCT bills.data
            FROM bills
            LEFT JOIN project_members ON project_members.project_id = bills.project_id
            WHERE bills.creator_id = ? OR project_members.user_id = ?
            ORDER BY bills.created_at DESC
            """.trimIndent(),
            arrayOf(userId, userId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(billFromJson(JSONObject(cursor.getString(0))))
            }
        }

    fun deleteBill(billId: String): Int =
        writableDatabase.delete("bills", "id = ?", arrayOf(billId))

    fun projectByInviteCode(code: String): Project? =
        readableDatabase.query(
            "projects",
            arrayOf("data"),
            "upper(invite_code) = upper(?)",
            arrayOf(code.trim()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) projectFromJson(JSONObject(cursor.getString(0))) else null
        }

    fun projectToMap(project: Project): Map<String, Any?> = projectToJson(project).toMap()

    fun projectFromMap(data: Map<String, Any?>): Project =
        projectFromJson(JSONObject(data))

    fun billToMap(bill: Bill): Map<String, Any?> = billToJson(bill).toMap()

    fun billFromMap(data: Map<String, Any?>): Bill =
        billFromJson(JSONObject(data))

    private fun projectToJson(project: Project) = JSONObject().apply {
        put("id", project.id)
        put("name", project.name)
        put("inviteCode", project.inviteCode)
        put("iconName", project.iconName)
        put("modelType", project.modelType)
        put("budgetConfig", JSONObject().apply {
            put("totalLimit", project.budgetConfig.totalLimit)
            put("remainingAmount", project.budgetConfig.remainingAmount)
            put("isAutoRenew", project.budgetConfig.isAutoRenew)
            put("cycleDays", project.budgetConfig.cycleDays)
            put("daysLeft", project.budgetConfig.daysLeft)
            put("lastRenewalTimestamp", project.budgetConfig.lastRenewalTimestamp)
        })
        put("memberIds", JSONArray(project.memberIds))
        put("isElectricityWaterEnabled", project.isElectricityWaterEnabled)
        put("requireBillApproval", project.requireBillApproval)
    }

    private fun billToJson(bill: Bill) = JSONObject().apply {
        put("id", bill.id)
        put("projectId", bill.projectId)
        put("title", bill.title)
        put("creatorId", bill.creatorId)
        putNullable("advanceUserId", bill.advanceUserId)
        put("debtObject", bill.debtObject?.let { debtObject ->
            JSONObject().apply {
                put("id", debtObject.id)
                put("name", debtObject.name)
                put("ownerUserId", debtObject.ownerUserId)
            }
        } ?: JSONObject.NULL)
        put("createdAt", bill.createdAt)
        put("billDate", bill.billDate)
        put("mode", bill.mode.name)
        put("nb2Permission", JSONObject().apply {
            put("canEditName", bill.nb2Permission.canEditName)
            put("canEditQty", bill.nb2Permission.canEditQty)
            put("canEditPrice", bill.nb2Permission.canEditPrice)
            put("canSelectItem", bill.nb2Permission.canSelectItem)
        })
        putNullable("receiptImageUri", bill.receiptImageUri)
        put("recurringDay", bill.recurringDay ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            bill.items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("unitPrice", item.unitPrice)
                    put("payerUserIds", JSONArray(item.payerUserIds))
                })
            }
        })
        put("memberStatuses", JSONObject().apply {
            bill.memberStatuses.forEach { (userId, status) ->
                put(userId, JSONObject().apply {
                    put("userId", status.userId)
                    put("itemCount", status.itemCount)
                    put("amountOwed", status.amountOwed)
                    put("isApproved", status.isApproved)
                    put("isPaid", status.isPaid)
                    putNullable("proofImageUri", status.proofImageUri)
                    put("isCreatorConfirmed", status.isCreatorConfirmed)
                })
            }
        })
        put("isMergedParent", bill.isMergedParent)
        put("childBillIds", JSONArray(bill.childBillIds))
        putNullable("mergedIntoBillId", bill.mergedIntoBillId)
        put("optimizedTransfers", JSONArray().apply {
            bill.optimizedTransfers.forEach { transfer ->
                put(JSONObject().apply {
                    put("id", transfer.id)
                    put("fromUserId", transfer.fromUserId)
                    put("toUserId", transfer.toUserId)
                    put("amount", transfer.amount)
                    put("transferCode", transfer.transferCode)
                    put("isPaid", transfer.isPaid)
                    put("isConfirmed", transfer.isConfirmed)
                })
            }
        })
        put("editHistory", JSONArray().apply {
            bill.editHistory.forEach { record ->
                put(JSONObject().apply {
                    put("editorUserId", record.editorUserId)
                    put("editedAt", record.editedAt)
                    put("changes", record.changes)
                })
            }
        })
    }

    private fun projectFromJson(json: JSONObject): Project {
        val budget = json.getJSONObject("budgetConfig")
        return Project(
            id = json.getString("id"),
            name = json.getString("name"),
            inviteCode = json.getString("inviteCode"),
            iconName = json.optString("iconName", "home"),
            modelType = json.optString("modelType", "Phòng trọ"),
            budgetConfig = BudgetConfig(
                totalLimit = budget.optLong("totalLimit", 4_000_000L),
                remainingAmount = budget.optLong("remainingAmount", 2_450_000L),
                isAutoRenew = budget.optBoolean("isAutoRenew", false),
                cycleDays = budget.optInt("cycleDays", 0),
                daysLeft = budget.optInt("daysLeft", 0),
                lastRenewalTimestamp = budget.optLong("lastRenewalTimestamp", System.currentTimeMillis())
            ),
            memberIds = json.getJSONArray("memberIds").toStringList(),
            isElectricityWaterEnabled = json.optBoolean("isElectricityWaterEnabled", true),
            requireBillApproval = json.optBoolean("requireBillApproval", false)
        )
    }

    private fun billFromJson(json: JSONObject): Bill {
        val permission = json.optJSONObject("nb2Permission") ?: JSONObject()
        val statuses = json.optJSONObject("memberStatuses") ?: JSONObject()
        val statusMap = buildMap {
            val keys = statuses.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val status = statuses.getJSONObject(key)
                val amountOwed = status.optLong("amountOwed", 0L)
                val isCreatorConfirmed = status.optBoolean("isCreatorConfirmed", false)
                val hasDebtObject = json.optJSONObject("debtObject") != null
                put(key, MemberBillStatus(
                    userId = status.optString("userId", key),
                    itemCount = status.optInt("itemCount", 0),
                    amountOwed = amountOwed,
                    isApproved = status.optBoolean("isApproved", false) &&
                        (hasDebtObject || amountOwed == 0L || isCreatorConfirmed),
                    isPaid = status.optBoolean("isPaid", false),
                    proofImageUri = status.optNullableString("proofImageUri"),
                    isCreatorConfirmed = isCreatorConfirmed
                ))
            }
        }
        val itemArray = json.optJSONArray("items") ?: JSONArray()
        val transferArray = json.optJSONArray("optimizedTransfers") ?: JSONArray()
        val editHistoryArray = json.optJSONArray("editHistory") ?: JSONArray()
        return Bill(
            id = json.getString("id"),
            projectId = json.getString("projectId"),
            title = json.getString("title"),
            creatorId = json.getString("creatorId"),
            advanceUserId = if (json.has("advanceUserId")) {
                json.optNullableString("advanceUserId")
            } else {
                json.getString("creatorId")
            },
            debtObject = json.optJSONObject("debtObject")?.let { debtObject ->
                BillDebtObject(
                    id = debtObject.optString("id", UUID.randomUUID().toString()),
                    name = debtObject.optString("name", "Khoản nợ chưa có người ứng"),
                    ownerUserId = debtObject.optString("ownerUserId", json.getString("creatorId"))
                )
            },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            billDate = json.optLong(
                "billDate",
                calendarDateMillis(json.optLong("createdAt", System.currentTimeMillis()))
            ),
            mode = InputMode.valueOf(json.optString("mode", InputMode.NB1.name)),
            nb2Permission = Nb2Permission(
                canEditName = permission.optBoolean("canEditName", true),
                canEditQty = permission.optBoolean("canEditQty", true),
                canEditPrice = permission.optBoolean("canEditPrice", true),
                canSelectItem = permission.optBoolean("canSelectItem", true)
            ),
            receiptImageUri = json.optNullableString("receiptImageUri"),
            recurringDay = if (json.isNull("recurringDay")) null else json.optInt("recurringDay"),
            items = buildList {
                for (index in 0 until itemArray.length()) {
                    val item = itemArray.getJSONObject(index)
                    add(BillItem(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        quantity = item.optInt("quantity", 1),
                        unitPrice = item.optLong("unitPrice", 0L),
                        payerUserIds = (item.optJSONArray("payerUserIds") ?: JSONArray()).toStringList()
                    ))
                }
            },
            memberStatuses = statusMap,
            isMergedParent = json.optBoolean("isMergedParent", false),
            childBillIds = (json.optJSONArray("childBillIds") ?: JSONArray()).toStringList(),
            mergedIntoBillId = json.optNullableString("mergedIntoBillId"),
            optimizedTransfers = buildList {
                for (index in 0 until transferArray.length()) {
                    val transfer = transferArray.getJSONObject(index)
                    add(OptimizedTransfer(
                        id = transfer.getString("id"),
                        fromUserId = transfer.getString("fromUserId"),
                        toUserId = transfer.getString("toUserId"),
                        amount = transfer.optLong("amount", 0L),
                        transferCode = transfer.optString("transferCode", ""),
                        isPaid = transfer.optBoolean("isPaid", false),
                        isConfirmed = transfer.optBoolean("isConfirmed", false)
                    ))
                }
            },
            editHistory = buildList {
                for (index in 0 until editHistoryArray.length()) {
                    val record = editHistoryArray.getJSONObject(index)
                    add(
                        BillEditHistory(
                            editorUserId = record.optString("editorUserId"),
                            editedAt = record.optLong("editedAt", 0L),
                            changes = record.optString("changes", "Cập nhật bill")
                        )
                    )
                }
            }
        )
    }

    private fun JSONObject.putNullable(name: String, value: String?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name)

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    private fun JSONObject.toMap(): Map<String, Any?> =
        keys().asSequence().associateWith { key -> get(key).toPlainValue() }

    private fun Any?.toPlainValue(): Any? = when (this) {
        JSONObject.NULL -> null
        is JSONObject -> toMap()
        is JSONArray -> (0 until length()).map { get(it).toPlainValue() }
        else -> this
    }

    companion object {
        private const val DATABASE_NAME = "smartsplit_app_data.db"
        private const val DATABASE_VERSION = 1
    }
}
