package ir.momensani.tooti;

/**
 * Created by Ali Momen Sani on 5/8/18.
 */

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import eu.siacs.conversations.services.XmppConnectionService;


public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Firebase notification received: " + remoteMessage.getMessageId());
        Map<String, String> data = remoteMessage.getData();

        if (data.size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());

            Intent intent = new Intent(this, XmppConnectionService.class);
            intent.setAction(XmppConnectionService.ACTION_GCM_MESSAGE_RECEIVED);
            Bundle extras = new Bundle();
            extras.putBoolean("push", true);
            for (Map.Entry<String, String> entry : data.entrySet()) {
                extras.putString(entry.getKey(), entry.getValue());
            }
            intent.replaceExtras(extras);

            startService(intent);
        }
    }
}
