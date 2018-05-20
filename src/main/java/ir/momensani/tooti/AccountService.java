package ir.momensani.tooti;

import io.reactivex.Observable;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Created by Ali Momen Sani on 5/9/18.
 */
public interface AccountService {
    @POST("request-sms")
    Observable<Response<Void>> requestSms(@Body AccountManager.SmsRequest request);

    @POST("register")
    Observable<Response<Void>> register(@Body AccountManager.RegisterRequest request);

    @POST("contacts")
    Observable<Response<AccountManager.SendContactsResponse>>
    sendContacts(@Body AccountManager.SendContactsRequest request);
}
