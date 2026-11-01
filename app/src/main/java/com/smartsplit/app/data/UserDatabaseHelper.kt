package com.smartsplit.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class UserDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "smartsplit.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_FULL_NAME = "full_name"
        private const val COLUMN_AVATAR = "avatar"
        private const val COLUMN_BANK_NAME = "bank_name"
        private const val COLUMN_BANK_BIN = "bank_bin"
        private const val COLUMN_ACCOUNT_NUMBER = "account_number"
        private const val COLUMN_QR_IMAGE_URI = "qr_image_uri"
        private const val COLUMN_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_USERNAME TEXT UNIQUE NOT NULL,
                $COLUMN_EMAIL TEXT UNIQUE NOT NULL,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_FULL_NAME TEXT NOT NULL,
                $COLUMN_AVATAR TEXT DEFAULT '',
                $COLUMN_BANK_NAME TEXT DEFAULT '',
                $COLUMN_BANK_BIN TEXT DEFAULT '',
                $COLUMN_ACCOUNT_NUMBER TEXT DEFAULT '',
                $COLUMN_QR_IMAGE_URI TEXT DEFAULT '',
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN $COLUMN_QR_IMAGE_URI TEXT DEFAULT ''")
        }
    }

    // Insert user
    fun insertUser(user: DatabaseUser): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, user.id)
            put(COLUMN_USERNAME, user.username)
            put(COLUMN_EMAIL, user.email)
            put(COLUMN_PASSWORD, user.password)
            put(COLUMN_FULL_NAME, user.fullName)
            put(COLUMN_AVATAR, user.avatar)
            put(COLUMN_BANK_NAME, user.bankName)
            put(COLUMN_BANK_BIN, user.bankBin)
            put(COLUMN_ACCOUNT_NUMBER, user.accountNumber)
            put(COLUMN_QR_IMAGE_URI, user.qrImageUri)
            put(COLUMN_CREATED_AT, user.createdAt)
        }
        return db.insert(TABLE_USERS, null, values)
    }

    // Get user by username
    fun getUserByUsername(username: String): DatabaseUser? {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_USERNAME = ?",
            arrayOf(username),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            cursorToUser(cursor)
        } else {
            cursor.close()
            null
        }
    }

    // Get user by email
    fun getUserByEmail(email: String): DatabaseUser? {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_EMAIL = ?",
            arrayOf(email),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            cursorToUser(cursor)
        } else {
            cursor.close()
            null
        }
    }

    // Login (check username and password)
    fun login(username: String, password: String): DatabaseUser? {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_USERNAME = ? AND $COLUMN_PASSWORD = ?",
            arrayOf(username, password),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            cursorToUser(cursor)
        } else {
            cursor.close()
            null
        }
    }

    // Get user by ID
    fun getUserById(id: String): DatabaseUser? {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            cursorToUser(cursor)
        } else {
            cursor.close()
            null
        }
    }

    // Update user profile
    fun updateUserProfile(
        id: String,
        fullName: String,
        avatar: String,
        bankName: String,
        bankBin: String,
        accountNumber: String
    ): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FULL_NAME, fullName)
            put(COLUMN_AVATAR, avatar)
            put(COLUMN_BANK_NAME, bankName)
            put(COLUMN_BANK_BIN, bankBin)
            put(COLUMN_ACCOUNT_NUMBER, accountNumber)
        }

        return db.update(
            TABLE_USERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id)
        )
    }

    fun updateUserQrImage(id: String, uri: String): Int {
        val values = ContentValues().apply {
            put(COLUMN_QR_IMAGE_URI, uri)
        }
        return writableDatabase.update(
            TABLE_USERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id)
        )
    }

    private fun cursorToUser(cursor: Cursor): DatabaseUser {
        return DatabaseUser(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
            email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
            password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
            fullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME)),
            avatar = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AVATAR)),
            bankName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BANK_NAME)),
            bankBin = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BANK_BIN)),
            accountNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNT_NUMBER)),
            qrImageUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QR_IMAGE_URI)) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
        ).also { cursor.close() }
    }
}

// Data class for user
data class DatabaseUser(
    val id: String,
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val avatar: String = "",
    val bankName: String = "",
    val bankBin: String = "",
    val accountNumber: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val qrImageUri: String = ""
)
