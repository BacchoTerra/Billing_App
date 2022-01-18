package com.simpleplus.billingapp

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.*
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object{
        const val TAG = "BILLING_TEST"
    }

    private lateinit var billingClient: BillingClient
    private lateinit var purchasesUpdatedListener: PurchasesUpdatedListener

    private lateinit var txtError: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var mAdapter: ProductsAdapter

    private val sessionPurchases = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtError = findViewById(R.id.txt)
        recyclerView = findViewById(R.id.recyclerView)

        initBillingClient()

    }

    private fun initBillingClient() {
        purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {

                //[purchases] is a list containing the items your just bought

                for (purchase in purchases) {
                    MainScope().launch {
                        handlePurchase(purchase)
                    }
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                Toast.makeText(this, "Cancelled by the user", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Error -> ${billingResult.debugMessage}", Toast.LENGTH_LONG)
                    .show()
            }
        }

        billingClient = BillingClient.newBuilder(applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        startConnection()
    }

    private suspend fun queryPreviousPurchases() : List<String> {
        val purchases = billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP).purchasesList

        return purchases.map {
            it.skus[0]
        }
    }

    private fun startConnection() {

        /**
         * Connect the app to the billing back-end.
         */
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                    MainScope().launch {
                        querySkuDetails()
                    }

                } else {
                    txtError.text = billingResult.debugMessage
                }
            }

            override fun onBillingServiceDisconnected() {
                startConnection()
            }
        })
    }

    /**
     * Query for the products set by an list of products ids.
     */
    suspend fun querySkuDetails() {
        val skuList = ArrayList<String>()
        skuList.add("lorem_ipsum_product_id") // Product ID from Google play console
        skuList.add("foo_product_id") // Product ID from Google play console
        skuList.add("initial_product_test") // Product ID from Google play console
        skuList.add("ada_love_lace_product_id") // Product ID from Google play console
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)

        // leverage querySkuDetails Kotlin extension function
        val skuDetailsResult = withContext(Dispatchers.IO) {
            billingClient.querySkuDetails(params.build())
        }

        skuDetailsResult.skuDetailsList?.let { listOfSkuDetails ->

            val purchasesDone = queryPreviousPurchases()
            populatePurchaseRecyclerView(listOfSkuDetails,purchasesDone)
        }
    }

    private fun populatePurchaseRecyclerView(listOfSkuDetails: List<SkuDetails>,purchasesDone: List<String>) {

        mAdapter = ProductsAdapter(listOfSkuDetails, purchasesDone)

        recyclerView.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }

    }

    /**
     * Opens the purchase bottom sheet.
     */
    private fun initPurchaseFlow(skuDetails: SkuDetails) {

        val flowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .build()

        val responseCode = billingClient.launchBillingFlow(this, flowParams).responseCode

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "initPurchaseFlow: RESPONSE OK, you should see the purchase dialog")
        } else {
            Log.i(TAG, "initPurchaseFlow: RESPONSE NOT OK, $responseCode")
        }

    }

    /**
     * Any action done on the purchase dialog are handled here.
     */
    private fun handlePurchase(purchase: Purchase) {

        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.

        //Use this logic to consume items instead of acknowledge them.
        //val consumeParams =
        //    ConsumeParams.newBuilder()
        //        .setPurchaseToken(purchase.purchaseToken)
        //       .build()
        //
        //val consumeResult = withContext(Dispatchers.IO) {
        //     billingClient.consumePurchase(consumeParams)
        // }


        /*
        * consumePurchase(consumeParams) -> Consumables items
        * acknowledgePurchase(consumeParams) -> Unique items
        */

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {

                //Use the acknowledge params to make sure that this is a unique purchase.
                //Unique purchases can't be bought more than one time.
                // https://stackoverflow.com/questions/56585626/how-to-acknowledge-in-app-purchases-in-android

                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) {

                    if (it.responseCode == BillingClient.BillingResponseCode.OK) {

                        Log.i(
                            TAG,
                            "onAcknowledge: Message -> ${it.debugMessage}/// code -> ${it.responseCode}"
                        )
                        Snackbar.make(txtError, "Purchase successfull", Snackbar.LENGTH_LONG).show()

                    } else {
                        Snackbar.make(txtError, "Ops, something went wrong..", Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    inner class ProductsAdapter(
        private val items: List<SkuDetails>,
        private val purchasesDone: List<String>
    ) :
        RecyclerView.Adapter<ProductsAdapter.ProductsViewHolder>() {

        inner class ProductsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val rootCard: MaterialCardView = itemView.findViewById(R.id.root)
            val txtTitle: TextView = itemView.findViewById(R.id.textView)
            val txtDesc: TextView = itemView.findViewById(R.id.textView2)
            val txtPrice: TextView = itemView.findViewById(R.id.textView3)
            val btnPurchase: View = itemView.findViewById(R.id.view)

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductsViewHolder {
            return ProductsViewHolder(
                layoutInflater.inflate(
                    R.layout.row_purchases,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ProductsViewHolder, position: Int) {

            val product = items[position]

            holder.txtTitle.text = product.title
            holder.txtDesc.text = product.description
            holder.txtPrice.text = product.price

            if (purchasesDone.contains(product.sku)){
                holder.rootCard.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.bought_color))
                holder.txtPrice.text = "Your already bought me!"
            }

            holder.btnPurchase.setOnClickListener {
                initPurchaseFlow(product)
            }

        }

        override fun getItemCount(): Int = items.size

    }

}