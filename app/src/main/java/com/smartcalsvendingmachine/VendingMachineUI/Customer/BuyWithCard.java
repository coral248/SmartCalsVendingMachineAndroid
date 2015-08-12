package com.smartcalsvendingmachine.VendingMachineUI.Customer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.smartcalsvendingmachine.R;
import com.smartcalsvendingmachine.VendingMachineUI.MainActivity;
import com.smartcalsvendingmachine.Database.VendingMachineDatabase;

import java.util.ArrayList;

public class BuyWithCard extends Activity {
    private ArrayList<Card> cards;
    private int code;
    private double price;
    private int quantity;
    private EditText cardNumberText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_swipe_card);

        Intent intent = getIntent();
        code = intent.getIntExtra("code", 0);
        price = intent.getDoubleExtra("price", 0);
        quantity = intent.getIntExtra("quantity", 0);

        cardNumberText = (EditText) findViewById(R.id.card_number);
        cards = new ArrayList<>();
    }

    public void onClick(View view){
        switch(view.getId()){
            case R.id.swipe:
                int cardNumber = Integer.parseInt(cardNumberText.getText().toString());
                new BuyItemTask().execute(cardNumber);
                break;
            case R.id.back:
                finish();
                break;
            case R.id.cancel:
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                break;
        }
    }

    // show success dialog
    private void showMoreDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(BuyWithCard.this);
        builder.setTitle("Swipe another card?");
        double total = 0;
        for (Card card : cards) {
            total += card.getBalance();
        }
        builder.setMessage("Total balance: $" + total);
        builder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id){
                cardNumberText.setText("");
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                String result = "";
                for(Card card : cards){
                    result += "Card " + card.getNumber() + " remaining balance: $"
                            + card.getBalance() + "\n";
                }
                showSuccessDialog(result);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // show success dialog
    private void showSuccessDialog(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Thank you for using SmartCal Vending Machine.");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getResources().getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent intent = new Intent(BuyWithCard.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
        alertDialog.show();
    }

    // show error dialog
    private void showErrorDialog(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getResources().getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    private class BuyItemTask extends AsyncTask<Integer, Void, String> {

        private Exception ex;

        protected String doInBackground(Integer... params) {
            try {
                MainActivity.cProxy.connect(MainActivity.getDefaults("serverIP", BuyWithCard.this));
                double balance = MainActivity.cProxy.checkBalance(params[0]);
                double total = 0;
                for (Card card : cards) {
                    total += card.getBalance();
                }
                if(total + balance < price){
                    cards.add(new Card(params[0], balance));
                    return null;
                }
                cards.add(new Card(params[0], price - total));
                String result = "";
                for(Card card : cards){
                    result += "\nCard " + card.getNumber() + " remaining balance: $"
                            + MainActivity.cProxy.updateBalance(card.getNumber(), card.getBalance());
                }
                MainActivity.cProxy.disconnect();

                MainActivity.VBD.query("UPDATE " + VendingMachineDatabase.ITEMS_TABLE
                        + " SET " + VendingMachineDatabase.ITEM_QUANTITY + " = " + (quantity - 1)
                        + " WHERE " + VendingMachineDatabase.ITEM_ID + " = " + code);

                ContentValues ctx = new ContentValues();
                ctx.put(VendingMachineDatabase.SALE_ITEM, code);
                ctx.put(VendingMachineDatabase.SALE_PROFIT, price);
                ctx.put(VendingMachineDatabase.SALE_PDATE,
                        android.text.format.DateFormat.format("yyyy-MM-dd hh:mm:ss", new java.util.Date()).toString());
                MainActivity.VBD.insert(VendingMachineDatabase.SALES_TABLE, ctx);

                return "Item successfully bought!" + result;
            } catch (Exception e) {
                ex = e;
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(String result) {
            if (result != null){
                showSuccessDialog(result);
            } else {
                if(ex != null){
                    showErrorDialog("Socket error.");
                }
                showMoreDialog();
            }
        }
    }
}
