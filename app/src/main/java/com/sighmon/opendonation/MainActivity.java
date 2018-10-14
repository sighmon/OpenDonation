package com.sighmon.opendonation;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import com.squareup.sdk.pos.ChargeRequest;
import com.squareup.sdk.pos.PosClient;
import com.squareup.sdk.pos.PosSdk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static com.squareup.sdk.pos.CurrencyCode.AUD;
import static com.squareup.sdk.pos.CurrencyCode.USD;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MainActivity extends AppCompatActivity {

    // Square point of sale
    private PosClient posClient;

    // Set any global variables
    private Integer donationValue;
    private Integer transactionTimeout;
    private Integer dialogAutoDismissTimeout;
    private boolean showDialogs;

    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set background image or colour
        LinearLayout mainLinearLayout = (LinearLayout) findViewById(R.id.mainLinearLayout);
        // To set a white background
        mainLinearLayout.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        // To set an image background
//        mainLinearLayout.setBackground(getDrawable(R.drawable.ic_launcher_background));

        // Set a custom logo
        // Copy your logo over the top of app/src/main/res/drawable/custom_logo.png
        // And un-comment the next line
        // setCustomLogo();

        // Set fullscreen
        hideSystemUI();

        // Hide the title bar
        hideTitleBar();

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Setup Square instance
        posClient = PosSdk.createClient(this, getString(R.string.square_client_id));

        // Setup default donation values
        Integer minimumDonationValue = 5;
        Integer maximumDonationValue = 99;
        donationValue = 5; // currently selected value
        transactionTimeout = 4; // in seconds
        showDialogs = true; // set false to hide success dialogs
        dialogAutoDismissTimeout = 10; // in seconds

        // Setup the donation amount picker
        NumberPicker donationAmountPicker = (NumberPicker) findViewById(R.id.donationAmount);
        donationAmountPicker.setMinValue(minimumDonationValue);
        donationAmountPicker.setMaxValue(maximumDonationValue);
        donationAmountPicker.setValue(donationValue);
        donationAmountPicker.setWrapSelectorWheel(false);
        donationAmountPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        // Set a listener for changes to the donation amount spinner
        donationAmountPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                donationValue = newVal;
            }
        });

        // Watch for donate button taps
        Button submitButton = (Button) findViewById(R.id.submitButton);
        submitButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTransaction();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            // Hide system UI
            hideSystemUI();
        }
    }

    // Setup a transaction
    private static final int CHARGE_REQUEST_CODE = 1;
    public void startTransaction() {
        ChargeRequest.Builder requestBuilder = new ChargeRequest.Builder(donationValue * 100, AUD);
        // Limit to card use only
        requestBuilder.restrictTendersTo(ChargeRequest.TenderType.CARD);
        requestBuilder.autoReturn(transactionTimeout * 1000, MILLISECONDS);
        ChargeRequest request = requestBuilder.build();
        try {
            Intent intent = posClient.createChargeIntent(request);
            startActivityForResult(intent, CHARGE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            showDialog(getString(R.string.dialog_title_error), getString(R.string.dialog_message_start_transaction_error), null);
            posClient.openPointOfSalePlayStoreListing();
        }
    }

    private void showDialog(String title, String message, DialogInterface.OnClickListener listener) {
        Log.d("MainActivity", title + " " + message);
        // Show dialog if requested
        if (showDialogs) {
            final AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, listener);
            final AlertDialog alert = dialog.create();
            alert.show();

            // Set auto-dismiss
            new CountDownTimer(dialogAutoDismissTimeout * 1000, 1000) {

                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    // Dismiss alert
                    if (alert.isShowing()) {
                        alert.dismiss();
                    }
                }
            }.start();
        }
    }

    // Result from Square
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHARGE_REQUEST_CODE) {
            if (data == null) {
                showDialog(getString(R.string.dialog_title_error), getString(R.string.dialog_message_activity_error), null);
                return;
            }

            if (resultCode == Activity.RESULT_OK) {
                // Thank you dialog message
                String message = getString(R.string.dialog_message_success, donationValue);
                showDialog(getString(R.string.dialog_title_success), message, null);

                ChargeRequest.Success success = posClient.parseChargeSuccess(data);
                String debugMessage = getString(R.string.dialog_message_success_client_transaction_id) + success.clientTransactionId;
                Log.d("ChargeRequest", debugMessage);
            } else {
                ChargeRequest.Error error = posClient.parseChargeError(data);

                if (error.code == ChargeRequest.ErrorCode.TRANSACTION_ALREADY_IN_PROGRESS) {
                    String title = getString(R.string.dialog_title_error_already_in_progress);
                    String message = getString(R.string.dialog_message_please_complete_current_string);
                    Log.d("ChargeRequest", message);

                    showDialog(title, message, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            // Some errors can only be fixed by launching Point of Sale
                            // from the Home screen.
                            posClient.launchPointOfSale();
                        }
                    });
                } else if (error.code == ChargeRequest.ErrorCode.TRANSACTION_CANCELED) {
                    // TODO: cancelled page
                    String message = getString(R.string.dialog_message_transaction_cancelled);
                    Log.d("ChargeRequest", message);
                    // showDialog(getString(R.string.dialog_title_transaction_cancelled), message, null);
                } else {
                    // TODO: error page
                    Log.d("ChargeRequest", error.debugDescription);
                    showDialog(getString(R.string.dialog_title_error_colon) + error.code, error.debugDescription, null);
                }
            }
        }
    }

    private void hideSystemUI() {
        // TODO: work out how to hide system UI for intents too.
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public void hideTitleBar() {
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void setCustomLogo() {
        ImageView logo = findViewById(R.id.custom_logo);
        if (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT || getResources().getConfiguration().orientation == ORIENTATION_UNDEFINED) {
            // Set the portrait logo
            logo.setImageResource(R.drawable.custom_logo);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int marginInDips = 15;
            lp.setMargins(0, marginInDips, 0, marginInDips);
            logo.setLayoutParams(lp);
        } else {
            // Set the landscape logo
            logo.setImageResource(R.drawable.custom_logo_land);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int marginInDips = 15;
            lp.setMargins(0, marginInDips, 0, marginInDips);
            logo.setLayoutParams(lp);

            // Reduce button margins to fit the description text
            Button submitButton = findViewById(R.id.submitButton);
            marginInDips = 10;
            lp.setMargins(0, marginInDips, 0, marginInDips);
            submitButton.setLayoutParams(lp);

            // Set the landscape logo layout weight to 1 to show the landscape logo
            final LinearLayout logoLayout  = findViewById(R.id.customLogoLinearLayout);
            final LinearLayout donationLayout  = findViewById(R.id.donationInputLinearLayout);
            if (logoLayout != null) {
                LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                );
                logoLayout.setLayoutParams(param);
                donationLayout.setLayoutParams(param);
            }
        }
    }
}
