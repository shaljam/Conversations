package ir.momensani.tooti.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.security.KeyChainAliasCallback;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.SocketException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRegisterBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.OmemoActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.ui.WelcomeActivity;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.XmppConnection;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import ir.momensani.tooti.AccountManager;
import rocks.xmpp.addr.Jid;

public class RegisterActivity extends OmemoActivity implements XmppConnectionService.OnAccountUpdate, KeyChainAliasCallback, XmppConnectionService.OnShowErrorToast {
    private static final String TAG = RegisterActivity.class.getSimpleName();

    public static final String SERVER = "t.momensani.ir";

    private static final int REQUEST_DATA_SAVER = 0xf244;
    private static final int REQUEST_CHANGE_STATUS = 0xee11;
    private TextInputLayout mAccountJidLayout;
    private EditText mPassword;
    private TextInputLayout mPasswordLayout;
    private Button mRegisterButton;
    private Button mLoginButton;

    private LinearLayout mNamePort;
    private EditText mHostname;
    private TextInputLayout mHostnameLayout;
    private EditText mPort;
    private TextInputLayout mPortLayout;
    private AlertDialog mCaptchaDialog = null;

    private Jid jidToEdit;
    private boolean mUsernameMode = Config.DOMAIN_LOCK != null;
    private boolean mShowOptions = false;
    private Account mAccount;

    private View mEditor;
    private View mConfirmationCodeCardView;
    private TextInputLayout mConfirmationLayout;
    private TextView mConfirmationTextView;

    private final PendingItem<PresenceTemplate> mPendingPresenceTemplate = new PendingItem<>();

    private String mPhoneNumber;

    private final View.OnClickListener mRegisterButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(final View v) {
            mEditor.setEnabled(false);

            mRegisterButton.setEnabled(false);
            mLoginButton.setEnabled(false);
            requestSms();
        }
    };

    private final View.OnClickListener mLoginButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(final View v) {
            mEditor.setEnabled(false);

            mRegisterButton.setEnabled(false);
            mLoginButton.setEnabled(false);

            final String phoneNumber = binding.phoneNumber.getText().toString();
            mPhoneNumber = "+98" + phoneNumber;

            if (!phoneNumberIsValid(mPhoneNumber)) {
                mAccountJidLayout.setError(RegisterActivity.this.getString(R.string.invalid_phone_number));
                binding.phoneNumber.requestFocus();
                removeErrorsOnAllBut(mAccountJidLayout);

                mEditor.setEnabled(true);
                mRegisterButton.setEnabled(true);
                mLoginButton.setEnabled(true);
                return;
            }

            final String password = binding.accountPassword.getText().toString();
            if (password.length() < 7) {
                mPasswordLayout.setError(RegisterActivity.this.getString(R.string.password_length));
                binding.accountPassword.requestFocus();
                removeErrorsOnAllBut(mPasswordLayout);

                mEditor.setEnabled(true);
                mRegisterButton.setEnabled(true);
                mLoginButton.setEnabled(true);
                return;
            }

            removeErrorsOnAllBut(null);

            registerSuccessful();
        }
    };

    private String mSavedInstanceAccount;
    private boolean mSavedInstanceInit = false;
    private XmppUri pendingUri = null;
    private ActivityRegisterBinding binding;

    private Handler mResendHandler = new Handler();
    private int mResendCounter = 0;
    private TextView mResendTextView;

    public void refreshUiReal() {
        if (mAccount != null) {
            if (mAccount.getStatus() == Account.State.ONLINE) {
                finishInitialSetup();
            }
            else if (mAccount.errorStatus()){
                mPasswordLayout.setError(getString(mAccount.getStatus().getReadableId()));
                removeErrorsOnAllBut(mPasswordLayout);
                mRegisterButton.setEnabled(true);
                mLoginButton.setEnabled(true);
                xmppConnectionService.deleteAccount(mAccount);
            }
        }
    }

    @Override
    public boolean onNavigateUp() {
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void onAccountUpdate() {
        Log.e(TAG, "onAccountUpdate");
        refreshUi();
    }

    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {

        }
    };

    private View.OnFocusChangeListener mEditTextFocusListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            EditText et = (EditText) view;
            if (b) {
                int resId = mUsernameMode ? R.string.username : R.string.account_settings_example_jabber_id;
                if (view.getId() == R.id.hostname) {
                    resId = true ? R.string.hostname_or_onion : R.string.hostname_example;
                }
                final int res = resId;
                new Handler().postDelayed(() -> et.setHint(res), 200);
            } else {
                et.setHint(null);
            }
        }
    };


    protected void finishInitialSetup() {
        runOnUiThread(() -> {
            SoftKeyboardUtils.hideSoftKeyboard(RegisterActivity.this);
            final Intent intent;
            final XmppConnection connection = mAccount.getXmppConnection();
            final boolean wasFirstAccount = xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1;
            if ((connection != null)) {
                intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                if (wasFirstAccount) {
                    intent.putExtra("init", true);
                }
            }
            else {
                return;
            }

            if (wasFirstAccount) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }

            WelcomeActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BATTERY_OP || requestCode == REQUEST_DATA_SAVER) {
            updateAccountInformation(mAccount == null);
        }
        if (requestCode == REQUEST_CHANGE_STATUS) {
            PresenceTemplate template = mPendingPresenceTemplate.pop();
            if (template != null && resultCode == Activity.RESULT_OK) {
                generateSignature(data, template);
            } else {
                Log.d(Config.LOGTAG, "pgp result not ok");
            }
        }
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        processFingerprintVerification(uri, true);
    }


    protected void processFingerprintVerification(XmppUri uri, boolean showWarningToast) {
        if (mAccount != null && mAccount.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(mAccount, uri.getFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
                updateAccountInformation(false);
            }
        } else if (showWarningToast) {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    protected boolean accountInfoEdited() {
        if (this.mAccount == null) {
            return false;
        }
        return jidEdited() ||
                !this.mAccount.getPassword().equals(this.mPassword.getText().toString()) ||
                !this.mAccount.getHostname().equals(this.mHostname.getText().toString()) ||
                !String.valueOf(this.mAccount.getPort()).equals(this.mPort.getText().toString());
    }

    protected boolean jidEdited() {
        final String unmodified;
        if (mUsernameMode) {
            unmodified = this.mAccount.getJid().getLocal();
        } else {
            unmodified = this.mAccount.getJid().asBareJid().toString();
        }
        return !unmodified.equals(this.binding.phoneNumber.getText().toString());
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (mAccount != null) {
            return http ? mAccount.getShareableLink() : mAccount.getShareableUri();
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mSavedInstanceAccount = savedInstanceState.getString("account");
            this.mSavedInstanceInit = savedInstanceState.getBoolean("initMode", false);
        }
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_register);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar());
        binding.phoneNumber.addTextChangedListener(this.mTextWatcher);
        binding.phoneNumber.setOnFocusChangeListener(this.mEditTextFocusListener);
        this.mAccountJidLayout = findViewById(R.id.phone_number_layout);
        this.mPassword = findViewById(R.id.account_password);
        this.mPassword.addTextChangedListener(this.mTextWatcher);
        this.mPasswordLayout = findViewById(R.id.account_password_layout);
        this.mNamePort = findViewById(R.id.name_port);
        this.mHostname = findViewById(R.id.hostname);
        this.mHostname.addTextChangedListener(mTextWatcher);
        this.mHostname.setOnFocusChangeListener(mEditTextFocusListener);
        this.mHostnameLayout = findViewById(R.id.hostname_layout);
        this.mPort = findViewById(R.id.port);
        this.mPort.setText("5222");
        this.mPort.addTextChangedListener(mTextWatcher);
        this.mPortLayout = findViewById(R.id.port_layout);
        this.mRegisterButton = findViewById(R.id.register_button);
        this.mRegisterButton.setOnClickListener(this.mRegisterButtonClickListener);
        this.mLoginButton = findViewById(R.id.login_button);
        this.mLoginButton.setOnClickListener(this.mLoginButtonClickListener);
        this.mEditor = findViewById(R.id.editor);
        this.mResendTextView = findViewById(R.id.resend_textview);
        this.mConfirmationCodeCardView = findViewById(R.id.confirmation_code_cardview);
        this.mConfirmationLayout = findViewById(R.id.confirmation_code_layout);
        this.mConfirmationTextView = findViewById(R.id.confirmation_code);

        mConfirmationTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (charSequence.length() < 6) {
                    return;
                }

                String codeString = charSequence.toString();

                int codeInteger;
                try {
                    codeInteger = Integer.parseInt(codeString);
                }
                catch (NumberFormatException nfe) {
                    return;
                }

                if (codeInteger < 100000) {
                    return;
                }

                register(codeString);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        final Intent intent = getIntent();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else if (intent != null) {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setDisplayShowHomeEnabled(false);
                ab.setDisplayHomeAsUpEnabled(false);
                ab.setTitle(R.string.title_activity_register);
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            final XmppUri uri = new XmppUri(intent.getData());
            if (xmppConnectionServiceBound) {
                processFingerprintVerification(uri, false);
            } else {
                this.pendingUri = uri;
            }
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        if (mAccount != null) {
            savedInstanceState.putString("account", mAccount.getJid().asBareJid().toString());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    protected void onBackendConnected() {
        boolean init = true;
        if (mSavedInstanceAccount != null) {
            try {
                this.mAccount = xmppConnectionService.findAccountByJid(Jid.of(mSavedInstanceAccount));
                init = false;
            } catch (IllegalArgumentException e) {
                this.mAccount = null;
            }

        } else if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
        }

        if (mAccount != null) {
            this.mUsernameMode |= mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && mAccount.isOptionSet(Account.OPTION_REGISTER);
            if (this.mAccount.getPrivateKeyAlias() != null) {
                this.mPassword.setHint(R.string.authenticate_with_certificate);
            }
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri, false);
                mPendingFingerprintVerificationUri = null;
            }
            updateAccountInformation(init);
        }


        if (mUsernameMode) {
            this.binding.phoneNumber.setHint(R.string.username_hint);
        } else {
            final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                    R.layout.simple_list_item,
                    xmppConnectionService.getKnownHosts());
            this.binding.phoneNumber.setAdapter(mKnownHostsAdapter);
        }

        if (pendingUri != null) {
            processFingerprintVerification(pendingUri, false);
            pendingUri = null;
        }

        invalidateOptionsMenu();
    }

    private String getUserModeDomain() {
        if (mAccount != null && mAccount.getJid().getDomain() != null) {
            return mAccount.getJid().getDomain();
        } else {
            return Config.DOMAIN_LOCK;
        }
    }

    private void generateSignature(Intent intent, PresenceTemplate template) {
        xmppConnectionService.getPgpEngine().generateSignature(intent, mAccount, template.getStatusMessage(), new UiCallback<String>() {
            @Override
            public void success(String signature) {
                xmppConnectionService.changeStatus(mAccount, template, signature);
            }

            @Override
            public void error(int errorCode, String object) {

            }

            @Override
            public void userInputRequried(PendingIntent pi, String object) {
                mPendingPresenceTemplate.push(template);
                try {
                    startIntentSenderForResult(pi.getIntentSender(), REQUEST_CHANGE_STATUS, null, 0, 0, 0);
                } catch (final IntentSender.SendIntentException ignored) {
                }
            }
        });
    }

    @Override
    public void alias(String alias) {
        if (alias != null) {
            xmppConnectionService.updateKeyInAccount(mAccount, alias);
        }
    }

    private void updateAccountInformation(boolean init) {
        if (init) {
            this.binding.phoneNumber.getEditableText().clear();
            if (mUsernameMode) {
                this.binding.phoneNumber.getEditableText().append(this.mAccount.getJid().getLocal());
            } else {
                this.binding.phoneNumber.getEditableText().append(this.mAccount.getJid().asBareJid().toString());
            }
            this.mPassword.getEditableText().clear();
            this.mPassword.getEditableText().append(this.mAccount.getPassword());
            this.mHostname.setText("");
            this.mHostname.getEditableText().append(this.mAccount.getHostname());
            this.mPort.setText("");
            this.mPort.getEditableText().append(String.valueOf(this.mAccount.getPort()));
            this.mNamePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);

        }

        final boolean editable = !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY);
        this.binding.phoneNumber.setEnabled(editable);
        this.binding.phoneNumber.setFocusable(editable);
        this.binding.phoneNumber.setFocusableInTouchMode(editable);


        if (mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) || !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
            this.binding.accountPasswordLayout.setPasswordVisibilityToggleEnabled(true);
        } else {
            this.binding.accountPasswordLayout.setPasswordVisibilityToggleEnabled(false);
        }

        if (this.mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
            if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(R.string.create_account);
                }
            }
        }
    }

    private void removeErrorsOnAllBut(TextInputLayout exception) {
        if (this.mAccountJidLayout != exception) {
            this.mAccountJidLayout.setErrorEnabled(false);
            this.mAccountJidLayout.setError(null);
        }
        if (this.mPasswordLayout != exception) {
            this.mPasswordLayout.setErrorEnabled(false);
            this.mPasswordLayout.setError(null);
        }
        if (this.mConfirmationLayout != exception) {
            this.mConfirmationLayout.setErrorEnabled(false);
            this.mConfirmationLayout.setError(null);
        }
        if (this.mPortLayout != exception) {
            this.mPortLayout.setErrorEnabled(false);
            this.mPortLayout.setError(null);
        }
    }

    public void onShowErrorToast(final int resId) {
        runOnUiThread(() -> Toast.makeText(RegisterActivity.this, resId, Toast.LENGTH_SHORT).show());
    }

    @SuppressLint("CheckResult")
    private void requestSms() {
        final String phoneNumber = binding.phoneNumber.getText().toString();
        mPhoneNumber = "+98" + phoneNumber;

        if (!phoneNumberIsValid(mPhoneNumber)) {
//            Toast.makeText(RegisterActivity.this, R.string.invalid_phone_number, Toast.LENGTH_SHORT).show();
            mAccountJidLayout.setError(this.getString(R.string.invalid_phone_number));
            binding.phoneNumber.requestFocus();
            removeErrorsOnAllBut(mAccountJidLayout);

            mEditor.setEnabled(true);
            mRegisterButton.setEnabled(true);
            mLoginButton.setEnabled(true);
            return;
        }

        final String password = binding.accountPassword.getText().toString();
        if (password.length() < 7) {
            mPasswordLayout.setError(this.getString(R.string.password_length));
            binding.accountPassword.requestFocus();
            removeErrorsOnAllBut(mPasswordLayout);

            mEditor.setEnabled(true);
            mRegisterButton.setEnabled(true);
            mLoginButton.setEnabled(true);
            return;
        }

        removeErrorsOnAllBut(null);

        AccountManager.getInstance().getService().requestSms(new AccountManager.SmsRequest(mPhoneNumber))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(response -> {
                    switch (response.code()) {
                        case 204:
                            smsRequestSuccessful();
                            break;
                        case 429:
                            mAccountJidLayout.setError(this.getString(R.string.too_many_requests));
                            removeErrorsOnAllBut(mAccountJidLayout);
                            smsRequestFailed();
                            break;
                        default:
                            smsRequestFailed();
                    }
                }, error -> {
                    if (error instanceof SocketException) {
                        mAccountJidLayout.setError(this.getString(R.string.failed_to_connect));
                        removeErrorsOnAllBut(mAccountJidLayout);
                    }

                    Log.d(TAG, error.getMessage());
                    smsRequestFailed();
                });
    }

    @SuppressLint("CheckResult")
    private void register(String code) {
        AccountManager.getInstance().getService()
                .register(new AccountManager.RegisterRequest(mPhoneNumber, SERVER,
                        Integer.parseInt(code), binding.accountPassword.getText().toString()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(response -> {
                    if (response.code() == 204) {
                        registerSuccessful();
                    }
                    else {
                        mConfirmationLayout.setError("Wrong code");
                        removeErrorsOnAllBut(mConfirmationLayout);
                        registerFailed();
                    }

                }, error -> {
                    if (error instanceof SocketException) {
                        mConfirmationLayout.setError(this.getString(R.string.failed_to_connect));
                        removeErrorsOnAllBut(mConfirmationLayout);
                    }

                    Log.d(TAG, error.getMessage());
                    registerFailed();
                });
    }

    Runnable resendRunnable = new Runnable() {
        @Override
        public void run() {
            mResendCounter -= 1;

            if (mResendCounter > 0) {
                mResendTextView.setText(String.valueOf(mResendCounter));
                mResendHandler.postDelayed(resendRunnable, 1000);
                return;
            }

            mResendTextView.setText(R.string.resend_code);
            mResendTextView.setEnabled(true);
        }
    };

    private void smsRequestSuccessful() {
        mConfirmationCodeCardView.setVisibility(View.VISIBLE);

        mResendCounter = 30;
        mResendTextView.setText("30");
        mResendHandler.postDelayed(resendRunnable, 1000);
        mConfirmationTextView.requestFocus();

        mRegisterButton.setText(R.string.confirm_code);
    }

    private void smsRequestFailed() {
        mEditor.setEnabled(true);
        mRegisterButton.setEnabled(true);
        mLoginButton.setEnabled(true);
        binding.phoneNumber.requestFocus();
    }

    private void registerSuccessful() {
        mConfirmationLayout.setEnabled(false);

        mAccount = new Account(Jid.of(String.format("%s@%s", mPhoneNumber, SERVER)),
                binding.accountPassword.getText().toString());
        mAccount.setPort(5222);
        mAccount.setHostname(SERVER);
//        mAccount.setHostname("192.168.1.109");
        mAccount.setOption(Account.OPTION_USETLS, true);
        mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
        mAccount.setOption(Account.OPTION_REGISTER, false);

        xmppConnectionService.createAccount(mAccount);
    }

    private void registerFailed() {
    }

    public boolean phoneNumberIsValid (String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        if (phoneNumber.length() != 13) {
            return false;
        }

        if (!phoneNumber.startsWith("+989") || !phoneNumber.matches("\\+[0-9]+")){
            return false;
        }

        return true;
    }

    public static void launch(AppCompatActivity activity) {
        Intent intent = new Intent(activity, RegisterActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(0,0);
    }
}