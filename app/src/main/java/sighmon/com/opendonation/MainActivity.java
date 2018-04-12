package sighmon.com.opendonation;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.squareup.sdk.pos.ChargeRequest;
import com.squareup.sdk.pos.PosClient;
import com.squareup.sdk.pos.PosSdk;

import static com.squareup.sdk.pos.CurrencyCode.AUD;
import static com.squareup.sdk.pos.CurrencyCode.USD;

public class MainActivity extends AppCompatActivity {

    // Square point of sale
    private PosClient posClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup Square instance
        posClient = PosSdk.createClient(this, getString(R.string.square_client_id));

        // Try a transaction
        startTransaction();
    }

    // To setup a transaction
    // TODO: make the donation amount an app setting.
    private static final int CHARGE_REQUEST_CODE = 1;
    public void startTransaction() {
        ChargeRequest request = new ChargeRequest.Builder(5_00, AUD).build();
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
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, listener)
                .show();
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
                ChargeRequest.Success success = posClient.parseChargeSuccess(data);
                String message = getString(R.string.dialog_message_success_client_transaction_id) + success.clientTransactionId;
                showDialog(getString(R.string.dialog_title_success), message, null);
            } else {
                ChargeRequest.Error error = posClient.parseChargeError(data);

                if (error.code == ChargeRequest.ErrorCode.TRANSACTION_ALREADY_IN_PROGRESS) {
                    String title = getString(R.string.dialog_title_error_already_in_progress);
                    String message = getString(R.string.dialog_message_please_complete_current_string);

                    showDialog(title, message, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            // Some errors can only be fixed by launching Point of Sale
                            // from the Home screen.
                            posClient.launchPointOfSale();
                        }
                    });
                } else {
                    showDialog(getString(R.string.dialog_title_error_colon) + error.code, error.debugDescription, null);
                }
            }
        }
    }
}
