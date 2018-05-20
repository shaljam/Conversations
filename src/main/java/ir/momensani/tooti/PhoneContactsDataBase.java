package ir.momensani.tooti;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/**
 * Created by Ali Momen Sani on 5/10/18.
 */
public class PhoneContactsDataBase extends SQLiteOpenHelper {

    public static  final Object lock  = new Object();

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    public static final String DATABASE_NAME = "phonecontacts.db";

    // Messages table name
    private static final String TABLE_ITEMS = "phone_contacts_table";

    private static PhoneContactsDataBase instance;

    private static final String ID = "_id";
    private static final String PHONE_NUMBER = "_phone_number";
    private static final String CONTACT_NAME = "_contact_name";
    private static final String JOINED = "_is_joined";
    private static final String DELETED = "_is_deleted";

    private static final String CREATE_PHONE_CONTACTS_TABLE =
            "CREATE TABLE " + TABLE_ITEMS + "(" +
                    PHONE_NUMBER + " TEXT UNIQUE," +
                    ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    CONTACT_NAME + " TEXT," +
                    JOINED + " BOOLEAN," +
                    DELETED + " BOOLEAN)";

    private PhoneContactsDataBase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static PhoneContactsDataBase getInstance (Context context) {
        if (instance == null) {
            instance = new PhoneContactsDataBase(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PHONE_CONTACTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEMS);

        // Create tables again
        onCreate(db);
    }

    public boolean phoneContactExists(String phoneNumber) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor mCursor = db.rawQuery("SELECT * FROM " + TABLE_ITEMS +
                " WHERE " + PHONE_NUMBER + " = '" + phoneNumber + "'", null);
        int count = mCursor.getCount();
        mCursor.close();

        return count != 0;
    }

    public void addPhoneContact(String phoneNumber, String name) {
        if (phoneContactExists(phoneNumber)) {
            updateName(phoneNumber, name);
            return;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();

        contentValues.put(PHONE_NUMBER, phoneNumber);
        contentValues.put(CONTACT_NAME, name);
        contentValues.put(JOINED, false);
        contentValues.put(DELETED, false);

        db.insert(TABLE_ITEMS, null, contentValues);
        db.close();
    }

    public void updateName(String phoneNumber, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contactValues = new ContentValues();

        contactValues.put(CONTACT_NAME, name);

        db.update(TABLE_ITEMS, contactValues,
                PHONE_NUMBER + " = '" + phoneNumber + "'", null);
        db.close();
    }


    public void updateIsJoined(String phoneNumber, boolean isJoined) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contactValues = new ContentValues();

        contactValues.put(JOINED, isJoined);

        db.update(TABLE_ITEMS, contactValues,
                PHONE_NUMBER + " = '" + phoneNumber + "'", null);
        db.close();
    }

    public void updateIsDeleted(String phoneNumber, boolean isDeleted) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contactValues = new ContentValues();

        contactValues.put(DELETED, isDeleted);

        db.update(TABLE_ITEMS, contactValues,
                PHONE_NUMBER + " = '" + phoneNumber + "'", null);
        db.close();
    }


    public ArrayList<PhoneContact> getAllPhoneContacts() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<PhoneContact> phoneContacts = new ArrayList<>();

        String selectQuery = "SELECT  * FROM " + TABLE_ITEMS;
        Cursor cursor = db.rawQuery(selectQuery, null);

        int iPhoneNumber = cursor.getColumnIndex(PHONE_NUMBER);
        int iContactName = cursor.getColumnIndex(CONTACT_NAME);
        int iJoined = cursor.getColumnIndex(JOINED);
        int iDeleted = cursor.getColumnIndex(DELETED);

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            PhoneContact phoneContact = new PhoneContact(
                    cursor.getString(iPhoneNumber),
                    cursor.getString(iContactName),
                    cursor.getInt(iJoined) > 0,
                    cursor.getInt(iDeleted) > 0
            );

            phoneContacts.add(phoneContact);
        }

        cursor.close();
        return phoneContacts;
    }

    public ArrayList<String> getJoinedNotDeletedPhoneContacts() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<String> phoneContacts = new ArrayList<>();

        String selectQuery = "SELECT  * FROM " + TABLE_ITEMS +
                " WHERE " + JOINED + " = '1'" + " AND " + DELETED + " != '1'";
        Cursor cursor = db.rawQuery(selectQuery, null);

        int iPhoneNumber = cursor.getColumnIndex(PHONE_NUMBER);

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            phoneContacts.add(cursor.getString(iPhoneNumber));
        }

        cursor.close();
        return phoneContacts;
    }

    public ArrayList<String> getNotDeletedPhoneContacts() {
        SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<String> phoneContacts = new ArrayList<>();

        String selectQuery = "SELECT  * FROM " + TABLE_ITEMS + " WHERE " + DELETED + " != '1'";
        Cursor cursor = db.rawQuery(selectQuery, null);

        int iPhoneNumber = cursor.getColumnIndex(PHONE_NUMBER);

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            phoneContacts.add(cursor.getString(iPhoneNumber));
        }

        cursor.close();
        return phoneContacts;
    }

    public PhoneContact getPhoneContact(String phoneNumber) {
        SQLiteDatabase db = this.getReadableDatabase();

        String selectQuery = "SELECT  * FROM " + TABLE_ITEMS +
                " WHERE " + PHONE_NUMBER + " = '" + phoneNumber + "'";
        Cursor cursor = db.rawQuery(selectQuery, null);

        int iPhoneNumber = cursor.getColumnIndex(PHONE_NUMBER);
        int iContactName = cursor.getColumnIndex(CONTACT_NAME);
        int iJoined = cursor.getColumnIndex(JOINED);
        int iDeleted = cursor.getColumnIndex(DELETED);

        cursor.moveToFirst();

        if (cursor.getCount() == 0) {
            cursor.close();
            return null;
        }

        PhoneContact phoneContact = new PhoneContact(
                cursor.getString(iPhoneNumber),
                cursor.getString(iContactName),
                cursor.getInt(iJoined) > 0,
                cursor.getInt(iDeleted) > 0
        );

        cursor.close();

        return phoneContact;
    }
}
