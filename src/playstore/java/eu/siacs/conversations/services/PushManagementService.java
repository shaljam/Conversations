package eu.siacs.conversations.services;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.iid.FirebaseInstanceId;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import ir.momensani.tooti.Utils;
import ir.momensani.tooti.ui.RegisterActivity;
import rocks.xmpp.addr.Jid;


public class PushManagementService {

	private static final Jid APP_SERVER =
			Jid.of(String.format("pubsub.%s", RegisterActivity.SERVER));

	protected final XmppConnectionService mXmppConnectionService;

	PushManagementService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	void registerPushTokenOnServer(final Account account) {
		Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": has push support");
		String token = FirebaseInstanceId.getInstance().getToken();
		String md5Token = Utils.md5(token);
		if (md5Token == null) {
			Log.e(Config.LOGTAG, "create push node failed to encode token: " + token);
			return;
		}

		IqPacket packet = mXmppConnectionService.getIqGenerator().createPushNode(APP_SERVER, md5Token);
		mXmppConnectionService.sendIqPacket(account, packet, (a, p) -> {
			if (p.getType() == IqPacket.TYPE.RESULT) {
				enablePushOnMongooseServer(a, APP_SERVER, md5Token, token);
			} else if (p.getType() == IqPacket.TYPE.ERROR && p.getError() != null && p.getError().getName().equals("conflict")) {
				Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": create push node conflict error");
				enablePushOnMongooseServer(a, APP_SERVER, md5Token, token);
			}
			else {
				Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": create push node invalid response");
			}
		});
	}

	private void enablePushOnServer(final Account account, final Jid jid, final String node, final String secret) {
		IqPacket enable = mXmppConnectionService.getIqGenerator().enablePush(jid, node, secret);
		mXmppConnectionService.sendIqPacket(account, enable, (a, p) -> {
			if (p.getType() == IqPacket.TYPE.RESULT) {
				Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": successfully enabled push on server");
			} else if (p.getType() == IqPacket.TYPE.ERROR) {
				Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": enabling push on server failed");
			}
		});
	}

	private void enablePushOnMongooseServer(final Account account, final Jid jid, final String node, String token) {
		IqPacket enable = mXmppConnectionService.getIqGenerator().enableMongoosePush(jid, node, token, false);
		mXmppConnectionService.sendIqPacket(account, enable, (a, p) -> {
			if (p.getType() == IqPacket.TYPE.RESULT) {
				Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": successfully enabled push on MongooseIM server");
			} else if (p.getType() == IqPacket.TYPE.ERROR) {
				Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": enabling push on MongooseIM server failed");
			}
		});
	}

	public boolean available(Account account) {
		final XmppConnection connection = account.getXmppConnection();
		return connection != null
				&& connection.getFeatures().sm()
				&& connection.getFeatures().push()
				&& playServicesAvailable();
	}

	private boolean playServicesAvailable() {
		return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mXmppConnectionService) == ConnectionResult.SUCCESS;
	}

	public boolean isStub() {
		return false;
	}


}
