package ir.momensani.tooti;

import java.util.List;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Ali Momen Sani on 5/9/18.
 */
public class AccountManager {

    private static AccountManager instance;

    public static AccountManager getInstance() {
        if (instance == null) {
            instance = new AccountManager();
        }

        return instance;
    }

    private AccountService service;

    private AccountManager() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://auth.momensani.ir")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(AccountService.class);
    }

    public AccountService getService() {
        return service;
    }

    public static class SmsRequest {
        final String phone;

        public SmsRequest(String phone) {
            this.phone = phone;
        }
    }

    public static class RegisterRequest {
        final String phone;
        final String server;
        final int verification_code;
        final String password;

        public RegisterRequest(String phone, String server, int verification_code, String password) {
            this.phone = phone;
            this.server = server;
            this.verification_code = verification_code;
            this.password = password;
        }
    }

    public static class SendContactsRequest {
        final String jid;
        final String password;
        final List<String> contacts;

        public SendContactsRequest(String jid, String password, List<String> contacts) {
            this.contacts = contacts;
            this.jid = jid;
            this.password = password;
        }
    }

    public static class SendContactsResponse {
        final List<String> contacts;

        public SendContactsResponse(List<String> contacts) {
            this.contacts = contacts;
        }

        public List<String> getContacts() {
            return contacts;
        }
    }
}
