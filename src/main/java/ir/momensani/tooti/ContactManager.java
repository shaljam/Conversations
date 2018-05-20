package ir.momensani.tooti;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.OnPhoneContactsLoadedListener;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import rocks.xmpp.addr.Jid;

/**
 * Created by Ali Momen Sani on 5/9/18.
 */
public class ContactManager {
    private final static String TAG = "ContactManager";

    @SuppressLint("CheckResult")
    public static void loadPhoneContacts(Context context, Account account, final OnPhoneContactsLoadedListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            listener.onPhoneContactsLoaded(new ArrayList<>());
            return;
        }

        List<Bundle> contacts = readPhoneContacts(context);

        if (contacts == null) {
            if (listener != null) {
                listener.onPhoneContactsLoaded(new ArrayList<>());
            }
            return;
        }

        PhoneContactsDataBase db =
                PhoneContactsDataBase.getInstance(context.getApplicationContext());
        HashSet<String> existingPhoneNumbers =
                new HashSet<>(Observable.fromIterable(db.getAllPhoneContacts())
                        .map(PhoneContact::getPhoneNumber)
                        .toList()
                        .blockingGet());

        // add new ones to db
        Observable.fromIterable(contacts)
                .subscribe(x -> {
                    String phoneNumber = x.getString("phonenumber");
                    if (existingPhoneNumbers.contains(phoneNumber)) {
                        return;
                    }

                    db.addPhoneContact(phoneNumber, x.getString("displayname"));
                });

        HashSet<String> notDeletedPhoneNumber =
                new HashSet<>(db.getNotDeletedPhoneContacts());

        // prepare to send to server
        List<String> stringContacts = Observable.fromIterable(contacts)
                .filter(x -> notDeletedPhoneNumber.contains(x.getString("phonenumber")))
                .map(x -> String.format("%s@%s",
                        x.getString("phonenumber"),
                        account.getServer()))
                .toList()
                .blockingGet();

        // check joined contacts with server
        AccountManager.getInstance().getService().sendContacts(
            new AccountManager.SendContactsRequest(
                    String.format("%s@%s", account.getUsername(), account.getServer()),
                    account.getPassword(), stringContacts))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(response -> {
                if (response.code() == 200) {
                    HashSet<String> contactsHashSet = new HashSet<>(response.body().getContacts());
                    List<Bundle> finalContacts = Observable.fromIterable(contacts)
                            .filter(contact -> contactsHashSet
                                    .contains(String.format("%s@%s",
                                            contact.getString("phonenumber"),
                                            account.getServer())))
                            .map(contact -> {
                                String phoneNumber = contact.getString("phonenumber");
                                contact.putString("jid", String.format("%s@%s",
                                            phoneNumber,
                                            account.getServer()));

                                // set the contact joined
                                db.updateIsJoined(phoneNumber, true);
                                return contact;
                            })
                            .toList().blockingGet();

                    if (listener != null) {
                        listener.onPhoneContactsLoaded(finalContacts);
                    }
                }
                else {
                    Log.e(TAG, String.format("failed to get contacts with response: %s %s",
                            response.code(), response.message()));
                    if (listener != null) {
                        List<Bundle> finalContacts =
                                getJoinedNotDeletedContacts(db, contacts, account.getServer());
                        listener.onPhoneContactsLoaded(finalContacts);
                    }
                }
            }, error -> {
                Log.d(TAG, error.getMessage());
                if (listener != null) {
                    List<Bundle> finalContacts =
                            getJoinedNotDeletedContacts(db, contacts, account.getServer());
                    listener.onPhoneContactsLoaded(finalContacts);
                }
            });
    }

    @Nullable
    private static List<Bundle> readPhoneContacts(Context context) {
        final String[] PROJECTION = {
                ContactsContract.Data._ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.Data.PHOTO_URI,
                ContactsContract.Data.LOOKUP_KEY
        };

        ArrayList<Bundle> contacts = new ArrayList<>();

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PROJECTION, null, null, null);

        if (cursor == null || !cursor.moveToFirst()) {
            Log.d(TAG, "cursor null or empty");
            return null;
        }

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndex(
                    ContactsContract.Data._ID));
            String number = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER));
            String name = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String normalizedNumber = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER));
            int type = cursor.getInt(cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.TYPE));
            String label = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.LABEL));

            if (label != null) {
                name = name + "" + label;
            }

            if (normalizedNumber == null) {
                normalizedNumber = number.replace(" ", "");
                Log.e(TAG, String.format("number\t%s,\t%s", number, normalizedNumber));
            }

            if (!normalizedNumber.startsWith("+")) {
                normalizedNumber = "+" + normalizedNumber;
            }

            if (!normalizedNumber.startsWith("+989") || normalizedNumber.length() != 13) {
                continue;
            }

            Bundle contact = new Bundle();
            contact.putInt("phoneid", id);
            contact.putString("displayname", name);
            contact.putString("photouri", cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)));
            contact.putString("lookup", cursor.getString(cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));
            contact.putString("phonenumber", normalizedNumber);

            contacts.add(contact);
        }

        cursor.close();

        return contacts;
    }

    public static void setContactDeleted(Context context, Jid jid) {
        PhoneContactsDataBase db = PhoneContactsDataBase.getInstance(context);
        db.updateIsDeleted(jid.getLocal(), true);
    }

    public static void setContactAdded(Context context, Jid jid) {
        PhoneContactsDataBase db = PhoneContactsDataBase.getInstance(context);
        db.updateIsDeleted(jid.getLocal(), false);
    }

    private static List<Bundle> getJoinedNotDeletedContacts(
            PhoneContactsDataBase db, List<Bundle> contacts, String server) {
        HashSet<String> contactsHashSet = new HashSet<>(db.getJoinedNotDeletedPhoneContacts());
        List<Bundle> finalContacts = Observable.fromIterable(contacts)
                .filter(contact -> contactsHashSet
                        .contains(contact.getString("phonenumber")))
                .map(contact -> {
                    String phoneNumber = contact.getString("phonenumber");
                    contact.putString("jid", String.format("%s@%s",
                            phoneNumber,
                            server));

                    return contact;
                })
                .toList().blockingGet();

        return finalContacts;
    }
}
